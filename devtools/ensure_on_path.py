#!/usr/bin/env python3
"""Ensure the directory containing the installed ``neqsim`` console script is on
the user's PATH so that the ``neqsim`` command works in any new terminal.

This is called at the end of ``install.cmd`` / ``install.ps1`` but is safe to run
standalone (``py devtools/ensure_on_path.py``). It is idempotent and never fails
the install: if it cannot update PATH it prints a clear manual fallback instead.

Strategy
--------
1. If ``neqsim`` already resolves on PATH, do nothing.
2. Otherwise locate the Scripts/bin directory that actually contains the console
   script by probing, in order: an active virtualenv, the per-user install
   location, and the global install location.
3. On Windows, append that directory to the *user* PATH via the registry
   (``HKCU\\Environment``) -- this avoids the 1024-char truncation and the
   system/user duplication that ``setx %PATH%`` causes -- and broadcast the
   change so new terminals pick it up.
4. On macOS/Linux, print the line to add to the shell rc file (best effort).
"""
import os
import shutil
import sys
import sysconfig


SCRIPT_NAME = "neqsim"


def _candidate_script_dirs():
    """Yield candidate directories that may hold the console script.

    The order matters: an active virtualenv wins, then the per-user location,
    then the global one.

    @return list of absolute directory paths (some may not exist)
    """
    dirs = []
    venv = os.environ.get("VIRTUAL_ENV")
    if venv:
        sub = "Scripts" if os.name == "nt" else "bin"
        dirs.append(os.path.join(venv, sub))
    # Per-user and global script locations for this interpreter.
    for scheme in _user_and_global_schemes():
        try:
            path = sysconfig.get_path("scripts", scheme)
        except (KeyError, ValueError):
            path = None
        if path:
            dirs.append(path)
    # De-duplicate while preserving order.
    seen = set()
    unique = []
    for d in dirs:
        key = os.path.normcase(os.path.normpath(d))
        if key not in seen:
            seen.add(key)
            unique.append(d)
    return unique


def _user_and_global_schemes():
    """Return the sysconfig scheme names for user and global installs.

    @return list of scheme name strings appropriate for this OS
    """
    if os.name == "nt":
        return ["nt_user", "nt"]
    return ["posix_user", "posix_prefix"]


def _script_filename():
    """Return the on-disk name of the console script for this OS.

    @return the executable filename (e.g. ``neqsim.exe`` on Windows)
    """
    return SCRIPT_NAME + ".exe" if os.name == "nt" else SCRIPT_NAME


def find_script_dir():
    """Locate the directory that contains the installed console script.

    @return the directory path, or ``None`` if the script was not found
    """
    fname = _script_filename()
    for d in _candidate_script_dirs():
        if d and os.path.isfile(os.path.join(d, fname)):
            return d
    return None


def _already_on_path():
    """Check whether the console script already resolves on PATH.

    @return the resolved path string, or ``None`` if not found on PATH
    """
    return shutil.which(SCRIPT_NAME)


def _same_dir(a, b):
    """Return whether two paths refer to the same directory.

    Comparison is case- and separator-insensitive (``normcase`` + ``normpath``)
    so it behaves correctly on Windows.

    @param a first path
    @param b second path
    @return ``True`` if both normalize to the same directory
    """
    return os.path.normcase(os.path.normpath(a)) == os.path.normcase(
        os.path.normpath(b)
    )


def _dir_on_path(directory):
    """Return whether ``directory`` is already on the current process PATH.

    Uses the live ``PATH`` (which reflects machine + user + session entries), so
    a directory placed on PATH by a global / all-users install is detected and
    not duplicated into the per-user PATH.

    @param directory the directory to test
    @return ``True`` if the directory is present on PATH
    """
    target = os.path.normcase(os.path.normpath(directory))
    for entry in os.environ.get("PATH", "").split(os.pathsep):
        if entry and os.path.normcase(os.path.normpath(entry)) == target:
            return True
    return False


def _add_to_windows_user_path(new_dir):
    """Append ``new_dir`` to the Windows per-user PATH via the registry.

    Reads and writes ``HKCU\\Environment`` directly (not ``setx``) to avoid PATH
    truncation and system/user duplication, then broadcasts the change.

    @param new_dir the directory to add to the user PATH
    @return ``True`` if PATH was updated, ``False`` if it was already present
    @throws OSError if the registry cannot be read or written
    """
    import winreg

    with winreg.OpenKey(
        winreg.HKEY_CURRENT_USER, "Environment", 0, winreg.KEY_READ | winreg.KEY_WRITE
    ) as key:
        try:
            current, regtype = winreg.QueryValueEx(key, "Path")
        except FileNotFoundError:
            current, regtype = "", winreg.REG_EXPAND_SZ
        if regtype not in (winreg.REG_SZ, winreg.REG_EXPAND_SZ):
            regtype = winreg.REG_EXPAND_SZ

        entries = [p for p in current.split(os.pathsep) if p]
        target = os.path.normcase(os.path.normpath(new_dir))
        for existing in entries:
            if os.path.normcase(os.path.normpath(existing)) == target:
                return False  # already present

        entries.append(new_dir)
        updated = os.pathsep.join(entries)
        # Preserve %VAR% references by using REG_EXPAND_SZ.
        winreg.SetValueEx(key, "Path", 0, winreg.REG_EXPAND_SZ, updated)

    _broadcast_environment_change()
    return True


def _broadcast_environment_change():
    """Broadcast WM_SETTINGCHANGE so new processes see the updated PATH.

    Best effort: failures are ignored because the PATH edit itself already
    succeeded and a new terminal would pick it up regardless.

    @return ``None``
    """
    try:
        import ctypes

        HWND_BROADCAST = 0xFFFF
        WM_SETTINGCHANGE = 0x1A
        SMTO_ABORTIFHUNG = 0x0002
        result = ctypes.c_long()
        ctypes.windll.user32.SendMessageTimeoutW(
            HWND_BROADCAST,
            WM_SETTINGCHANGE,
            0,
            ctypes.c_wchar_p("Environment"),
            SMTO_ABORTIFHUNG,
            5000,
            ctypes.byref(result),
        )
    except Exception:  # noqa: BLE001 - broadcasting is optional
        pass


# Marker so the block we add to a shell rc file can be found and is only
# written once (idempotent across repeated installs).
_POSIX_MARKER = "# >>> neqsim PATH >>>"
_POSIX_MARKER_END = "# <<< neqsim PATH <<<"


def _posix_rc_files():
    """Return the shell rc file(s) to update based on the user's shell.

    @return list of (path, syntax) tuples where syntax is ``"posix"`` or
        ``"fish"``
    """
    shell = os.path.basename(os.environ.get("SHELL", "")).lower()
    home = os.path.expanduser("~")
    if shell == "zsh":
        return [(os.path.join(home, ".zshrc"), "posix")]
    if shell == "fish":
        return [(os.path.join(home, ".config", "fish", "config.fish"), "fish")]
    if shell == "bash":
        # Linux login+interactive read ~/.bashrc; macOS Terminal reads
        # ~/.bash_profile. Update whichever exist, defaulting to ~/.bashrc.
        candidates = [os.path.join(home, ".bashrc")]
        bash_profile = os.path.join(home, ".bash_profile")
        if sys.platform == "darwin" or os.path.isfile(bash_profile):
            candidates.append(bash_profile)
        return [(c, "posix") for c in candidates]
    # Unknown shell: ~/.profile is the most broadly sourced fallback.
    return [(os.path.join(home, ".profile"), "posix")]


def _add_to_posix_path(new_dir):
    """Append ``new_dir`` to PATH in the user's shell rc file(s), idempotently.

    Writes a clearly marked block so it is only added once and can be found or
    removed later. Uses fish syntax for the fish shell and POSIX ``export`` for
    everything else.

    @param new_dir the directory to add to PATH
    @return list of rc file paths that were updated (empty if already present)
    """
    updated = []
    for rc_path, syntax in _posix_rc_files():
        try:
            existing = ""
            if os.path.isfile(rc_path):
                with open(rc_path, "r", encoding="utf-8") as handle:
                    existing = handle.read()
            # Skip if our block or the directory is already referenced.
            if _POSIX_MARKER in existing or new_dir in existing:
                continue

            if syntax == "fish":
                line = 'fish_add_path "{}"'.format(new_dir)
            else:
                line = 'export PATH="{}:$PATH"'.format(new_dir)
            block = "\n{}\n{}\n{}\n".format(_POSIX_MARKER, line, _POSIX_MARKER_END)

            parent = os.path.dirname(rc_path)
            if parent and not os.path.isdir(parent):
                os.makedirs(parent, exist_ok=True)
            with open(rc_path, "a", encoding="utf-8") as handle:
                handle.write(block)
            updated.append(rc_path)
        except OSError:
            # Non-fatal: fall through so main() can print the manual fallback.
            continue
    return updated


def main():
    """Entry point: ensure the console-script directory is on PATH.

    Locates the directory where *this* install placed the console script and
    ensures that specific directory is on PATH -- rather than stopping as soon
    as any ``neqsim`` resolves. That distinction matters on machines where an
    elevated / all-users install already put ``neqsim`` on the system PATH: a
    later non-elevated (per-user) install puts the script in a *different*
    directory that would otherwise never be added.

    Always exits 0 so it never fails an install; prints a manual fallback if it
    cannot update PATH automatically.

    @return ``None``
    """
    script_dir = find_script_dir()

    # If the script lives in the active virtualenv, it is already on PATH for
    # that environment; do not persist an ephemeral venv location to the user
    # PATH.
    venv = os.environ.get("VIRTUAL_ENV")
    if venv and script_dir:
        venv_scripts = os.path.join(venv, "Scripts" if os.name == "nt" else "bin")
        if _same_dir(script_dir, venv_scripts):
            print(
                "'{}' is installed in the active virtualenv and already on "
                "PATH:\n  {}".format(SCRIPT_NAME, script_dir)
            )
            return

    if not script_dir:
        # We could not find the script where an install would place it. If it
        # nonetheless resolves from a prior / global install, that is fine;
        # otherwise tell the user how to run the CLI via the module form.
        resolved = _already_on_path()
        if resolved:
            print("'{}' is already on PATH ({}).".format(SCRIPT_NAME, resolved))
            return
        print(
            "Could not locate the '{}' script directory. "
            "Use 'py -m neqsim_cli --help' to run the CLI.".format(SCRIPT_NAME)
        )
        return

    if _dir_on_path(script_dir):
        print(
            "'{}' script directory is already on PATH:\n  {}".format(
                SCRIPT_NAME, script_dir
            )
        )
        return

    if os.name == "nt":
        try:
            changed = _add_to_windows_user_path(script_dir)
        except OSError as exc:
            print("Could not update the user PATH automatically: {}".format(exc))
            print(
                "Add this folder to PATH manually, or use 'py -m neqsim_cli':\n  "
                + script_dir
            )
            return
        # Update this process too (helps if a parent reads our environment).
        os.environ["PATH"] = os.environ.get("PATH", "") + os.pathsep + script_dir
        if changed:
            print("Added to your user PATH:\n  " + script_dir)
            print(
                "Open a NEW terminal, then '{}' will work. "
                "Until then, use 'py -m neqsim_cli'.".format(SCRIPT_NAME)
            )
        else:
            print(
                "'{}' directory is already in the user PATH:\n  {}\n"
                "Open a new terminal to pick it up.".format(SCRIPT_NAME, script_dir)
            )
        print(
            "If running '{0}' later shows \"The term '{0}' is not recognized\" "
            "in a VS Code terminal, fully quit and reopen VS Code -- a new "
            "integrated terminal is NOT enough (VS Code captures PATH at "
            "launch). A virtualenv avoids this.".format(SCRIPT_NAME)
        )
    else:
        # macOS/Linux: append to the shell rc file so a new shell picks it up.
        updated = _add_to_posix_path(script_dir)
        # Update this process too (best effort).
        os.environ["PATH"] = script_dir + os.pathsep + os.environ.get("PATH", "")
        if updated:
            print("Added to your PATH via:")
            for rc_path in updated:
                print("  " + rc_path)
            print(
                "Open a NEW terminal (or 'source' the file above), then '{}' "
                "will work. Until then, use 'python -m neqsim_cli'.".format(SCRIPT_NAME)
            )
        else:
            print(
                "The '{}' directory is already referenced in your shell "
                "config:\n  {}\nOpen a new terminal to pick it up, or run "
                "'python -m neqsim_cli'.".format(SCRIPT_NAME, script_dir)
            )


if __name__ == "__main__":
    main()
