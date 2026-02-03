---
title: "Black-oil flash playbook from regression tests"
description: "The black-oil flash workflow is exercised in `SystemBlackOilTest`, which demonstrates both Eclipse deck import and direct tabular setup before running a flash. The following notes pair the tested setu..."
---

# Black-oil flash playbook from regression tests

The black-oil flash workflow is exercised in `SystemBlackOilTest`, which demonstrates both Eclipse deck import and direct tabular setup before running a flash. The following notes pair the tested setup with the theory it relies on so you can reproduce the flow efficiently.

## Importing Eclipse PVT decks

`testBasicFlash` constructs a minimal Eclipse deck with metric units, PVTO/PVTG/PVTW tables, and standard-condition densities, then feeds it to `EclipseBlackOilImporter.fromFile(...)`.【F:src/test/java/neqsim/blackoil/SystemBlackOilTest.java†L31-L74】 The importer returns a `BlackOilPVTTable` and an initialized `SystemBlackOil` instance whose bubblepoint is read directly from the tables.

Key steps mirrored from the test:

1. Write the deck text to disk (or keep it in memory) and call the importer.
2. Supply standard-condition densities (oil/gas/water) when constructing `SystemBlackOil`.
3. Set the current pressure, temperature, and standard volumes (`setStdTotals`) before calling `flash()`.
4. Inspect reservoir volumes, viscosities, and densities from the returned system object— the test only asserts positivity but those values correspond to the PVTO/PVTG/PVTW correlations.

The flash solves phase-split mass balance for three pseudo-phases using deck-derived formation volume factors \(B_o, B_g, B_w\), dissolved gas–oil ratio \(R_s\), and vaporized oil–gas ratio \(R_v\). Reservoir volumes are calculated as

\[
V_{res} = B_x \times V_{std}
\]

for each phase \(x\in\{o,g,w\}\), with viscosities pulled directly from the tables.

## Building PVT tables in code

`testDirectPVTTable` shows how to bypass deck parsing by interpolating PVTO/PVTG/PVTW data onto a merged pressure grid, then wrapping it in `BlackOilPVTTable.Record` entries.【F:src/test/java/neqsim/blackoil/SystemBlackOilTest.java†L77-L133】 The resulting table is flashed the same way as the imported one.

When scripting your own tests:

- Provide monotone pressure arrays for \(B_o\), \(B_g\), \(B_w\), \(R_s\), and \(R_v\) to avoid interpolation ambiguity.
- Use consistent viscosity units (Pa·s) and choose a bubblepoint (`Pb`) inside the pressure grid so gas liberation follows expected two-phase behavior.
- After `flash()`, validate density, viscosity, and reservoir volume signs as sanity checks, then compare against lab or simulator references.
