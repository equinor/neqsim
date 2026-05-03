# Safe Accessor Response Schema

`getVariableValueSafe` and `setVariableValueSafe` return JSON payloads designed for resilient clients.

## Typical fields
- `status`
- `originalAddress`
- `correctedAddress` (when auto-corrected)
- `value` and `unit` (for read operations)
- `errorCategory` and `message` (on failure)

Treat the payload as a machine-readable contract for retry behavior.
