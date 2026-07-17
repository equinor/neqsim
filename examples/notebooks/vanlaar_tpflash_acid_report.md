
## TPflash-only VanLaar acid report update

**Method.** Every result in this section is generated from `SystemVanLaarActivitySRK` plus `ThermodynamicOperations.TPflash()`. For the experiment comparison and P-T isolines, each condition uses **1000 ppm water on a 1e6 mol CO2 basis** and iterates acid inventory until the acid phase reaches the requested source strength: **65 wt% HNO3** or **98 wt% H2SO4**. No solubility helper values are substituted into the tables. The CO2-rich carrier phase is selected as the flashed phase with the highest CO2 moles, and the acid phase is selected as the flashed phase with the lowest CO2 moles. Failed TPflash points are skipped in plots and marked as ERROR in tables.

**Execution status.** 2 TPflash points failed and were marked as ERROR.

**Experiment comparison.** The parity plot in `tpflash_acid_experiment_comparison.png` compares targeted-source TPflash values with IFE, Rotvoll and ship-transport measurements. These are finite-inventory flash calculations, so they can differ from the old source-fugacity shortcut report if the material source definition is different.

### HNO3 targeted-source TPflash vs experiment rows

| Source | P [bar] | T [C] | Experiment [ppm] | TPflash [ppm] | Deviation [%] | Aq acid [wt%] | Acid feed [mol] | Status | Error |
|---|---|---|---|---|---|---|---|---|---|
| Rotvoll | 100.00 | 0.00 | 1828 | 1886 | 3.16 | 64.99 | 2406 | OK |  |
| Rotvoll | 100.00 | 40.00 | 2443 | 2740 | 12.2 | 64.99 | 3153 | OK |  |
| IFE | 98.60 | 24.00 | 2150 | 2183 | 1.52 | 65 | 2663 | OK |  |
| IFE | 98.60 | 53.00 | 830 | 721.8 | -13 | 65.02 | 1008 | OK |  |
| IFE | 101.30 | 53.00 | 520 | 755.6 | 45.3 | 64.99 | 1045 | OK |  |
| IFE | 99.30 | 48.00 | 600 | 851.9 | 42 | 65.02 | 1195 | OK |  |
| IFE | 119.00 | 48.00 | 1250 | 1260 | 0.797 | 65.02 | 1618 | OK |  |
| IFE | 169.10 | 48.00 | 2230 | 1638 | -26.5 | 65.01 | 2016 | OK |  |
| Ship transport | 20.00 | -21.00 | 723 | 525.9 | -27.3 | 65.02 | 1054 | OK |  |
| Ship transport | 20.00 | -29.00 | 313 | 428.8 | 37 | 65.02 | 958.2 | OK |  |
| Ship transport | 23.56 | -19.19 | 787.5 | 597.3 | -24.1 | 65 | 1124 | OK |  |
| Ship transport | 22.86 | -17.92 | 896.4 | 660.2 | -26.3 | 64.98 | 1186 | OK |  |

### H2SO4 targeted-source TPflash vs experiment rows

| Source | P [bar] | T [C] | Experiment [ppm] | TPflash [ppm] | Deviation [%] | Aq acid [wt%] | Acid feed [mol] | Status | Error |
|---|---|---|---|---|---|---|---|---|---|
| IFE | 94.60 | 25.00 | 2.26 | 2.26 | -0.00364 | 98 | 9000 | OK |  |
| IFE | 77.90 | 46.50 | 0.06 | 0.0925 | 54.2 | 98 | 9000 | OK |  |
| IFE | 98.40 | 47.20 | 1.18 | 0.6856 | -41.9 | 98 | 9000 | OK |  |
| IFE | 118.60 | 47.90 | 2.4 | 2.851 | 18.8 | 98 | 9000 | OK |  |
| IFE | 168.70 | 48.40 | 7.7 | 7.982 | 3.66 | 98 | 9000 | OK |  |

**P-T isolines.** `tpflash_acid_pt_isolines.png` shows pressure isolines for TPflash systems with 1000 ppm water and acid inventory iterated to 65 wt% HNO3 or 98 wt% H2SO4 in the aqueous phase.

**HNO3 impact on H2SO4 formation.** `tpflash_hno3_h2so4_interaction.png` scans HNO3 inventory at 53 C and 100 bar with 30 ppm water and fixed H2SO4 inventory. HNO3 adds a second volatile acid that partitions into both the carrier and liquid phases, increases the acid-water liquid amount, and dilutes or shifts the H2SO4-rich liquid composition.

**Water below 30 ppm.** In the fixed H2SO4 material-TPflash screening case at 53 C and 100 bar, the closest 30 ppm-water point gives approximately **81.61 wt% H2SO4** if successful. Drying below 30 ppm reduces the total amount of liquid water available, but the liquid that remains can become more concentrated in H2SO4.

### Water-limit material TPflash H2SO4 screening at 53 C and 100 bar

| Water feed [ppm] | Carrier water [ppm] | Carrier H2SO4 [ppm] | Aqueous H2SO4 [wt%] | Aqueous beta | Status | Error |
|---|---|---|---|---|---|---|
| 1 | 0.4169 | 0.3162 | 94.02 | 2.2669e-06 | OK |  |
| 3 | 1.958 | 0.1578 | 90.58 | 2.8847e-06 | OK |  |
| 10 | 8.322 | 0.05687 | 86.31 | 3.6207e-06 | OK |  |
| 30 | 27.57 | 0.01651 | 81.61 | 4.4159e-06 | OK |  |
| 60 | 56.93 | 0.006096 | 77.99 | 5.0570e-06 | OK |  |
| 100 | ERROR | ERROR | ERROR | ERROR | ERROR | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| 300 | ERROR | ERROR | ERROR | ERROR | ERROR | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| 1000 | 986.4 | 1.377e-06 | 46.34 | 1.4592e-05 | OK |  |

**Recommendation.** For corrosion risk, do not use water ppm alone as the acceptance criterion. Track three outputs together: aqueous beta or liquid inventory, H2SO4 wt% in the liquid, and carrier-phase acid ppm.

**Example iteration.** Targeting 1 ppm mol H2SO4 at 53 C and 100 bar required about `1e+05` mol H2SO4 source inventory per 1e6 mol CO2 in this material-TPflash setup; status: `target above TPflash saturation at upper bound`.


## Gas-Ppm Targeted TPflash Inventory Check

This section keeps the same 1000 ppm water basis, but iterates acid inventory to match each measured gas-phase acid ppm. It reports the aqueous acid strength that the material TPflash predicts. The beta column reports the smallest positive phase beta from the TPflash result instead of using zero for a missing phase. This separates two questions: (1) how much acid is needed to maintain a 65/98 wt% liquid source, and (2) how much acid inventory is needed to match the measured gas concentration.

### HNO3 TPflash acid inventory required to match measured gas ppm

| Source | P [bar] | T [C] | Target ppm | TPflash ppm | Acid feed [mol] | Aq acid [wt%] | Smallest beta | Status | Error |
|---|---|---|---|---|---|---|---|---|---|
| Rotvoll | 100.00 | 0.00 | 1828 | 1829 | 2344 | 64.77 | 1.479e-03 | converged |  |
| Rotvoll | 100.00 | 40.00 | 2443 | 2442 | 2831 | 64.09 | 1.126e-03 | converged |  |
| IFE | 98.60 | 24.00 | 2150 | 2151 | 2629 | 64.89 | 1.362e-03 | converged |  |
| IFE | 98.60 | 53.00 | 830 | 829.9 | 1151 | 66.24 | 8.899e-04 | converged |  |
| IFE | 101.30 | 53.00 | 520 | 520.2 | 728.7 | 61.75 | 6.572e-04 | converged |  |
| IFE | 99.30 | 48.00 | 600 | ERROR | 1e+05 | ERROR | ERROR | error at upper bound | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| IFE | 119.00 | 48.00 | 1250 | ERROR | 1e+05 | ERROR | ERROR | error at upper bound | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| IFE | 169.10 | 48.00 | 2230 | ERROR | 1e+05 | ERROR | ERROR | error at upper bound | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| Ship transport | 20.00 | -21.00 | 723 | ERROR | 1e+05 | ERROR | ERROR | error at upper bound | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| Ship transport | 20.00 | -29.00 | 313 | ERROR | 1e+05 | ERROR | ERROR | error at upper bound | java.lang.IllegalStateException: Van Laar TPflash did not converge to a fugacity |
| Ship transport | 23.56 | -19.19 | 787.5 | 787.2 | 1359 | 66.82 | 1.561e-03 | converged |  |
| Ship transport | 22.86 | -17.92 | 896.4 | 895.8 | 1473 | 67.01 | 1.564e-03 | converged |  |

### H2SO4 TPflash acid inventory required to match measured gas ppm

| Source | P [bar] | T [C] | Target ppm | TPflash ppm | Acid feed [mol] | Aq acid [wt%] | Smallest beta | Status | Error |
|---|---|---|---|---|---|---|---|---|---|
| IFE | 94.60 | 25.00 | 2.26 | 2.26 | 8997 | 98 | 9.896e-03 | converged |  |
| IFE | 77.90 | 46.50 | 0.06 | 0.05996 | 3460 | 94.96 | 4.440e-03 | converged |  |
| IFE | 98.40 | 47.20 | 1.18 | 0.8006 | 1e+05 | 99.82 | 9.173e-02 | target above TPflash saturation at upper bound |  |
| IFE | 118.60 | 47.90 | 2.4 | 2.404 | 5268 | 96.63 | 6.227e-03 | converged |  |
| IFE | 168.70 | 48.40 | 7.7 | 7.696 | 7721 | 97.67 | 8.638e-03 | converged |  |
