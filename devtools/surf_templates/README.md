# SURF study templates

`neqsim surf-study --init DIR` copies `study.yaml` (the specification template), `standards_register.yaml` and this file.

- Fill every `unavailable - TO BE CONFIRMED` value from a source, set its `input_class` (specified, measured, calculated, estimated,
  synthetic) and cite the source id. The command refuses to run while any placeholder remains (exit code 2).
- Units are part of the key: `_m`, `_mm`, `_bara`, `_c`, `_kg_s`, `_w_m2k`. Gauge pressures (`_barg`) are rejected.
- A rigid line is defined by `od_mm` and `wall_mm`; its hydraulic inside diameter is OD - 2 x wall. A flexible line needs `id_mm` and a supplier U-value.
- `standards_register.yaml` lists public publisher metadata only. Replace it with the project's controlled editions in the task or
  enterprise layer; never copy licensed text or company data into a public repository.
- The synthetic example `devtools/surf_examples/four_well_tieback/study.yaml` shows every field.

Run: `neqsim surf-study study.yaml --out report --strict`. Stages: `--stage build-model | run | report`.
