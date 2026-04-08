"""
Example: Simple single simulation job.

Demonstrates the minimal pattern for a job script.
Runs a basic NeqSim flash calculation and saves results.

Run with::

    python -m neqsim_runner go example_simple.py \
        --args '{"temperature_C": 25, "pressure_bara": 60}'
"""

import json

try:
    from neqsim_runner.job_helpers import get_args, save_result
except ImportError:
    import os
    get_args = lambda: json.loads(os.environ.get("NEQSIM_JOB_ARGS", "{}"))
    save_result = lambda data, **kw: print(json.dumps(data, indent=2))


def main():
    args = get_args()
    temp_c = args.get("temperature_C", 25.0)
    pressure = args.get("pressure_bara", 60.0)

    from neqsim import jneqsim

    fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + temp_c, pressure)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.10)
    fluid.addComponent("propane", 0.05)
    fluid.setMixingRule("classic")

    ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
    ops.TPflash()
    fluid.initProperties()

    result = {
        "temperature_C": temp_c,
        "pressure_bara": pressure,
        "density_kg_m3": float(fluid.getDensity("kg/m3")),
        "molar_mass_kg_mol": float(fluid.getMolarMass("kg/mol")),
        "Z_factor": float(fluid.getZ()),
        "number_of_phases": int(fluid.getNumberOfPhases()),
    }

    save_result(result)
    print(f"Density: {result['density_kg_m3']:.2f} kg/m3")
    print(f"Z-factor: {result['Z_factor']:.4f}")


if __name__ == "__main__":
    main()
