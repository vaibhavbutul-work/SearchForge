# ==============================================================================
# SearchForge End-to-End Smoke Test Script (PowerShell)
# Usage: .\scripts\smoke-test.ps1 -BaseUrl "http://localhost" -AdminKey "<key>"
# ==============================================================================
param(
    [string]$BaseUrl = "http://localhost",
    [string]$AdminKey = $env:SEARCHFORGE_ADMIN_API_KEY
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0

function Assert-Check {
    param([string]$Name, [scriptblock]$Check)
    try {
        & $Check
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:passed++
    } catch {
        Write-Host "[FAIL] $Name -> $($_.Exception.Message)" -ForegroundColor Red
        $script:failed++
    }
}

Assert-Check "Frontend SPA Availability (GET /)" {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 10
    if ($resp.StatusCode -ne 200) { throw "Expected 200, got $($resp.StatusCode)" }
    if ($resp.Content -notmatch "SearchForge") { throw "Unexpected HTML content" }
}

Assert-Check "Public Health Endpoint (GET /api/v1/health)" {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 10
    if ($resp.StatusCode -ne 200) { throw "Expected 200, got $($resp.StatusCode)" }
    if (-not $resp.Headers["X-Request-Id"]) { throw "Missing X-Request-Id header" }
    $json = $resp.Content | ConvertFrom-Json
    if ($json.status -ne "UP") { throw "Health status is $($json.status)" }
}

Assert-Check "Actuator Readiness Probe (GET /actuator/health/readiness)" {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/actuator/health/readiness" -UseBasicParsing -TimeoutSec 10
    if ($resp.StatusCode -ne 200) { throw "Expected 200, got $($resp.StatusCode)" }
}

Assert-Check "Rate Limit Headers on Search Endpoint" {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/v1/search?q=searchforge&mode=lexical&page=0&size=5" -UseBasicParsing -TimeoutSec 10
    if ($resp.StatusCode -ne 200) { throw "Expected 200, got $($resp.StatusCode)" }
    if (-not $resp.Headers["X-RateLimit-Limit"]) { throw "Missing X-RateLimit-Limit" }
    if (-not $resp.Headers["X-RateLimit-Remaining"]) { throw "Missing X-RateLimit-Remaining" }
}

Assert-Check "Semantic & Hybrid Search Modes" {
    foreach ($m in @("semantic", "hybrid")) {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/api/v1/search?q=inverted+index&mode=$m&page=0&size=5" -UseBasicParsing -TimeoutSec 15
        if ($resp.StatusCode -ne 200) { throw "Mode $m returned $($resp.StatusCode)" }
    }
}

Assert-Check "Admin Auth Guard Rejects Missing API Key (401)" {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/lifecycle/metrics" -UseBasicParsing -TimeoutSec 10 | Out-Null
        throw "Expected 401 Unauthorized but succeeded"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -ne 401) { throw "Expected 401, got $code" }
    }
}

if ($AdminKey) {
    Assert-Check "Authenticated Admin Lifecycle Metrics (200)" {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/api/v1/lifecycle/metrics" -Headers @{"X-API-KEY"=$AdminKey} -UseBasicParsing -TimeoutSec 10
        if ($resp.StatusCode -ne 200) { throw "Expected 200, got $($resp.StatusCode)" }
    }
} else {
    Write-Host "[SKIP] Authenticated Admin Check (SEARCHFORGE_ADMIN_API_KEY not provided)" -ForegroundColor Yellow
}

Write-Host "`nSmoke Test Summary: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
