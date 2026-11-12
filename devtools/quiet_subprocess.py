"""Keep child processes started by the NeqSim toolkit from opening console windows on Windows.

When the toolkit runs from a hook, an MCP server or an agent tool call, the Python process
often has no visible console. Every console child (pip, git, az, java, docker) then makes
Windows pop up a window. ``install()`` makes ``subprocess.Popen`` add ``CREATE_NO_WINDOW``
whenever the caller did not choose ``creationflags`` or ``startupinfo`` itself and the
current process does not own a visible console. Interactive terminals are left untouched.

Set ``NEQSIM_SHOW_CONSOLE_WINDOWS=1`` to turn the behaviour off. Does nothing off Windows.
"""

import os
import subprocess

CREATE_NO_WINDOW = 0x08000000
# Index of ``creationflags`` among the positional arguments of ``Popen`` (after ``self``).
_CREATIONFLAGS_POSITION = 13

_installed = False


def _has_visible_console():
    """Return True when this process is attached to a visible console window.

    :return: True for an interactive terminal window, False when headless or hidden.
    """
    try:
        import ctypes

        hwnd = ctypes.windll.kernel32.GetConsoleWindow()
        return bool(hwnd) and bool(ctypes.windll.user32.IsWindowVisible(hwnd))
    except (AttributeError, OSError):
        return True


def install():
    """Patch ``subprocess.Popen`` once so children start with a hidden console.

    :return: True when the patch is active, False when it is not needed or disabled.
    """
    global _installed
    if os.name != "nt" or os.environ.get("NEQSIM_SHOW_CONSOLE_WINDOWS") == "1":
        return False
    if _installed:
        return True
    if _has_visible_console():
        return False
    original = subprocess.Popen.__init__

    def quiet_init(self, *args, **kwargs):
        if len(args) <= _CREATIONFLAGS_POSITION and not kwargs.get("creationflags") \
                and kwargs.get("startupinfo") is None:
            kwargs["creationflags"] = CREATE_NO_WINDOW
        original(self, *args, **kwargs)

    subprocess.Popen.__init__ = quiet_init
    _installed = True
    return True
