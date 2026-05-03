# Variable Catalog Patterns

Use `getVariableList(unitName)` and `getVariableList(unitName, type)` to build catalogs for UI forms and APIs.

## Patterns
- Build a per-unit map: `unit -> [SimulationVariable]`.
- Split into INPUT and OUTPUT catalogs.
- Persist default units with each variable.

## Why
Catalog-first automation prevents runtime write attempts to read-only variables.
