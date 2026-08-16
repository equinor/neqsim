"""
====================================================================================================
CUSTOM EXPERIMENT AT 20 BAR, -24 °C (LIQUID CO2) - 1-HOUR STEP RESOLUTION TABLE
====================================================================================================
Experimental Sequence:
1. Phase 0 (0 to 50 h): Pressurization with Pure CO2 at 20 bar, -24 °C
2. Phase 1 (50 to 144 h, dur 94h): H2O=10, H2S=10, SO2=10, NO2=0, O2=10 ppm
3. Phase 2 (144 to 450 h, dur 306h): H2O=10, H2S=10, SO2=10, NO2=10, O2=10 ppm (ALL 10 ppm)

Output:
- Generates 1-hour step resolution table (0.0 to 450.0 h) saved to user_20bar_minus24C_1hr_table.csv
- Clean formatting eliminating -0.00 artifacts
- 3-Panel Plot with y-axis 0.0 to 20.0 ppm
"""

import os
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
from neqsim_co2_kinetics import CO2ImpurityReactorExperiment


def run_custom_20bar_minus24C_1hr_experiment(output_dir=None):
    output_path = Path(output_dir or os.environ.get("NEQSIM_TUTORIAL_OUTPUT_DIR", "."))
    output_path.mkdir(parents=True, exist_ok=True)

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

    # Generate 1-HOUR STEP RESOLUTION TABLE (451 rows from 0.0 to 450.0 h)
    df_1hr = exp.get_table_results(resolution_hours=1.0)

    # Save to CSV file
    csv_path = output_path / "user_20bar_minus24C_1hr_table.csv"
    df_1hr.to_csv(csv_path, index=False)
    print(f"1-hour step resolution table saved to: {csv_path}")

    # Display key 1-hour interval checkpoints
    checkpoints = [0.0, 50.0, 60.0, 100.0, 140.0, 144.0, 145.0, 150.0, 160.0, 170.0, 180.0, 190.0, 200.0, 300.0, 400.0, 450.0]
    df_check = df_1hr[df_1hr['Time (h)'].isin(checkpoints)]

    print("=" * 120)
    print("KEY 1-HOUR INTERVAL CHECKPOINTS (CLEAN NON-NEGATIVE FORMATTING):")
    print("=" * 120)
    print(df_check.to_string(index=False))

    # 3-Panel Plot with Y-AXIS FORCED FROM 0 TO 20 PPM
    _, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(11, 10), sharex=True)

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

    ax2.plot(t_h, ppm['H2SO4'], label='H2SO4 Sulfuric Acid (Formed)', linewidth=3.5, color='#FF0033', marker='o', markevery=40)
    ax2.fill_between(t_h, ppm['H2SO4'], color='#FF0033', alpha=0.25, label='H2SO4 Shaded Acid Accumulation')
    ax2.plot(t_h, ppm['HNO3'],  label='HNO3 Nitric Acid', linewidth=2.0, color='#d35400', linestyle='--')
    ax2.set_ylabel('Acid Products (ppm)', fontsize=11, fontweight='bold')
    ax2.set_ylim(0.0, 20.0)
    ax2.grid(True, linestyle='--', alpha=0.6)
    ax2.legend(loc='upper left', frameon=True)

    max_h2so4 = np.max(ppm['H2SO4'])
    if max_h2so4 > 0.05:
        max_idx = np.argmax(ppm['H2SO4'])
        max_t = t_h[max_idx]
        ax2.annotate(
            f'H2SO4 Acid Peak: {max_h2so4:.2f} ppm',
            xy=(max_t, max_h2so4),
            xytext=(max_t - 90.0, min(max_h2so4 + 3.0, 17.5)),
            arrowprops={'facecolor': '#FF0033', 'shrink': 0.08, 'width': 3.0, 'headwidth': 10.0},
            fontsize=12,
            fontweight='bold',
            color='#B20000',
            bbox={'boxstyle': 'round,pad=0.3', 'fc': '#FFE6E6', 'ec': '#FF0033', 'lw': 1.5}
        )

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

    save_path = output_path / "user_20bar_minus24C_experiment.png"
    plt.savefig(save_path, dpi=300, bbox_inches='tight')
    print(f"Plot saved successfully to: {save_path}")

    return df_1hr

if __name__ == "__main__":
    run_custom_20bar_minus24C_1hr_experiment()
