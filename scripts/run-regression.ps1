# ==============================================================================
# SearchForge Automated End-to-End Regression Suite Runner (PowerShell)
# Usage: .\scripts\run-regression.ps1 [-IncludeLiveHttp]
# ==============================================================================
param(
    [switch]$IncludeLiveHttp = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=== [1/3] Running Backend Integration & Regression Suites (@Tag('integration')) ===" -ForegroundColor Cyan
Push-Location backend
try {
    .\mvnw.cmd test -Dgroups=integration --batch-mode --no-transfer-progress
    if ($LASTEXITCODE -ne 0) { throw "Backend integration regression failed" }
} finally {
    Pop-Location
}

Write-Host "`n=== [2/3] Running Frontend Unit Suite ===" -ForegroundColor Cyan
Push-Location frontend
try {
    npm test
    if ($LASTEXITCODE -ne 0) { throw "Frontend unit tests failed" }

    if ($IncludeLiveHttp) {
        Write-Host "`n=== [3/3] Running Frontend-Backend Live HTTP Integration Suite ===" -ForegroundColor Cyan
        npm run test:integration
        if ($LASTEXITCODE -ne 0) { throw "Frontend-Backend live integration failed" }
    } else {
        Write-Host "`n=== [3/3] Skipping Live HTTP Integration (pass -IncludeLiveHttp when stack is up) ===" -ForegroundColor Yellow
    }
} finally {
    Pop-Location
}

Write-Host "`nSearchForge Regression Suite PASSED." -ForegroundColor Green
