---
title: "DOE Big Hill terminal-cut boundary qualification"
description: "Public-data qualification of one-sided refinery-assay boiling-boundary metadata."
---

# DOE Big Hill terminal-cut boundary qualification

This benchmark qualifies one-sided boiling-boundary metadata against the public U.S. Department of
Energy Strategic Petroleum Reserve Big Hill Sweet assay. It prevents an open terminal cut from
being silently converted into a fabricated finite interval.

## Source and provenance

The source is the DOE/SPR **Big Hill Sweet** comprehensive assay workbook:

- workbook: <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- DOE assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in the workbook: **24 September 2021**

DOE/SPR publicly distributes the workbook with an informational-use disclaimer and no separate
license statement. The regression freezes only the attributed 1050 degF+ terminal boundary, its
11.56 mass% yield and its 1.0089 reported relative density. The workbook is not redistributed.

## Engineering contract

The DOE table reports a residue with a lower boiling limit of **1050 degF** and no finite upper
limit. NeqSim stores that value as **838.7055555556 K**, reports that a lower limit is present, and
reports that no upper limit or complete boiling range is present.

A one-sided limit does not determine a representative boiling point. It is therefore insufficient
by itself for the existing inverse molecular-weight correlation. Pseudo-component generation must
fail before mutating the thermodynamic system unless the caller also supplies either:

- an independently supported representative boiling point and density; or
- an explicit molar mass and density.

The focused generation control uses 650 g/mol only as an explicit engineering input to prove this
API path and exact mass closure. DOE does not report that value, and this qualification does not
validate it.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillTerminalBoundaryTest` requires:

- exact Fahrenheit-to-Kelvin preservation of the DOE 1050 degF+ lower limit;
- Kelvin, Celsius and Fahrenheit terminal-limit inputs to agree numerically;
- independent lower/upper presence views and unchanged complete-range semantics;
- contradictory limits or representative values to fail without partial cut mutation;
- one-sided metadata to survive thermodynamic-system cloning;
- missing representative properties to fail before adding any component; and
- an explicitly characterized terminal cut to generate with the existing exact mass-closure gate.

The capability is **qualified for terminal-bound metadata and fail-closed pseudo-component
preparation**. It is O(1) per boundary assignment and does not alter thermodynamic calculations.

## Stop boundary

This evidence does not choose a representative boiling point or molecular weight for the DOE
residue, infer C2-C4 gas composition, split a plus fraction, validate critical properties or VLE,
match atmospheric product yields, change a column solver, or qualify vacuum fractionation. Those
remain separate #3305 engineering contracts.
