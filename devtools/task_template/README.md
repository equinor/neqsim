# Canonical Task Template Files

This directory contains the **authoritative versions** of task template files
that `new_task.py` uses when creating new task folders.

## How it works

When `new_task.py` runs `setup_workspace()`, it:

1. Creates `task_solve/TASK_TEMPLATE/` from embedded strings (baseline)
2. Overlays files from **this directory** on top (canonical versions)

This means improvements to `generate_report.py`, starter notebooks, or
step-1 templates only need to be made here — they automatically propagate
to new tasks without updating the embedded strings in `new_task.py`.

## Directory structure

```
task_template/
  study_config.yaml              # Intake, inputs, runner execution, notebooks, report configuration
  step1_scope_and_research/
    analysis.md                  # Deep analysis template (Step 1.5)
    neqsim_improvements.md       # NIP proposal template
  step2_analysis/
    starters/
      README.md                  # Starter notebook index
      starter_process_sim.ipynb  # Process simulation starter
      starter_pvt_study.ipynb    # PVT study starter
      starter_flow_assurance.ipynb
      starter_field_economics.ipynb
  step3_report/
    generate_report.py           # Report generator (Word + HTML)
```

## Updating templates

Edit files here, commit, and push. All future tasks will use the updated
versions. Existing task folders are not affected.
