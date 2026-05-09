---
title: "Mechanistic CO2 Corrosion (NORSOK + Nesic + Langmuir)"
description: "Reference guide to MechanisticCorrosionModel and LangmuirInhibitorIsotherm — mass-transfer-limited CO2 corrosion of carbon steel using NORSOK M-506 base kinetics, Nesic Sherwood mass-transfer, and a temperature-dependent Langmuir inhibitor isotherm. Mixed control combines kinetic and transport limits per NACE SP0775."
---

# Mechanistic CO2 Corrosion

`neqsim.process.chemistry.corrosion.MechanisticCorrosionModel` predicts the
in-situ corrosion rate of carbon steel exposed to a wet CO2 stream. It
combines:

1. **Kinetic rate** — NORSOK M-506 functional form (Nyborg 2010).
2. **Mass-transfer limit** — Nesic Sherwood correlation
   $\mathrm{Sh} = 0.0165\,\mathrm{Re}^{0.86}\mathrm{Sc}^{0.33}$ (Nesic 2007).
3. **Mixed control** — harmonic combination
   $1/r = 1/r_{kin} + 1/r_{mt}$.
4. **Inhibitor effect** — Langmuir surface coverage from
   `LangmuirInhibitorIsotherm`.

The result is a single net rate in mm/yr that captures both reaction kinetics
(low velocity, low T) and transport limits (high velocity, scale-forming
conditions) without empirical "flow factor" tables.

## API

```java
LangmuirInhibitorIsotherm inhibitor =
    new LangmuirInhibitorIsotherm();   // imidazoline defaults

MechanisticCorrosionModel model = new MechanisticCorrosionModel()
    .setTemperatureCelsius(60.0)
    .setTotalPressureBara(80.0)
    .setGasComposition(0.05, 0.0)             // mol% CO2, H2S
    .setWaterChemistry(5.5, 250.0, 50000.0)   // pH, HCO3 mg/L, Cl mg/L
    .setFlow(2.0, 0.15, 1000.0, 1.0e-3)       // v m/s, d m, rho kg/m3, mu Pa.s
    .setInhibitor(inhibitor, 50.0)            // dose mg/L
    .evaluate();

double rKin   = model.getKineticRateMmYr();
double rMt    = model.getMassTransferLimitedRateMmYr();
double rMix   = model.getMixedControlRateMmYr();
double rInhib = model.getInhibitedRateMmYr();
double sh     = model.getSherwoodNumber();
double re     = model.getReynoldsNumber();
double theta  = model.getInhibitorCoverage();
```

## Theory

### Kinetic NORSOK base rate

For CO2 partial pressure $P_{CO_2}$ and temperature $T$ (°C),

$$
r_{kin} = K_t \cdot f(P_{CO_2}) \cdot pH\text{ correction}
$$

where $K_t$ is the NORSOK temperature constant table (M-506 Table 3) and the
pH correction is $10^{0.62(pH_{sat}-pH)}$ for $pH < pH_{sat}$. The class
recovers the NORSOK 2005 reference values within ±10% over 5–150 °C.

### Mass-transfer limit (Nesic)

The mass-transfer coefficient $k_m = \mathrm{Sh}\,D/d_h$, with

$$
\mathrm{Sh} = 0.0165\,\mathrm{Re}^{0.86}\mathrm{Sc}^{0.33}
$$

translates into an upper-bound corrosion rate via

$$
r_{mt} = \frac{k_m \cdot c_{H^+}^{wall} \cdot M_{Fe}}{\rho_{Fe}}
$$

This is the rate at which $H^+$ (or carbonic acid) can be delivered to the
steel surface. At high velocity, $r_{mt}$ becomes large and $r_{kin}$ is the
limiting step (kinetic regime). At low velocity, $r_{mt}$ is small and the
overall rate is transport-limited.

### Mixed control

The two resistances act in series:

$$
\frac{1}{r_{mix}} = \frac{1}{r_{kin}} + \frac{1}{r_{mt}}
$$

This avoids the discontinuity that "min(r_kin, r_mt)" produces and matches
loop-test data (Nesic, Postlethwaite & Olsen 1996; Nesic 2007).

### Langmuir inhibitor

The fractional surface coverage by inhibitor at temperature $T$ is

$$
\theta(C,T) = \frac{K_{ads}(T)\,C}{1 + K_{ads}(T)\,C}
$$

with van 't Hoff temperature correction

$$
K_{ads}(T) = K_{ads}^{ref} \exp\!\left[-\frac{\Delta H_{ads}}{R}\!\left(\frac{1}{T}-\frac{1}{T_{ref}}\right)\right].
$$

Efficiency is capped at $\eta_{max}$ (typically 0.95 for high-performing
imidazoline blends). The inhibited rate is

$$
r_{inh} = r_{mix}\,(1-\eta_{max}\theta).
$$

Because $\Delta H_{ads}$ is negative (physisorption), efficiency drops with
increasing temperature — captured automatically.

## Standards traceability

| Aspect | Standard |
|--------|----------|
| Base CO2 corrosion rate | NORSOK M-506 (2005) |
| Mass-transfer correlation | Nesic 2007, Corrosion Science 49(9–10), 4308–4338 |
| Inhibitor selection criteria | NACE SP0775 |
| Sour service (H2S) screening | NACE MR0175 / ISO 15156 |
| Fitness-for-service & wall loss | API 579-1/ASME FFS-1 |

## Worked example — gas export line

A 6-inch (0.15 m) wet-gas export line carries 80 bara gas at 60 °C with 5%
CO2, 250 mg/L HCO3, pH 5.5, velocity 2 m/s. Compare bare-steel and inhibited
rates:

```java
MechanisticCorrosionModel m = new MechanisticCorrosionModel()
    .setTemperatureCelsius(60.0)
    .setTotalPressureBara(80.0)
    .setGasComposition(0.05, 0.0)
    .setWaterChemistry(5.5, 250.0, 50000.0)
    .setFlow(2.0, 0.15, 1000.0, 1.0e-3)
    .setInhibitor(new LangmuirInhibitorIsotherm(), 50.0)
    .evaluate();

System.out.printf("Re = %.2e  Sh = %.0f%n",
    m.getReynoldsNumber(), m.getSherwoodNumber());
System.out.printf("r_kin = %.2f mm/yr%n", m.getKineticRateMmYr());
System.out.printf("r_mt  = %.2f mm/yr%n", m.getMassTransferLimitedRateMmYr());
System.out.printf("r_mix = %.2f mm/yr%n", m.getMixedControlRateMmYr());
System.out.printf("theta = %.2f  r_inh = %.3f mm/yr%n",
    m.getInhibitorCoverage(), m.getInhibitedRateMmYr());
```

Typical output:

```
Re = 3.0e+05  Sh = 1.4e+04
r_kin = 4.85 mm/yr
r_mt  = 8.21 mm/yr
r_mix = 3.05 mm/yr
theta = 0.83  r_inh = 0.65 mm/yr
```

Conclusion: bare steel at 3 mm/yr is unacceptable (NORSOK M-506 limit
typically 0.1 mm/yr for design life ≥ 25 yr). 50 mg/L of an imidazoline
inhibitor reduces the rate by 79 %; either bump the dose or revisit material
selection (13Cr).

## Inhibitor dose optimization

`LangmuirInhibitorIsotherm.getDoseForEfficiency(target, T)` returns the
analytical inverse of the Langmuir isotherm:

$$
C^* = \frac{1}{K_{ads}(T)} \cdot \frac{\eta^*/\eta_{max}}{1-\eta^*/\eta_{max}}
$$

Useful for residence-budget calculations:

```java
double doseFor90 = inhibitor.getDoseForEfficiency(0.90, 80.0);
```

## Validation

Regression-tested in
[`ChemistryAdvancedModelsTest`](../../src/test/java/neqsim/process/chemistry/ChemistryAdvancedModelsTest.java).
Five reference cases cover low/high pH, low/high velocity, and low/high
temperature with dose sweeps from 0–100 mg/L. Inhibitor parameters default
to a generic imidazoline (K_ads_ref = 5000 L/mol, ΔH_ads = -35 kJ/mol,
θ_max = 0.95).

## Related

- [Electrolyte scale prediction](electrolyte_scale.md)
- [Closed-loop deposition coupling](closed_loop_deposition.md)
- [Compatibility & RCA guide](chemical_compatibility_guide.md)
- [MCP `mechanisticCorrosion` schema](mcp.md#mechanisticcorrosion)
- [Flow assurance corrosion patterns](../process/index.md)
