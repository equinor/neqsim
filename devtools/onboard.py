"""
onboard.py - Interactive onboarding wizard for new NeqSim contributors.

Walks through environment setup step by step: checks prerequisites, builds
the project, installs the Python package, verifies the agent system, and
runs a quick simulation to confirm everything works.

Usage:
    neqsim onboard           # interactive walkthrough
    neqsim onboard --check   # just check, don't fix anything
    neqsim onboard --skip-build  # skip Maven build (if already built)

Inspired by OpenClaw's `openclaw onboard` pattern.
"""
import glob
import os
import subprocess
import sys
from datetime import datetime


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# ── Helpers ──────────────────────────────────────────────

def _banner(text):
    print("\n" + "=" * 60)
    print("  " + text)
    print("=" * 60)


def _step(num, title):
    print("\n--- Step {n}: {t} ---".format(n=num, t=title))


def _ok(msg):
    print("  [OK] " + msg)


def _fail(msg):
    print("  [!!] " + msg)


def _info(msg):
    print("  [..] " + msg)


def _ask_continue(prompt="Continue?"):
    """Ask user to press Enter or type 'skip' to skip."""
    try:
        answer = input("  {p} [Enter=yes, s=skip, q=quit] ".format(p=prompt))
    except (EOFError, KeyboardInterrupt):
        print()
        sys.exit(0)
    answer = answer.strip().lower()
    if answer in ("q", "quit", "exit"):
        print("  Exiting.")
        sys.exit(0)
    if answer in ("s", "skip"):
        return False
    return True


def _run_cmd(cmd, cwd=None, timeout=300):
    """Run a command and return (success, stdout, stderr)."""
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True,
            cwd=cwd or PROJECT_ROOT, timeout=timeout
        )
        return result.returncode == 0, result.stdout, result.stderr
    except FileNotFoundError:
        return False, "", "Command not found: {c}".format(c=cmd[0])
    except subprocess.TimeoutExpired:
        return False, "", "Command timed out after {t}s".format(t=timeout)


# ══════════════════════════════════════════════════════════
# Steps
# ══════════════════════════════════════════════════════════

def step_welcome():
    _banner("NeqSim Onboarding Wizard")
    print()
    print("  This wizard will set up your NeqSim development environment.")
    print("  It checks prerequisites, builds the project, and verifies")
    print("  everything works — including the agentic AI system.")
    print()
    print("  You can skip any step or quit at any time.")
    print()
    print("  Project root: {r}".format(r=PROJECT_ROOT))


def step_java(check_only=False):
    _step(1, "Java")
    ok, stdout, stderr = _run_cmd(["java", "-version"])
    output = stderr if stderr else stdout
    version_line = output.strip().split("\n")[0] if output else "unknown"

    if ok:
        _ok("Java found: {v}".format(v=version_line))
        _info("NeqSim builds target Java 8 bytecode via Maven compiler plugin.")
        _info("Any JDK 8+ works for development.")
        return True
    else:
        _fail("Java not found on PATH.")
        print()
        print("  To fix:")
        print("    1. Install JDK 8 or later (e.g., Adoptium Temurin, Oracle JDK)")
        print("    2. Add the JDK bin/ directory to your PATH")
        print("    3. Verify: java -version")
        print()
        print("  Download: https://adoptium.net/")
        return False


def step_maven(check_only=False):
    _step(2, "Maven Wrapper")
    if sys.platform == "win32":
        mvnw = os.path.join(PROJECT_ROOT, "mvnw.cmd")
    else:
        mvnw = os.path.join(PROJECT_ROOT, "mvnw")

    if os.path.isfile(mvnw):
        _ok("Maven wrapper found: {f}".format(f=os.path.basename(mvnw)))
    else:
        _fail("Maven wrapper not found at {f}".format(f=mvnw))
        print("  This file should exist after cloning the repo.")
        return False

    # Quick check that it runs
    _info("Testing Maven wrapper...")
    ok, stdout, stderr = _run_cmd([mvnw, "--version"], timeout=30)
    if ok:
        lines = stdout.strip().split("\n")
        maven_line = lines[0] if lines else "unknown"
        _ok("Maven works: {v}".format(v=maven_line[:80]))
        return True
    else:
        _fail("Maven wrapper failed to run.")
        if stderr:
            print("  Error: {e}".format(e=stderr.strip()[:200]))
        return False


def step_build(check_only=False, skip=False):
    _step(3, "Build NeqSim JAR")

    # Check if JAR already exists
    target_dir = os.path.join(PROJECT_ROOT, "target")
    existing_jars = []
    if os.path.isdir(target_dir):
        existing_jars = [
            j for j in glob.glob(os.path.join(target_dir, "neqsim-*.jar"))
            if not any(s in j for s in ["-sources", "-javadoc", "-tests"])
        ]

    if existing_jars:
        jar = existing_jars[0]
        mod_time = datetime.fromtimestamp(os.path.getmtime(jar))
        age_hours = (datetime.now() - mod_time).total_seconds() / 3600
        _ok("JAR already exists: {n} ({h:.0f}h old)".format(
            n=os.path.basename(jar), h=age_hours
        ))
        if check_only or skip:
            return True
        if age_hours < 24:
            _info("JAR is recent. You can skip rebuilding.")
            if not _ask_continue("Rebuild anyway?"):
                return True
    elif check_only or skip:
        _fail("No JAR found in target/. Run without --check or --skip-build.")
        return False

    if check_only:
        return bool(existing_jars)

    _info("Building NeqSim (this takes 1-3 minutes)...")
    _info("Running: mvnw package -DskipTests")
    print()

    if sys.platform == "win32":
        mvnw = os.path.join(PROJECT_ROOT, "mvnw.cmd")
    else:
        mvnw = os.path.join(PROJECT_ROOT, "mvnw")

    # Run build with live output
    try:
        proc = subprocess.Popen(
            [mvnw, "package", "-DskipTests", "-Dmaven.javadoc.skip=true"],
            cwd=PROJECT_ROOT,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True
        )
        last_lines = []
        for line in proc.stdout:
            line = line.rstrip()
            last_lines.append(line)
            if len(last_lines) > 5:
                last_lines.pop(0)
            # Print progress indicators
            if "[INFO] BUILD" in line or "[ERROR]" in line or "Compiling" in line:
                print("    " + line)
        proc.wait(timeout=600)

        if proc.returncode == 0:
            _ok("Build successful!")
            # Find the JAR
            new_jars = [
                j for j in glob.glob(os.path.join(target_dir, "neqsim-*.jar"))
                if not any(s in j for s in ["-sources", "-javadoc", "-tests"])
            ]
            if new_jars:
                _ok("JAR: {n}".format(n=os.path.basename(new_jars[0])))
            return True
        else:
            _fail("Build failed (exit code {c})".format(c=proc.returncode))
            print("  Last output:")
            for line in last_lines:
                print("    " + line)
            return False
    except subprocess.TimeoutExpired:
        _fail("Build timed out after 10 minutes.")
        return False
    except Exception as e:
        _fail("Build error: {e}".format(e=e))
        return False


def step_python_neqsim(check_only=False):
    _step(4, "Python neqsim Package")

    ok, stdout, stderr = _run_cmd(
        [sys.executable, "-c", "import neqsim; print(neqsim.__file__)"],
        timeout=15
    )

    if ok:
        path = stdout.strip()
        _ok("neqsim package installed: {p}".format(p=path[:80]))

        # Check JAR in Python package
        lib_dir = os.path.join(os.path.dirname(path), "lib", "java11")
        if os.path.isdir(lib_dir):
            jars = glob.glob(os.path.join(lib_dir, "neqsim-*.jar"))
            if jars:
                _ok("JAR deployed to Python package: {n}".format(
                    n=os.path.basename(jars[0])
                ))
            else:
                _fail("No neqsim JAR in Python package lib/java11/")
                _info("Copy the built JAR:")
                _info("  Copy-Item target/neqsim-*.jar {d}".format(d=lib_dir))
        return True
    else:
        _fail("Python neqsim package not installed.")
        print()
        print("  To fix:")
        print("    pip install neqsim")
        print()
        if check_only:
            return False
        if _ask_continue("Install neqsim now?"):
            _info("Running: pip install neqsim")
            ok2, stdout2, stderr2 = _run_cmd(
                [sys.executable, "-m", "pip", "install", "neqsim"],
                timeout=120
            )
            if ok2:
                _ok("neqsim installed successfully!")
                return True
            else:
                _fail("Installation failed: {e}".format(
                    e=stderr2.strip()[:200]
                ))
                return False
        return False


def step_devtools(check_only=False):
    _step(5, "Developer Tools")

    # Check if devtools is pip-installed
    ok, stdout, stderr = _run_cmd(
        [sys.executable, "-c", "import neqsim_dev_setup; print('OK')"],
        timeout=10
    )

    if ok:
        _ok("devtools package installed (neqsim_dev_setup)")
    else:
        _info("devtools package not installed (optional, for Jupyter dev workflow)")
        if not check_only:
            print()
            print("  This enables live Java editing from Jupyter notebooks.")
            print("  Install with: pip install -e devtools/")
            if _ask_continue("Install devtools now?"):
                ok2, stdout2, stderr2 = _run_cmd(
                    [sys.executable, "-m", "pip", "install", "-e",
                     os.path.join(PROJECT_ROOT, "devtools")],
                    timeout=60
                )
                if ok2:
                    _ok("devtools installed!")
                else:
                    _fail("Install failed: {e}".format(
                        e=stderr2.strip()[:200]
                    ))

    # Check key scripts
    scripts = ["new_task.py", "new_skill.py", "neqsim_doctor.py"]
    for s in scripts:
        path = os.path.join(SCRIPT_DIR, s)
        if os.path.isfile(path):
            _ok("Found: devtools/{s}".format(s=s))
        else:
            _info("Missing: devtools/{s}".format(s=s))

    return True


def step_agents(check_only=False):
    _step(6, "Agent System")

    agents_dir = os.path.join(PROJECT_ROOT, ".github", "agents")
    skills_dir = os.path.join(PROJECT_ROOT, ".github", "skills")

    # Count agents
    if os.path.isdir(agents_dir):
        agents = [f for f in os.listdir(agents_dir) if f.endswith(".agent.md")]
        _ok("{n} agents found in .github/agents/".format(n=len(agents)))
    else:
        _fail("No .github/agents/ directory")

    # Count skills
    if os.path.isdir(skills_dir):
        skills = [d for d in os.listdir(skills_dir)
                  if os.path.isdir(os.path.join(skills_dir, d))]
        _ok("{n} skills found in .github/skills/".format(n=len(skills)))
    else:
        _fail("No .github/skills/ directory")

    # Check cross-tool configs
    configs = {
        "AGENTS.md": "Codex / Claude Code / generic",
        "CLAUDE.md": "Claude Code",
        ".cursorrules": "Cursor",
        ".windsurfrules": "Windsurf",
        ".github/copilot-instructions.md": "VS Code Copilot",
    }
    for filename, tool in configs.items():
        path = os.path.join(PROJECT_ROOT, filename)
        if os.path.isfile(path):
            _ok("{tool}: {f}".format(tool=tool, f=filename))
        else:
            _info("Missing {f} ({tool})".format(f=filename, tool=tool))

    return True


def step_verify(check_only=False):
    _step(7, "Quick Verification")

    if check_only:
        _info("Skipping simulation test in --check mode.")
        return True

    _info("Running a quick NeqSim simulation via Python...")

    test_code = """
import sys
try:
    from neqsim import jneqsim
    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()
    density = fluid.getDensity("kg/m3")
    print("SUCCESS density={d:.2f} kg/m3".format(d=density))
except Exception as e:
    print("FAILED: {e}".format(e=e))
    sys.exit(1)
"""

    ok, stdout, stderr = _run_cmd(
        [sys.executable, "-c", test_code],
        timeout=60
    )

    if ok and "SUCCESS" in stdout:
        _ok(stdout.strip())
        return True
    else:
        _fail("Simulation test failed.")
        if stdout:
            print("  stdout: " + stdout.strip()[:300])
        if stderr:
            print("  stderr: " + stderr.strip()[:300])
        print()
        print("  Common fixes:")
        print("  - Rebuild JAR and copy to Python package")
        print("  - Check Java is on PATH")
        print("  - Try: pip install --upgrade neqsim")
        return False


def step_done(results):
    _banner("Onboarding Complete")
    print()
    passed = sum(1 for r in results if r)
    total = len(results)
    print("  {p}/{t} steps passed.".format(p=passed, t=total))
    print()

    if passed == total:
        print("  Your environment is ready! Here's what to try next:")
        print()
        print("  1. Run the doctor:     neqsim doctor")
        print("  2. Create a task:      neqsim new-task \"My task\"")
        print("  3. Create a skill:     neqsim new-skill \"my-topic\"")
        print("  4. Use an agent:       @solve.task JT cooling for rich gas")
        print("  5. Read the docs:      AGENTS.md, CONTEXT.md, CONTRIBUTING.md")
    else:
        print("  Some steps had issues — see the [!!] items above.")
        print("  Fix them and run this wizard again, or use:")
        print("    neqsim doctor")
        print("  for a detailed diagnostic.")

    print()
    print("=" * 60)


# ══════════════════════════════════════════════════════════
# Main
# ══════════════════════════════════════════════════════════

def main():
    args = sys.argv[1:]
    check_only = "--check" in args
    skip_build = "--skip-build" in args

    if "--help" in args or "-h" in args:
        print("Usage:")
        print("  neqsim onboard            # interactive walkthrough")
        print("  neqsim onboard --check    # just check, don't fix")
        print("  neqsim onboard --skip-build  # skip Maven build")
        sys.exit(0)

    results = []

    step_welcome()

    if not _ask_continue("Ready to start?"):
        sys.exit(0)

    results.append(step_java(check_only))
    results.append(step_maven(check_only))
    results.append(step_build(check_only, skip=skip_build))
    results.append(step_python_neqsim(check_only))
    results.append(step_devtools(check_only))
    results.append(step_agents(check_only))
    results.append(step_verify(check_only))

    step_done(results)

    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
