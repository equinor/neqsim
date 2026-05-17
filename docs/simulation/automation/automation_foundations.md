# ProcessAutomation Foundations

This guide consolidates core automation patterns for `ProcessAutomation`.

## Address Resolution

Addresses are resolved in this order:
1. Optional area prefix (`Area::...`)
2. Unit name
3. Stream-port token (if present)
4. Property token

Use exact names from discovery APIs and rebuild address maps when topology changes.

## Variable Cataloging

Use:
- `getVariableList(unitName)`
- `getVariableList(unitName, SimulationVariable.VariableType.INPUT/OUTPUT)`

Recommended pattern:
- Build `unit -> variables` catalogs.
- Separate writable INPUT variables from read-only OUTPUT variables.
- Persist default units with each variable for UI and API contracts.

## Unit Handling

`getVariableValue(address, unit)` and `setVariableValue(address, value, unit)` are unit-aware.

Practical conventions:
- Temperature: `C` or `K` (choose one baseline per workflow)
- Pressure: `bara`
- Flow: track mass (`kg/hr`) vs molar (`mole/sec`) explicitly

Always persist both value and unit in logs/results payloads.

## Safe Accessor JSON Schema

Safe accessors (`getVariableValueSafe`, `setVariableValueSafe`) return JSON payloads with fields such as:
- `status`
- `originalAddress`
- `correctedAddress` (if auto-corrected)
- `value`, `unit` (read responses)
- `errorCategory`, `message` (failure responses)

Treat responses as machine-readable retry/control flow signals.
