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

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

DEMO_CODE = textwrap.dedent("""\
    from neqsim import jneqsim

    # ── Create a natural gas fluid ──────────────────────────────────
    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")

    # ── Flash calculation ───────────────────────────────────────────
    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    # ── Read results ────────────────────────────────────────────────
    gas = fluid.getPhase("gas")
    print()
    print("  Natural gas at 25 °C, 60 bara")
    print("  ─────────────────────────────────────")
    print("  Density:            {:.2f} kg/m³".format(gas.getDensity("kg/m3")))
    print("  Viscosity:          {:.6f} kg/(m·s)".format(gas.getViscosity("kg/msec")))
    print("  Thermal cond.:      {:.4f} W/(m·K)".format(gas.getThermalConductivity("W/mK")))
    print("  Z-factor:           {:.4f}".format(gas.getZ()))
    print("  Molar mass:         {:.4f} kg/mol".format(fluid.getMolarMass("kg/mol")))
    print("  Number of phases:   {}".format(fluid.getNumberOfPhases()))
    print()
    print("  Try: fluid.setTemperature(273.15 + 80.0)")
    print("       ops.TPflash(); fluid.initProperties()")
    print("       gas.getDensity('kg/m3')")
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

    # Check neqsim is importable
    try:
        import neqsim  # noqa: F401
    except ImportError:
        print("  [!!] neqsim is not installed.")
        print("       Install it:  pip install neqsim")
        print("       Then retry:  neqsim try")
        sys.exit(1)

    print()
    print("  NeqSim Interactive Playground")
    print("  ─────────────────────────────")
    print("  Setting up a sample fluid (natural gas, SRK EOS, 25 °C, 60 bara)...")
    print()

    # Build the startup code that the REPL will execute
    startup = DEMO_CODE if demo_mode else textwrap.dedent("""\
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

    # Write a temp startup file
    import tempfile
    startup_file = os.path.join(tempfile.gettempdir(), "neqsim_try_startup.py")
    with open(startup_file, "w") as f:
        f.write(startup)

    # Launch interactive Python with the startup file
    os.environ["PYTHONSTARTUP"] = startup_file
    os.execv(sys.executable, [sys.executable, "-i", startup_file])


if __name__ == "__main__":
    main()
