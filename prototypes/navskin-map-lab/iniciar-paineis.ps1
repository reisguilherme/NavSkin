$ErrorActionPreference = 'Stop'
$taskProject = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$taskUrl = 'http://127.0.0.1:4176'
$taskHealth = $null
try { $taskHealth = Invoke-RestMethod "$taskUrl/health" -TimeoutSec 2 } catch {}
if ($taskHealth -and $taskHealth.app -ne 'navskin-map-lab') { throw 'A porta 4176 esta sendo usada por outro aplicativo.' }
if (-not $taskHealth) {
    $taskNode = (Get-Command node.exe -ErrorAction Stop).Source
    $taskRuntime = Join-Path $taskProject '.runtime'
    New-Item -ItemType Directory -Path $taskRuntime -Force | Out-Null
    $taskServer = Join-Path $PSScriptRoot 'server.mjs'
    $taskProcess = Start-Process -FilePath $taskNode -ArgumentList @('"' + $taskServer + '"') -WorkingDirectory $taskProject -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskRuntime 'map-lab-server.log') -RedirectStandardError (Join-Path $taskRuntime 'map-lab-server-error.log') -PassThru
    for ($taskAttempt = 0; $taskAttempt -lt 20; $taskAttempt++) {
        Start-Sleep -Milliseconds 250
        try { $taskHealth = Invoke-RestMethod "$taskUrl/health" -TimeoutSec 1; if ($taskHealth.app -eq 'navskin-map-lab') { break } } catch {}
        if ($taskProcess.HasExited) { break }
    }
    if ($taskHealth.app -ne 'navskin-map-lab') { throw 'Nao foi possivel iniciar o painel. Consulte .runtime/map-lab-server-error.log.' }
}
Start-Process "$taskUrl/"
Write-Host "Paineis prontos em $taskUrl/"
