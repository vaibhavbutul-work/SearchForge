# scripts/load-env.ps1
# Loads key-value pairs from .env into the current PowerShell session environment.
# Note: Run with dot-sourcing so variables persist in your active session:
#   . .\scripts\load-env.ps1

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $scriptDir "..\.env"

if (-not (Test-Path $envFile)) {
    Write-Warning ".env file not found at $envFile. Copy .env.example to .env first."
    return
}

# Ensure WindowsPowerShell is in PATH for mvnw.cmd if running under PowerShell 7
$powershellCmd = Get-Command "powershell" -ErrorAction SilentlyContinue
if (-not $powershellCmd) {
    $winPS = "C:\Windows\System32\WindowsPowerShell\v1.0"
    if (Test-Path $winPS) {
        $env:Path = "$winPS;$env:Path"
    }
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    # Skip empty lines and comment lines starting with #
    if ($line -and -not ($line.StartsWith("#"))) {
        $parts = $line -split "=", 2
        if ($parts.Length -eq 2) {
            $key = $parts[0].Trim()
            $val = $parts[1].Trim()
            # Set variable in the current Process environment ($env:KEY)
            [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            Write-Host "  [env] $key loaded" -ForegroundColor Cyan
        }
    }
}

Write-Host "Environment variables loaded successfully from .env!" -ForegroundColor Green
