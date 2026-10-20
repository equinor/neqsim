---
title: Labyrinth Seal Leakage
description: "Gas leakage through straight-through labyrinth seals with the Stodola, Egli, Vermes and Kearton correlations, using any NeqSim fluid."
---

# Labyrinth seal leakage

`LabyrinthSealFlowCalculator` (package `neqsim.process.equipment.compressor.seal`) estimates the gas mass flow
through a straight-through labyrinth seal with teeth on the stator. Typical uses are the process-side labyrinth
upstream of a dry gas seal, balance-piston labyrinths, and checking seal-gas supply against measured flow.

The calculator works with any NeqSim fluid. Gas properties at the inlet come from a TP flash of a clone of the
fluid; the caller's fluid is not changed:

- gas density $\rho_0$
- molar mass $M$
- the real-gas isentropic exponent $\kappa = -\frac{C_p}{C_v}\frac{v}{p}\left(\frac{\partial p}{\partial v}\right)_T$

## Geometry

`LabyrinthSealGeometry` holds the dimensions. Every setter takes a length unit (`"m"`, `"mm"`, `"inch"`, ...):

| Symbol | Setter | Meaning |
|---|---|---|
| $D$ | `setShaftDiameter` | Shaft diameter under the teeth |
| $c$ | `setRadialClearance` | Radial clearance between tooth tips and shaft |
| $t$ | `setToothPitch` | Axial distance between teeth |
| $b$ | `setTipWidth` | Tooth tip width (must be smaller than $t$) |
| $h$ | `setToothHeight` | Tooth height (informational) |
| $n$ | `setNumberOfTeeth` | Number of throttling teeth |

The leakage area is the annulus $A = \pi c (D + c)$.

## Methods

The equations below use pressure in kPa. $p_0$ is the upstream pressure, $p_n$ the downstream pressure and
$r = p_n/p_0$.

| `LabyrinthFlowMethod` | Mass flow |
|---|---|
| `STODOLA` | $\dot m = 0.71\,A\sqrt{1000\,\rho_0 (p_0^2 - p_n^2)/(p_0 n)}$ |
| `EGLI` | $\dot m = A\,C_r C_t C_c\,\rho_0 \sqrt{1000\,R T_0/M}$ with Aungier's contraction $C_r$, throttling $C_t$ and carry-over $C_c$ coefficients |
| `VERMES` | $\dot m = \mu A\sqrt{1000\,\rho_0 p_0}\sqrt{(1 - r^2)/(n + \ln r)}$, $\mu = 1/\sqrt{1-\alpha}$, $\alpha = 8.52/((t-b)/c + 7.23)$ |
| `KEARTON` | $\dot m = C_k\,0.71\,A\sqrt{1000\,p_0\rho_0}\sqrt{F(1 - r^2)/n}$, $C_k = 1 + 3.27(1 - e^{-0.4n})(c/t)^{2/3}$, $F$ = mean tooth pressure ratio of $p_i = \sqrt{p_0^2 - \tfrac{i}{n}(p_0^2 - p_n^2)}$ |

The Egli coefficients are:

- $C_r = 1 - 1/\left(3 + (54.3/(1 + 100c/b))^{3.45}\right)$
- $C_t = 2.143(\ln n - 1.464)(1 - r)^{0.375 r}/(n - 4.322)$
- $C_c$ is a function of $c/t$ whose constants change at $n = 12$

Egli is not defined near $n = 4.3$, where $C_t$ is singular.

Each result is capped at the critical flow of the clearance:

$$
\dot m_{crit} = A\sqrt{1000\,\rho_0 p_0\,\kappa\left(\frac{2}{\kappa+1}\right)^{(\kappa+1)/(\kappa-1)}}
$$

`isChoked()` reports when the cap is active. For Vermes, the flow is also treated as choked when $n + \ln r \le 0$.

## Validity range

Range problems never throw; this is the same convention the differential-pressure flow meters use. Some
geometries are outside a correlation's mathematical range:

- Egli with a contraction, throttling or carry-over coefficient that is not positive (for example $c/t = 0.5$ with
  five teeth).
- Vermes with a residual-energy factor $\alpha \ge 1$ (tip width close to the pitch).

For these, the method returns `NaN` instead of a negative or meaningless leakage. `getValidityViolations()` lists the
reason, prefixed by the method name, and `isWithinValidityRange()` returns `false`. `calculateAllMethods()` still
returns every method, with `NaN` for the undefined ones. `toJson()` writes undefined numbers as `null`.

Invalid inputs still throw. These include missing values, an outlet pressure that is not below the inlet pressure,
and non-positive dimensions.

## Example

```java
SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
geometry.setShaftDiameter(108.566, "mm");
geometry.setToothHeight(2.0, "mm");
geometry.setToothPitch(2.0, "mm");
geometry.setTipWidth(0.3, "mm");
geometry.setRadialClearance(0.2286, "mm");
geometry.setNumberOfTeeth(5);

LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(gas, geometry);
calc.setInletPressure(2.765, "barg");
calc.setInletTemperature(86.12, "C");
calc.setOutletPressure(1.5417, "barg");   // or calc.setPressureDrop(1223.3, "mbar")
calc.setMethod(LabyrinthFlowMethod.KEARTON);
calc.calculate();

double leakageKgPerHour = calc.getMassFlowRate("kg/hr");
double standardFlow = calc.getStandardVolumeFlowRate("Sm3/hr");
boolean choked = calc.isChoked();
boolean inRange = calc.isWithinValidityRange();   // reasons in calc.getValidityViolations()
Map<LabyrinthFlowMethod, Double> allMethodsKgPerSec = calc.calculateAllMethods();
```

Other outputs:

- `getActualVolumeFlowRate(unit)`: volume flow at inlet conditions.
- `getClearanceVelocity(unit)`: mean velocity in the clearance, based on inlet density.
- `getCriticalMassFlowRate(unit)`: the critical-flow cap.
- `hasLiquidAtInlet()`: true when the inlet flash finds liquid; gas-phase properties are then used.
- `toJson()`: summary of inputs and results.

Standard volumes are ideal-gas volumes at 15 °C and 1.01325 bara by default. Change them with
`setStandardConditions(...)`.

## Validation

Twenty-two reference cases from a GERG-2008 VBA leakage workbook cover:

- pressure drop up to venting to atmosphere
- 2 to 20 teeth
- clearance, pitch and temperature
- pressures up to 50 barg
- pure methane and pure nitrogen

With a `SystemGERG2008Eos` fluid, every single-phase case agrees within $2\times10^{-5}$ relative. The remaining
offset comes from the component molar masses used by the NeqSim GERG-2008 phase. SRK and PR fluids agree within
0.1 % for a lean gas at low pressure.

## Limitations

- These are empirical correlations for straight-through labyrinths with teeth on the stator. Expect a
  spread of tens of percent between methods; use the spread as a measure of model uncertainty.
- Rotation, eccentricity, stepped or interlocking geometry and honeycomb lands are not modelled.
- Properties are evaluated at the inlet only.

## Related

- [Dry gas seal condensation and conditioning](dry_gas_seal_conditioning)
- [Seal support-system monitoring](dry_gas_seal_monitoring)
- [Compressors](compressors)
