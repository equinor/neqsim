"""
neqsim_doctor.py - Diagnostic tool for NeqSim development environment.

Checks that your environment is correctly set up for NeqSim development and
agentic workflows. Reports issues with actionable fix suggestions.

Usage:
    neqsim doctor          # run all checks
    neqsim doctor --fix    # attempt auto-fixes where possible
    neqsim doctor --skip-jar  # check a CLI installation before the Java build

Inspired by OpenClaw's `openclaw doctor` pattern.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import sysconfig
import time
from datetime import datetime


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
# Toolkit mode: installed via pip / the agent plugin, no source checkout beside us.
TOOLKIT_MODE = not os.path.isfile(os.path.join(PROJECT_ROOT, "pom.xml"))
MCP_MIN_JAVA = 21

# ── Result tracking ──────────────────────────────────────
_results = []


def _check(name, passed, message, fix_hint=None):
    """Record a check result."""
    status = "PASS" if passed else "FAIL"
    _results.append({
        "name": name,
        "passed": passed,
        "message": message,
        "fix_hint": fix_hint,
    })
    icon = "OK" if passed else "!!"
    print("  [{icon}] {name}: {msg}".format(icon=icon, name=name, msg=message))
    if not passed and fix_hint:
        print("       Fix: {hint}".format(hint=fix_hint))


def _warn(name, message, fix_hint=None):
    """Record a warning (not a failure)."""
    _results.append({
        "name": name,
        "passed": True,
        "message": "WARN: " + message,
        "fix_hint": fix_hint,
    })
    print("  [??] {name}: {msg}".format(name=name, msg=message))
    if fix_hint:
        print("       Hint: {hint}".format(hint=fix_hint))


# ══════════════════════════════════════════════════════════
# Checks
# ══════════════════════════════════════════════════════════

def _portable_jdk_hint():
    """Return a no-admin, portable-JDK remediation hint for this platform.

    Installing a JDK system-wide often requires administrator rights. A portable
    (unpacked archive) JDK placed inside the user profile works without admin,
    which is the common situation on locked-down corporate PCs.

    @return a multi-line remediation string tailored to the current platform
    """
    if sys.platform.startswith("win"):
        return (
            "No admin? Use a portable JDK (no installer needed):\n"
            "         1. Download a Temurin JDK 21 .zip from "
            "https://adoptium.net/temurin/releases/?package=jdk\n"
            "         2. Extract into your profile, e.g. C:\\Users\\<id>\\jdk-21\n"
            "         3. Set user-scope env vars (no admin, new terminal to apply):\n"
            "            [Environment]::SetEnvironmentVariable('JAVA_HOME','C:\\Users\\<id>\\jdk-21','User')\n"
            "            [Environment]::SetEnvironmentVariable('PATH',"
            "$env:PATH+';C:\\Users\\<id>\\jdk-21\\bin','User')\n"
            "         mvnw.cmd needs java on PATH or a valid JAVA_HOME to build."
        )
    return (
        "No admin? Use a portable JDK (no installer needed):\n"
        "         1. Download a Temurin JDK 21 .tar.gz from "
        "https://adoptium.net/temurin/releases/?package=jdk\n"
        "         2. Extract into your home dir, e.g. $HOME/jdk-21\n"
        "         3. Add to your shell rc file:\n"
        "            export JAVA_HOME=$HOME/jdk-21\n"
        "            export PATH=$JAVA_HOME/bin:$PATH\n"
        "         ./mvnw needs java on PATH or a valid JAVA_HOME to build."
    )


def _parse_java_major(version_output):
    """Parse the Java major version from `java -version` output.

    Handles both legacy ("1.8.0_392") and modern ("21.0.2") version strings.

    @param version_output the combined stdout/stderr text from `java -version`
    @return the integer major version, or None when it cannot be determined
    """
    match = re.search(r'version "([^"]+)"', version_output or "")
    if not match:
        return None
    raw = match.group(1)
    parts = raw.split(".")
    try:
        if parts[0] == "1" and len(parts) > 1:
            return int(parts[1])  # 1.8.0_x -> 8
        return int(re.split(r"[^0-9]", parts[0])[0])  # 21.0.2 -> 21
    except (ValueError, IndexError):
        return None


def _java_home_is_valid():
    """Return whether JAVA_HOME points at a usable JDK.

    @return a (is_valid, java_home) tuple; java_home is "" when unset
    """
    java_home = os.environ.get("JAVA_HOME", "").strip()
    if not java_home:
        return False, ""
    java_bin = "java.exe" if sys.platform.startswith("win") else "java"
    return os.path.isfile(os.path.join(java_home, "bin", java_bin)), java_home


def check_java():
    """Check Java is installed, discoverable, and a supported version.

    Validates two independent discovery paths the Maven wrapper relies on:
    `java` on PATH and a valid `JAVA_HOME`. Emits a no-admin portable-JDK
    remedy when neither is usable — the most common failure on locked-down
    corporate machines where installing a JDK system-wide needs admin rights.
    """
    print("\n--- Java ---")
    java_on_path = shutil.which("java") is not None
    java_home_valid, java_home = _java_home_is_valid()

    version_output = ""
    major = None
    if java_on_path:
        try:
            result = subprocess.run(
                ["java", "-version"],
                capture_output=True, text=True, timeout=10
            )
            # Java writes its version banner to stderr.
            version_output = result.stderr or result.stdout or ""
            version_line = version_output.strip().split("\n")[0]
            major = _parse_java_major(version_output)
            _check("Java installed", True, version_line)
        except Exception as e:  # noqa: BLE001 - report any launch failure
            _check(
                "Java installed", False,
                "java on PATH but failed to run: {e}".format(e=e),
                fix_hint=_portable_jdk_hint()
            )
    elif java_home_valid:
        _check(
            "Java installed", True,
            "Not on PATH, but JAVA_HOME is set ({p})".format(p=java_home)
        )
        _warn(
            "Java on PATH",
            "java is not on PATH; mvnw uses JAVA_HOME but other tools may not",
            fix_hint="Add {p}{sep}bin to PATH (user-scope, no admin)".format(
                p=java_home, sep=os.sep)
        )
    else:
        _check(
            "Java installed", False,
            "No java on PATH and JAVA_HOME is not set/valid",
            fix_hint=_portable_jdk_hint()
        )

    # JAVA_HOME status (mvnw prefers it; a stale value silently breaks builds).
    if os.environ.get("JAVA_HOME", "").strip():
        _check(
            "JAVA_HOME",
            java_home_valid,
            "Valid: {p}".format(p=java_home) if java_home_valid
            else "Set but invalid (no bin/java): {p}".format(p=java_home),
            fix_hint=None if java_home_valid
            else "Point JAVA_HOME at a real JDK home. " + _portable_jdk_hint()
        )
    elif java_on_path:
        _warn(
            "JAVA_HOME",
            "Not set (java found on PATH, so mvnw still works)",
            fix_hint="Optional: set JAVA_HOME so all Java tooling agrees on one JDK"
        )

    # Version guard: NeqSim must compile/run on Java 8+.
    if major is not None:
        _check(
            "Java version >= 8",
            major >= 8,
            "Detected Java {major}".format(major=major),
            fix_hint=None if major >= 8
            else "NeqSim requires JDK 8 or newer. " + _portable_jdk_hint()
        )
        # The installed plugin needs Java 21 for its MCP server. In a source
        # workspace, older supported JDKs may still run CLI-only checks, so report
        # the MCP limitation without failing unrelated workspace health checks.
        mcp_name = "Java version >= {n} (MCP server)".format(n=MCP_MIN_JAVA)
        mcp_message = "Detected Java {major}".format(major=major)
        mcp_hint = (
            "The NeqSim MCP server needs a JDK {n}+ (winget install "
            "EclipseAdoptium.Temurin.{n}.JDK). "
        ).format(n=MCP_MIN_JAVA) + _portable_jdk_hint()
        if major >= MCP_MIN_JAVA:
            _check(mcp_name, True, mcp_message)
        elif TOOLKIT_MODE:
            _check(mcp_name, False, mcp_message, fix_hint=mcp_hint)
        else:
            _warn(mcp_name, mcp_message, fix_hint=mcp_hint)
        if major < MCP_MIN_JAVA and java_on_path:
            _check_shadowed_jdk(major, java_home_valid, java_home)
    return major


def _check_shadowed_jdk(path_major, java_home_valid, java_home):
    """Report a newer JDK at JAVA_HOME hidden behind an older ``java`` on PATH.

    The plugin's MCP server is started as plain ``java``, so PATH order decides.
    On Windows the machine PATH precedes the user PATH, so a user-installed JDK
    cannot overtake an Oracle ``javapath`` or Software Center Java 8 without
    the older entry being removed.

    @param path_major major version of the ``java`` found first on PATH
    @param java_home_valid whether JAVA_HOME points at a JDK
    @param java_home the JAVA_HOME value
    """
    if not java_home_valid:
        return
    java_bin = os.path.join(java_home, "bin", "java.exe" if sys.platform.startswith("win") else "java")
    try:
        result = subprocess.run([java_bin, "-version"], capture_output=True, text=True, timeout=10)
    except Exception:  # noqa: BLE001 - JAVA_HOME already reported above
        return
    home_major = _parse_java_major(result.stderr or result.stdout or "")
    if home_major is None or home_major <= path_major:
        return
    path_java = shutil.which("java")
    hint = (
        "The MCP server runs `java` from PATH and finds the Java {p} at {pj}. "
        "The JDK {h} at JAVA_HOME is shadowed."
    ).format(p=path_major, pj=path_java, h=home_major)
    if sys.platform.startswith("win"):
        hint += (
            " On Windows the machine PATH comes before the user PATH, so adding your "
            "JDK to the user PATH is not enough: remove the old Java from the machine "
            "PATH (Software Center / IT), or start VS Code from a terminal where "
            "$env:PATH = \"{h}\\bin;$env:PATH\" so the session inherits the right java."
        ).format(h=java_home)
    _check("java on PATH is the newest JDK", False,
           "PATH -> Java {p}, JAVA_HOME -> Java {h}".format(p=path_major, h=home_major),
           fix_hint=hint)


def check_maven():
    """Check Maven wrapper is available."""
    print("\n--- Maven ---")
    if sys.platform == "win32":
        mvnw = os.path.join(PROJECT_ROOT, "mvnw.cmd")
    else:
        mvnw = os.path.join(PROJECT_ROOT, "mvnw")

    _check(
        "Maven wrapper",
        os.path.isfile(mvnw),
        "Found: {path}".format(path=os.path.basename(mvnw)) if os.path.isfile(mvnw)
        else "Not found",
        fix_hint="mvnw / mvnw.cmd should be in the repo root"
    )

    # Check .mvn directory
    mvn_dir = os.path.join(PROJECT_ROOT, ".mvn")
    _check(
        ".mvn directory",
        os.path.isdir(mvn_dir),
        "Found" if os.path.isdir(mvn_dir) else "Missing",
        fix_hint="The .mvn/ directory with wrapper jar should exist"
    )

    # Check pom.xml
    pom = os.path.join(PROJECT_ROOT, "pom.xml")
    _check(
        "pom.xml",
        os.path.isfile(pom),
        "Found" if os.path.isfile(pom) else "Missing",
    )


def check_neqsim_jar():
    """Check if the NeqSim JAR has been built."""
    print("\n--- NeqSim JAR ---")
    target_dir = os.path.join(PROJECT_ROOT, "target")
    if not os.path.isdir(target_dir):
        _check(
            "JAR built", False, "target/ directory not found",
            fix_hint="Run: mvnw.cmd package -DskipTests"
        )
        return

    jars = glob.glob(os.path.join(target_dir, "neqsim-*.jar"))
    # Filter out sources/javadoc jars
    main_jars = [j for j in jars if not any(
        s in j for s in ["-sources", "-javadoc", "-tests"]
    )]

    if main_jars:
        # Old versions linger in target/ after a version bump; judge the newest build.
        jar = max(main_jars, key=os.path.getmtime)
        mod_time = datetime.fromtimestamp(os.path.getmtime(jar))
        age_hours = (datetime.now() - mod_time).total_seconds() / 3600
        age_str = "{:.1f} hours ago".format(
            age_hours) if age_hours < 48 else "{:.0f} days ago".format(age_hours / 24)
        _check("JAR built", True, "{name} (built {age})".format(
            name=os.path.basename(jar), age=age_str
        ))
        if age_hours > 168:  # 1 week
            _warn("JAR freshness", "JAR is over a week old",
                  fix_hint="Rebuild: mvnw.cmd package -DskipTests")
    else:
        _check(
            "JAR built", False, "No neqsim-*.jar in target/",
            fix_hint="Run: mvnw.cmd package -DskipTests"
        )


def check_packaged_jar():
    """Toolkit mode: the NeqSim engine is the JAR inside the pip `neqsim` package."""
    print("\n--- NeqSim engine (packaged JAR) ---")
    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import neqsim_dev_setup
        jars = neqsim_dev_setup._find_packaged_jars()
    except Exception as error:  # noqa: BLE001
        _check("neqsim_dev_setup", False, str(error),
               fix_hint="Reinstall: pip install --force-reinstall neqsim-dev-setup")
        return
    if not jars:
        _check("Packaged NeqSim JAR", False, "no JAR from `pip install neqsim` and NEQSIM_JAR unset",
               fix_hint="{py} -m pip install neqsim   (or set NEQSIM_JAR)".format(py=sys.executable))
        return
    jar = jars[-1]
    _check("Packaged NeqSim JAR", True, os.path.basename(jar))
    try:
        flash_script = "".join([
            "from neqsim_dev_setup import neqsim_init, neqsim_classes;",
            "ns=neqsim_classes(neqsim_init(project_root='/nonexistent', verbose=False));",
            "f=ns.SystemSrkEos(298.15,50.0);f.addComponent('methane',1.0);f.setMixingRule('classic');",
            "ns.ThermodynamicOperations(f).TPflash();f.initProperties();",
            "print('FLASH_OK', round(float(f.getDensity('kg/m3')),2), len(ns.MISSING_CLASSES))",
        ])
        result = subprocess.run(
            [sys.executable, "-c", flash_script],
            capture_output=True, text=True, timeout=120)
        line = next((l for l in result.stdout.splitlines() if l.startswith("FLASH_OK")), None)
        if line:
            _, rho, missing = line.split()
            _check("JVM starts and flashes", True,
                   "methane at 25 C / 50 bara: {r} kg/m3".format(r=rho))
            if int(missing):
                _warn("Classes newer than the JAR",
                      "{n} classes on master are not in this release".format(n=missing),
                      fix_hint="Upgrade: pip install -U neqsim (or clone equinor/neqsim for latest)")
        else:
            _check("JVM starts and flashes", False,
                   (result.stderr or result.stdout).strip().splitlines()[-1:] or "no output",
                   fix_hint="Check java on PATH / JAVA_HOME and that jpype1 is installed")
    except Exception as error:  # noqa: BLE001
        _check("JVM starts and flashes", False, str(error))


def check_mcp_launcher(java_major):
    """Toolkit mode: the plugin's MCP server launcher and its cached jar."""
    print("\n--- NeqSim MCP server ---")
    cache = os.path.join(os.path.expanduser("~"), ".neqsim", "mcp-server")
    jars = glob.glob(os.path.join(cache, "neqsim-mcp-server-*-runner.jar"))
    data = os.environ.get("PLUGIN_DATA")
    if data:
        jars += glob.glob(os.path.join(data, "neqsim-mcp-server-*-runner.jar"))
    if jars:
        _check("Server jar cached", True, os.path.basename(sorted(jars)[-1]))
    else:
        _warn("Server jar cached", "not downloaded yet (fetched on first chat session that "
              "uses the plugin; ~85 MB from github.com/equinor/neqsim/releases)")
    marker = os.path.join(data or cache, "latest-release.txt")
    if os.path.isfile(marker):
        try:
            with open(marker, encoding="utf-8") as handle:
                parts = handle.read().split()
            age_h = (time.time() * 1000 - int(parts[1])) / 3.6e6
            _check("Tracking latest release", True,
                   "v{v}, checked {h:.0f} h ago (re-checked daily)".format(v=parts[0], h=age_h))
        except (IndexError, ValueError, OSError):
            _warn("Tracking latest release", "latest-release.txt unreadable; the launcher "
                  "re-resolves on next start")
    if java_major is not None and java_major < MCP_MIN_JAVA:
        _check("Server can start", False,
               "Java {m} < {n}".format(m=java_major, n=MCP_MIN_JAVA),
               fix_hint="Install a JDK {n}+ and put it first on PATH".format(n=MCP_MIN_JAVA))


def check_python_neqsim():
    """Check if the Python neqsim package is installed."""
    print("\n--- Python neqsim ---")
    try:
        result = subprocess.run(
            [sys.executable, "-c", "import neqsim; print(neqsim.__file__)"],
            capture_output=True, text=True, timeout=15
        )
        if result.returncode == 0:
            path = result.stdout.strip()
            _check("neqsim package", True, "Installed at: {p}".format(
                p=path[:80]
            ))

            # Verify the runtime classpath by loading a NeqSim class rather
            # than scanning for a JAR file on disk: the neqsim package resolves
            # its own classpath via jpype.addClassPath("lib/*") on JVM start.
            cls_check = subprocess.run(
                [sys.executable, "-c",
                 "from neqsim import jneqsim;"
                 "f = jneqsim.thermo.system.SystemSrkEos(298.15, 10.0);"
                 "f.addComponent('methane', 1.0);"
                 "print('CLASSPATH_OK', f.getNumberOfComponents())"],
                capture_output=True, text=True, timeout=90,
            )
            if cls_check.returncode == 0 and "CLASSPATH_OK" in cls_check.stdout:
                _check("NeqSim classpath", True,
                       "NeqSim classes load correctly")
                _check_duplicate_runtime_jars(path)
            else:
                _check(
                    "NeqSim classpath", False,
                    "Could not load NeqSim classes: {err}".format(
                        err=(cls_check.stderr.strip()
                             or cls_check.stdout.strip())[:120]),
                    fix_hint="Reinstall: pip install --force-reinstall neqsim"
                )
        else:
            # Fallback: local repository runtime via devtools/neqsim_dev_setup.py
            dev_setup = os.path.join(
                PROJECT_ROOT, "devtools", "neqsim_dev_setup.py")
            if os.path.isfile(dev_setup):
                local_check_code = (
                    "import sys;"
                    "sys.path.insert(0, {devtools_path!r});"
                    "from neqsim_dev_setup import neqsim_init, neqsim_classes;"
                    "ns = neqsim_init(project_root={project_root!r}, recompile=False, verbose=False);"
                    "ns = neqsim_classes(ns);"
                    "fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0);"
                    "fluid.addComponent('methane', 1.0);"
                    "fluid.setMixingRule('classic');"
                    "print('LOCAL_RUNTIME_OK')"
                ).format(
                    devtools_path=os.path.join(PROJECT_ROOT, "devtools"),
                    project_root=PROJECT_ROOT,
                )
                local_result = subprocess.run(
                    [sys.executable, "-c", local_check_code],
                    capture_output=True, text=True, timeout=90,
                )
                if local_result.returncode == 0 and "LOCAL_RUNTIME_OK" in local_result.stdout:
                    _warn(
                        "neqsim package", "Not installed (optional in repository development mode)")
                    _check("Local dev runtime", True,
                           "Available via devtools/neqsim_dev_setup.py")
                    return

            _check(
                "neqsim package", False,
                "Not installed: {err}".format(err=result.stderr.strip()[:100]),
                fix_hint="pip install neqsim or run from the repo with devtools"
            )
    except Exception as e:
        _check(
            "neqsim package", False, str(e),
            fix_hint="pip install neqsim or run from the repo with devtools"
        )


def _check_duplicate_runtime_jars(neqsim_init_path):
    """Flag several neqsim-*.jar versions sharing the runtime lib/ directory.

    The package adds ``lib/*`` to the classpath, so a leftover older JAR is
    loaded alongside the current one. Classes then resolve across two versions
    of the same package and fail late with IllegalAccessError/NoSuchMethodError
    instead of anything that points at the real cause.
    """
    lib_dir = os.path.join(os.path.dirname(neqsim_init_path), "lib")
    if not os.path.isdir(lib_dir):
        return
    jars = [os.path.basename(j)
            for j in glob.glob(os.path.join(lib_dir, "neqsim-*.jar"))
            if not any(s in os.path.basename(j)
                       for s in ("-sources", "-javadoc", "-tests"))]
    if len(jars) > 1:
        _check(
            "Single NeqSim JAR on runtime classpath", False,
            "{n} versions in {d}: {names}".format(
                n=len(jars), d=lib_dir, names=", ".join(sorted(jars))),
            fix_hint="Delete the stale JAR(s); keep only the current version"
        )
    elif jars:
        _check("Single NeqSim JAR on runtime classpath", True, jars[0])


def check_agent_files():
    """Check agent and skill files are well-formed."""
    print("\n--- Agent System ---")

    # Check key instruction files exist
    for name, path in [
        ("AGENTS.md", os.path.join(PROJECT_ROOT, "AGENTS.md")),
        ("CONTEXT.md", os.path.join(PROJECT_ROOT, "CONTEXT.md")),
        ("copilot-instructions.md", os.path.join(PROJECT_ROOT,
         ".github", "copilot-instructions.md")),
        ("CLAUDE.md", os.path.join(PROJECT_ROOT, "CLAUDE.md")),
    ]:
        _check(name, os.path.isfile(path),
               "Found" if os.path.isfile(path) else "Missing",
               fix_hint="This file should exist in the repo root" if not os.path.isfile(path) else None)

    # Count agents
    agents_dir = os.path.join(PROJECT_ROOT, ".github", "agents")
    if os.path.isdir(agents_dir):
        agents = [f for f in os.listdir(agents_dir) if f.endswith(".agent.md")]
        _check("Agents", True, "{n} agent files found".format(n=len(agents)))

        # Check each agent has YAML frontmatter
        bad_agents = []
        for a in agents:
            with open(os.path.join(agents_dir, a), "r", encoding="utf-8") as f:
                content = f.read(500)
                if not content.startswith("---"):
                    bad_agents.append(a)
        if bad_agents:
            _warn("Agent frontmatter",
                  "{n} agent(s) missing YAML frontmatter: {names}".format(
                      n=len(bad_agents),
                      names=", ".join(bad_agents[:5])
                  ))
    else:
        _check("Agents", False, "No .github/agents/ directory")

    # Count skills
    skills_dir = os.path.join(PROJECT_ROOT, ".github", "skills")
    if os.path.isdir(skills_dir):
        skills = [d for d in os.listdir(skills_dir)
                  if os.path.isdir(os.path.join(skills_dir, d))]
        _check("Skills", True, "{n} skill folders found".format(n=len(skills)))

        # Check each skill has SKILL.md
        missing_skill_md = []
        for s in skills:
            skill_file = os.path.join(skills_dir, s, "SKILL.md")
            if not os.path.isfile(skill_file):
                missing_skill_md.append(s)
        if missing_skill_md:
            _check("Skill files", False,
                   "{n} skill(s) missing SKILL.md: {names}".format(
                       n=len(missing_skill_md),
                       names=", ".join(missing_skill_md[:5])
                   ),
                   fix_hint="Each skill folder needs a SKILL.md file")
        else:
            _check("Skill files", True, "All skills have SKILL.md")

        # Check skills have YAML frontmatter with required fields
        bad_skills = []
        for s in skills:
            skill_file = os.path.join(skills_dir, s, "SKILL.md")
            if os.path.isfile(skill_file):
                with open(skill_file, "r", encoding="utf-8") as f:
                    content = f.read(500)
                    if not content.startswith("---"):
                        bad_skills.append(s)
                    elif "description:" not in content[:500]:
                        bad_skills.append(s)
        if bad_skills:
            _warn("Skill frontmatter",
                  "{n} skill(s) with incomplete frontmatter: {names}".format(
                      n=len(bad_skills),
                      names=", ".join(bad_skills[:5])
                  ))
    else:
        _check("Skills", False, "No .github/skills/ directory")


def check_cross_tool_files():
    """Check that cross-tool agent config files exist."""
    print("\n--- Cross-Tool Config ---")
    files = {
        "CLAUDE.md": "Claude Code",
        ".cursorrules": "Cursor",
        ".windsurfrules": "Windsurf",
    }
    for filename, tool in files.items():
        path = os.path.join(PROJECT_ROOT, filename)
        _check(
            "{tool} config".format(tool=tool),
            os.path.isfile(path),
            "Found: {f}".format(f=filename) if os.path.isfile(path)
            else "Missing: {f}".format(f=filename),
            fix_hint="Create {f} pointing to AGENTS.md".format(f=filename)
            if not os.path.isfile(path) else None
        )


def check_cli_on_path():
    """Check that the ``neqsim`` console script resolves on PATH.

    Every docs page, agent instruction and install message is written as
    ``neqsim <command>``. When the script directory is missing from PATH the
    command is "not recognized" and the user falls back to
    ``python -m neqsim_cli`` -- which works, so a broken PATH is easy to live
    with and easy to never report.

    @return ``None``
    """
    print("\n--- CLI command ---")
    interpreter = os.path.splitext(os.path.basename(sys.executable))[0]
    module_form = "{exe} -m neqsim_cli".format(exe=interpreter)
    resolved = shutil.which("neqsim")

    if resolved:
        _check("'neqsim' command", True, resolved)
        try:
            own_scripts = sysconfig.get_path("scripts")
        except (KeyError, ValueError):
            own_scripts = None
        if own_scripts and not _same_dir(os.path.dirname(resolved), own_scripts):
            _warn(
                "'neqsim' interpreter",
                "the command on PATH comes from {found}, not from the "
                "interpreter running this check ({own})".format(
                    found=os.path.dirname(resolved), own=own_scripts),
                fix_hint="These are different installs and can disagree. Use "
                         "'{mod}' to be certain which one you "
                         "run.".format(mod=module_form),
            )
        return

    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import ensure_on_path
        script_dir = ensure_on_path.find_script_dir()
    except Exception:
        script_dir = None

    if script_dir:
        message = "installed in {dir} but that folder is not on PATH".format(
            dir=script_dir)
        venv = os.environ.get("VIRTUAL_ENV")
        in_venv = venv and _same_dir(
            script_dir,
            os.path.join(venv, "Scripts" if sys.platform.startswith("win") else "bin"))
        if in_venv:
            # A venv is activated per terminal, so "open a new terminal" is the
            # wrong advice here and a reboot changes nothing.
            fix = ("activate the virtualenv in this terminal ({venv}); it is "
                   "not activated, only VIRTUAL_ENV is set. A new terminal or "
                   "a reboot will not help. '{mod}' works "
                   "meanwhile.".format(venv=venv, mod=module_form))
        else:
            fix = ("run '{exe} devtools/ensure_on_path.py', then open a NEW "
                   "terminal -- in VS Code quit and reopen the window, a new "
                   "integrated terminal is not enough. '{mod}' works "
                   "meanwhile.".format(exe=interpreter, mod=module_form))
    else:
        message = "not on PATH, and no installed script was found"
        fix = ("reinstall with 'install.cmd' (Windows) or './install.sh', then "
               "open a new terminal. '{mod}' works "
               "meanwhile.".format(mod=module_form))
    _check("'neqsim' command", False, message, fix_hint=fix)


def _same_dir(a, b):
    """Return whether two paths refer to the same directory.

    @param a first path
    @param b second path
    @return ``True`` when both normalize to the same directory
    """
    return (os.path.normcase(os.path.normpath(a))
            == os.path.normcase(os.path.normpath(b)))


def check_devtools():
    """Check devtools scripts are available."""
    print("\n--- DevTools ---")
    scripts = [
        ("new_task.py", "Task scaffolding"),
        ("new_skill.py", "Skill scaffolding"),
        ("install_agent.py", "Agent installer"),
        ("consistency_checker.py", "Consistency checker"),
        ("neqsim_dev_setup.py", "Dev setup for notebooks"),
    ]
    for script, desc in scripts:
        path = os.path.join(SCRIPT_DIR, script)
        _check(
            desc,
            os.path.isfile(path),
            "Found" if os.path.isfile(path) else "Missing",
        )


def check_task_root():
    """Report the folder new task folders are created in."""
    print("\n--- Task destination ---")
    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import new_task
        resolved = new_task.resolve_task_root()
    except Exception as error:
        _check("Task root", False, str(error),
               fix_hint="Set a valid folder: neqsim --set-task-root \"PATH\"")
        return
    if os.environ.get("NEQSIM_TASK_ROOT"):
        source = "from NEQSIM_TASK_ROOT"
    elif os.path.exists(new_task.task_defaults_path()):
        source = "saved in ~/.neqsim/task_defaults.json"
    else:
        source = "repository default - change with: neqsim --set-task-root \"PATH\""
    _check("New tasks are created in", True,
           "{root} ({source})".format(root=resolved, source=source))


def check_report_template():
    """Report the Word template every generated task report is built from."""
    print("\n--- Report template ---")
    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import new_task
        template = new_task.resolve_report_template()
    except Exception as error:
        _check("Report template", False, str(error),
               fix_hint="Set a valid file: neqsim --set-report-template \"PATH\" "
                        "(or neqsim --reset-report-template)")
        return
    if not template:
        _check("Word reports use", True,
               "built-in styling - change with: neqsim --set-report-template \"PATH\"")
        return
    source = ("from NEQSIM_REPORT_TEMPLATE" if os.environ.get("NEQSIM_REPORT_TEMPLATE")
              else "saved in ~/.neqsim/task_defaults.json")
    _check("Word reports are built from", True,
           "{template} ({source})".format(template=template, source=source))


def check_document_root():
    """Report the folder agents read source documents from."""
    print("\n--- Source documents ---")
    if SCRIPT_DIR not in sys.path:
        sys.path.insert(0, SCRIPT_DIR)
    try:
        import new_task
        root = new_task.resolve_document_root()
    except Exception as error:
        _check("Document root", False, str(error),
               fix_hint="Set an existing folder: neqsim --set-document-root \"PATH\" "
                        "(or neqsim --reset-document-root)")
        return
    if not root:
        _check("Documents are read from", True,
               "not configured - set one with: neqsim --set-document-root \"PATH\"")
        return
    source = ("from NEQSIM_DOCUMENT_ROOT" if os.environ.get("NEQSIM_DOCUMENT_ROOT")
              else "saved in ~/.neqsim/task_defaults.json")
    _check("Documents are read from", True,
           "{root} and all subfolders ({source})".format(root=root, source=source))


def check_git():
    """Check git status."""
    print("\n--- Git ---")
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            capture_output=True, text=True, timeout=5,
            cwd=PROJECT_ROOT
        )
        if result.returncode == 0:
            branch = result.stdout.strip()
            _check("Git branch", True, branch)
        else:
            _check("Git", False, "Not a git repository")
    except FileNotFoundError:
        _check("Git", False, "git not found on PATH",
               fix_hint="Install git")
    except Exception as e:
        _check("Git", False, str(e))


# ══════════════════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════════════════

def main(argv=None):
    """Run health checks, optionally skipping the build artifact for CLI setup."""
    argv = sys.argv[1:] if argv is None else argv
    _results.clear()
    print("=" * 60)
    print("  NeqSim Doctor - Environment Diagnostic")
    print("  Mode: {m}".format(
        m="toolkit (pip / agent plugin, no source checkout)" if TOOLKIT_MODE
        else "workspace (source checkout at {r})".format(r=PROJECT_ROOT)))
    print("=" * 60)

    java_major = check_java()
    if TOOLKIT_MODE:
        # Build tooling is irrelevant here; what matters is that the packaged
        # engine, the MCP launcher, the CLI and the task/document folders work.
        check_packaged_jar()
        check_mcp_launcher(java_major)
        check_cli_on_path()
        check_task_root()
        check_report_template()
        check_document_root()
    else:
        check_maven()
        if "--skip-jar" in argv:
            _warn("JAR built", "Not checked (--skip-jar); build the JAR before simulations")
        else:
            check_neqsim_jar()
        check_python_neqsim()
        check_cli_on_path()
        check_agent_files()
        check_cross_tool_files()
        check_devtools()
        check_task_root()
        check_report_template()
        check_document_root()
        check_git()

    # Summary
    passed = sum(1 for r in _results if r["passed"])
    failed = sum(1 for r in _results if not r["passed"])
    total = len(_results)

    print("\n" + "=" * 60)
    print("  Summary: {passed}/{total} checks passed".format(
        passed=passed, total=total
    ))
    if failed > 0:
        print("  {failed} issue(s) found — see [!!] items above".format(
            failed=failed
        ))
        print()
        print("  Quick fixes:")
        for r in _results:
            if not r["passed"] and r.get("fix_hint"):
                print("    - {name}: {hint}".format(
                    name=r["name"], hint=r["fix_hint"]
                ))
    else:
        print("  All checks passed! Environment is ready.")
    print("=" * 60)

    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
