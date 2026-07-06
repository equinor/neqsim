#!/usr/bin/env python3
"""
neqsim_try.py - Interactive playground for NeqSim.

Drops the user into a Python REPL with neqsim pre-imported and a sample
fluid ready to explore.  Zero setup, instant "aha moment".

Usage:
    neqsim try                # interactive REPL with sample fluid
    neqsim try --demo         # run a demo, print results, then drop into REPL
"""
import os
import sys
import textwrap
import importlib.util
from pathlib import Path

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# Preamble for the pip-installed neqsim package.
# Do NOT hand-build a JAR classpath here: the neqsim package resolves its own
# classpath (jpype.addClassPath("lib/*")) when the JVM autostarts on import.
# We only inject the Java 22+ native-access flag, which the package reads from
# the NEQSIM_JVM_ARGS environment variable before it starts the JVM.
_PIP_JVM_PREAMBLE = textwrap.dedent("""\
    import os, subprocess as _sp, re as _re
    try:
        _ver = _sp.check_output(["java", "-version"], stderr=_sp.STDOUT, text=True)
        _m = _re.search(r'"(\\d+)', _ver)
        if _m and int(_m.group(1)) >= 22:
            _flag = "--enable-native-access=ALL-UNNAMED"
            _existing = os.environ.get("NEQSIM_JVM_ARGS", "")
            if _flag not in _existing:
                os.environ["NEQSIM_JVM_ARGS"] = (_existing + " " + _flag).strip()
    except Exception:
        pass
""")


def _build_pip_demo_code():
    """Return demo startup code for pip-installed neqsim."""
    return _PIP_JVM_PREAMBLE + textwrap.dedent("""\
    from neqsim import jneqsim

    # -- Create a natural gas fluid ------------------------------------
    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")

    # -- Flash calculation -----------------------------------------------
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    # -- Read results ----------------------------------------------------
    gas = fluid.getPhase("gas")
    print()
    print("  Natural gas at 25 C, 60 bara")
    print("  -------------------------------------")
    print("  Density:            {:.2f} kg/m3".format(gas.getDensity("kg/m3")))
    print("  Viscosity:          {:.6f} kg/(m*s)".format(gas.getViscosity("kg/msec")))
    print("  Thermal cond.:      {:.4f} W/(m*K)".format(gas.getThermalConductivity("W/mK")))
    print("  Z-factor:           {:.4f}".format(gas.getZ()))
    print("  Molar mass:         {:.4f} kg/mol".format(fluid.getMolarMass("kg/mol")))
    print("  Number of phases:   {}".format(fluid.getNumberOfPhases()))
    print()
    print("  Try: fluid.setTemperature(273.15 + 80.0)")
    print("       ops.TPflash(); fluid.initProperties()")
    print("       gas.getDensity('kg/m3')")
    print()
""")


def _build_pip_repl_code():
    """Return REPL startup code for pip-installed neqsim."""
    return _PIP_JVM_PREAMBLE + textwrap.dedent("""\
        from neqsim import jneqsim

        fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
        fluid.addComponent("methane", 0.85)
        fluid.addComponent("ethane", 0.10)
        fluid.addComponent("propane", 0.05)
        fluid.setMixingRule("classic")

        ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.initProperties()
        gas = fluid.getPhase("gas")

        print("  Ready!  Variables: fluid, ops, gas")
        print()
        print("  Quick examples:")
        print("    gas.getDensity('kg/m3')")
        print("    gas.getViscosity('kg/msec')")
        print("    fluid.setTemperature(273.15 + 80.0)")
        print("    ops.TPflash(); fluid.initProperties()")
        print("    gas.getDensity('kg/m3')")
        print()
    """)


def _build_devtools_bootstrap():
    """Return startup bootstrap that loads classes from the local repository."""
    return textwrap.dedent("""\
        import sys
        from pathlib import Path

        PROJECT_ROOT = Path({project_root!r})
        sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

        from neqsim_dev_setup import neqsim_init, neqsim_classes

        ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=False)
        ns = neqsim_classes(ns)
    """).format(project_root=str(PROJECT_ROOT))


def _build_devtools_demo_code():
    """Return demo startup code for local devtools/classes mode."""
    return _build_devtools_bootstrap() + textwrap.dedent("""\
        fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)
        fluid.addComponent("methane", 0.85)
        fluid.addComponent("ethane", 0.10)
        fluid.addComponent("propane", 0.05)
        fluid.setMixingRule("classic")

        ops = ns.ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.initProperties()

        gas = fluid.getPhase("gas")
        print()
        print("  Natural gas at 25 C, 60 bara")
        print("  -------------------------------------")
        print("  Density:            {:.2f} kg/m3".format(gas.getDensity("kg/m3")))
        print("  Viscosity:          {:.6f} kg/(m*s)".format(gas.getViscosity("kg/msec")))
        print("  Thermal cond.:      {:.4f} W/(m*K)".format(gas.getThermalConductivity("W/mK")))
        print("  Z-factor:           {:.4f}".format(gas.getZ()))
        print("  Molar mass:         {:.4f} kg/mol".format(fluid.getMolarMass("kg/mol")))
        print("  Number of phases:   {}".format(fluid.getNumberOfPhases()))
        print()
        print("  Try: fluid.setTemperature(273.15 + 80.0)")
        print("       ops.TPflash(); fluid.initProperties()")
        print("       gas.getDensity('kg/m3')")
        print()
    """)


def _build_devtools_repl_code():
    """Return REPL startup code for local devtools/classes mode."""
    return _build_devtools_bootstrap() + textwrap.dedent("""\
        fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)
        fluid.addComponent("methane", 0.85)
        fluid.addComponent("ethane", 0.10)
        fluid.addComponent("propane", 0.05)
        fluid.setMixingRule("classic")

        ops = ns.ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.initProperties()
        gas = fluid.getPhase("gas")

        print("  Ready!  Variables: fluid, ops, gas")
        print()
        print("  Quick examples:")
        print("    gas.getDensity('kg/m3')")
        print("    gas.getViscosity('kg/msec')")
        print("    fluid.setTemperature(273.15 + 80.0)")
        print("    ops.TPflash(); fluid.initProperties()")
        print("    gas.getDensity('kg/m3')")
        print()
    """)


def main():
    args = sys.argv[1:]

    if "-h" in args or "--help" in args:
        print("Usage: neqsim try [--demo]")
        print()
        print("  Drops you into a Python REPL with NeqSim pre-imported and a")
        print("  sample natural gas fluid ready to explore.")
        print()
        print("Options:")
        print("  --demo    Run a demo first, then drop into the REPL")
        print()
        print("Inside the REPL, `fluid`, `ops`, and `gas` are already defined.")
        print("Type any Python expression to explore fluid properties.")
        sys.exit(0)

    demo_mode = "--demo" in args

    pip_neqsim_available = importlib.util.find_spec("neqsim") is not None
    devtools_available = (
        (Path(PROJECT_ROOT) / "devtools" / "neqsim_dev_setup.py").exists()
        and (Path(PROJECT_ROOT) / "pom.xml").exists()
    )

    if not pip_neqsim_available and not devtools_available:
        print("  [!!] Could not find NeqSim runtime.")
        print("       Option 1: pip install neqsim")
        print("       Option 2: run from the NeqSim repository root")
        print("       Then retry:  neqsim try")
        sys.exit(1)

    print()
    print("  NeqSim Interactive Playground")
    print("  -----------------------------")
    if pip_neqsim_available:
        print("  Runtime: pip package (neqsim)")
    else:
        print("  Runtime: local repository classes (devtools)")
    print("  Setting up a sample fluid (natural gas, SRK EOS, 25 C, 60 bara)...")
    print()

    # Build the startup code that the REPL will execute.
    if pip_neqsim_available:
        startup = _build_pip_demo_code() if demo_mode else _build_pip_repl_code()
    else:
        startup = _build_devtools_demo_code() if demo_mode else _build_devtools_repl_code()

    # Write a temp startup file
    import tempfile
    startup_file = os.path.join(tempfile.gettempdir(), "neqsim_try_startup.py")
    with open(startup_file, "w", encoding="utf-8") as f:
        f.write(startup)

    # Launch interactive Python with the startup file
    os.environ["PYTHONSTARTUP"] = startup_file
    os.environ["PYTHONUTF8"] = "1"
    import subprocess
    sys.exit(subprocess.call([sys.executable, "-i", startup_file]))


if __name__ == "__main__":
    main()
