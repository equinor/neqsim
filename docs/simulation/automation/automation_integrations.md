# ProcessAutomation Integration Patterns

This guide covers model integration patterns for multi-area systems, optimizers, and digital twins.

## Multi-Area Addressing (ProcessModel)

Use area-qualified addresses:
- `Separation::HP Sep.pressure`
- `Compression::Main Compressor.outletPressure`

Best practices:
- Discover areas via `getAreaList()`
- Discover unit names per area via `getUnitList(area)`
- Avoid hardcoding area labels in shared libraries

## Optimizer Integration

Typical loop:
1. Select INPUT variables from typed catalogs.
2. Apply candidate values.
3. Run the process model.
4. Read OUTPUT variables for objectives/constraints.

Use safe accessors during early-stage model evolution where naming changes are frequent.

## Digital Twin Integration

Recommended service pattern:
- Poll telemetry-aligned OUTPUT variables on a fixed cadence.
- Apply supervisory setpoints to validated INPUT addresses.
- Track correction and failure rates from diagnostics for governance.

Pair automation access with lifecycle snapshots for reproducible operational states.
