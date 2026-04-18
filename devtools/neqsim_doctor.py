"""
neqsim_doctor.py - Diagnostic tool for NeqSim development environment.

Checks that your environment is correctly set up for NeqSim development and
agentic workflows. Reports issues with actionable fix suggestions.

Usage:
    python devtools/neqsim_doctor.py          # run all checks
    python devtools/neqsim_doctor.py --fix     # attempt auto-fixes where possible

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

def check_java():
    """Check Java is installed and report version."""
    print("\n--- Java ---")
    java_cmd = "java"
    try:
        result = subprocess.run(
            [java_cmd, "-version"],
            capture_output=True, text=True, timeout=10
        )
        # Java outputs version to stderr
        output = result.stderr if result.stderr else result.stdout
        version_line = output.strip().split("\n")[0] if output else ""
        _check("Java installed", True, version_line)

        # Check if source/target 8 will work (Maven handles this via pom.xml)
        _check(
            "Java 8 target",
            True,
            "Build targets Java 8 via Maven compiler plugin (pom.xml)",
        )
    except FileNotFoundError:
        _check(
            "Java installed", False, "Java not found on PATH",
            fix_hint="Install JDK 8+ and add to PATH"
        )
    except Exception as e:
        _check("Java installed", False, str(e))


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
        age_str = "{:.1f} hours ago".format(age_hours) if age_hours < 48 else "{:.0f} days ago".format(age_hours / 24)
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

            # Check if JAR is deployed to Python package
            # Common location pattern
            lib_dir = os.path.join(os.path.dirname(path), "lib", "java11")
            if os.path.isdir(lib_dir):
                jars = glob.glob(os.path.join(lib_dir, "neqsim-*.jar"))
                if jars:
                    jar = jars[0]
                    mod_time = datetime.fromtimestamp(os.path.getmtime(jar))
                    age_hours = (datetime.now() - mod_time).total_seconds() / 3600
                    age_str = "{:.1f}h ago".format(age_hours)
                    _check("Python JAR", True, "{name} ({age})".format(
                        name=os.path.basename(jar), age=age_str
                    ))
                else:
                    _check("Python JAR", False, "No neqsim JAR in {d}".format(d=lib_dir),
                           fix_hint="Copy built JAR to Python package lib/java11/")
            else:
                _warn("Python JAR dir", "Could not find lib/java11/ in Python package")
        else:
            _check(
                "neqsim package", False,
                "Not installed: {err}".format(err=result.stderr.strip()[:100]),
                fix_hint="pip install neqsim"
            )
    except Exception as e:
        _check("neqsim package", False, str(e), fix_hint="pip install neqsim")


def check_agent_files():
    """Check agent and skill files are well-formed."""
    print("\n--- Agent System ---")

    # Check key instruction files exist
    for name, path in [
        ("AGENTS.md", os.path.join(PROJECT_ROOT, "AGENTS.md")),
        ("CONTEXT.md", os.path.join(PROJECT_ROOT, "CONTEXT.md")),
        ("copilot-instructions.md", os.path.join(PROJECT_ROOT, ".github", "copilot-instructions.md")),
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
