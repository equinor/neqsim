---
title: "DOE Big Hill terminal-Watson qualification"
description: "Public-data qualification of a Watson-derived representative boiling point for the Big Hill 1050 degF+ residue."
---

# DOE Big Hill terminal-Watson qualification

This benchmark qualifies a source-derived representative normal boiling point for an open-ended
refinery residue. It complements the one-sided-boundary qualification without inventing a finite
upper cut limit.

## Source and provenance

The source is the public DOE/SPR **Big Hill Sweet** comprehensive assay workbook:

- workbook: <https://www.spr.doe.gov/reports/Assays/2024/BigHillSwAssay.xlsx>
- DOE assay landing page: <https://www.spr.doe.gov/reports/Crude_Oil_Assays.html>
- report date recorded in the workbook: **24 September 2021**

The workbook reports the 1050 degF+ residue as 11.56 mass%, SG60/60 = 1.0089 and UOP K = 11.7.
DOE/SPR distributes the workbook for informational use and does not provide a separate license
statement. The regression freezes only those attributed numerical facts; the workbook is not
redistributed.

## Calculation

For representative normal boiling point $T_b$ in K, specific gravity $SG$ and dimensionless
Watson factor $K_W$:

$$K_W=\frac{(1.8T_b)^{1/3}}{SG}$$

Inverting the same relation gives:

$$T_b=\frac{(K_W SG)^3}{1.8}$$

The frozen DOE values imply:

- representative boiling point: **913.7543263804 K**;
- equivalent temperature: **1185.0877874847 degF**;
- published lower boundary: **1050 degF = 838.7055555556 K**.

The representative point is above the reported lower boundary and does not create an upper
boundary.

## Acceptance and maturity

`OilAssayCharacterisationDoeBigHillWatsonTerminalTest` requires:

- the DOE values to reproduce 913.7543263804 K;
- a forward K-to-temperature and independent temperature-to-K round-trip;
- exact-equivalent SG and API-gravity input views to agree;
- missing density, non-positive factors and ambiguous dual representative inputs to fail closed;
- a derived representative point outside a terminal boundary to fail before system mutation;
- factor, boundary and derived temperature metadata to survive cloning; and
- pseudo-component generation through the existing correlation to preserve exact assay mass.

The capability is **qualified for Watson-derived representative-boiling-point metadata and
fail-closed terminal-cut preparation**. It is O(1) per cut.

## Evidence boundary

The DOE workbook independently supplies the Watson factor and SG, but not residue molecular weight.
Consequently this benchmark does not validate the molecular weight inferred by NeqSim's existing
inverse petroleum correlation, critical properties, acentric factor, EOS behavior, VLE, atmospheric
product yields or vacuum fractionation. It also does not infer the C2-C4 gas composition or a
representative property for the C5-175 degF light fraction. Those remain separate #3305 contracts.
