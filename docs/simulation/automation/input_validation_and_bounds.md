# Input Validation and Physical Bounds

Safe writes (`setVariableValueSafe`) perform bounds checks before applying values.

## Client-side recommendations
- Keep engineering limits in config (temperature, pressure, efficiency).
- Compare requested value against both client and server-side constraints.
- Log rejected writes with context (address, value, requested unit).
