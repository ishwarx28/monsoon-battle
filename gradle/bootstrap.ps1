# Source-only Windows bootstrap; respects the machine's PowerShell execution policy.
$ErrorActionPreference = 'Stop'
$props = @{}
Get-Content "$PSScriptRoot/wrapper/gradle-wrapper.properties" | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $props[$matches[1]] = $matches[2] }
}
$url = $props['distributionUrl'].Replace('\:', ':')
if ($url -notmatch '/gradle-([0-9.]+)-bin.zip$') { throw 'Invalid Gradle distribution metadata' }
$version = $matches[1]
$sha = $props['distributionSha256Sum']
if ($sha -notmatch '^[a-fA-F0-9]{64}$') { throw 'Missing Gradle checksum' }
$homeDir = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }
$cached = Get-ChildItem "$homeDir/wrapper/dists/gradle-$version-*/*/gradle-$version/bin/gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($cached) { & $cached.FullName @args; exit $LASTEXITCODE }
$cache = Join-Path $homeDir "monsoon-bootstrap/$version"
$exe = Join-Path $cache "gradle-$version/bin/gradle.bat"
if (!(Test-Path $exe)) {
    $mutex = New-Object System.Threading.Mutex($false, "MonsoonGradle_$version")
    if (!$mutex.WaitOne(300000)) { throw 'Another Gradle bootstrap is still running' }
    $tmp = Join-Path ([IO.Path]::GetTempPath()) ([Guid]::NewGuid().ToString())
    try {
        if (!(Test-Path $exe)) {
            New-Item -ItemType Directory -Path $tmp -Force | Out-Null
            Invoke-WebRequest -Uri $url -OutFile "$tmp/gradle.zip" -UseBasicParsing
            if ((Get-FileHash "$tmp/gradle.zip" -Algorithm SHA256).Hash.ToLower() -ne $sha.ToLower()) { throw 'Gradle checksum mismatch' }
            Expand-Archive "$tmp/gradle.zip" "$tmp/unpacked"
            New-Item -ItemType Directory -Path $cache -Force | Out-Null
            Move-Item "$tmp/unpacked/gradle-$version" $cache
        }
    } finally {
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
        $mutex.ReleaseMutex(); $mutex.Dispose()
    }
}
& $exe @args
exit $LASTEXITCODE
