"""
====================================================================================================
960-HOUR MULTI-PHASE CSTR EXPERIMENT AT 98 BAR, +26 °C WITH 1-HOUR STEP RESOLUTION TABLE
====================================================================================================
Phases:
• Phase 0 (0 to 50 h, dur 50h): Pressurization & Pure CO2 Flow (0 ppm)
• Phase 1 (50 to 101 h, dur 51h): H2O=47, H2S=0, SO2=20, NO2=5.0, O2=0 ppm
• Phase 2 (101 to 215 h, dur 114h): H2O=47, H2S=0, SO2=20, NO2=8.5, O2=0 ppm
• Phase 3 (215 to 409 h, dur 194h): H2O=47, H2S=20, SO2=20, NO2=8.5, O2=20 ppm
• Phase 4 (409 to 447 h, dur 38h): H2O=47, H2S=20, SO2=20, NO2=16.0, O2=20 ppm
• Phase 5 (447 to 575 h, dur 128h): H2O=47, H2S=20, SO2=20, NO2=16.0, O2=40 ppm
• Phase 6 (575 to 676 h, dur 101h): H2O=47, H2S=20, SO2=20, NO2=16.0, O2=20 ppm
• Phase 7 (676 to 773 h, dur 97h): H2O=47, H2S=20, SO2=20, NO2=8.5, O2=20 ppm
• Phase 8 (773 to 863 h, dur 90h): H2O=47, H2S=20, SO2=20, NO2=0.0, O2=20 ppm
• Phase 9 (863 to 960 h, dur 97h): H2O=47, H2S=0, SO2=20, NO2=0.0, O2=20 ppm

Features:
- Generates complete 1-hour step resolution table (0.0 to 960.0 h, 961 rows) exported to CSV
- Clean zero-floor non-negative formatting (no -0.00 artifacts)
- High-res 3-panel plot highlighting H2SO4 acid formation
"""

import os
from pathlib import Path

from neqsim_co2_kinetics import CO2ImpurityReactorExperiment


def run_960h_experiment(output_dir=None):
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

    # Phase 0: 0 to 50 h (50h)
    exp.add_phase(50.0, {'H2O': 0, 'H2S': 0, 'SO2': 0, 'NO2': 0, 'O2': 0}, "Phase 0: Pressurization (0-50h)")

    # Phase 1: 50 to 101 h (51h)
    exp.add_phase(51.0, {'H2O': 47, 'H2S': 0, 'SO2': 20, 'NO2': 5.0, 'O2': 0}, "Phase 1: H2O 47, SO2 20, NO2 5 (50-101h)")

    # Phase 2: 101 to 215 h (114h)
    exp.add_phase(114.0, {'H2O': 47, 'H2S': 0, 'SO2': 20, 'NO2': 8.5, 'O2': 0}, "Phase 2: H2O 47, SO2 20, NO2 8.5 (101-215h)")

    # Phase 3: 215 to 409 h (194h)
    exp.add_phase(194.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 8.5, 'O2': 20}, "Phase 3: H2O 47, H2S 20, SO2 20, NO2 8.5, O2 20 (215-409h)")

    # Phase 4: 409 to 447 h (38h)
    exp.add_phase(38.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 16.0, 'O2': 20}, "Phase 4: H2O 47, H2S 20, SO2 20, NO2 16, O2 20 (409-447h)")

    # Phase 5: 447 to 575 h (128h)
    exp.add_phase(128.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 16.0, 'O2': 40}, "Phase 5: H2O 47, H2S 20, SO2 20, NO2 16, O2 40 (447-575h)")

    # Phase 6: 575 to 676 h (101h)
    exp.add_phase(101.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 16.0, 'O2': 20}, "Phase 6: H2O 47, H2S 20, SO2 20, NO2 16, O2 20 (575-676h)")

    # Phase 7: 676 to 773 h (97h)
    exp.add_phase(97.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 8.5, 'O2': 20}, "Phase 7: H2O 47, H2S 20, SO2 20, NO2 8.5, O2 20 (676-773h)")

    # Phase 8: 773 to 863 h (90h)
    exp.add_phase(90.0, {'H2O': 47, 'H2S': 20, 'SO2': 20, 'NO2': 0.0, 'O2': 20}, "Phase 8: H2O 47, H2S 20, SO2 20, NO2 0, O2 20 (773-863h)")

    # Phase 9: 863 to 960 h (97h)
    exp.add_phase(97.0, {'H2O': 47, 'H2S': 0, 'SO2': 20, 'NO2': 0.0, 'O2': 20}, "Phase 9: H2O 47, H2S 0, SO2 20, NO2 0, O2 20 (863-960h)")

    # Run the full experiment
    exp.run_experiment()

    # Generate 1-HOUR STEP RESOLUTION TABLE (961 rows from 0.0 to 960.0 h)
    df_1hr = exp.get_table_results(resolution_hours=1.0)

    # Save to CSV file
    csv_path = output_path / "user_960hr_1hr_table.csv"
    df_1hr.to_csv(csv_path, index=False)
    print(f"1-hour step resolution table (961 rows) saved to: {csv_path}")

    # Key Phase Checkpoint Table
    checkpoints = [0.0, 50.0, 101.0, 215.0, 409.0, 447.0, 575.0, 676.0, 773.0, 863.0, 960.0]
    df_checkpoints = df_1hr[df_1hr['Time (h)'].isin(checkpoints)]

    # Save the plot in the configured output directory.
    save_fig_path = output_path / "co2_impurity_dynamics_960hr.png"
    exp.plot_results(save_path=save_fig_path, title="960-Hour Multi-Phase CSTR CO2 Impurity Kinetics (98 bar, +26 °C)")

    print("=" * 120)
    print("960-HOUR MULTI-PHASE CSTR EXPERIMENT REPORT (98 BAR, +26 °C)")
    print("=" * 120)
    print(exp.generate_reactor_report())
    print("=" * 120)

    print("\nSUMMARY TABLE AT KEY PHASE CHECKPOINTS (0 TO 960 HOURS):")
    print("=" * 120)
    print(df_checkpoints.to_string(index=False))

    return df_checkpoints, df_1hr

if __name__ == "__main__":
    run_960h_experiment()
