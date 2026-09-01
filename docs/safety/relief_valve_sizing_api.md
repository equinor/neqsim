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
| Two-phase required area | $5.88\times10^{-5}$ m² |
| Fire heat input for 80 m² with drainage | 1570 kW |

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
implementation uses a fixed environmental factor of 1.0 and branches on `hasDrainage`, giving approximately

$$Q=43192A_w^{0.82}\ \mathrm{W}$$

with drainage, and

$$Q=70959A_w^{0.82}\ \mathrm{W}$$

without drainage, for $A_w$ in m². The `hasFireFighting` argument is currently retained in the public signature but
does not change the numerical factor. Do not infer a firefighting credit from that boolean.

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
