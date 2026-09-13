# =============================================================================
#  PRAGATI — one-click demo starter (Windows)
#  AI Smart Allocation for the PM Internship Scheme  (SIH25033)
#
#  What this does:
#    1. Checks prerequisites (Java 17, Python 3.10+, optionally Node.js)
#    2. Installs the AI service dependencies (first run only)
#    3. Builds the frontend if a build is not already present
#    4. Starts the AI service (port 8000) and the backend (port 8080)
#
#  Then open:   http://localhost:8080
#  Sign in with any demonstration role (credentials are pre-filled).
#
#  Stop everything by closing the two console windows (or press Ctrl+C here).
# =============================================================================

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host ""
Write-Host "  PRAGATI — Smart Internship Allocation" -ForegroundColor Cyan
Write-Host "  Starting the demonstration environment..." -ForegroundColor Cyan
Write-Host ""

function Test-Cmd($name) {
  try { $null = Get-Command $name -ErrorAction Stop; return $true } catch { return $false }
}

# --- 1. Prerequisites ---------------------------------------------------------
$haveJava = Test-Cmd "java"
if (-not $haveJava) {
  Write-Host "  [!] Java 17 (JDK) was not found." -ForegroundColor Yellow
  Write-Host "      Install JDK 17, e.g.:  winget install EclipseAdoptium.Temurin.17.JDK" -ForegroundColor Yellow
  Write-Host "      Then re-run this script." -ForegroundColor Yellow
  exit 1
}
$javaVer = & java -version 2>&1 | Select-Object -First 1
Write-Host "  [ok] $javaVer" -ForegroundColor Green

$havePy = Test-Cmd "python"
if (-not $havePy) {
  Write-Host "  [!] Python 3.10+ was not found. Install with:  winget install Python.Python.3.11" -ForegroundColor Yellow
  exit 1
}
Write-Host "  [ok] $(& python --version)" -ForegroundColor Green

# --- 2. AI service dependencies (first run only) -----------------------------
$aiDir = Join-Path $root "ai-service"
$venv = Join-Path $aiDir ".venv\Scripts\python.exe"
if (-not (Test-Path $venv)) {
  Write-Host "  ... creating Python environment for the AI service (first run)..." -ForegroundColor DarkGray
  & python -m venv (Join-Path $aiDir ".venv")
}
$installed = & $venv -c "import fastapi, ortools" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "  ... installing AI service dependencies (first run, may take a minute)..." -ForegroundColor DarkGray
  & $venv -m pip install -q -r (Join-Path $aiDir "requirements.txt")
}
Write-Host "  [ok] AI service environment ready" -ForegroundColor Green

# --- 3. Frontend build (only if missing) -------------------------------------
$dist = Join-Path $root "backend\frontend-dist"
if (-not (Test-Path (Join-Path $dist "index.html"))) {
  if (Test-Cmd "npm") {
    Write-Host "  ... building the frontend (first run, may take a minute)..." -ForegroundColor DarkGray
    Push-Location (Join-Path $root "frontend")
    & npm install
    & npm run build
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    Copy-Item -Recurse -Force (Join-Path (Join-Path $root "frontend") "dist\*") $dist
    Pop-Location
    Write-Host "  [ok] frontend built" -ForegroundColor Green
  } else {
    Write-Host "  [!] No frontend build found and Node.js/npm is not installed." -ForegroundColor Yellow
    Write-Host "      Install Node 18+ (winget install OpenJS.NodeJS.LTS) and re-run, or run the backend without the UI." -ForegroundColor Yellow
  }
} else {
  Write-Host "  [ok] frontend build found" -ForegroundColor Green
}

# --- 4. Start services --------------------------------------------------------
Write-Host ""
Write-Host "  Starting the AI service on http://localhost:8000 ..." -ForegroundColor Cyan
Start-Process -WindowStyle Normal -FilePath $venv -ArgumentList "-m","uvicorn","main:app","--host","127.0.0.1","--port","8000" -WorkingDirectory $aiDir

# Wait until the AI service answers (up to ~20 s).
$aiUp = $false
for ($i = 0; $i -lt 40; $i++) {
  try { $null = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 1; $aiUp = $true; break } catch { Start-Sleep -Milliseconds 500 }
}
if ($aiUp) { Write-Host "  [ok] AI service is up" -ForegroundColor Green }
else { Write-Host "  [!] AI service did not answer yet — allocation will run in deterministic" -ForegroundColor Yellow
       Write-Host "      fallback mode and resume extraction stays unavailable." -ForegroundColor Yellow }

# Backend: prefer the prebuilt jar (fastest, most reproducible); build only if missing.
$jar = Join-Path $root "backend\target\pragati-backend.jar"
Push-Location (Join-Path $root "backend")
if (Test-Path $jar) {
  Write-Host "  [ok] backend jar found — starting it directly" -ForegroundColor Green
  & java -jar "target\pragati-backend.jar"
} elseif (Test-Cmd "mvn") {
  Write-Host "  ... no jar yet — building the backend with Maven (first run only)..." -ForegroundColor DarkGray
  & mvn -q -B package -DskipTests
  if ($LASTEXITCODE -ne 0) { throw "Maven build failed — see the log above." }
  & java -jar "target\pragati-backend.jar"
} else {
  Write-Host "  [!] No backend jar and Maven is not installed." -ForegroundColor Yellow
  Write-Host "      Install Maven (winget install Apache.Maven) and re-run." -ForegroundColor Yellow
  Pop-Location
  exit 1
}
Pop-Location

Write-Host ""
Write-Host "  Done. If the backend started, open  http://localhost:8080  in your browser." -ForegroundColor Cyan
