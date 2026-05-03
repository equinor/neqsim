# Multi-Area Addressing Patterns

For `ProcessModel`, addresses should include area qualification: `Area::Unit.property`.

## Examples
- `Separation::HP Sep.pressure`
- `Compression::Main Compressor.outletPressure`

## Best practices
- Use `getAreaList()` and `getUnitList(area)` to generate validated paths.
- Avoid hard-coded area names in reusable tooling.
