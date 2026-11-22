# Flow-assurance study report templates

`neqsim flow-assurance-report --init <dir>` copies these files (without the `_template` suffix) and stops. It creates no study result.

| File | Purpose |
|------|---------|
| `basis.yaml` | Objective, decisions, battery limits, sources, conventions, fluids, acceptance criteria, assumptions, data gaps, model |
| `route.yaml` | Assets, segments (geometry, layers, ambient), features; unknown values are left out or marked `unavailable - TO BE CONFIRMED` |
| `cases.yaml` | Cases with explicit rate bases, topology variants and events |
| `standards_register.yaml` | Seed standards applicability register (public metadata only; re-verify before every issue) |

Rules: never invent route coordinates, design conditions, material properties, inspection evidence or approval identities. A value that
cannot be sourced stays `unavailable - TO BE CONFIRMED` and gets a `data_gaps` entry. A route that still contains the marker in a number
is rejected with exit code 2 and a message naming the field. The synthetic reference example is `devtools/fa_examples/offshore_tieback/`.
