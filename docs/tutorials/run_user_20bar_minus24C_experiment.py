"""
====================================================================================================
CUSTOM EXPERIMENT AT 20 BAR, -24 °C (LIQUID CO2)
====================================================================================================
Experimental Sequence:
1. Phase 0 (0 to 50 h): Pressurization with Pure CO2 at 20 bar, -24 °C
2. Phase 1 (50 to 144 h, dur 94h): H2O=10, H2S=10, SO2=10, NO2=0, O2=10 ppm
3. Phase 2 (144 to 450 h, dur 306h): H2O=10, H2S=10, SO2=10, NO2=10, O2=10 ppm (ALL 10 ppm)

Plot Formatting:
y-axis forced from 0.0 to 20.0 ppm across all subplots.
"""

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment

def run_custom_20bar_minus24C_experiment():
    exp = CO2ImpurityReactorExperiment(
        target_pressure_bar=20.0,
        target_temp_C=-24.0,
        diameter_cm=6.5,
        volume_ml=300.0,
        mass_flow_g_h=50.0,
        material='carbon_steel'
    )

    exp.set_initial_vessel_charge(gas_name='N2', pressure_bar=1.0, temp_C=25.0)

    # 1. Phase 0 (0 to 50 h): Pressurization with Pure CO2
    exp.add_phase(50.0, {'H2O': 0, 'H2S': 0, 'SO2': 0, 'NO2': 0, 'O2': 0}, "Phase 0: Pressurization to 20 bar, -24 °C (0-50h)")

    # 2. Phase 1 (50 to 144 h): H2O=10, H2S=10, SO2=10, NO2=0, O2=10 ppm
    exp.add_phase(94.0, {'H2O': 10.0, 'H2S': 10.0, 'SO2': 10.0, 'NO2': 0.0, 'O2': 10.0}, "Phase 1: NO2=0 ppm (50-144h)")

    # 3. Phase 2 (144 to 450 h): H2O=10, H2S=10, SO2=10, NO2=10, O2=10 ppm (All 10 ppm)
    exp.add_phase(306.0, {'H2O': 10.0, 'H2S': 10.0, 'SO2': 10.0, 'NO2': 10.0, 'O2': 10.0}, "Phase 2: ALL 10 ppm (144-450h)")

    results = exp.run_experiment()
    t_h = results['time_hours']
    ppm = results['ppm']

    # Generate Checkpoint Summary Table (at 0, 50, 144, 200, 300, 400, 450 h)
    checkpoints = [0.0, 50.0, 144.0, 200.0, 300.0, 400.0, 450.0]
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
    print("CUSTOM 20 BAR, -24 °C CSTR EXPERIMENT REPORT (0 TO 450 HOURS)")
    print("=" * 120)
    print(exp.generate_reactor_report())
    print("=" * 120)

    print("\nCHECKPOINT SUMMARY TABLE (0 TO 450 HOURS):")
    print("=" * 120)
    print(df_cp.to_string(index=False))

    # Generate 10-hour resolution time series table
    df_series = exp.get_table_results(resolution_hours=10.0)

    # 3-Panel Plot with Y-AXIS FORCED FROM 0 TO 20 PPM
    fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(11, 10), sharex=True)

    # Panel 1: Reactants
    ax1.plot(t_h, ppm['H2S'], label='H2S', linewidth=2.0, color='#e74c3c')
    ax1.plot(t_h, ppm['SO2'], label='SO2', linewidth=2.0, color='#f39c12')
    ax1.plot(t_h, ppm['NO2'], label='NO2', linewidth=2.0, color='#9b59b6')
    ax1.plot(t_h, ppm['O2'],  label='O2',  linewidth=2.0, color='#2ecc71')
    ax1.plot(t_h, ppm['H2O'], label='H2O', linewidth=2.0, color='#3498db')
    ax1.set_ylabel('Reactants (ppm)', fontsize=11, fontweight='bold')
    ax1.set_ylim(0.0, 20.0)
    ax1.set_title('CSTR Experiment at 20 bar, -24 °C (y-axis: 0 to 20 ppm)', fontsize=13, fontweight='bold')
    ax1.grid(True, linestyle='--', alpha=0.6)
    ax1.legend(loc='upper right', frameon=True)

    # Panel 2: H2SO4 & Acids (Highlight Panel)
    ax2.plot(t_h, ppm['H2SO4'], label='H2SO4 Sulfuric Acid (Formed)', linewidth=3.5, color='#FF0033', marker='o', markevery=40)
    ax2.fill_between(t_h, ppm['H2SO4'], color='#FF0033', alpha=0.25, label='H2SO4 Shaded Acid Accumulation')
    ax2.plot(t_h, ppm['HNO3'],  label='HNO3 Nitric Acid', linewidth=2.0, color='#d35400', linestyle='--')
    ax2.set_ylabel('Acid Products (ppm)', fontsize=11, fontweight='bold')
    ax2.set_ylim(0.0, 20.0)
    ax2.grid(True, linestyle='--', alpha=0.6)
    ax2.legend(loc='upper left', frameon=True)

    # Annotate Peak H2SO4 if formed
    max_h2so4 = np.max(ppm['H2SO4'])
    if max_h2so4 > 0.05:
        max_idx = np.argmax(ppm['H2SO4'])
        max_t = t_h[max_idx]
        ax2.annotate(
            f'H2SO4 Acid Peak: {max_h2so4:.2f} ppm',
            xy=(max_t, max_h2so4),
            xytext=(max_t - 90.0, min(max_h2so4 + 3.0, 17.5)),
            arrowprops=dict(facecolor='#FF0033', shrink=0.08, width=3.0, headwidth=10.0),
            fontsize=12,
            fontweight='bold',
            color='#B20000',
            bbox=dict(boxstyle="round,pad=0.3", fc="#FFE6E6", ec="#FF0033", lw=1.5)
        )

    # Panel 3: NO Gas & NH3 Ammonia
    ax3.plot(t_h, ppm['NO'],    label='NO Gas', linewidth=2.5, color='#8e44ad')
    ax3.plot(t_h, ppm['NH3'],   label='NH3 Ammonia', linewidth=2.5, color='#16a085')
    ax3.plot(t_h, ppm['S8'],    label='S8 Elemental Sulfur', linewidth=2.0, color='#f1c40f')
    ax3.set_xlabel('Time (hours)', fontsize=11, fontweight='bold')
    ax3.set_ylabel('Gaseous Products (ppm)', fontsize=11, fontweight='bold')
    ax3.set_ylim(0.0, 20.0)
    ax3.grid(True, linestyle='--', alpha=0.6)
    ax3.legend(loc='upper right', frameon=True)

    cum_t = 0.0
    for phase in exp.phases[:-1]:
        cum_t += phase['duration_hours']
        ax1.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)
        ax2.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)
        ax3.axvline(cum_t, color='black', linestyle=':', linewidth=1.5, alpha=0.7)

    plt.tight_layout()

    save_path = "c:/NeqSim CO2 kinetic model/user_20bar_minus24C_experiment.png"
    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    print(f"Plot saved successfully to: {save_path}")

    # Copy to brain artifact directory
    brain_path = r"C:\Users\erosh\.gemini\antigravity\brain\80e52b2b-3260-4b68-836a-84d8cc8e46fd\user_20bar_minus24C_experiment.png"
    import shutil
    shutil.copy(save_path, brain_path)
    print(f"Artifact plot copied to: {brain_path}")

    return df_cp, df_series

if __name__ == "__main__":
    run_custom_20bar_minus24C_experiment()
