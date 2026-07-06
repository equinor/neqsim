# install.ps1 - Bootstrap the NeqSim devtools package on Windows.
#
# Fixes the common "pip not available / python not available" errors by
# locating a working Python interpreter (py launcher, python3, or python,
# skipping the Microsoft Store stub) and running `python -m pip` for you.
#
# Usage (from the repo root, in PowerShell):
#     .\install.ps1              # install neqsim-dev-setup (editable)
#     .\install.ps1 pdf          # also install the optional [pdf] extras
#     .\install.ps1 ocr          # also install the optional [ocr] extras
#
# No need to activate a venv first, and no need to have `pip` on PATH.

param(
    [string]$Extras = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$Devtools = Join-Path $RepoRoot "devtools"

function Test-Python {
    param([string[]]$Command)
    try {
        # A real interpreter prints its version; the Microsoft Store alias
        # stub prints nothing (and often opens the Store), so require output.
        $out = & $Command[0] $Command[1..($Command.Length - 1)] `
            "-c" "import sys; print(sys.version.split()[0])" 2>$null
        if ($LASTEXITCODE -eq 0 -and $out) {
            return $true
        }
    } catch {
        return $false
    }
    return $false
}

Write-Host "NeqSim devtools installer" -ForegroundColor Cyan
Write-Host "Locating a working Python interpreter..."

# Candidate launchers, most reliable first. The `py` launcher is preferred on
# Windows because it works even when `python` is the non-functional Store stub.
$candidates = @(
    @("py", "-3"),
    @("python3"),
    @("python")
)

$python = $null
foreach ($cand in $candidates) {
    if (Test-Python -Command $cand) {
        $python = $cand
        break
    }
}

if ($null -eq $python) {
    Write-Host ""
    Write-Host "ERROR: No working Python interpreter was found." -ForegroundColor Red
    Write-Host ""
    Write-Host "Fix: install Python 3.8+ from one of:" -ForegroundColor Yellow
    Write-Host "  * https://www.python.org/downloads/  (check 'Add python.exe to PATH')"
    Write-Host "  * winget install Python.Python.3.12"
    Write-Host ""
    Write-Host "If 'python' opens the Microsoft Store, disable the alias under:" -ForegroundColor Yellow
    Write-Host "  Settings > Apps > Advanced app settings > App execution aliases"
    Write-Host "  (turn off the python.exe / python3.exe entries), then re-run this script."
    exit 1
}

$pythonDisplay = ($python -join " ")
$version = & $python[0] $python[1..($python.Length - 1)] "-c" "import sys; print(sys.version.split()[0])"
Write-Host "Using Python $version via '$pythonDisplay'" -ForegroundColor Green

# Ensure pip is available for this interpreter.
& $python[0] $python[1..($python.Length - 1)] "-m" "pip" "--version" *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "pip not found for this interpreter - bootstrapping with ensurepip..."
    & $python[0] $python[1..($python.Length - 1)] "-m" "ensurepip" "--upgrade"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: could not bootstrap pip. Re-run the Python installer and enable pip." -ForegroundColor Red
        exit 1
    }
}

# Build the install target (support optional extras like devtools[pdf]).
$target = $Devtools
if ($Extras) {
    $target = "$Devtools[$Extras]"
}

Write-Host "Installing (editable): $target"
& $python[0] $python[1..($python.Length - 1)] "-m" "pip" "install" "-e" "$target"
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: pip install failed. See the output above for details." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Done. Verify with:" -ForegroundColor Green
Write-Host "  $pythonDisplay -m neqsim_cli --help"
Write-Host "If the 'neqsim' command is not found, see devtools/README.md > Troubleshooting."
