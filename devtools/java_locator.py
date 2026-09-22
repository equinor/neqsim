"""
java_locator.py - Find an installed JDK/JRE when it is not on PATH.

On locked-down corporate machines Java is often installed but invisible to the
shell: no ``java`` on PATH and no ``JAVA_HOME``. JPype then fails with
``JVMNotFoundException`` even though a perfectly usable JVM sits in Program
Files, the user profile, or inside the VS Code Java extension. This module
searches the places Java actually lands and returns a home JPype can use.

Typical use::

    from java_locator import ensure_java_home
    ensure_java_home()          # sets JAVA_HOME for this process if needed
    import jpype; jpype.startJVM(...)

Discovery is read-only and never installs anything. Nothing here needs admin
rights, and no environment variable is changed outside the current process.
"""
import collections
import glob
import os
import re
import subprocess
import sys


JavaInstall = collections.namedtuple(
    "JavaInstall", "home major is_jdk jvm_library source")

_IS_WINDOWS = sys.platform.startswith("win")
_IS_MAC = sys.platform == "darwin"


def _exe(name):
    """Return the platform executable file name for a tool.

    @param name the tool name without extension, e.g. "java"
    @return the file name including the Windows .exe suffix when applicable
    """
    return name + ".exe" if _IS_WINDOWS else name


def _jvm_library(home):
    """Locate the JVM shared library inside a Java home.

    JPype loads this library directly, so a home without it is unusable even
    when ``bin/java`` runs fine.

    @param home the candidate Java home directory
    @return the absolute path to the JVM shared library, or None
    """
    if _IS_WINDOWS:
        relatives = [
            ("bin", "server", "jvm.dll"),
            ("bin", "client", "jvm.dll"),
            ("jre", "bin", "server", "jvm.dll"),
        ]
    elif _IS_MAC:
        relatives = [
            ("lib", "server", "libjvm.dylib"),
            ("jre", "lib", "server", "libjvm.dylib"),
        ]
    else:
        relatives = [
            ("lib", "server", "libjvm.so"),
            ("lib", "amd64", "server", "libjvm.so"),
            ("lib", "aarch64", "server", "libjvm.so"),
            ("jre", "lib", "amd64", "server", "libjvm.so"),
        ]
    for parts in relatives:
        candidate = os.path.join(home, *parts)
        if os.path.isfile(candidate):
            return candidate
    return None


def parse_java_major(version_text):
    """Parse a Java major version from a version string or banner.

    Handles legacy ("1.8.0_392") and modern ("21.0.2") numbering.

    @param version_text a raw version string or `java -version` output
    @return the integer major version, or None when it cannot be determined
    """
    if not version_text:
        return None
    quoted = re.search(r'version "([^"]+)"', version_text)
    raw = quoted.group(1) if quoted else version_text.strip().strip('"')
    parts = raw.split(".")
    try:
        if parts[0] == "1" and len(parts) > 1:
            return int(parts[1])
        return int(re.split(r"[^0-9]", parts[0])[0])
    except (ValueError, IndexError):
        return None


def _major_from_release_file(home):
    """Read the major version from the JDK ``release`` file without running Java.

    @param home the Java home directory
    @return the integer major version, or None when the file is absent/unreadable
    """
    release = os.path.join(home, "release")
    try:
        with open(release, "r", errors="ignore") as handle:
            for line in handle:
                if line.startswith("JAVA_VERSION"):
                    return parse_java_major(line.split("=", 1)[-1])
    except (OSError, IndexError):
        return None
    return None


def _major_from_java_binary(home):
    """Read the major version by running ``java -version`` in a Java home.

    @param home the Java home directory
    @return the integer major version, or None when the probe fails
    """
    java = os.path.join(home, "bin", _exe("java"))
    try:
        result = subprocess.run(
            [java, "-version"], capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.SubprocessError):
        return None
    return parse_java_major(result.stderr or result.stdout or "")


def _inspect(home, source, allow_binary_probe=True):
    """Turn a candidate directory into a JavaInstall when it is usable.

    @param home the candidate Java home directory
    @param source a short label describing where the candidate came from
    @param allow_binary_probe whether running `java -version` is permitted when
        the JDK release file is missing
    @return a JavaInstall, or None when the directory is not a usable Java home
    """
    if not home:
        return None
    home = os.path.abspath(home)
    if not os.path.isfile(os.path.join(home, "bin", _exe("java"))):
        return None
    library = _jvm_library(home)
    if not library:
        return None
    major = _major_from_release_file(home)
    if major is None and allow_binary_probe:
        major = _major_from_java_binary(home)
    is_jdk = os.path.isfile(os.path.join(home, "bin", _exe("javac")))
    return JavaInstall(home, major, is_jdk, library, source)


def _java_home_from_path():
    """Resolve the Java home of the ``java`` executable on PATH.

    Asks the JVM for its own ``java.home`` so a launcher shim (for example the
    Windows ``javapath`` directory) does not yield a bogus home.

    @return the Java home directory, or None when java is not on PATH
    """
    try:
        result = subprocess.run(
            ["java", "-XshowSettings:properties", "-version"],
            capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.SubprocessError):
        return None
    match = re.search(r"java\.home\s*=\s*(.+)", result.stderr or result.stdout or "")
    return match.group(1).strip() if match else None


def _registry_java_homes():
    """Read Java homes registered under HKLM\\SOFTWARE\\JavaSoft on Windows.

    @return a list of Java home directories, empty on non-Windows or on error
    """
    if not _IS_WINDOWS:
        return []
    try:
        import winreg
    except ImportError:
        return []
    homes = []
    roots = ["JDK", "Java Development Kit", "JRE", "Java Runtime Environment"]
    for product in roots:
        key_path = "SOFTWARE\\JavaSoft\\" + product
        for view in (winreg.KEY_WOW64_64KEY, winreg.KEY_WOW64_32KEY):
            try:
                key = winreg.OpenKey(
                    winreg.HKEY_LOCAL_MACHINE, key_path, 0,
                    winreg.KEY_READ | view)
            except OSError:
                continue
            try:
                index = 0
                while True:
                    try:
                        version = winreg.EnumKey(key, index)
                    except OSError:
                        break
                    index += 1
                    try:
                        sub = winreg.OpenKey(key, version)
                        home, _ = winreg.QueryValueEx(sub, "JavaHome")
                        homes.append(home)
                    except OSError:
                        continue
            finally:
                key.Close()
    return homes


def _glob_roots():
    """Return glob patterns for directories that commonly contain Java homes.

    Covers vendor installers, no-admin unpacked JDKs in the user profile, and
    the JREs bundled with the VS Code Java extension and JetBrains IDEs — the
    usual places Java hides on a managed corporate machine.

    @return a list of (glob pattern, source label) tuples for this platform
    """
    home = os.path.expanduser("~")
    patterns = []
    if _IS_WINDOWS:
        local = os.environ.get("LOCALAPPDATA", os.path.join(home, "AppData", "Local"))
        vendors = [
            "Java", "Eclipse Adoptium", "Eclipse Foundation", "AdoptOpenJDK",
            "Microsoft", "Amazon Corretto", "Zulu", "BellSoft", "Semeru",
            "RedHat", "SapMachine", "GraalVM",
        ]
        for base in ("C:\\Program Files", local + "\\Programs", local):
            for vendor in vendors:
                patterns.append((os.path.join(base, vendor, "*"), "vendor install"))
        patterns.extend([
            (os.path.join(home, ".jdks", "*"), "user profile"),
            (os.path.join(home, "*jdk*"), "user profile"),
            (os.path.join(home, "*", "*jdk*"), "user profile"),
            (os.path.join(home, "graalvm", "*"), "user profile"),
            (os.path.join(home, "scoop", "apps", "*", "current"), "scoop"),
            ("C:\\appl\\*jdk*", "site install"),
            ("C:\\appl\\*", "site install"),
            (os.path.join(local, "JetBrains", "*", "jbr"), "JetBrains IDE"),
        ])
    elif _IS_MAC:
        patterns.extend([
            ("/Library/Java/JavaVirtualMachines/*/Contents/Home", "vendor install"),
            (os.path.join(home, "Library", "Java", "JavaVirtualMachines",
                          "*", "Contents", "Home"), "user profile"),
            (os.path.join(home, ".jdks", "*"), "user profile"),
            (os.path.join(home, ".sdkman", "candidates", "java", "*"), "sdkman"),
            ("/opt/homebrew/opt/openjdk*/libexec/openjdk.jdk/Contents/Home", "homebrew"),
        ])
    else:
        patterns.extend([
            ("/usr/lib/jvm/*", "vendor install"),
            ("/opt/java/*", "vendor install"),
            ("/opt/*jdk*", "vendor install"),
            (os.path.join(home, ".jdks", "*"), "user profile"),
            (os.path.join(home, ".sdkman", "candidates", "java", "*"), "sdkman"),
            (os.path.join(home, "*jdk*"), "user profile"),
        ])
    # JREs shipped inside the VS Code Java extension, on every platform.
    for editor in (".vscode", ".vscode-insiders", ".vscode-server"):
        patterns.append((
            os.path.join(home, editor, "extensions", "redhat.java-*", "jre", "*"),
            "VS Code Java extension"))
    return patterns


def find_java_installs(min_major=8, probe_limit=8):
    """Find usable Java installations, best first.

    Ranking prefers a full JDK over a JRE and then the newest version, so a
    build-capable Java is chosen ahead of a bundled runtime when both exist.

    @param min_major the lowest acceptable Java major version
    @param probe_limit the maximum number of candidates whose version may be
        read by launching `java -version` when the release file is missing
    @return a list of JavaInstall records, best candidate first
    """
    candidates = []
    env_home = os.environ.get("JAVA_HOME", "").strip()
    if env_home:
        candidates.append((env_home, "JAVA_HOME"))
    path_home = _java_home_from_path()
    if path_home:
        candidates.append((path_home, "PATH"))
    for registry_home in _registry_java_homes():
        candidates.append((registry_home, "Windows registry"))
    for pattern, source in _glob_roots():
        for match in sorted(glob.glob(pattern)):
            if os.path.isdir(match):
                candidates.append((match, source))

    found = {}
    seen = set()
    probes = 0
    for home, source in candidates:
        key = os.path.normcase(os.path.abspath(home))
        if key in seen:
            continue
        seen.add(key)
        install = _inspect(home, source, allow_binary_probe=probes < probe_limit)
        if install is None:
            continue
        if install.major is None:
            probes += 1
        if install.major is None or install.major >= min_major:
            found[key] = install
    return sorted(
        found.values(),
        key=lambda i: (i.is_jdk, i.major or 0),
        reverse=True)


def find_java_home(min_major=8):
    """Return the best usable Java home on this machine.

    @param min_major the lowest acceptable Java major version
    @return the Java home directory, or None when no usable Java was found
    """
    installs = find_java_installs(min_major=min_major)
    return installs[0].home if installs else None


def ensure_java_home(min_major=8, verbose=False):
    """Make a JVM discoverable to this process when Java is installed but hidden.

    Leaves a working ``JAVA_HOME`` untouched. Otherwise it searches for an
    installed Java and sets ``JAVA_HOME`` plus ``PATH`` for the current process
    only — no user or machine environment variable is modified.

    @param min_major the lowest acceptable Java major version
    @param verbose whether to print the resolved Java home
    @return the Java home now in effect, or None when no usable Java was found
    """
    current = os.environ.get("JAVA_HOME", "").strip()
    if current and _inspect(current, "JAVA_HOME"):
        return current
    installs = find_java_installs(min_major=min_major)
    if not installs:
        return None
    best = installs[0]
    os.environ["JAVA_HOME"] = best.home
    bin_dir = os.path.join(best.home, "bin")
    if bin_dir not in os.environ.get("PATH", "").split(os.pathsep):
        os.environ["PATH"] = bin_dir + os.pathsep + os.environ.get("PATH", "")
    if verbose:
        print("JAVA_HOME set for this process: {h} (Java {v}, {kind}, found via {s})".format(
            h=best.home, v=best.major or "?",
            kind="JDK" if best.is_jdk else "JRE", s=best.source))
    return best.home


def main(argv=None):
    """Print the Java installations found on this machine.

    @param argv optional argument list; unused, present for CLI symmetry
    @return process exit code: 0 when a Java was found, 1 otherwise
    """
    installs = find_java_installs()
    if not installs:
        print("No usable Java found. Install a JDK 21 (no admin needed: unpack a "
              "Temurin archive into your user profile).")
        return 1
    print("Usable Java installations (best first):")
    for install in installs:
        print("  Java {v:<4} {kind}  {home}   [{source}]".format(
            v=install.major or "?", kind="JDK" if install.is_jdk else "JRE",
            home=install.home, source=install.source))
    return 0


if __name__ == "__main__":
    sys.exit(main())
