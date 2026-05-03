# Unit Conversion Matrix for Automation

`getVariableValue(address, unit)` and `setVariableValue(address, value, unit)` support unit-aware access.

## Practical guidance
- Temperature: use `C`, `K`, or `F` consistently in a workflow.
- Pressure: standardize on `bara` for process-level scripts.
- Flow: distinguish mass (`kg/hr`) and molar (`mole/sec`) outputs.

Always store both value and unit in external logs.
