---
title: Labyrinth Seal Leakage
description: "Gas leakage through straight-through labyrinth seals with the Stodola, Egli, Vermes, Kearton and tooth-by-tooth Neumann methods, using any NeqSim fluid."
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
$r = p_n/p_0$. The tooth-by-tooth `NEUMANN` method is described in its own section below.

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

Each closed-form result is capped at the critical flow of the clearance:

$$
\dot m_{crit} = A\sqrt{1000\,\rho_0 p_0\,\kappa\left(\frac{2}{\kappa+1}\right)^{(\kappa+1)/(\kappa-1)}}
$$

`isChoked()` reports when the cap is active. For Vermes, the flow is also treated as choked when $n + \ln r \le 0$.

The Kearton equation peaks at a pressure ratio $r^*(n)$ that depends only on the number of teeth: 0.58 for one tooth
($1/\sqrt{3}$), 0.43 for two, 0.26 for five and 0.12 for twenty. Below $r^*$ the equation would predict less leakage
as the backpressure falls. Physically, the last tooth is choked there, so lowering the backpressure further cannot
change the flow. NeqSim therefore holds Kearton at its peak value below $r^*$ and reports `isChoked()`. For Kearton,
the critical flow is the lower of the clearance limit above and this peak.

## Neumann (tooth by tooth)

`NEUMANN` is the modified Neumann method for teeth on the stator. It solves the pressure after each tooth instead of
using one closed-form expression. For a trial flow $\dot m$ (pressures in Pa):

$$
p_{i+1}^2 = p_i^2 - \left(\frac{\dot m}{C_{f,i}\,\mu_i A}\right)^2 \frac{p_i}{\rho_i}
$$

$$
C_{f,i} = \frac{\pi}{\pi + 2 - 5\beta_i + 2\beta_i^2}, \qquad
\beta_i = \max\left[\left(\frac{p_i}{p_{i+1}}\right)^{(\kappa_i-1)/\kappa_i} - 1,\ 0\right]
$$

$$
\mu_0 = 1, \qquad \mu_{i>0} = \sqrt{\frac{n}{n(1-\alpha)+\alpha}}, \qquad \alpha = 1 - \frac{1}{(1 + 16.6\,c/t)^2}
$$

- $C_{f,i}$ depends on $p_{i+1}$. Each tooth is therefore solved for its pressure ratio with a bracketed Brent search
  between the critical ratio and 1. The residual rises steadily over that interval, so the root is unique. A plain
  fixed-point iteration can oscillate between the choke pressure and a wrong value for high $\kappa$ (for example
  argon, $\kappa \approx 5/3$).
- After each tooth, the gas is flashed at constant inlet enthalpy (PH flash). This gives $T_{i+1}$, $\rho_{i+1}$ and
  $\kappa_{i+1}$ for the next tooth.
- The leakage is the flow for which the pressure after the last tooth equals the outlet pressure. It is found with a
  Brent root search, seeded with the Kearton flow.
- A tooth chokes when $p_{i+1}$ would fall below $p_i\,(2/(\kappa_i+1))^{\kappa_i/(\kappa_i-1)}$. If the outlet
  pressure cannot be reached without a tooth choking, the leakage is the largest flow at which no tooth chokes, and
  `isChoked()` is true.

The tooth profile is available after the calculation:

- `getToothPressures(unit)`: $n+1$ pressures, from the inlet to after the last tooth.
- `getToothTemperatures(unit)`: the matching temperatures.
- `getToothDensities()`: the matching gas densities in kg/m3.

These arrays are empty for the other methods.

Choose the fluid with run time in mind. Each Neumann calculation needs roughly $8n$ to $10n$ PH flashes. With a cubic
EOS a calculation takes well under a second. For GERG-2008 fluids with a single-phase inlet, the calculator first tries
NeqSim's dedicated `PHflashGERG2008`, and a five-tooth seal takes about 45 s with a 14-component gas, including a TP flash that confirms each expanded state is stable. That flash
only handles single-phase gas. When an expanded state turns two-phase or misses the inlet enthalpy, the general
`PHflash` is used instead. Every state is checked against the inlet enthalpy. If liquid is present at the inlet, the
gas phase alone is expanded.

For Neumann, `getCriticalMassFlowRate(unit)` is the largest flow at which no tooth chokes. When the leakage is not
choked, this value is found by a separate search on the first request (including from `toJson()`), which costs more
flashes. If that search fails, the critical flow is `NaN` and the reason is added to `getValidityViolations()`.

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

calc.setMethod(LabyrinthFlowMethod.NEUMANN);
calc.calculate();
double neumannKgPerHour = calc.getMassFlowRate("kg/hr");
double[] toothPressuresBarg = calc.getToothPressures("barg");   // inlet, after tooth 1, ..., after tooth 5
double[] toothTemperaturesC = calc.getToothTemperatures("C");
```

Other outputs:

- `getActualVolumeFlowRate(unit)`: volume flow at inlet conditions.
- `getClearanceVelocity(unit)`: mean velocity in the clearance, based on inlet density.
- `getCriticalMassFlowRate(unit)`: the critical-flow cap; for Neumann, the largest non-choking flow.
- `hasLiquidAtInlet()`: true when the inlet flash finds liquid; gas-phase properties are then used.
- `toJson()`: summary of inputs and results.

Standard volumes are ideal-gas volumes at 15 °C and 1.01325 bara by default. Change them with
`setStandardConditions(...)`. That method requires a finite, positive absolute temperature and pressure. It checks both
before storing either, so a rejected call leaves the previous conditions unchanged.

## Validation

Twenty-two reference cases from a GERG-2008 VBA leakage workbook cover:

- pressure drop up to venting to atmosphere
- 2 to 20 teeth
- clearance, pitch and temperature
- pressures up to 50 barg
- pure methane and pure nitrogen

With a `SystemGERG2008Eos` fluid, the four closed-form methods agree within $2\times10^{-5}$ relative for every
single-phase case. The remaining offset comes from the component molar masses used by the NeqSim GERG-2008 phase. SRK
and PR fluids agree within 0.1 % for a lean gas at low pressure.

There is one deliberate exception. Two cases are below the Kearton peak ratio: two teeth vented to atmosphere, and
three teeth at a pressure ratio of 0.03. The workbook keeps the falling Kearton equation there. NeqSim holds the choked
peak and is 3.1 % and 9.3 % higher, respectively.

The Neumann method agrees within 0.021 % for every single-phase case with an SRK fluid, and within 0.005 % for the
worked case with GERG-2008. The workbook finds the temperature after each tooth with a 0.05 % secant tolerance, lets
the reference enthalpy drift, and stops its flow iteration after 11 passes. NeqSim uses exact PH flashes and a
converged root.

The workbook returns errors in two situations where NeqSim gives a result:

- choked cases, where NeqSim reports the choked flow;
- Vermes when $n + \ln r \le 0$.

When water condenses at the inlet, NeqSim uses only the gas phase, while the workbook treats the fluid as single-phase
gas. Neumann leakage is then 2 to 5 % lower.

## Limitations

- These are empirical correlations for straight-through labyrinths with teeth on the stator. Expect a
  spread of tens of percent between methods; use the spread as a measure of model uncertainty.
- Rotation, eccentricity, stepped or interlocking geometry and honeycomb lands are not modelled.
- Properties for the closed-form methods are evaluated at the inlet only. Neumann follows the isenthalpic expansion
  tooth by tooth.

## Related

- [Dry gas seal condensation and conditioning](dry_gas_seal_conditioning)
- [Seal support-system monitoring](dry_gas_seal_monitoring)
- [Compressors](compressors)
