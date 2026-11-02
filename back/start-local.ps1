$ErrorActionPreference = 'Stop'

$projectDir = $PSScriptRoot
$jarPath = Join-Path $projectDir 'target/umm-oa-back-0.1.0-SNAPSHOT.jar'
$wrapper = Join-Path $projectDir 'mvnw.cmd'
$port = 8080

try {
    if ($env:SERVER_PORT) {
        if (-not [int]::TryParse($env:SERVER_PORT, [ref]$port) -or $port -lt 1 -or $port -gt 65535) {
            throw 'SERVER_PORT must be a number between 1 and 65535.'
        }
    }
    if (-not (Test-Path -LiteralPath $wrapper)) {
        throw "Maven Wrapper was not found: $wrapper"
    }
    $jdkCandidates = @(
        $env:JAVA_HOME
        (Join-Path $env:USERPROFILE '.jdks/jdk-17.0.21')
        (Get-ChildItem -Path "$env:LOCALAPPDATA/Programs/Microsoft/jdk-17*", "$env:ProgramFiles/Eclipse Adoptium/jdk-17*", "$env:ProgramFiles/Java/jdk-17*" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object { $_.FullName })
    )
    $javaOnPath = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($javaOnPath) {
        $jdkCandidates += Split-Path (Split-Path $javaOnPath.Source -Parent) -Parent
    }
    $jdk = $jdkCandidates | Where-Object {
        $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe')) -and (Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe'))
    } | Select-Object -First 1
    if (-not $jdk) {
        throw 'JDK 17 or newer was not found. Set JAVA_HOME to your JDK installation.'
    }
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH

    # Recognize this project's exact JAR, and the previous desktop spring-boot:run launcher.
    # Never stop another application merely because it owns the configured port.
    $javaProcesses = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")
    $projectPattern = [regex]::Escape($projectDir.TrimEnd('\').Replace('/', '\'))
    $mavenParents = @($javaProcesses | Where-Object {
        $_.CommandLine -match ('(?i)(?:^|\s)"?-Dmaven\.multiModuleProjectDirectory="?' + $projectPattern + '[\\/]?"?(?:\s|$)') -and
        $_.CommandLine -match '(?:^|\s)spring-boot:run(?:\s|$)'
    })
    $mavenIds = @($mavenParents | ForEach-Object { $_.ProcessId })
    $backends = @($javaProcesses | Where-Object {
        $match = [regex]::Match([string]$_.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))')
        $isProjectJar = $false
        if ($match.Success) {
            $argument = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
            $isProjectJar = $argument.Replace('/', '\') -ieq $jarPath.Replace('/', '\')
        }
        $isProjectJar -or ($_.ParentProcessId -in $mavenIds -and $_.CommandLine -match '(?:^|\s)com\.seal\.oa\.UmmOaApplication(?:\s|$)')
    })
    $backendIds = @($backends | ForEach-Object { $_.ProcessId })
    $listeners = @(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        if ($listener.OwningProcess -notin $backendIds) {
            throw "Port $port is used by another application (PID $($listener.OwningProcess)). Close that application before starting this backend."
        }
    }

    Write-Host "[INFO] Project: $projectDir"
    Write-Host "[INFO] JDK: $jdk"
    Write-Host "[INFO] URL: http://127.0.0.1:$port"
    # Release the existing JAR before clean/package, since Windows locks running JAR files.
    foreach ($backend in @($backends) + @($mavenParents)) {
        $runningProcess = Get-Process -Id $backend.ProcessId -ErrorAction SilentlyContinue
        if ($runningProcess) {
            Write-Host "[INFO] Stopping the previous UMM OA backend (PID $($backend.ProcessId))..."
            try {
                Stop-Process -InputObject $runningProcess -ErrorAction Stop
                if (-not $runningProcess.WaitForExit(30000)) {
                    throw "The previous backend did not stop (PID $($backend.ProcessId))."
                }
            } finally {
                $runningProcess.Dispose()
            }
        }
    }
    if (@(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue).Count -gt 0) {
        throw "Port $port is still in use. The backend was not started."
    }

    # Relative data/uploads paths must always resolve inside back, even from the desktop.
    Push-Location -LiteralPath $projectDir
    try {
        Write-Host '[INFO] Building a fresh backend JAR: clean package (tests skipped)...'
        # Use the wrapper's PowerShell resolver without nesting its batch FOR command.
        $resolverCode = '$ErrorActionPreference = ''Stop''; $ProgressPreference = ''SilentlyContinue''; $scriptDir = ''' + $projectDir.Replace("'", "''") + '''; $script = ''mvnw.cmd''; & ([scriptblock]::Create((Get-Content -LiteralPath (Join-Path $scriptDir $script) -Raw)))'
        $encodedResolver = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($resolverCode))
        $resolverOutput = @(& (Join-Path $PSHOME 'powershell.exe') -NoLogo -NoProfile -EncodedCommand $encodedResolver)
        $mavenLine = $resolverOutput | Where-Object { $_ -like 'MVN_CMD=*' } | Select-Object -Last 1
        # The hybrid wrapper uses exit $?; PowerShell converts successful $true to exit code 1.
        # Validate its returned executable here; the actual build exit code is checked below.
        if (-not $mavenLine) {
            throw 'Maven Wrapper did not return a Maven executable.'
        }
        $mavenCommand = $mavenLine.Substring('MVN_CMD='.Length)
        if (-not (Test-Path -LiteralPath $mavenCommand)) {
            throw "Maven executable was not found: $mavenCommand"
        }
        $build = Start-Process -FilePath $env:ComSpec -ArgumentList @(
            '/d', '/e:on', '/s', '/c', ('""' + $mavenCommand + '" -B -DskipTests clean package"')
        ) -WorkingDirectory $projectDir -NoNewWindow -Wait -PassThru
        if ($build.ExitCode -ne 0) {
            throw 'Build failed. No old JAR will be started. Fix the error above and run start-local.bat again.'
        }
        if (-not (Test-Path -LiteralPath $jarPath)) {
            throw "Build completed but the backend JAR was not found: $jarPath"
        }
        $jar = Get-Item -LiteralPath $jarPath
        if ($jar.Length -eq 0) {
            throw "The rebuilt JAR is empty: $jarPath"
        }
        Write-Host "[INFO] JAR: $($jar.FullName)"
        Write-Host "[INFO] Built: $($jar.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))"
        Write-Host "[INFO] Size: $($jar.Length) bytes"
        if (-not $env:DB_URL -and -not $env:SPRING_PROFILES_ACTIVE) {
            Write-Host "[INFO] Local database: $(Join-Path $projectDir 'data/umm_oa.mv.db')"
        }
        Write-Host '[INFO] Starting the freshly rebuilt backend. Wait for the Spring Boot Started message.'
        Write-Host '[INFO] Press Ctrl+C or close this window to stop the backend.'
        & (Join-Path $jdk 'bin/java.exe') '-Dfile.encoding=UTF-8' '-jar' $jarPath "--server.port=$port"
        $backendExit = $LASTEXITCODE
        if ($backendExit -ne 0) {
            throw "Backend exited with code $backendExit. See the error above."
        }
    } finally {
        Pop-Location
    }
    exit $backendExit
} catch {
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
