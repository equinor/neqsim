---
title: "CPA liquid-water heat capacity: caloric alpha option"
description: "Opt-in water attraction function fitted to IAPWS-95 heat capacity and phase equilibrium, with reproducible calibration and validation limits."
keywords: "CPA, water, heat capacity, caloric, IAPWS-95, alpha, Mathias-Copeman, enthalpy, Palma"
---

The standard `SystemSrkCPAstatoil` water parameterization underpredicts liquid
isobaric heat capacity by about 8–17% between 5 and 60 °C near atmospheric
pressure. Its good density predictions do not establish caloric accuracy.
Issue [#3762](https://github.com/equinor/neqsim/issues/3762) is a model-calibration
limitation: numerical differentiation confirms the legacy `getCp()` agrees
with the legacy enthalpy derivative. The evidence does **not** establish a
missing association derivative.

## Enable the calibrated water alpha

```java
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
fluid.setUseCaloricWaterAlpha(true);
fluid.addComponent("water", 1.0);
fluid.setMixingRule(10);
new ThermodynamicOperations(fluid).TPflash();
fluid.initProperties();
double cp = fluid.getPhase(0).getCp("J/kgK");
double density = fluid.getPhase(0).getDensity("kg/m3");
```

The same setter is accessible through the Python/JPype interface. It may be
called before or after adding water. Reflash and initialize properties after
changing it. `setUseCaloricWaterAlpha(false)` restores the legacy choice;
`isUsingCaloricWaterAlpha()` reports the selection. The default remains legacy
CPA. Following any change to the component list, set the mixing rule again.

The new choice changes the **EOS attraction function**, so fugacity, enthalpy,
entropy, and heat capacities use its analytic derivatives consistently. It does
not patch the reported heat capacity. Other components retain their existing
attraction functions, including TEG. Water ideal-gas heat capacity, association
energy/volume, covolume, and existing physical-property volume correction remain
unchanged. Mixtures containing water can nevertheless change because the water
attraction participates in the mixing rule.

## Equations and provenance

The reduced-temperature polynomial is

$$a(T)=a_0\alpha(T),\qquad \alpha(T)=\left[1+\sum_{j=1}^{5}c_j\left(1-\sqrt{T/T_c}\right)^j\right]^2.$$

| Coefficient | Value |
|---|---:|
| $c_1$ | 0.6670190973128074 |
| $c_2$ | -0.25656681951681287 |
| $c_3$ | 1.3687767086793399 |
| $c_4$ | -3.1891110889699097 |
| $c_5$ | 5.218580308820144 |

These are **new NeqSim calibration coefficients**, inspired by the flexible-alpha
approach of [Palma, Queimada and Coutinho (2017)](https://doi.org/10.1021/acs.iecr.7b03522).
They are not that paper's parameter set. NeqSim retains its original water
$a_0=0.12277$ Pa m⁶/mol², $b=1.4515\times10^{-5}$ m³/mol,
association energy 16655 J/mol, association volume 0.0692, four-site 4C scheme,
and $T_c=647.3$ K. The five-coefficient polynomial implementation is reused from
`AttractiveTermMatCop5PRUMR`; only its algebra is reused, and the surrounding EOS
remains SRK-CPA. Attractive-term selector 23 is used internally.

Both $a'(T)$ and $a''(T)$ enter the existing residual Helmholtz derivatives.
There is no hard temperature switch and no pressure- or phase-dependent
heat-capacity multiplier. Thermodynamic checks include

$$C_p=\left(\frac{\partial H}{\partial T}\right)_{P,n}
=T\left(\frac{\partial S}{\partial T}\right)_{P,n}.$$

For mixtures these identities refer to a fixed-composition single phase;
an equilibrium two-phase temperature derivative also includes redistribution
between phases and need not equal the sum of phase heat capacities.

## Calibration and independent validation

Training uses **12 temperatures at 5 °C intervals from 5 to 60 °C, at 1 bar**.
The objective includes relative liquid heat-capacity and untranslated density
errors, plus liquid/vapour fugacity equality at the IAPWS saturation pressure:

$$J=\sum_k\left[\left(\frac{C_{p,k}/C_{p,k}^{\rm ref}-1}{0.02}\right)^2
+\left(\frac{\rho_k/\rho_k^{\rm ref}-1}{0.01}\right)^2
+\left(\frac{\ln(f_k^L/f_k^V)}{0.02}\right)^2\right].$$

These scales are fitting weights, not experimental uncertainties. The density
term uses the unshifted EOS volume; production density acceptance separately
checks NeqSim's existing volume-corrected `getDensity("kg/m3")` path. The fit does
not make untranslated density equivalent to the physical-property density.

Production results at **1.01325 bara** (compressed/subcooled liquid, not saturated
liquid at this pressure) are:

| Temperature, °C | Legacy Cp | Caloric-alpha Cp | IAPWS-95 Cp | Cp error | Density error |
|---:|---:|---:|---:|---:|---:|
| 5 | 3509.13 | 4203.82 | 4205.04 | -0.029% | +0.112% |
| 10 | 3546.42 | 4195.86 | 4195.16 | +0.017% | -0.007% |
| 25 | 3651.67 | 4181.82 | 4181.31 | +0.012% | -0.192% |
| 40 | 3746.86 | 4178.83 | 4179.41 | -0.014% | -0.191% |
| 60 | 3858.79 | 4186.15 | 4184.95 | +0.029% | +0.013% |

Cp is in J/(kg K). These temperatures are represented in the training data;
the small pressure difference does **not** make this an independent temperature
validation. The recomputed IAPWS-95 values differ slightly from the rounded
values originally reported in the issue.

The checked-in CSV contains 49 stable liquid states and four steam states,
including temperature holdouts at 7.5, 18, 32, 80, 100, 125, and 150 °C, and
pressure holdouts at 10, 50, and 100 bara. The 55 °C points are correctly labelled
as training temperatures. Regression gates require liquid Cp error below 2%,
and physical density error below 0.5% only between 5 and 60 °C. Separate
saturation-pressure tests cover 5–150 °C with a 2% acceptance band.

The independent pure-CPA evaluator also checks 37 states with temperatures
excluded from fitting, reaching 150 °C and 100 bar; its maximum Cp error is
0.841%. Tests of the production implementation check Cp/enthalpy/entropy
consistency, alpha derivatives, PH/PS closure, cloning, serialization, model
selection, pure TEG, single-liquid water/MEG consistency, and preservation of
steam Cp relative to the legacy model. Mixture consistency is not an
experimental mixture-accuracy validation.

### Reproduce the calibration

`devtools/generate_water_caloric_alpha_reference.py` contains an independent,
analytic pure-water 4C CPA evaluator and reads the existing water parameters
from `COMP.csv`. Optional research dependencies are NumPy, SciPy and `iapws`;
they are not NeqSim runtime dependencies.

```bash
python devtools/generate_water_caloric_alpha_reference.py --refit --output-dir /tmp/cpa-water-reference
./mvnw -Dtest=CPAWaterCaloricTest,SystemSrkCPAstatoilTest,PHFlashCPATest test
```

The checked-in `src/test/resources/thermo/water_caloric_alpha_fit.json` records
coefficients, fixed parameters, training data, independent holdouts, objective,
optimizer configuration and package versions. The adjacent
`water_caloric_alpha_iapws95_reference.csv` supplies the Java reference tests.
Reference values are calculations using `iapws.IAPWS95` 1.5.5, following
[IAPWS R6-95(2018)](https://iapws.org/technical-guidance/release/IAPWS-95).
The optional GPLv3 reference package is used as a numerical oracle; its source
is not copied or incorporated into the Java implementation.

## Literature assessment and limits

- [Palma et al. (2017)](https://path.web.ua.pt/publications/acs.iecr.7b03522.pdf)
  use a five-parameter alpha and a broader pure-water parameter refit, with
  critical-point constraints and volume translation. They report saturated-liquid
  Cp average absolute deviation of 0.84%, compared with 7.43% for their original
  CPA comparison over 260–450 K. Their complete parameter replacement has a
  different density tradeoff; transplanting only their alpha coefficients is
  not valid. This implementation instead retains NeqSim's existing association
  and covolume parameters.
- [Zhu et al. (2020)](https://doi.org/10.1021/acs.jced.0c00649) examine heat
  capacities across EOS models and support explicitly considering caloric
  properties rather than inferring second-derivative accuracy from VLE alone.
- [Novak, Liang and Kontogeorgis (2024)](https://doi.org/10.1063/5.0186752)
  introduce interconverting water structures into PC-SAFT association. This is
  a candidate mechanism for water anomalies, but their atmospheric liquid Cp
  comparison reports 18.9% average absolute deviation: it is not evidence that
  a two-state term automatically solves the quantitative Cp bias here.

**Recommended scope:** opt-in pure liquid-water caloric calculations within the
checked 5–150 °C and 1–100 bara envelope, above the saturation pressure. Density
qualification is narrower: 5–60 °C. This is not IAPWS accuracy throughout that
rectangle, and the listed checks do not establish a mathematical error bound.
At 5 °C and 300 bar the independent evaluator already gives about +2.5% Cp error.
Near-critical, supercritical, electrolyte, hydrate and mixture-property accuracy
remain unqualified. Steam Cp is preserved, not newly validated to 2% against
IAPWS; some pre-existing steam errors remain several percent.

A future water-structure term should be added to the **residual Helmholtz
energy**, with consistent temperature, volume and composition derivatives,
and fitted jointly to Cp/Cv, density/expansivity, sound speed, VLE and mixture
data. A density-independent water correction applied to every phase would
also change ideal-gas/steam caloric properties. A liquid-only Cp override would
break the derivative identities used in energy and entropy flashes. The
present results do not require either approach for the issue's operating range.
