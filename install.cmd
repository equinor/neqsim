@echo off
REM install.cmd - Windows wrapper for install.ps1
REM
REM Runs the NeqSim devtools installer without hitting the PowerShell
REM "file is not digitally signed" / execution-policy error. It launches
REM install.ps1 with -ExecutionPolicy Bypass for this one process only and
REM does NOT change any system or user execution-policy settings.
REM
REM Usage (from the repo root, in cmd or by double-clicking):
REM     install.cmd                 :: install neqsim-dev-setup (editable)
REM     install.cmd pdf             :: also install the optional [pdf] extras
REM     install.cmd ocr             :: also install the optional [ocr] extras
REM     install.cmd -Uv             :: use the fast 'uv' package manager
REM     install.cmd -InstallJdk     :: also download a portable Temurin JDK
REM
REM All arguments are forwarded verbatim to install.ps1.

setlocal
set "SCRIPT_DIR=%~dp0"

REM Prefer Windows PowerShell; fall back to PowerShell 7+ (pwsh) if present.
where powershell >nul 2>&1
if %ERRORLEVEL%==0 (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%install.ps1" %*
) else (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%install.ps1" %*
)

exit /b %ERRORLEVEL%
