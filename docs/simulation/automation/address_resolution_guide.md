# Address Resolution Guide

This page explains how `ProcessAutomation` resolves string addresses such as `"HP Sep.gasOutStream.temperature"`.

## Resolution order
1. Parse optional area prefix (`Area::...`).
2. Resolve unit name.
3. Resolve stream port (if present).
4. Resolve property token.

## Recommended pattern
- Prefer exact unit names from `getUnitList()`.
- Cache resolved addresses in your client.
- Re-discover address maps whenever model topology changes.
