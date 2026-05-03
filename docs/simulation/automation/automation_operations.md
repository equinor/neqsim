# ProcessAutomation Operations and Troubleshooting

This guide consolidates diagnostics, validation, and troubleshooting practices.

## Diagnostics Playbook

Use `AutomationDiagnostics` for operational insight:
1. Collect safe-access responses during runtime.
2. Inspect `getDiagnostics().getLearningReport()` regularly.
3. Promote repeated auto-corrections into canonical address dictionaries.
4. Alert when bounds violations or address failures increase.

## Input Validation and Bounds

`setVariableValueSafe` performs bounds checks before write operations.

Client recommendations:
- Keep engineering limits in configuration.
- Validate requested values before calls.
- Log rejected writes with address/value/unit context.

## Troubleshooting Checklist

Common failure modes:
- Unit not found: refresh discovered unit list and name mapping.
- Variable not writable: verify variable type is INPUT.
- Address drift after model edits: regenerate catalogs.
- Frequent auto-corrections: migrate to canonical addresses.

Quick checks:
- Discovery output (`getAreaList`, `getUnitList`) matches model topology.
- Stream-port segments are included where required.
- Requested units are valid for the addressed property.
