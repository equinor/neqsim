---
title: "Hydrocarbon dielectric inputs"
description: "Primary data, temperature ranges and derivative validation for n-butane and n-hexane liquid reference permittivities used by electrolyte models."
---

# Hydrocarbon dielectric inputs

The temperature-only component function is
`epsilon(T) = d0 + d1/T + d2*T + d3*T^2 + d4*T^3`, with T in kelvin.
The n-butane and n-hexane coefficients replace a duplicated malformed row that
gave a relative permittivity of approximately 826675 at 298.15 K.

These are liquid reference contributions to the existing electrolyte solvent
mixing rule. They are not pressure-dependent dielectric correlations for a pure
gas, compressed liquid, or hydrocarbon-rich mixture. Their use in an aqueous
mixture retains that model assumption; restoring the inputs does not establish
quantitative gas-salt or mixed-solvent accuracy. No mixture density is inserted
into a pure-fluid dielectric equation.

| Component | Source and reference state | Qualified temperature range |
|---|---|---|
| n-butane | Haynes measurements of saturated liquid, NBS Monograph 169 (1982), Table 13, p. 91 | 135–303.15 K |
| n-hexane | NBS Circular 514 (1951), p. 21; low-pressure liquid | 263.15–323.15 K |

For n-butane, the five existing coefficient terms were fitted by ordinary linear
least squares to the even-numbered rows (zero-based index, ascending temperature)
of 37 saturated-liquid measurements. The remaining 18 rows were excluded from
fitting. Maximum absolute residuals are 0.000142 for fitting and 0.000193 for
validation. No electrolyte, solubility, or flash result was used in this fit.
The coefficient vector is
`[2.3274411639642683, -1.3837961585135172, -0.002494897128685319, 4.225465544584647e-6, -7.19842856393903e-9]`.
The monograph's full pressure-density formulation is its equation (12); this
temperature-only representation must not be identified with that full equation.

For n-hexane, the published linear temperature relation is retained intact:
`epsilon(T) = 1.890 - 0.00155*(T - 293.15)`.
The table explicitly limits the relation to −10 to 50 °C. Independent measurements
by Mopsik (1967), Table 1, give 1.9178 at 273.15 K and 1.8799 at 298.15 K,
both within 0.005 of this older correlation. The 0.005 test tolerance reflects
the inter-source difference; it is not the uncertainty of Mopsik's measurements.

Both standard and extended database loading obtain the coefficients from
canonical `COMP.csv`; the existing extended loader replaces shared neutral
properties after reading `COMP_EXT.csv`. Regression tests exercise both loading
modes and check independent values and finite differences of both temperature
derivatives.
Extrapolation beyond the stated ranges is unqualified, including temperatures
above the n-butane data range in warm process simulations. The polynomial must
not be used as a general high-temperature pure-fluid property model.

## Sources

- W. M. Haynes and R. D. Goodwin, *Thermophysical Properties of Normal Butane
  from 135 to 700 K at Pressures to 70 MPa*, NBS Monograph 169 (1982),
  [doi:10.6028/NBS.MONO.169](https://doi.org/10.6028/NBS.MONO.169).
  The title describes the thermodynamic-property model; dielectric measurements
  have the narrower range stated in section 2.10 and Table 13.
- A. A. Maryott and E. R. Smith, *Table of Dielectric Constants of Pure Liquids*,
  NBS Circular 514 (1951), section 2.5 and p. 21,
  [doi:10.6028/NBS.CIRC.514](https://doi.org/10.6028/NBS.CIRC.514).
- F. I. Mopsik, *Dielectric Constant of N-Hexane as a Function of Temperature,
  Pressure, and Density*, Journal of Research of the National Bureau of Standards
  71A (1967), 287–292,
  [original paper](https://nvlpubs.nist.gov/nistpubs/jres/71A/jresv71An4p287_A1b.pdf).
