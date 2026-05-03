# Automation Troubleshooting

## Common issues
- **Unit not found**: refresh unit list and validate naming.
- **Variable not writable**: ensure variable type is INPUT.
- **Address drift after model edits**: regenerate catalogs.
- **Frequent auto-corrections**: migrate to canonical addresses.

## Quick checklist
- `getAreaList()` / `getUnitList()` still match expected topology.
- Addresses include stream-port segments where required.
- Requested units are valid for target properties.
