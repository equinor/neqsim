---
title: "Relief-Valve Sizing Screening"
description: "Static NeqSim screening APIs for gas, liquid, two-phase, and fire-case relief calculations, with explicit SI units and engineering limitations."
---

`ReliefValveSizing` is a static screening utility for preliminary pressure-relief calculations. It does not hold a
thermodynamic system or relief-valve state: the constructor is private, and callers supply the required relieving
properties directly to each static method.

The calculations support transparent comparisons and early design studies. They do not establish scenario
completeness, certified valve capacity, installation acceptability, conformity with a purchased standard edition, or
approval for design or construction. Confirm the governing edition, certified coefficients, applicable correction
factors, inlet and outlet hydraulics, reaction forces, disposal-system back pressure, and independent review before
using a result for an engineered relief system.

**Class:** `neqsim.process.util.fire.ReliefValveSizing`

## Unit contract

The public methods do not accept unit strings. Convert all inputs before calling them.

| Quantity | Required unit or basis |
| --- | --- |
| Mass flow | kg/s |
| Volumetric flow | m³/s at relieving conditions |
| Set and back pressure | Pa absolute |
| Temperature | K |
| Molecular weight | kg/mol |
| Density | kg/m³ at relieving conditions |
| Dynamic viscosity | Pa·s |
| Latent heat | J/kg |
| Heat capacity | J/(kg·K) |
| Orifice and wetted area | m² |
| Gas fraction, overpressure, $Z$, and $C_p/C_v$ | dimensionless |

## Current API

| Screening task | Static method | Result |
| --- | --- | --- |
| Gas or vapour required area | `calculateRequiredArea(...)` | `PSVSizingResult`, including required and selected areas |
| Gas or vapour capacity | `calculateMassFlowCapacity(...)` | Capacity in kg/s |
| Liquid required area | `calculateLiquidReliefArea(...)` | `LiquidPSVSizingResult` |
| Two-phase required area | `calculateTwoPhaseReliefArea(...)` | Area in m² |
| Wetted-surface fire heat input | `calculateAPI521FireHeatInput(...)` | Heat input in W |
| Reseat pressure screening | `calculateBlowdownPressure(...)` | Pressure in Pa |
| Approximate valve coefficient | `calculateCv(...)` | Approximate $C_v$ |

## Complete Java 8 example

The following program exercises the gas, liquid, two-phase, and fire helpers with explicit SI conversions.

```java
import java.util.Locale;
import java.util.logging.Logger;
import neqsim.process.util.fire.ReliefValveSizing;

public final class ReliefValveSizingExample {
  private static final Logger LOGGER =
      Logger.getLogger(ReliefValveSizingExample.class.getName());

  private ReliefValveSizingExample() {
  }

  public static void main(String[] args) {
    ReliefValveSizing.PSVSizingResult gasResult =
        ReliefValveSizing.calculateRequiredArea(
            5000.0 / 3600.0,
            110.0e5,
            0.10,
            1.013e5,
            333.15,
            0.018,
            0.95,
            1.30,
            false,
            false);

    ReliefValveSizing.LiquidPSVSizingResult liquidResult =
        ReliefValveSizing.calculateLiquidReliefArea(
            50.0 / 3600.0,
            850.0,
            25.0e5,
            0.10,
            1.013e5,
            0.004,
            false);

    double twoPhaseArea =
        ReliefValveSizing.calculateTwoPhaseReliefArea(
            30000.0 / 3600.0,
            80.0e5,
            0.10,
            5.0e5,
            373.15,
            0.30,
            50.0,
            700.0,
            250000.0,
            2500.0);

    double fireHeatInput =
        ReliefValveSizing.calculateAPI521FireHeatInput(80.0, true, true);

    if (gasResult.getRequiredArea() <= 0.0
        || liquidResult.getRequiredAreaM2() <= 0.0
        || twoPhaseArea <= 0.0
        || fireHeatInput <= 0.0) {
      throw new IllegalStateException("Relief screening produced a non-positive result");
    }

    LOGGER.info(
        String.format(
            Locale.ROOT,
            "gas=%.6g m2 (%s), liquid=%.6g m2 (%s), two-phase=%.6g m2, fire=%.1f kW",
            gasResult.getRequiredArea(),
            gasResult.getRecommendedOrifice(),
            liquidResult.getRequiredAreaM2(),
            liquidResult.getRecommendedOrifice(),
            twoPhaseArea,
            fireHeatInput / 1000.0));
  }
}
```

The current implementation gives approximately:

| Result | Screening value |
| --- | ---: |
| Gas required area and selected standard orifice | $6.74\times10^{-5}$ m²; D |
| Liquid required area and selected standard orifice | $2.71\times10^{-4}$ m²; G |
| Two-phase required area | $5.89\times10^{-5}$ m² |
| Fire heat input for 80 m² with adequate drainage and prompt firefighting | 1570 kW |

These values are regression examples for the stated inputs, not recommended design cases.

## Gas and vapour screening

`calculateRequiredArea(...)` uses the source implementation's gas/vapour screening equation and returns both the
required area and the first standard D-through-T area that is not smaller. The method uses $K_d=0.975$, $K_c=0.9$
when an upstream rupture disk is declared and $K_c=1.0$ otherwise, plus the implemented simplified back-pressure
correction. It does not obtain molecular weight, compressibility, or heat-capacity ratio from a NeqSim fluid; the
caller must supply properties that are consistent at the relieving state.

Use these explicit result getters:

- `getRequiredArea()` and `getRequiredAreaIn2()`;
- `getRecommendedOrifice()`, `getSelectedArea()`, and `getSelectedAreaIn2()`;
- `getMassFlowCapacity()` in kg/s; and
- `getDischargeCoefficient()`, `getBackPressureCorrectionFactor()`, and
  `getCombinationCorrectionFactor()`.

### Forward gas capacity

`calculateMassFlowCapacity` evaluates isentropic nozzle discharge using effective area in m², molecular
weight in kg/mol and absolute pressures in Pa. It uses the choked expression below the critical downstream
pressure ratio and the subcritical expression above it. Forward capacity is zero when back pressure
reaches or exceeds the relieving pressure.

For 100 mm², 50 bara relieving pressure, 300 K, 0.020 kg/mol, $Z=1$, $k=1.3$ and $K_d=0.975$, the
capacity is 0.92110 kg/s at 1 bara back pressure and 0.76684 kg/s at 40 bara. Earlier versions used a
kilomole gas constant with kg/mol inputs and understated forward capacity by approximately 31.6.
Recalculate historical results that used this method with the documented units. Required-area screening
uses a separate equation.

## Liquid screening

`calculateLiquidReliefArea(...)` requires volumetric flow at relieving conditions, density, absolute pressures,
viscosity, and the balanced-bellows selection. The current implementation applies its simplified $K_d$, $K_w$,
$K_v$, and overpressure correction before selecting a standard orifice.

`LiquidPSVSizingResult` exposes `getRequiredAreaM2()`, `getRequiredAreaIn2()`, `getMassFlowRate()`,
`getVolumeFlowRate()`, `getRecommendedOrifice()`, `getSelectedAreaIn2()`, `getDischargeCoefficient()`,
`getBackPressureCorrectionFactor()`, and `getViscosityCorrectionFactor()`. It does not contain a $K_c$ field.

## Two-phase omega-method screening

The source defines the inlet mixture specific volume as

$$v_{\mathrm{mix}}=xv_g+(1-x)v_l$$

and evaluates

$$\omega=\frac{xv_g}{v_{\mathrm{mix}}}+\frac{c_{p,l}TP_0(v_g-v_l)^2}{h_{fg}^2v_{\mathrm{mix}}}$$

where $x$ is inlet gas mass fraction, $v_g$ and $v_l$ are gas and liquid specific volumes, $c_{p,l}$ is liquid heat
capacity, $T$ is inlet temperature, $P_0$ is relieving pressure, and $h_{fg}$ is latent heat. The method then applies
the implemented critical-ratio expression, back-pressure floor, mass-flux expression, and $K_d=0.85$.

The method returns only required area. It does not select an orifice, calculate fluid properties, establish the
applicable two-phase scenario, or qualify homogeneous-equilibrium assumptions.

## Wetted-surface fire heat input

Call `calculateAPI521FireHeatInput(wettedAreaM2, hasDrainage, hasFireFighting)` with all three arguments. The current
implementation uses a fixed environmental factor of 1.0 and gives approximately

$$Q=43192A_w^{0.82}\ \mathrm{W}$$

when adequate drainage and prompt firefighting are both credited, and

$$Q=70959A_w^{0.82}\ \mathrm{W}$$

when either credit is absent, for $A_w$ in m². At 80 m² this gives 1.570 MW and 2.580 MW respectively.
In previous versions the lower coefficient depended on drainage alone. Recalculate historical fire loads where
prompt firefighting was unavailable. New `FireCaseRelief` instances require explicit credit for both conditions.

## Sizing a valve shared by several relief cases

`OverpressureProtectionStudy.evaluate()` calculates the required area for each credible demand using its own phase,
rate and relieving properties. Access the per-case areas, assumptions, pressure checks and warnings through
`getScenarioResults()`. The original case register retains exclusions. The largest required area selects the valve.
Equal areas retain declaration order. A credible entry with a zero or invalid rate or missing required properties
leaves the overall size unresolved. Document an excluded case explicitly when no relief demand applies.

`getGoverningScenario()` on the evaluated result identifies the orifice-sizing case.
`getMaximumLoadScenario()` identifies the maximum mass rate for disposal. The legacy
`governingScenario()` on the study itself remains a mass-load lookup. Re-run historical serialized studies
to obtain per-case evidence. The disposal rate still needs a project concurrency basis and network hydraulics.

For two gases at 50 bara set pressure, 300 K, $Z=1$ and $k=1.3$, 2 kg/s at 0.032 kg/mol requires 0.242 in²,
while 1.5 kg/s at 0.008 kg/mol requires 0.363 in². These cases share 10% overpressure and atmospheric backpressure.
The second controls the orifice. The first supplies the maximum individual disposal load.

The set pressure and MAWP inputs are absolute. The screening methods apply percentage overpressure and accumulation
to gauge pressure relative to 1.01325 bara. For instance, a set pressure of 11.01325 bara and 10% overpressure
give 12.01325 bara relieving pressure. Earlier helper versions multiplied absolute pressure directly.
The accumulation check uses the assumed relieving pressure. An independent transient run is needed to establish the
actual peak. Check the governing construction code for low-pressure allowances and staggered device settings.

The installed-device section in the engineering calculation report checks each credible demand against the selected
area and project piping-loss limits. It uses scenario mass flow and a fixed Darcy friction factor of 0.02.
It records the gauge set-pressure denominator and relieving density. Rated-flow installation checks, certified
valve data and local header backpressure require separate substantiation.

## Engineering limits

- Select the relieving scenario and accumulation basis from the governing project requirements; do not reuse the
  example's 10% overpressure automatically.
- Obtain fluid properties at a consistent relieving state and document their source and uncertainty.
- Check certified capacity data and the actual valve, rupture-disk, inlet-line, tailpipe, header, and flare-system
  configuration independently.
- Treat the built-in standard-orifice selection as a screening convenience; it does not select a vendor valve or
  certify a purchased device.
- Use a dynamic pressure/inventory model when the transient source, heat input, phase change, disposal-system
  interaction, or valve dynamics control the case.

## Related documentation

- [Dynamic PSV sizing example](psv_dynamic_sizing_example.md)
- [Fire and blowdown capabilities](fire_blowdown_capabilities.md)
- [Blocked-in liquid thermal expansion](blocked_in_liquid_thermal_expansion.md)
- [Safety systems documentation](README.md)
