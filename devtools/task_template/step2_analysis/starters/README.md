# Notebook Starter Templates

Pre-built notebook templates for common engineering task types.
Copy the relevant starter to your `step2_analysis/` folder and customize.

## Available Starters

| Template | Use For |
|----------|---------|
| `starter_process_sim.ipynb` | Separation, compression, heat exchange, general process simulation |
| `starter_pvt_study.ipynb` | CME, CVD, saturation pressure, phase envelopes, fluid characterization |
| `starter_flow_assurance.ipynb` | Hydrate prediction, pipeline hydraulics, wax/corrosion screening |
| `starter_field_economics.ipynb` | NPV, Monte Carlo sensitivity, production profiles, project economics |

## How to Use

1. Copy the starter to your task's `step2_analysis/` folder:
   ```
   cp starters/starter_process_sim.ipynb ../01_my_analysis.ipynb
   ```
2. Edit the title, date, and fluid composition
3. Customize the process configuration for your specific case
4. Run all cells and verify results
5. Update `results.json` at the end

## Common Patterns Across All Starters

- **Dual-boot setup**: Works with both local dev build (`neqsim_dev_setup`) and pip-installed `neqsim`
- **Path resolution**: Auto-resolves `TASK_DIR`, `FIGURES_DIR` from notebook location
- **Results save**: Loads existing `results.json` before adding new data (merge, not overwrite)
- **Mandatory figures**: At least 2-3 matplotlib plots with axis labels, units, titles, grids
- **Discussion cells**: After every figure, a markdown cell with observation/mechanism/implication/recommendation
