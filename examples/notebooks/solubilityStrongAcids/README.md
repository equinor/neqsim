# H2O–HNO3–H2SO4 vapour-pressure model (Taleb, Ponche & Mirabel, 1996)

This folder contains the NeqSim implementation and validation of the Van Laar
activity-coefficient model for the equilibrium partial vapour pressures of water,
nitric acid, and sulfuric acid over binary and ternary H2O / HNO3 / H2SO4 mixtures
at low temperature (190–298 K).

**Reference:** D. Taleb, J.-L. Ponche, P. Mirabel, *Vapor pressures in the ternary
system water–nitric acid–sulfuric acid at low temperature*, Journal of Geophysical
Research, **101**(D20), 25967–25977, 1996. (PDF: `H2SO4+HNO3-H2O.pdf`)

## Model implementation

The model is implemented as a pure-static Java class:

`src/main/java/neqsim/thermo/util/empiric/NitricSulfuricAcidVaporPressure.java`

Indices used throughout: **1 = H2O, 2 = HNO3, 3 = H2SO4**. Binary subsystems are
I = H2O–HNO3, II = H2O–H2SO4, III = HNO3–H2SO4.

Public methods (all pressures returned in **pascal**):

| Method                                                         | Description                |
| -------------------------------------------------------------- | -------------------------- |
| `pureVaporPressureWater(T)`                                    | Pure H2O vapour pressure   |
| `pureVaporPressureNitricAcid(T)`                               | Pure HNO3 vapour pressure  |
| `pureVaporPressureSulfuricAcid(T)`                             | Pure H2SO4 vapour pressure |
| `activityCoefficientWater/NitricAcid/SulfuricAcid(x1,x2,x3,T)` | Van Laar γ (Eq 10a–c)      |
| `partialPressureWater/NitricAcid/SulfuricAcid(x1,x2,x3,T)`     | γ·x·P0                     |
| `moleFractionsFromMassFractions(wH2O,wHNO3,wH2SO4)`            | wt % → mole fractions      |

Key correlations (T in K, log = log10):

$$\log_{10} P^0_{\mathrm{H_2O}}\,[\mathrm{mbar}] = 8.42926609 - \frac{1827.17843}{T} - \frac{71208.271}{T^2}$$

$$\log_{10} P^0_{\mathrm{HNO_3}}\,[\mathrm{torr}] = 7.61628 - \frac{1486.238}{T - 43}$$

$$\ln P^0_{\mathrm{H_2SO_4}}\,[\mathrm{atm}] = -\frac{10156}{T} + 16.259$$

The ternary activity coefficients use the Van Laar form of Eqs (10a)–(10c) with the
binary parameters of Eqs (6), (7) and (9). The System III (HNO3–H2SO4) parameters
($A_{III,2} = -250.52$, $A_{III,3} = -100.21$, $B_{III,2} = 1/B_{III,3} = 0.4$) are
fitted to Vandoni's 273 K data and assumed temperature-independent
($T\log\gamma = \mathrm{const}$).

## Composition basis for Figures 6 and 7

Each panel of Figures 6 and 7 holds the **total-mixture** HNO3 weight percent fixed
(the panel label) and sweeps the H2SO4 weight percent (the x-axis); water is the
balance:

```
w_HNO3  = panel label   (fixed)
w_H2SO4 = x-axis value
w_H2O   = 100 - w_HNO3 - w_H2SO4
```

Each curve therefore terminates at `w_H2SO4 = 100 - w_HNO3` (water content reaches
zero), which is why high-HNO3 panels span a progressively shorter x-range — e.g.
panel i (79.84 % HNO3) stops at ≈ 20 wt % H2SO4, exactly as in the paper.

## Validation scripts

Run any script directly with Python (the helper boots the JVM against the compiled
classes in `target/classes`, so run `./mvnw compile` once first):

```bash
cd examples/notebooks/solubilityStrongAcids
python3 validate_fig2_binary_nitric.py     # Fig 2: binary H2O-HNO3, P vs 1/T
python3 validate_fig3_water_activity.py    # Fig 3: binary H2O-H2SO4 water activity @298 K
python3 validate_fig4_5_binary_sulfuric.py # Fig 4 & 5: binary H2O-H2SO4 water pressure
python3 validate_fig6_water_ternary.py     # Fig 6: ternary P_H2O @273 K (3x3 panels)
python3 validate_fig7_nitric_ternary.py    # Fig 7: ternary P_HNO3 @273 K (3x3 panels)
```

Each script writes a `validate_figN_*.png` that reproduces the corresponding paper
figure's solid (model) curves.

`_neqsim_model.py` is the shared helper that starts the JVM and exposes thin Python
wrappers around the Java model.

### Headline result — Figure 7 peak P_HNO3 (model vs paper, 273 K)

| Panel | HNO3 wt % | model peak (torr) | paper peak (torr) | peak at wt % H2SO4 |
| ----- | --------- | ----------------- | ----------------- | ------------------ |
| a     | 4.88      | 1.13              | ~1.1              | 86                 |
| c     | 21.90     | 4.59              | ~4.6              | 70                 |
| f     | 49.95     | 8.95              | ~9.0              | 44                 |
| g     | 59.92     | 10.16             | ~11               | 35                 |
| i     | 79.84     | 12.19             | ~12               | 18                 |

The model reproduces the existence, magnitude, and location of the maximum in the
nitric-acid partial pressure as sulfuric acid is added — the central result of the
paper.

## Unit test

`src/test/java/neqsim/thermo/util/empiric/NitricSulfuricAcidVaporPressureTest.java`
covers the pure-component anchors, the binary-limit reductions of the ternary
equations, the mole-fraction conversion, and the Figure 7 panel-c ternary maximum.

```bash
./mvnw test -Dtest=NitricSulfuricAcidVaporPressureTest
```
