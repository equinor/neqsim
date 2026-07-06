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
#     .\install.ps1 -Uv          # use the fast 'uv' package manager instead of pip
#     .\install.ps1 pdf -Uv      # extras + uv
#
# No need to activate a venv first, and no need to have `pip` on PATH.

param(
    [string]$Extras = "",
    [switch]$Uv
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$Devtools = Join-Path $RepoRoot "devtools"

# Return the launcher's fixed arguments (everything after the executable),
# safely handling single-element commands (avoids PowerShell's `1..0`
# descending-range footgun that would inject a $null and duplicate the exe).
function Get-PyArgs {
    param([string[]]$Command)
    if ($Command.Length -gt 1) {
        return $Command[1..($Command.Length - 1)]
    }
    return @()
}

function Test-Python {
    param([string[]]$Command)
    try {
        $rest = Get-PyArgs $Command
        # A real interpreter prints its version; the Microsoft Store alias
        # stub prints nothing (and often opens the Store), so require output.
        $out = & $Command[0] @rest "-c" "import sys; print(sys.version.split()[0])" 2>$null
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

# Candidate launchers, most preferred first:
#   1. An active virtual environment (VIRTUAL_ENV) - install where the user expects.
#   2. `python` / `python3` - these respect an active venv (the Store stub is
#      filtered out by Test-Python because it prints no version to stdout).
#   3. `py -3` last resort - the launcher IGNORES an active venv, so it must not
#      win when a venv is active (otherwise we install into the system Python).
$candidates = @()
if ($env:VIRTUAL_ENV) {
    $venvPy = Join-Path $env:VIRTUAL_ENV "Scripts\python.exe"
    if (Test-Path $venvPy) {
        $candidates += , @($venvPy)
    }
}
$candidates += , @("python")
$candidates += , @("python3")
$candidates += , @("py", "-3")

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

$pyArgs = Get-PyArgs $python
$pythonDisplay = ($python -join " ")
$version = & $python[0] @pyArgs "-c" "import sys; print(sys.version.split()[0])"
$pythonExe = & $python[0] @pyArgs "-c" "import sys; print(sys.executable)"
Write-Host "Using Python $version via '$pythonDisplay'" -ForegroundColor Green
if ($env:VIRTUAL_ENV) {
    Write-Host "Active virtual environment: $env:VIRTUAL_ENV" -ForegroundColor Green
}

# Build the install target (support optional extras like devtools[pdf]).
$target = $Devtools
if ($Extras) {
    $target = "$Devtools[$Extras]"
}

if ($Uv) {
    # Fast path: install with uv into the detected interpreter's environment.
    if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
        Write-Host ""
        Write-Host "ERROR: -Uv was requested but 'uv' is not installed." -ForegroundColor Red
        Write-Host "Install uv with one of:" -ForegroundColor Yellow
        Write-Host "  * winget install astral-sh.uv"
        Write-Host "  * powershell -c `"irm https://astral.sh/uv/install.ps1 | iex`""
        Write-Host "  * $pythonDisplay -m pip install uv"
        Write-Host "Or re-run without -Uv to use pip."
        exit 1
    }
    Write-Host "Installing (editable, via uv): $target"
    & uv pip install --python "$pythonExe" -e "$target"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: 'uv pip install' failed. See the output above for details." -ForegroundColor Red
        exit 1
    }
} else {
    # Ensure pip is available for this interpreter.
    & $python[0] @pyArgs "-m" "pip" "--version" *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "pip not found for this interpreter - bootstrapping with ensurepip..."
        & $python[0] @pyArgs "-m" "ensurepip" "--upgrade"
        if ($LASTEXITCODE -ne 0) {
            Write-Host "ERROR: could not bootstrap pip. Re-run the Python installer and enable pip." -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "Installing (editable): $target"
    & $python[0] @pyArgs "-m" "pip" "install" "-e" "$target"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: pip install failed. See the output above for details." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "Done. Verify with:" -ForegroundColor Green
Write-Host "  $pythonDisplay -m neqsim_cli --help"
Write-Host "If the 'neqsim' command is not found, see devtools/README.md > Troubleshooting."
