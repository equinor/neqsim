# Energy-network example notebooks

These notebooks demonstrate the professional Energy Networks functionality introduced after Energy Networks v3.

| Notebook | Main topics | Required implementation PRs |
|---|---|---|
| `01_energy_dispatch_and_reporting.ipynb` | Multi-party allocation, priorities, shortage, curtailment, cost, emissions, merit-order dispatch | #2603 and #2613 |
| `02_rotating_equipment_and_converter_maps.ipynb` | Motor/VFD performance, mechanical shafts, generator and prime-mover part-load curves | #2608, #2609, #2615 |
| `03_thermal_utilities_and_hydraulics.ipynb` | Utility mass flow, temperature quality, exergy, cooling-water header hydraulics | #2610, #2612, #2616 |
| `04_time_series_commitment_offshore_benchmark.ipynb` | Time series, commitment constraints, offshore wind/gas benchmark | #2614, #2617, #2618 |

## Running from a repository checkout

Build the branch containing the required implementation classes, then start Jupyter from anywhere below the NeqSim repository root:

```bash
./mvnw -DskipTests package
jupyter lab examples/notebooks/energy_networks
```

The notebooks use `devtools/neqsim_dev_setup.py`, which loads compiled workspace classes from `target/classes`. Set `NEQSIM_PROJECT_ROOT` when the notebook is launched outside the repository tree.

## Colab

The notebook headers contain Colab links targeting `master`. They become directly runnable after the required implementation PRs have merged and the corresponding Java classes are available on `master`. Before that point, use a local checkout containing the listed branches.

## Scope

The notebooks provide process-energy integration and screening workflows. They do not replace specialist studies for AC load flow, short-circuit protection, detailed steam-network hydraulics, equipment vendor guarantees, or mixed-integer unit commitment.