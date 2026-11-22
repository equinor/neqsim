---
title: "Choke Valve Calculations and Operating Practice"
description: "Choke flow coefficient (Cv) to orifice conversion, discharge coefficient, multiphase model selection, sizing and operating-range screening, clean-up and sand limits, bean-up, and Cv-based condition monitoring with NeqSim."
---

# Choke Valve Calculations and Operating Practice

A choke is a control valve that restricts flow to set production or injection rate, protect
downstream equipment from slugging and hold a stable downstream pressure. Chokes are critical
elements: malfunction affects safety, environment and rate, and trim erosion is likely when the
pressure ratio is high, the gas fraction is high, sand is produced, the Cv is too large, the design
or material is wrong, or during clean-up. This page gives the calculations and screening rules, and
the NeqSim classes that implement them. Flow models are described in
[Well Choke Implementation](well_choke_implementation.md), and the protection scenario in
[Choke Collapse PSD Protection](../wiki/choke_collapse_psd_protection.md).

## Choke characteristic, Cv and capacity

The relation between stem travel (opening) and flow capacity is the choke characteristic, normally
given as a Cv-versus-opening curve. Plug-and-cage trims reach a linear, equal-percentage or custom
curve through the hole pattern in the cage; rotating-disc trims give an S-shaped curve and reach
maximum Cv when the disc holes overlap. Use `TabulatedValveCharacteristic` for a vendor or
measured curve and the `Linear`, `EqualPercentage`, `QuickOpening` and `ModifiedParabolic`
characteristics for idealised trims.

The US flow coefficient $C_v$ is the flow of water at 60 degF in US gallons per minute that passes
with a pressure drop of 1 psi. A choke with $C_v = 1$ passes $6.31 \cdot 10^{-5}$ m³/s at 6895 Pa.
If the pressure drop is only the acceleration of the fluid to the throat velocity $U = \sqrt{2
\Delta P/\rho}$ (Bernoulli, no pressure recovery), the flow area is

$$
A_1 = \frac{Q_1}{\sqrt{2 \Delta P_1 / \rho_w}} = 1.70 \cdot 10^{-5}\ \text{m}^2
$$

which is a circular orifice of 4.65 mm. A choke of $C_v = 100$ is therefore a hundred such areas:
an orifice of 46.5 mm. With a discharge coefficient $C_d$ that combines the contraction at the vena
contracta and energy losses (about 0.61 for a sharp 90 degree entry, approaching one for a rounded
entry, specific to the trim and opening):

$$
C_v = 46\,224\, C_d\, D^2 \qquad (D \text{ in m}), \qquad A = \frac{C_v A_1}{C_d}
$$

Convert Cv to a diameter with the same $C_d$ that the flow model will use. Then the capacity of the
model equals the data-sheet Cv. Letting a simulator convert Cv to a diameter internally hides the
discharge coefficient it assumed, and results can differ by several percent from a model that uses a
different $C_d$.

```java
import neqsim.process.mechanicaldesign.valve.choke.ChokeFlowCoefficient;

double d = ChokeFlowCoefficient.orificeDiameterFromCv(100.0, 1.0);   // 0.0465 m
double cv = ChokeFlowCoefficient.cvFromOrificeDiameter(d, 1.0);      // 100
double cd = ChokeFlowCoefficient.dischargeCoefficientFromCv(100.0, d); // 1.0
```

For a multiphase choke model on a valve:

```java
ValveMechanicalDesign design = choke.getMechanicalDesign();
design.setValveSizingStandard("Sachdeva");
design.setChokeFlowCoefficient(100.0, 0.84);        // diameter and Cd set together
double cvAt50 = design.getChokeEquivalentCv(50.0);  // 50 with a linear characteristic
```

## Flow of mixtures other than water

For gas and gas-liquid mixtures use a multiphase model. The mechanistic model
(`SachdevaChokeFlow`) assumes frozen composition, homogeneous flow, polytropic gas expansion,
incompressible liquid and no friction, and handles critical and subcritical flow. The empirical
Gilbert-type correlations (`GilbertChokeFlow`) are critical-flow estimates. A pure isenthalpic
valve (`ThrottlingValve` without a choke sizing method) gives only the downstream state for a
given pressure drop and carries no choke characteristic. Models that report the pressure at a
venturi constriction rather than downstream of the restriction are for metering and rate
allocation, not for sizing or equivalent-choke-size calculations.

The discharge coefficient is the main calibration parameter. Express actual flow as mass flow when
comparing models: the split into standard-condition gas and liquid volumes depends on the PVT model
and masks the real agreement.

## Sizing considerations

| Consideration | Recommendation | Class |
|---|---|---|
| Operating range, plug-and-cage trim | Operate above about 20 % of maximum Cv | `ChokeOperatingEnvelope` |
| Operating range, rotating-disc trim | Operate above about 60 % of maximum Cv | `ChokeOperatingEnvelope` |
| Collapse capacity | The pressure-protection system must handle the flow of a collapsed (fully open) choke; do not oversize the choke beyond that capacity | `ChokeOperatingEnvelope`, `ChokeCollapseAnalyzer` |
| Noise | Check the predicted level against the limit for the flow condition and trim | `ControlValveNoise_IEC_60534_8_3` |
| Clean-up | Smallest port at least three times the largest particle; avoid complex entry paths | `ChokeOperatingEnvelope` |

```java
ChokeOperatingEnvelope.Result result = new ChokeOperatingEnvelope()
    .setTrimType(ChokeOperatingEnvelope.TrimType.PLUG_AND_CAGE)
    .setOperatingPoint(500.0, 15.0, new LinearCharacteristic())
    .setCleanUpSizes(4.0, 14.0)
    .setCollapseCapacity(30.0, 40.0)
    .setOpeningTimes(120.0, 60.0)
    .evaluate();
ChokeOperatingEnvelope.Status status = result.getOverallStatus();
String text = result.get("operating range").getMessage();
```

A WARN is a deviation from a recommended limit that needs justification; FAIL is a violated hard
criterion; NOT_EVALUATED means the input was not supplied and is never a pass.

## Operation

**Bean-up.** Wells are started by opening the choke gradually to the target, manually or through an
automatic sequence in the control system. The choke must open more slowly than the upstream block
valve: set `setOpeningTravelTime` on the choke valve and verify it with
`ChokeOperatingEnvelope.setOpeningTimes`.

**Clean-up.** Port size must pass the debris, and the trim material must withstand clean-up
particles and the pressure drop. Hard erosion-resistant materials such as tungsten carbide are
brittle, so a brick stopper upstream is relevant; clean-up chokes are often steel. A junk catcher
upstream helps. For subsea wells, do most of the choking topside, where an eroded choke is easier to
replace.

**Sand.** Erosion of the plug nose and cage reduces controllability, and the trim must be replaced
when the choking response degrades or the closed choke leaks. The minimum safe opening depends on
throttling velocity, pressure drop and sand load; supply that minimum to
`ChokeOperatingEnvelope.setSandService`. Without a calculation, keep at least 20 % of maximum Cv.
`ChokeFlowCoefficient.throttlingVelocity` gives the nominal velocity from flow and Cv.

## Condition monitoring

Compare the observed Cv, from measured flow and pressure drop, with the calibrated theoretical Cv
at the actual position:

* above theory: erosion (opened flow area) or bypass;
* below theory: plugging or deposits;
* flow when commanded closed: seat leakage;
* no Cv change after an opening change: stuck or disconnected stem.

```java
double observed = ChokeFlowCoefficient.observedCvFromMassFlow(massFlowKgPerS, densityKgPerM3, dpPa);
double theoretical = ChokeFlowCoefficient.cvAtOpening(500.0, 40.0, characteristic);
ChokeConditionMonitor.Assessment a =
    new ChokeConditionMonitor().setDeviationTolerance(0.10).assessCv(observed, theoretical);
ChokeConditionMonitor.Condition condition = a.getCondition();
double capacityRatio = a.getCapacityRatio();   // scale the collapse flow with this
```

Use `assessSeries` for a historian window; it uses the median deviation and tests responsiveness.
Where choke collapse sets the relief or protection capacity, multiply the design collapse flow by
the capacity ratio and keep the deviation inside the accepted limit. The default 10 % tolerance and
0.5 % closed-leak limit are screening values; replace them with the acceptance criteria of the
asset. For gas or flashing service the observed Cv is an equivalent coefficient on the upstream
mixture density: compare it only with a theoretical curve of the same definition.

## Limitations

* The multiphase models are homogeneous (no slip) and the geometry-specific loss and pressure
  recovery of long (bean-type) chokes are represented through the discharge coefficient, not
  resolved.
* The minimum position for sand erosion is an input; it is not computed here.
* The screening limits are recommendations, not a substitute for vendor limits.

## References

* Sachdeva, R., Schmidt, Z., Brill, J.P. and Blais, R.M. (1986). Two-Phase Flow Through Chokes. SPE 15657.
* Perkins, T.K. (1990). Critical and Subcritical Flow of Multiphase Mixtures Through Chokes. SPE 20633.
* IEC 60534-2-1 and IEC 60534-8-3 for single-phase sizing and valve noise.
* DNV-RP-O501 for erosive wear in piping systems and chokes.
