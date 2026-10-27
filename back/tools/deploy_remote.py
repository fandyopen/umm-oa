from __future__ import annotations

import os
import pathlib
import sys
import tarfile
import tempfile
import time

import paramiko


HOST = os.environ.get("UMM_OA_SSH_HOST", "119.45.125.62")
PORT = int(os.environ.get("UMM_OA_SSH_PORT", "22"))
USER = os.environ.get("UMM_OA_SSH_USER", "root")
PASSWORD = os.environ.get("UMM_OA_SSH_PASSWORD")
PROJECT_DIR = pathlib.Path(__file__).resolve().parents[2]
BACK_DIR = PROJECT_DIR / "back"
DIST_DIR = PROJECT_DIR / "dist"


def connect() -> paramiko.SSHClient:
    if not PASSWORD:
        raise RuntimeError("UMM_OA_SSH_PASSWORD is required")
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        HOST,
        port=PORT,
        username=USER,
        password=PASSWORD,
        timeout=20,
        banner_timeout=20,
        auth_timeout=20,
    )
    return client


def run(client: paramiko.SSHClient, command: str, timeout: int = 1800) -> None:
    print(f"+ {command}", flush=True)
    _, stdout, stderr = client.exec_command(command, timeout=timeout)
    status = stdout.channel.recv_exit_status()
    output = stdout.read().decode("utf-8", errors="replace")
    error = stderr.read().decode("utf-8", errors="replace")
    if output:
        print(output, end="", flush=True)
    if error:
        print(error, end="", flush=True)
    if status != 0:
        raise RuntimeError(f"Remote command failed with status {status}: {command}")


def archive_backend(target: pathlib.Path) -> None:
    def include(info: tarfile.TarInfo) -> tarfile.TarInfo | None:
        parts = pathlib.PurePosixPath(info.name).parts
        if any(part in {"target", ".git", "data"} for part in parts):
            return None
        if len(parts) >= 2 and parts[1] in {"docs", "tools"}:
            return None
        if parts[:3] == ("back", "db", "source"):
            return None
        if parts[:4] == ("back", "db", "mysql", "source"):
            return None
        return info

    with tarfile.open(target, "w:gz") as archive:
        archive.add(BACK_DIR, arcname="back", filter=include)


def archive_frontend(target: pathlib.Path) -> None:
    with tarfile.open(target, "w:gz") as archive:
        archive.add(DIST_DIR, arcname="dist")


def main() -> int:
    if len(sys.argv) > 1:
        client = connect()
        try:
            run(client, " ".join(sys.argv[1:]), timeout=600)
            return 0
        finally:
            client.close()

    if not DIST_DIR.exists():
        raise RuntimeError("dist does not exist; run npm run build first")

    timestamp = time.strftime("%Y%m%d%H%M%S")
    remote_root = f"/tmp/umm-oa-deploy-{timestamp}"
    remote_backend = f"{remote_root}/umm-oa-back.tar.gz"
    remote_frontend = f"{remote_root}/umm-oa-dist.tar.gz"

    client = connect()

    try:
        with tempfile.TemporaryDirectory(prefix="umm-oa-deploy-") as temporary:
            temporary_dir = pathlib.Path(temporary)
            backend_archive = temporary_dir / "umm-oa-back.tar.gz"
            frontend_archive = temporary_dir / "umm-oa-dist.tar.gz"
            print("Creating deployment archives", flush=True)
            archive_backend(backend_archive)
            archive_frontend(frontend_archive)

            run(client, f"mkdir -p {remote_root}")
            sftp = client.open_sftp()
            try:
                print("Uploading backend source", flush=True)
                sftp.put(str(backend_archive), remote_backend)
                print("Uploading frontend build", flush=True)
                sftp.put(str(frontend_archive), remote_frontend)
            finally:
                sftp.close()

        run(client, f"tar -xzf {remote_backend} -C {remote_root}")
        run(client, f"tar -xzf {remote_frontend} -C {remote_root}")
        run(
            client,
            f"cd {remote_root}/back && chmod +x mvnw && "
            "JAVA_HOME=/data/runtime/jdk-17 "
            "PATH=/data/runtime/jdk-17/bin:$PATH "
            "./mvnw -q clean verify",
            timeout=1800,
        )

        live_dir = "/data/www/umm-oa"
        live_back = f"{live_dir}/back"
        backup_jar = f"{live_back}/umm-oa-back.jar.previous"
        new_jar = f"{remote_root}/back/target/umm-oa-back.jar"
        h2_jar_command = (
            "H2_JAR=$(find /root/.m2/repository/com/h2database/h2 "
            "-name 'h2-*.jar' | sort | tail -1)"
        )
        h2_url = (
            "jdbc:h2:file:/data/www/umm-oa/back/data/umm_oa;"
            "MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
        )

        run(client, "systemctl stop umm-oa-back")
        run(client, f"cp -a {live_back}/umm-oa-back.jar {backup_jar}")
        run(client, f"cp -a {new_jar} {live_back}/umm-oa-back.jar")
        run(
            client,
            f"{h2_jar_command} && "
            f"/data/runtime/jdk-17/bin/java -cp \"$H2_JAR\" org.h2.tools.RunScript "
            f"-url '{h2_url}' -user sa -script {remote_root}/back/db/h2/capability.sql",
        )
        run(
            client,
            f"{h2_jar_command} && "
            f"/data/runtime/jdk-17/bin/java -cp \"$H2_JAR\" org.h2.tools.RunScript "
            f"-url '{h2_url}' -user sa -script {remote_root}/back/db/h2/module.sql",
        )

        for directory in ("src", "db"):
            run(
                client,
                f"mkdir -p {live_back}/{directory} && "
                f"cp -a {remote_root}/back/{directory}/. {live_back}/{directory}/",
            )
        run(client, f"cp -a {remote_root}/back/pom.xml {live_back}/pom.xml")
        run(
            client,
            f"if [ -d {live_dir}/dist ]; then "
            f"mv {live_dir}/dist {live_dir}/dist.previous-{timestamp}; fi",
        )
        run(client, f"cp -a {remote_root}/dist {live_dir}/dist")
        run(client, f"chown -R www:www {live_dir}/dist {live_back}/data")
        run(client, "systemctl start umm-oa-back")
        run(
            client,
            "for i in $(seq 1 60); do "
            "if curl -fsS http://127.0.0.1:8080/captchaImage >/dev/null; then exit 0; fi; "
            "sleep 2; done; "
            "systemctl status umm-oa-back --no-pager; exit 1",
            timeout=180,
        )
        run(client, "systemctl status umm-oa-back --no-pager")
        print(f"Deployment complete: {remote_root}", flush=True)
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
