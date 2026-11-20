---
title: "Mercury limits in reservoir fluids (cinnabar and liquid mercury)"
description: "Upper limits of elemental mercury in a reservoir fluid from equilibrium with mercury sulfide and liquid mercury, using EOS fugacities, with the saturation index of a measured content."
keywords: "mercury, Hg, cinnabar, metacinnabar, HgS, mineral equilibrium, reservoir fluid, natural gas, pyrite, siderite, UMR-PRU-MC, saturation index, trace sulfide, mercury removal"
---

`neqsim.thermo.util.mineral` gives the highest content of elemental mercury a reservoir fluid can
hold before a condensed mercury phase forms. Two phases are considered: mercury sulfide (cinnabar,
or the black polymorph metacinnabar) and liquid mercury.

The results are limits, not predictions. Pure cinnabar overpredicts the mercury measured in
reservoir fluids by up to three orders of magnitude (Bryndzia et al., 2022), so the mercury content
of a reservoir is set by supply from the source rock and not by this equilibrium. The limits are
used as a worst case and to judge a measured value.

## Method

Mercury sulfide is reduced by the methane of the fluid:

$$
4\,\mathrm{HgS} + \mathrm{CH_4} + 2\,\mathrm{H_2O(l)} \rightleftharpoons
4\,\mathrm{Hg} + 4\,\mathrm{H_2S} + \mathrm{CO_2}
$$

$$
4 \ln f_{\mathrm{Hg}} = \ln K(T,P) + \ln f_{\mathrm{CH_4}} + 2 \ln a_{\mathrm{H_2O}}
+ 4 \ln a_{\mathrm{HgS}} - 4 \ln f_{\mathrm{H_2S}} - \ln f_{\mathrm{CO_2}}
$$

The H2S fugacity is either that of the fluid, or that of the pyrite and Fe-carbonate buffer of
[`ReservoirH2SEstimator`](solid_gas_reaction_equilibrium.md). With the buffer the reaction becomes

$$
8\,\mathrm{HgS} + 4\,\mathrm{FeCO_3} + \mathrm{CH_4} \rightleftharpoons
8\,\mathrm{Hg} + 4\,\mathrm{FeS_2} + 5\,\mathrm{CO_2} + 2\,\mathrm{H_2O(l)}
$$

and no H2S measurement is needed. Liquid mercury limits the fugacity at the vapour pressure with
the pressure correction of the liquid, $\mathrm{Hg(l)} \rightleftharpoons \mathrm{Hg(g)}$.

The fugacities of CH4, CO2 and H2S come from the equation of state of the fluid. Mercury is treated
as a dilute solute: its fugacity coefficient $\varphi_p$ in each fluid phase $p$ is taken at trace
content, and the mercury content of the whole fluid at a limit is

$$
z_{\mathrm{Hg}} = f_{\mathrm{Hg}} \sum_p \frac{\beta_p}{\varphi_p P}
$$

with $\beta_p$ the phase fraction. The error is of the order of the mercury mole fraction.

The HgS activity $a_{\mathrm{HgS}}$ is 1 for pure cinnabar. A lower value represents mercury held
as a trace component of pyrite or other sulfides; the limit is proportional to it. A value of 0.003
matched a global data set in Bryndzia et al. (2022).

## Data

| Species | Source | Data |
| --- | --- | --- |
| Mercury, ideal gas | CODATA (Cox et al., 1989) | $\Delta_f H_{298}$ 61.38 kJ/mol, $S_{298}$ 174.971 J/(mol K), $C_p$ = 5R/2 |
| Mercury, liquid | CODATA; NIST-JANAF heat capacity | $S_{298}$ 75.90 J/(mol K), molar volume 14.82 cm3/mol |
| Cinnabar | Robie and Hemingway (1995); $C_p$ from Mills (1974) | $\Delta_f H_{298}$ -54.3 ± 2.1 kJ/mol, $S_{298}$ 82.5 ± 2.1 J/(mol K), 28.42 cm3/mol |
| Metacinnabar | Robie and Hemingway (1995); $C_p$ as cinnabar | $\Delta_f H_{298}$ -46.7 ± 1.5 kJ/mol, $S_{298}$ 96.2 ± 4.2 J/(mol K), 30.17 cm3/mol |

The liquid and gas data reproduce the vapour pressure correlation of Huber et al. (2006) within
1.1 % from 0 to 250 °C. The enthalpy and entropy uncertainties of the minerals give about 0.5 log10
units (one standard deviation) in the sulfide limit, reported by
`getSulfideLimitLog10Uncertainty()`.

`MineralMercuryBuffers.all()` returns the reactions for both polymorphs. Reaction enthalpy per mole
mercury at 100 °C: 61 kJ for liquid mercury, 94 kJ for cinnabar with hydrogen, 100 kJ for cinnabar
with the pyrite-siderite buffer.

## Example

```java
SystemInterface gas = new SystemSrkEos(273.15 + 110.0, 350.0);
gas.addComponent("nitrogen", 0.5);
gas.addComponent("CO2", 2.0);
gas.addComponent("methane", 78.0);
gas.addComponent("ethane", 8.0);
gas.addComponent("propane", 4.0);
gas.addComponent("n-butane", 2.5);
gas.addComponent("n-hexane", 5.0);
gas.setMixingRule("classic");
// UMR-PRU-MC has mercury parameters; the fluid is given 1 microgram mercury per Sm3
SystemInterface fluid = MercuryFluidConverter.toUmrPruMc(gas, 1.0, "ug/Sm3");

ReservoirMercuryEstimator estimator = new ReservoirMercuryEstimator();
ReservoirMercuryEstimator.Estimate pure = estimator.estimate(fluid);
double cinnabarLimit = pure.getSulfideLimit("ug/Sm3"); // about 38 000
double liquidLimit = pure.getLiquidLimit("ug/Sm3"); // about 58 000
double bufferedH2S = pure.getH2SPpm(); // about 1.9 ppm
double saturationIndex = pure.getSulfideSaturationIndex(); // about -4.6 for the 1 ug/Sm3 given

estimator.setMercurySulfideActivity(0.003);
double traceLimit = estimator.estimate(fluid).getSulfideLimit("ug/Sm3"); // about 113
```

For the same gas along a typical pressure-temperature path:

| T (°C) | P (bara) | Cinnabar limit (µg/Sm³) | Trace-sulfide limit, a = 0.003 (µg/Sm³) | Liquid mercury limit (µg/Sm³) | Buffered H2S (ppm) |
| --- | --- | --- | --- | --- | --- |
| 50 | 150 | 320 | 1.0 | 3 500 | 0.1 |
| 80 | 280 | 4 500 | 13 | 17 000 | 0.5 |
| 110 | 350 | 38 000 | 113 | 58 000 | 1.9 |
| 140 | 520 | 215 000 | 640 | 159 000 | 5.5 |
| 170 | 650 | 950 000 | 2 900 | 371 000 | 14 |

Above about 130 °C the liquid mercury limit is the lower of the two: mercury sulfide is then not
stable relative to liquid mercury and `getControllingPhase()` returns `"liquid mercury"`.

Other calls:

- `setHydrogenSulfideBasis(HydrogenSulfideBasis.FLUID)` uses the H2S of the fluid; the limit is
  inversely proportional to its fugacity.
- `setSulfidePhase(SulfidePhase.METACINNABAR)` gives a limit about twice that of cinnabar at 120 °C.
- `sulfideSaturationTemperature(fluid, moleFraction)` returns the temperature below which the
  sulfide can hold the given mercury content back.
- `Estimate.getFluid()` returns the fluid with mercury at the lower limit; flash it to separator
  conditions with `MercuryPhaseEquilibrium` to express the limit in separator gas.
- `getApparentMercurySulfideActivity()` is the HgS activity at which a measured content would be at
  equilibrium.

## Verification

`MineralMercuryBuffersTest` and `ReservoirMercuryEstimatorTest` check the vapour pressure against
Huber et al. (2006), the element balance of every reaction, the consistency of the buffered
reaction with its parts, the scaling with HgS activity and H2S fugacity, the inversion by the
saturation temperature and the agreement of the liquid limit with `MercuryPhaseEquilibrium` (within
15 %; that class uses the pure-mercury fugacity of UMR-PRU-MC).

Compare the limits with measured mercury before using them for a field. The saturation index and
the apparent HgS activity of `Estimate` express a measured content relative to the sulfide limit; a
set of measurements that rises with temperature more slowly than the limit (about 0.02 log10 units
per °C) is supply limited, not buffered by the sulfide.

## Limitations

- Upper limits only. The mercury content of a reservoir is supply limited when the limit is high.
- The HgS activity of 0.003 is an empirical envelope, not a measured activity.
- Elemental mercury; organic and ionic mercury and particulate HgS are not modelled.
- The buffered H2S needs pyrite and an Fe-carbonate in the rock; `estimate` warns when they are not
  both stable.
- The heat capacity of metacinnabar is taken equal to that of cinnabar.
- Use an equation of state with mercury parameters (UMR-PRU-MC through `MercuryFluidConverter`).
  With SRK and default parameters the fugacity coefficient of mercury differs by about 40 %.

## References

- Bryndzia, L.T., Burgess, J.M., Bourdet, J. (2022). Predicting the solubility of mercury in
  hydrocarbons. SPE Journal, SPE-212271-PA.
- Cox, J.D., Wagman, D.D., Medvedev, V.A. (1989). CODATA key values for thermodynamics. Hemisphere.
- Huber, M.L., Laesecke, A., Friend, D.G. (2006). Correlation for the vapor pressure of mercury.
  Industrial and Engineering Chemistry Research 45, 7351-7361.
- Mills, K.C. (1974). Thermodynamic data for inorganic sulphides, selenides and tellurides.
  Butterworths.
- Robie, R.A., Hemingway, B.S. (1995). Thermodynamic properties of minerals and related substances
  at 298.15 K and 1 bar. USGS Bulletin 2131.
- Sandengen, K. (2024). Mercury on the Norwegian Continental Shelf (NCS). What value to use for
  design? Geoenergy Science and Engineering 242, 213263.
