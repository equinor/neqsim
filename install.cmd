@echo off
REM install.cmd - Windows installer for the NeqSim devtools package.
REM
REM This is a PURE BATCH installer that calls Python directly and does NOT run
REM any PowerShell script. Use it when PowerShell refuses to run install.ps1
REM with a "file is not digitally signed" / execution-policy error -- including
REM locked-down machines where the execution policy is enforced by Group Policy
REM (so even "powershell -ExecutionPolicy Bypass" is ignored).
REM
REM It finds a working Python (preferring an active virtualenv via python /
REM python3, falling back to the py launcher, and skipping the Microsoft Store
REM stub), bootstraps pip if needed, and installs the devtools package in
REM editable mode -- exactly what install.ps1 does at its core.
REM
REM Usage (from the repo root, in cmd.exe or by double-clicking):
REM     install.cmd                 :: install neqsim-dev-setup (editable)
REM     install.cmd pdf             :: also install the optional [pdf] extras
REM     install.cmd ocr             :: also install the optional [ocr] extras
REM     install.cmd uv              :: install with the fast 'uv' package manager
REM
REM For the optional -InstallJdk / advanced options, use install.ps1 on a
REM machine where PowerShell scripts are allowed.

setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
set "DEVTOOLS=%SCRIPT_DIR%devtools"

REM ---- First positional arg: extras name, or "uv" to use the uv installer ----
REM NOTE: do not use "if COND cmd & goto" here -- the & runs the goto
REM unconditionally in batch, which would skip the extras assignment.
set "EXTRAS="
set "USE_UV="
if /I "%~1"=="uv" goto :arg1_uv
if not "%~1"=="" set "EXTRAS=%~1"
goto :arg2
:arg1_uv
set "USE_UV=1"
:arg2
if /I "%~2"=="uv" set "USE_UV=1"

echo NeqSim devtools installer (batch)
echo Locating a working Python interpreter...

REM ---- Find a Python that actually prints a version (skips the Store stub) ----
REM Order matters: prefer interpreters that RESPECT an active virtualenv.
REM `python` / `python3` resolve to the venv's python when a venv is activated
REM (its Scripts dir is prepended to PATH), so the package -- and the `neqsim`
REM console script -- install where the user expects. `py -3` is LAST because
REM the py launcher IGNORES an active venv and would install into the system
REM Python instead, putting the script in a per-user Scripts dir that the venv's
REM PATH does not include. (install.ps1 uses the same ordering.)
set "PY="
call :try_python "python" ""
if defined PY goto :found
call :try_python "python3" ""
if defined PY goto :found
call :try_python "py" "-3"
if defined PY goto :found

echo.
echo ERROR: No working Python interpreter was found.
echo Install Python 3.8+ from https://www.python.org/downloads/
echo (check "Add python.exe to PATH"), then re-run install.cmd.
exit /b 1

:found
echo Using Python via: %PY%

REM ---- Build the editable target, with optional extras ----
set "TARGET=%DEVTOOLS%"
if defined EXTRAS set "TARGET=%DEVTOOLS%[%EXTRAS%]"

if defined USE_UV goto :install_uv

REM ---- Ensure pip, then install with pip ----
%PY% -m pip --version >nul 2>&1
if not errorlevel 1 goto :pip_ok
echo pip not found for this interpreter - bootstrapping with ensurepip...
%PY% -m ensurepip --upgrade
if errorlevel 1 goto :err_pip_boot
:pip_ok
echo Installing (editable): %TARGET%
%PY% -m pip install -e "%TARGET%"
if errorlevel 1 goto :err_pip
goto :done

:install_uv
where uv >nul 2>&1
if errorlevel 1 goto :err_no_uv
for /f "delims=" %%E in ('%PY% -c "import sys; print(sys.executable)"') do set "PYEXE=%%E"
echo Installing (editable, via uv): %TARGET%
uv pip install --python "%PYEXE%" -e "%TARGET%"
if errorlevel 1 goto :err_uv
goto :done

:done
echo.
echo Ensuring the 'neqsim' command is on your PATH...
%PY% "%DEVTOOLS%\ensure_on_path.py"
echo.
echo Done. Verify with:
echo   %PY% -m neqsim_cli --help
echo If the 'neqsim' command is not found in this window, open a NEW terminal
echo (PATH changes only apply to newly opened terminals), or use the line above.
echo If running 'neqsim' shows "The term 'neqsim' is not recognized" in a VS Code
echo terminal, fully quit and reopen VS Code -- a new integrated terminal is NOT
echo enough (VS Code captures PATH at launch). A virtualenv avoids this.
exit /b 0

:err_pip_boot
echo ERROR: could not bootstrap pip. Re-run the Python installer and enable pip.
exit /b 1
:err_pip
echo ERROR: pip install failed. See the output above for details.
exit /b 1
:err_no_uv
echo ERROR: 'uv' was requested but is not installed. Run: %PY% -m pip install uv
exit /b 1
:err_uv
echo ERROR: 'uv pip install' failed. See the output above for details.
exit /b 1

REM ---- Helper: set PY only if the candidate prints a version ----
:try_python
set "_CMD=%~1"
set "_ARG=%~2"
if defined _ARG "%~1" %~2 -c "import sys; print(sys.version.split()[0])" >nul 2>&1
if not defined _ARG "%~1" -c "import sys; print(sys.version.split()[0])" >nul 2>&1
if errorlevel 1 goto :eof
if defined _ARG set "PY=%_CMD% %_ARG%"
if not defined _ARG set "PY=%_CMD%"
goto :eof
