"""
Script to simulate the complete 960-hour multi-phase CO2 impurity CSTR experiment at 98 bar, +26 °C.

Phase Sequence & Impurity Feeds:
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
"""

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment

def run_960h_experiment():
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
    results = exp.run_experiment()

    # Generate 10-hour or 20-hour resolution summary table
    df_summary = exp.get_table_results(resolution_hours=20.0)

    # Generate key phase transition summary table
    checkpoints = [0.0, 50.0, 101.0, 215.0, 409.0, 447.0, 575.0, 676.0, 773.0, 863.0, 960.0]
    t_h = results['time_hours']
    rows_checkpoint = []

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
            'HNO3 (ppm)': round(float(results['ppm']['HNO3'][idx]), 3),
            'NH3 (ppm)': round(float(results['ppm']['NH3'][idx]), 3),
            'S8 (ppm)': round(float(results['ppm']['S8'][idx]), 3),
        }
        rows_checkpoint.append(row)

    df_checkpoints = pd.DataFrame(rows_checkpoint)

    # Plot 2-panel figure and save to file
    save_fig_path = r"C:\Users\erosh\.gemini\antigravity\brain\80e52b2b-3260-4b68-836a-84d8cc8e46fd\co2_impurity_dynamics_960hr.png"
    exp.plot_results(save_path=save_fig_path, title="960-Hour Multi-Phase CSTR CO2 Impurity Kinetics (98 bar, +26 °C)")

    print("=" * 120)
    print("960-HOUR MULTI-PHASE CSTR EXPERIMENT REPORT (98 BAR, +26 °C)")
    print("=" * 120)
    print(exp.generate_reactor_report())
    print("=" * 120)

    print("\nSUMMARY TABLE AT KEY PHASE CHECKPOINTS (0 TO 960 HOURS):")
    print("=" * 120)
    print(df_checkpoints.to_string(index=False))

    print("\n20-HOUR RESOLUTION TIME-SERIES TABLE:")
    print("=" * 120)
    print(df_summary.to_string(index=False))

    return df_checkpoints, df_summary

if __name__ == "__main__":
    run_960h_experiment()
