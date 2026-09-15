---
title: "Sarir atmospheric side-stripper equilibrium-contact screen"
description: "Source-bounded single-stage screening for published Sarir side-stripper steam services."
---

# Sarir atmospheric side-stripper equilibrium-contact screen

`SarirAtmosphericSideStripperContactScreen` adds a deliberately narrow fractionation
screen to the source-bounded Sarir atmospheric workflow. It contacts one calculated
atmospheric-column liquid side draw with the matching published side-stripper steam
row on one equilibrium `SimpleTray`.

## Public source and provenance

The source is Almansouri (2022), *Journal of Engineering Research*, DOI
[`10.66411/jer.v33i.46`](https://doi.org/10.66411/jer.v33i.46), licensed CC BY 4.0.
The executable boundary uses only these Table 3 values:

| Receiving service | Steam rate (kg/h) | Temperature (°C) | Pressure (kPa, as reported) |
|---|---:|---:|---:|
| Kerosene side stripper | 68.04 | 150 | 476 |
| Diesel side stripper | 226.8 | 150 | 476 |

The source does not publish the pressure basis, steam quality, enthalpy, injection
location, side-stripper tray count, pressure profile, or tray efficiency. Those gaps
are not silently filled.

## Required boundary

The screen accepts an already rigorously solved `SarirAtmosphericFractionationCase`.
It selects the configured kerosene or diesel liquid side draw and requires the caller
to provide all of the following:

- the corresponding side-stripper service;
- an independently prepared, essentially pure-water, single-gas-phase stream at the
  exact published rate and temperature;
- an explicit `ABSOLUTE` or `GAUGE` interpretation of the reported pressure;
- a non-blank description of the independent thermodynamic-state basis; and
- an explicit pressure for the single equilibrium contact, in bar absolute.

The prepared stream must retain the crude system's component slate and numbering, for
example by starting from `getEmptySystemClone()`. This prevents ambiguous mixer
component mapping.

The Java contact fixture explicitly uses bottom-up liquid draw indices 3 and 2,
below the feed at index 4, at 700 K reboiler temperature and reflux ratio 1.0.
These synthetic locations provide nonzero liquid feeds for contact qualification;
they are not published plant tray locations. With the same controls, the earlier
indices 24 and 15 can be dry even when the atmospheric column reports convergence.
Convergence alone therefore does not establish a usable liquid side draw.

## Calculation and evidence

The selected liquid side draw and steam are added to a standalone `SimpleTray`. A
single pressure-specified adiabatic equilibrium contact is calculated. The immutable
result reports:

- selected service and exact source-row label;
- source and modeled steam rates;
- atmospheric side-draw rate;
- contact pressure and calculated temperature;
- vapor and liquid product rates; and
- relative total mass-closure error.

Configuration or execution fails closed if the atmospheric column is not rigorously
solved, the service is unsupported, the side draw is absent, the source boundary has
changed, the steam state is inconsistent, the component slate is incompatible, the
contact pressure is invalid, either product is nonpositive, or closure exceeds
`1e-6`.

## Scope boundary

This is a one-stage engineering screen, not a side-stripper design or plant
reproduction. It does not claim or infer a tray mapping, tray count, multistage
topology, tray efficiency, pressure profile, steam quality, water-removal train, heat
duty, product specification, or plant yield. Those require independent data and a
separate capability contract.
