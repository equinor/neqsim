"""
Script to simulate 10 ppm stepwise impurity addition experiment with pressurization.
Each impurity (10 ppm) is added sequentially every 100 hours!

The co-catalysed R3b acid pathway can proceed after H2S, NO2, H2O, SO2, and O2 are all present
in Phase 5 (450-550 h).

Phases:
0. 0 - 50 h: Pressurization with pure CO2 (N2 charge at start, P = 98 bar, T = +26 °C) -> H2SO4 = 0.000 ppm
1. 50 - 150 h (100 h): H2O = 10 ppm -> H2SO4 = 0.000 ppm
2. 150 - 250 h (100 h): H2O = 10 ppm + SO2 = 10 ppm -> H2SO4 = 0.000 ppm
3. 250 - 350 h (100 h): H2O = 10 ppm + SO2 = 10 ppm + NO2 = 10 ppm -> H2SO4 = 0.000 ppm (No H2S yet!)
4. 350 - 450 h (100 h): H2O = 10 ppm + SO2 = 10 ppm + NO2 = 10 ppm + H2S = 10 ppm; O2 remains absent
5. 450 - 550 h (100 h): Add O2 = 10 ppm so all five feed impurities are present
6. 550 - 650 h (100 h): Shut off H2S (H2S = 0 ppm)
7. 650 - 750 h (100 h): Flush with pure CO2 (0.000 ppm)
"""

import os
from pathlib import Path

import numpy as np
import pandas as pd
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment


def run_stepwise_10ppm_experiment(output_dir=None):
    output_path = Path(output_dir or os.environ.get("NEQSIM_TUTORIAL_OUTPUT_DIR", "."))
    output_path.mkdir(parents=True, exist_ok=True)

    exp = CO2ImpurityReactorExperiment(
        target_pressure_bar=98.0,
        target_temp_C=26.0,
        diameter_cm=6.5,
        volume_ml=300.0,
        mass_flow_g_h=50.0,
        material='carbon_steel'
    )

    exp.set_initial_vessel_charge(gas_name='N2', pressure_bar=1.0, temp_C=25.0)

    # 1. Pressurization phase (0 to 50 h)
    exp.add_phase(50.0, {'H2O': 0, 'H2S': 0, 'SO2': 0, 'NO2': 0, 'O2': 0}, "Phase 0: Pressurization with Pure CO2 (0-50h)")

    # 2. Step 1: Add H2O (50 to 150 h)
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 0, 'SO2': 0, 'NO2': 0, 'O2': 0}, "Phase 1: Add H2O 10 ppm (50-150h)")

    # 3. Step 2: Add SO2 (150 to 250 h)
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 0, 'SO2': 10.0, 'NO2': 0, 'O2': 0}, "Phase 2: Add SO2 10 ppm (150-250h)")

    # 4. Step 3: Add NO2 (250 to 350 h) - H2SO4 = 0.000 ppm (No H2S present yet!)
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 0}, "Phase 3: Add NO2 10 ppm (250-350h)")

    # 5. Step 4: Add H2S (350 to 450 h); O2 is still absent.
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 10.0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 0}, "Phase 4: Add H2S 10 ppm (350-450h)")

    # 6. Step 5: Add O2 (450 to 550 h), enabling the R3b pathway.
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 10.0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 10.0}, "Phase 5: Add O2 10 ppm - All 5 Impurities (450-550h)")

    # 7. Step 6: Shut off H2S (550 to 650 h).
    exp.add_phase(100.0, {'H2O': 10.0, 'H2S': 0.0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 10.0}, "Phase 6: Shut off H2S (550-650h)")

    # 8. Step 7: Flush with Pure CO2 (650 to 750 h)
    exp.add_phase(100.0, {'H2O': 0.0, 'H2S': 0.0, 'SO2': 0.0, 'NO2': 0.0, 'O2': 0.0}, "Phase 7: Pure CO2 Flush (650-750h)")

    results = exp.run_experiment()

    # Generate Checkpoint Summary Table (at 0, 50, 150, 250, 350, 450, 550, 650, 750 h)
    checkpoints = [0.0, 50.0, 150.0, 250.0, 350.0, 450.0, 550.0, 650.0, 750.0]
    t_h = results['time_hours']
    rows_cp = []

    for cp in checkpoints:
        idx = np.argmin(np.abs(t_h - cp))
        row = {
            'Time (h)': round(float(t_h[idx]), 1),
            'H2S (ppm)': round(float(results['ppm']['H2S'][idx]), 2),
            'SO2 (ppm)': round(float(results['ppm']['SO2'][idx]), 2),
            'NO2 (ppm)': round(float(results['ppm']['NO2'][idx]), 2),
            'NO (ppm)': round(float(results['ppm']['NO'][idx]), 3),
            'O2 (ppm)': round(float(results['ppm']['O2'][idx]), 2),
            'H2O (ppm)': round(float(results['ppm']['H2O'][idx]), 2),
            'H2SO4 (ppm)': round(float(results['ppm']['H2SO4'][idx]), 3),
            'NH3 (ppm)': round(float(results['ppm']['NH3'][idx]), 3),
            'S8 (ppm)': round(float(results['ppm']['S8'][idx]), 3),
        }
        rows_cp.append(row)

    df_cp = pd.DataFrame(rows_cp)

    print("=" * 120)
    print("STEPWISE 10 PPM EXPERIMENT: R3B ENABLED AFTER O2 IS ADDED IN PHASE 5")
    print("=" * 120)
    print(df_cp.to_string(index=False))

    # Generate 20-hour resolution master time series table
    df_series = exp.get_table_results(resolution_hours=20.0)

    # Plot results and save figure
    exp.plot_results(
        save_path=output_path / "stepwise_10ppm_100hr_h2so4_nh3_experiment.png",
        title="Stepwise Impurity Addition: R3B Enabled After O2 Addition"
    )

    return df_cp, df_series

if __name__ == "__main__":
    run_stepwise_10ppm_experiment()
