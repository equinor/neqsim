"""Frozen independent off-grid SRK paths; same constants and bounds as the parent reference."""
import json
from independent_reference import generate

TEMPERATURES = [442.5, 452.5, 457.5]
WATER_MOLE_FRACTIONS = [.06, .085, .095]
SAMPLE_MOLES = [.01, 10., 100.]
PRESSURES_BARA = [200, 180, 160, 140, 120, 100, 90, 80, 70, 60, 50, 40, 30,
                  20, 15, 10, 7.5, 5, 3.5, 2]

if __name__ == "__main__":
    records = generate(TEMPERATURES, WATER_MOLE_FRACTIONS, SAMPLE_MOLES, PRESSURES_BARA)
    print(json.dumps(records, indent=2, allow_nan=False))
