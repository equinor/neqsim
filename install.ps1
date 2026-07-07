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
#     .\install.ps1 -InstallJdk  # also download a portable Temurin JDK (no admin)
#
# No need to activate a venv first, and no need to have `pip` on PATH.
#
# "File is not digitally signed" / execution-policy error?
# This is a Windows restriction on running scripts, not a problem with the
# file. Pick ONE of these (no admin rights needed):
#
#   1. Use the wrapper (simplest - bypasses the policy for one run only):
#          .\install.cmd
#
#   2. Run this script with a one-time bypass (no permanent change):
#          powershell -ExecutionPolicy Bypass -File .\install.ps1
#
#   3. Allow local scripts for your user account, then run normally:
#          Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
#          Unblock-File -Path .\install.ps1   # clears the "downloaded" mark
#          .\install.ps1

param(
    [string]$Extras = "",
    [switch]$Uv,
    [switch]$InstallJdk,
    [string]$JdkVersion = "21"
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
Write-Host "Ensuring the 'neqsim' command is on your PATH..." -ForegroundColor Cyan
& $python[0] @pyArgs (Join-Path $Devtools "ensure_on_path.py")

Write-Host ""
Write-Host "Done. Verify with:" -ForegroundColor Green
Write-Host "  $pythonDisplay -m neqsim_cli --help"
Write-Host "If 'neqsim' is not found in this window, open a NEW terminal (PATH changes"
Write-Host "only apply to newly opened terminals), or use the line above."

# ── JDK advisory (non-fatal) ─────────────────────────────────────────────
# The Python devtools do not need Java, but building the NeqSim JAR
# (mvnw.cmd install) and the local dev-notebook runtime DO. Check for a JDK
# so users on locked-down PCs get the portable-JDK remedy up front.

function Install-PortableJdk {
    # Download a portable Temurin (Eclipse Adoptium) JDK into the user profile
    # and set user-scope JAVA_HOME/PATH. Requires no administrator rights.
    # The download is verified against the SHA-256 checksum published by the
    # Adoptium assets API before it is extracted.
    param([string]$Version = "21")

    if ($env:PROCESSOR_ARCHITECTURE -match "ARM64") { $arch = "aarch64" } else { $arch = "x64" }
    $dest = Join-Path $HOME ".neqsim\jdk"
    $tmpZip = Join-Path $env:TEMP "temurin-$Version-$arch.zip"
    $assetsUrl = "https://api.adoptium.net/v3/assets/latest/$Version/hotspot?architecture=$arch&image_type=jdk&os=windows&vendor=eclipse"

    Write-Host ""
    Write-Host "Querying Adoptium for a portable Temurin JDK $Version ($arch)..." -ForegroundColor Cyan
    try {
        $assets = Invoke-RestMethod -Uri $assetsUrl -UseBasicParsing
    } catch {
        Write-Host "ERROR: could not query the Adoptium API: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Behind a proxy? Set `$env:HTTPS_PROXY and retry, or install a JDK manually." -ForegroundColor Yellow
        return $null
    }

    $pkg = $null
    foreach ($a in @($assets)) {
        if ($a.binary -and $a.binary.image_type -eq "jdk" -and $a.binary.package -and $a.binary.package.link) {
            $pkg = $a.binary.package
            break
        }
    }
    if (-not $pkg) {
        Write-Host "ERROR: no matching JDK package in the Adoptium response." -ForegroundColor Red
        return $null
    }
    $expected = $pkg.checksum

    Write-Host "Downloading $($pkg.name)..."
    try {
        Invoke-WebRequest -Uri $pkg.link -OutFile $tmpZip -UseBasicParsing
    } catch {
        Write-Host "ERROR: JDK download failed: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Behind a proxy? Set `$env:HTTPS_PROXY and retry, or install a JDK manually." -ForegroundColor Yellow
        return $null
    }

    if ($expected) {
        Write-Host "Verifying SHA-256 checksum..."
        $actual = (Get-FileHash -Path $tmpZip -Algorithm SHA256).Hash.ToLower()
        if ($actual -ne $expected.ToLower()) {
            Write-Host "ERROR: checksum mismatch - download may be corrupt or tampered." -ForegroundColor Red
            Write-Host "  expected: $expected"
            Write-Host "  actual:   $actual"
            Remove-Item $tmpZip -ErrorAction SilentlyContinue
            return $null
        }
        Write-Host "Checksum OK." -ForegroundColor Green
    } else {
        Write-Host "WARNING: Adoptium returned no checksum; skipping verification." -ForegroundColor Yellow
    }

    if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Write-Host "Extracting to $dest ..."
    Expand-Archive -Path $tmpZip -DestinationPath $dest -Force
    Remove-Item $tmpZip -ErrorAction SilentlyContinue

    # Temurin extracts to a versioned subfolder (e.g. jdk-21.0.5+11).
    $jdkHome = Get-ChildItem -Path $dest -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
        Select-Object -First 1
    if (-not $jdkHome) {
        Write-Host "ERROR: Could not find bin\java.exe in the extracted JDK." -ForegroundColor Red
        return $null
    }

    $javaHomePath = $jdkHome.FullName
    $jdkBin = Join-Path $javaHomePath "bin"
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHomePath, "User")
    $userPath = [Environment]::GetEnvironmentVariable("PATH", "User")
    if (-not $userPath) { $userPath = "" }
    if ($userPath -notlike "*$jdkBin*") {
        [Environment]::SetEnvironmentVariable("PATH", ($userPath.TrimEnd(';') + ";" + $jdkBin), "User")
    }
    # Make it usable in this session too, so a following mvnw.cmd install works.
    $env:JAVA_HOME = $javaHomePath
    $env:PATH = "$jdkBin;$env:PATH"

    Write-Host "Portable JDK installed (no admin):" -ForegroundColor Green
    Write-Host "  JAVA_HOME = $javaHomePath"
    Write-Host "  Added to user PATH: $jdkBin"
    Write-Host "  Open a NEW terminal for the persistent env vars to apply everywhere."
    return $javaHomePath
}

$jdkFound = $false
$javaHome = [Environment]::GetEnvironmentVariable("JAVA_HOME")
if ($javaHome -and (Test-Path (Join-Path $javaHome "bin\java.exe"))) {
    $jdkFound = $true
    Write-Host ""
    Write-Host "JDK detected via JAVA_HOME: $javaHome" -ForegroundColor Green
} elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $jdkFound = $true
    Write-Host ""
    Write-Host "JDK detected on PATH (java)." -ForegroundColor Green
    if (-not $javaHome) {
        Write-Host "Tip: set JAVA_HOME so all Java tooling agrees on one JDK." -ForegroundColor Yellow
    }
}

if (-not $jdkFound) {
    if ($InstallJdk) {
        Install-PortableJdk -Version $JdkVersion
    } else {
        Write-Host ""
        Write-Host "NOTE: No JDK found (needed for 'mvnw.cmd install' and local dev notebooks)." -ForegroundColor Yellow
        Write-Host "Easiest fix (no admin): re-run with -InstallJdk to auto-download a portable JDK:" -ForegroundColor Yellow
        Write-Host "       .\install.ps1 -InstallJdk"
        Write-Host "Or install one manually:" -ForegroundColor Yellow
        Write-Host "  1. Download a Temurin JDK 21 .zip:"
        Write-Host "       https://adoptium.net/temurin/releases/?package=jdk"
        Write-Host "  2. Extract into your profile, e.g. C:\Users\$env:USERNAME\jdk-21"
        Write-Host "  3. Set user-scope env vars (no admin; open a new terminal to apply):"
        Write-Host "       [Environment]::SetEnvironmentVariable('JAVA_HOME','C:\Users\$env:USERNAME\jdk-21','User')"
        Write-Host "       [Environment]::SetEnvironmentVariable('PATH',`$env:PATH+';C:\Users\$env:USERNAME\jdk-21\bin','User')"
        Write-Host "  (Pure Python/pip 'neqsim' use still needs a JVM present via jpype.)"
        Write-Host "Run '$pythonDisplay -m neqsim_cli doctor' to re-check your environment." -ForegroundColor Cyan
    }
} elseif ($InstallJdk) {
    Write-Host ""
    Write-Host "A JDK is already available; skipping -InstallJdk." -ForegroundColor Yellow
    Write-Host "Delete the old JAVA_HOME / PATH entries first if you want to switch to a portable JDK."
}
