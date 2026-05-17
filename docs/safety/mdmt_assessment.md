---
title: "MDMT Assessment per ASME UCS-66 and API 579"
description: "Minimum design metal temperature evaluation using MDMTCalculator — UCS-66 Curves A/B/C/D, UCS-66.1 stress-ratio reduction, API 579 fitness-for-service, and EN 13445 alternatives. Pairs with API 521 blowdown to verify low-temperature embrittlement margins."
---

# MDMT Assessment per ASME UCS-66 and API 579

`neqsim.process.safety.mdmt.MDMTCalculator` evaluates whether a vessel's
material selection is acceptable for the lowest temperature reached during
blowdown, autorefrigeration, or cold-vapour upset.

## Method

ASME BPVC Section VIII Div. 1 UCS-66 maps material grade and thickness to an
exemption temperature using one of four curves (A=carbon steel, B=normalized,
C/D=fine grain / impact tested). UCS-66.1 allows a thickness-equivalent
reduction when the actual stress is below the design stress:

$$
\Delta T = -\,\ln\!\left(\frac{\sigma_{actual}}{\sigma_{design}}\right) \cdot 100\,^{\circ}\mathrm{F}
$$

If the curve-A/B/C/D temperature minus $\Delta T$ is ≤ the calculated MDMT, the
vessel is exempt without impact testing.

## Code pattern

```java
MDMTCalculator calc = new MDMTCalculator();
calc.setMaterial("SA-516-70");      // selects Curve B
calc.setThicknessMM(38.0);
calc.setStressRatio(0.65);          // actual / allowable
double mdmt = calc.computeUCS66();  // °C — required minimum metal T
boolean ok = blowdownTmin >= mdmt + 5.0;  // 5 °C margin per operator practice
```

## Applicable standards

| Standard | Scope |
|----------|-------|
| ASME BPVC VIII Div. 1 UCS-66 | Carbon and low-alloy steel exemption curves |
| ASME BPVC VIII Div. 1 UCS-66.1 | Stress-ratio thickness reduction |
| API 579-1 / ASME FFS-1 | Fitness-for-service brittle-fracture assessment |
| EN 13445-2 | European pressure-vessel material rules |
| NORSOK M-001 | Material selection for petroleum installations |

## See also

- [Depressurization per API 521](depressurization_per_API_521.md)
- [Dispersion and Consequence Analysis](dispersion_and_consequence.md)
