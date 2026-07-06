"""
neqsim_doctor.py - Diagnostic tool for NeqSim development environment.

Checks that your environment is correctly set up for NeqSim development and
agentic workflows. Reports issues with actionable fix suggestions.

Usage:
    neqsim doctor          # run all checks
    neqsim doctor --fix    # attempt auto-fixes where possible

Inspired by OpenClaw's `openclaw doctor` pattern.
"""
import glob
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

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
        jar = main_jars[0]
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

def main():
    print("=" * 60)
    print("  NeqSim Doctor - Environment Diagnostic")
    print("=" * 60)

    check_java()
    check_maven()
    check_neqsim_jar()
    check_python_neqsim()
    check_agent_files()
    check_cross_tool_files()
    check_devtools()
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
