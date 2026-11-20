---
name: neqsim-choke-valve-practice
description: "Choke valve calculations and operating practice: Cv to orifice diameter and discharge coefficient, multiphase choke model choice, trim characteristic, sizing checks (20 % / 60 % of max Cv, collapse capacity, noise), clean-up port size, sand limits, bean-up and opening speed, and Cv-based condition monitoring for erosion, plugging, seat leak or stuck stem. USE WHEN: sizing, rating, simulating or monitoring a production or injection choke."
last_verified: "2026-11-20"
---

# Choke valve calculations and operating practice

Read `docs/process/choke_valve_practice.md` for the equations and `docs/process/well_choke_implementation.md`
for the flow models. Verify signatures in the source before use. Use `@choke-valve` for the workflow,
`@process-model` for the surrounding flowsheet and `@safety-depressuring` for the relief scenario.

## Select the route

| Need | Route |
| --- | --- |
| Cv to orifice diameter, Cd from Cv and diameter, observed Cv from flow and dP | `ChokeFlowCoefficient` |
| Choke in a flowsheet with a data-sheet Cv | `ValveMechanicalDesign.setValveSizingStandard("Sachdeva")`, then `setChokeFlowCoefficient(cv, cd)` |
| Equivalent Cv at an opening | `ValveMechanicalDesign.getChokeEquivalentCv(percentOpening)` |
| Opening curve (vendor, measured, disc S-curve) | `TabulatedValveCharacteristic`; idealised trims: `LinearCharacteristic`, `EqualPercentageCharacteristic` |
| Critical or subcritical two-phase rate | `SachdevaChokeFlow` (homogeneous, frozen composition, polytropic gas); Gilbert family for critical-flow estimates |
| Downstream state only (no characteristic) | `ThrottlingValve` isenthalpic expansion |
| Collapse (loss of critical flow), flashing, cavitation | `ChokeCollapseAnalyzer`; protection scenario in `choke_collapse_psd_protection.md` |
| Noise | `ControlValveNoise_IEC_60534_8_3` |
| Sizing and operating screening | `ChokeOperatingEnvelope` |
| Erosion, plugging, seat leak, stuck stem | `ChokeConditionMonitor` |

## Define and execute the case

1. Record fluid, upstream and downstream pressure, temperature, rate, trim type, maximum Cv, the opening
   curve, the discharge coefficient and its source. State whether flow is critical.
2. Convert Cv to a diameter with the Cd the model uses; never let a Cv-to-orifice conversion hide its Cd.
   Cd depends on trim and opening: calibrate it on a measured point when one exists. The Gilbert family
   has Cd inside its constant and ignores it.
3. Report flow as mass flow when comparing models. Standard-condition gas and liquid volumes depend on the
   PVT model and mask agreement. The model is homogeneous, so check results against a measured test.
4. Screen the design with `ChokeOperatingEnvelope`: above about 20 % of maximum Cv for plug-and-cage,
   above about 60 % for rotating-disc, smallest port at least three times the largest particle, collapsed
   choke flow within the protection capacity, noise within the limit, and a choke that opens more slowly
   than its block valve. A missing input is NOT_EVALUATED, not a pass.
5. For sand, keep at least 20 % of maximum Cv unless a minimum position was calculated from throttling
   velocity (`ChokeFlowCoefficient.throttlingVelocity`), pressure drop and sand load; pass that minimum to
   `setSandService`. The calculation itself is not in NeqSim: state its source.
6. Bean-up: open gradually with `setOpeningTravelTime` and a stepped target (transient mode); clean-up
   chokes are steel or erosion-resistant and need a junk catcher, and subsea wells are best choked topside.
7. Monitor with observed Cv (`observedCvFromMassFlow`) against the calibrated theoretical Cv at the actual
   opening: above theory is erosion, below is plugging, flow at zero opening is seat leak, no change with
   opening is a stuck stem. Use `assessSeries` on a window. Scale the design collapse flow with
   `getCapacityRatio()` when collapse sets the relief capacity.
8. Eroded choke case: set `setChokeFlowCoefficient(observedCv, cd)` (choke path) or `setCv(observedCv)`
   (Kv path). `setFoulingFraction` can only lower the flow.

## Validate and report

- Check the round trip Cv to diameter to Cv and the Cv = 1 definition (1 US gal/min of water at 1 psi).
- Run a liquid case: the model flow must reproduce the Cv, `observedCv` of the result equals the data-sheet Cv.
- Report each screening limit as a recommendation with its WARN or FAIL, and give the default monitor
  tolerances (10 % deviation, 0.5 % closed leak) as assumptions to replace with asset criteria.
- Separate calibrated, vendor-supplied and assumed values.

## Boundaries

- No slip, no long-choke pressure recovery, no vendor-specific loss model: capture these through Cd.
- The sand minimum-position chart, material erosion rates and brick-stopper design are inputs, not
  calculated here. Use the erosion screening skills for line velocity and wall loss.
- A venturi-type model that reports the constriction pressure is for metering, not for sizing.
