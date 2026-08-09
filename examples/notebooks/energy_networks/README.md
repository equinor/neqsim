# Energy-network example notebooks

These notebooks demonstrate the professional Energy Networks functionality available in NeqSim 3.17.0 and later.

| Notebook | Main topics | Implementation history |
|---|---|---|
| `01_energy_dispatch_and_reporting.ipynb` | Multi-party allocation, priorities, shortage, curtailment, cost, emissions, merit-order dispatch | #2603 and #2613 |
| `02_rotating_equipment_and_converter_maps.ipynb` | Motor/VFD performance, mechanical shafts, generator and prime-mover part-load curves | #2608, #2609, #2615 |
| `03_thermal_utilities_and_hydraulics.ipynb` | Utility mass flow, temperature quality, exergy, cooling-water header hydraulics | #2610, #2612, #2616 |
| `04_time_series_commitment_offshore_benchmark.ipynb` | Time series, commitment constraints, offshore wind/gas benchmark | #2614, #2617, #2618 |

All listed implementation PRs are merged and included in the public NeqSim 3.17.0 package.

## Run locally

Create an isolated Python environment, install the public release, and start Jupyter:

```bash
python -m venv neqsim-energy-networks
source neqsim-energy-networks/bin/activate
python -m pip install "neqsim==3.17.0" jupyter matplotlib pandas
jupyter lab examples/notebooks/energy_networks
```

On Windows, activate the environment with `neqsim-energy-networks\\Scripts\\activate`.
The notebooks use the public `neqsim` package and do not require a local NeqSim
checkout or compiled workspace classes.

## Colab

The notebook headers contain Colab links targeting `master`. Each notebook setup
cell installs NeqSim 3.17.0 from public PyPI in a clean Colab runtime.

## Scope

The notebooks provide process-energy integration and screening workflows. They do not replace specialist studies for AC load flow, short-circuit protection, detailed steam-network hydraulics, equipment vendor guarantees, or mixed-integer unit commitment.
