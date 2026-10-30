$ErrorActionPreference = 'Stop'

$projectDir = $PSScriptRoot
$jarPath = Join-Path $projectDir 'target/umm-oa-back-0.1.0-SNAPSHOT.jar'
$wrapper = Join-Path $projectDir 'mvnw.cmd'
$localJdk = Join-Path $env:USERPROFILE '.jdks/jdk-17.0.21'

try {
    if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/java.exe')) {
        $env:JAVA_HOME = $localJdk
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        throw 'JDK 17 was not found. Set JAVA_HOME to your JDK installation.'
    }
    if (-not (Test-Path -LiteralPath $wrapper)) {
        throw "Maven Wrapper was not found: $wrapper"
    }
    $env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH

    # Only stop Java processes launched with this project's exact JAR path.
    $backends = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
        $match = [regex]::Match([string]$_.CommandLine, '(?i)(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))')
        if ($match.Success) {
            $argument = if ($match.Groups[1].Success) { $match.Groups[1].Value } else { $match.Groups[2].Value }
            $argument.Replace('/', '\') -ieq $jarPath.Replace('/', '\')
        }
    })
    $backendIds = @($backends | ForEach-Object { $_.ProcessId })
    $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        if ($listener.OwningProcess -notin $backendIds) {
            throw "Port 8080 is used by another process (PID $($listener.OwningProcess)). Close it before starting this backend."
        }
    }

    foreach ($backend in $backends) {
        Write-Host "Stopping the previous UMM OA backend (PID $($backend.ProcessId))..."
        $runningProcess = Get-Process -Id $backend.ProcessId -ErrorAction SilentlyContinue
        if ($runningProcess) {
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

    Push-Location -LiteralPath $projectDir
    try {
        Write-Host 'Building a fresh backend JAR from the current local source files...'
        # Use the wrapper's PowerShell resolver without nesting its batch FOR command.
        $resolverCode = '$ProgressPreference = ''SilentlyContinue''; $scriptDir = ''' + $projectDir.Replace("'", "''") + '''; $script = ''mvnw.cmd''; & ([scriptblock]::Create((Get-Content -LiteralPath (Join-Path $scriptDir $script) -Raw)))'
        $encodedResolver = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($resolverCode))
        $resolverOutput = @(& (Join-Path $PSHOME 'powershell.exe') -NoLogo -NoProfile -EncodedCommand $encodedResolver)
        $mavenLine = $resolverOutput | Where-Object { $_ -like 'MVN_CMD=*' } | Select-Object -Last 1
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
            throw 'Build failed. The backend was not started.'
        }
        if (-not (Test-Path -LiteralPath $jarPath)) {
            throw "Build completed but the backend JAR was not found: $jarPath"
        }
        Write-Host 'Starting the rebuilt UMM OA backend on http://127.0.0.1:8080'
        Write-Host 'Press Ctrl+C or close this window to stop the backend.'
        & (Join-Path $env:JAVA_HOME 'bin/java.exe') '-jar' $jarPath
        $backendExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    exit $backendExit
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
