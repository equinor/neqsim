# Frost bidirectional methane assessment

The staged binary flashes use a synthetic equimolar feed at each measured
temperature and absolute pressure. The measured quantities are phase
compositions. Neither source provides a measured overall binary feed or phase
amounts for this test. Only the binary pair is selected for HV. Public profiles
and databases are unaffected.

Frost's 2014 DTU thesis, Table 2.6 (printed p. 49), supplies 23 methane/water
pairs. Table 2.9 (p. 55) supplies 11 liquid methane measurements and eight vapor
MEG values. Three unavailable vapor observations are represented by empty cells.
The tables were checked against rendered PDF pages. The 298 K MEG gas peaks
scatter and were sometimes absent. Pointwise author uncertainty for Table 2.9
was not established. The 22 pressure-matched journal compiler rows from
DOI 10.1021/je400684k are a separate version of the same methane/water
experiment. The unmatched vapor value at 323.56 K and liquid methane
mole fraction 0.00211 has no journal compiler pressure and is excluded from that
version. The compiler expanded 95% uncertainties apply to pressure and gas water.
The small disagreement at 54.8 bar between rounded thesis and journal values
is preserved. No pointwise liquid uncertainty is inferred.

The independent solver uses two central mole-difference steps (1e-10 and
5e-11 mol), mechanically stable cubic roots and both log-fugacity equations.
Run `python3 devtools/audit_huron_vidal_frost.py SOURCE_DIRECTORY OUTPUT.json`
from the repository root. Save the [thesis PDF][thesis] as `frost.pdf` and the
[NIST JSON][thermoml] as `frost.json` in SOURCE_DIRECTORY. The program checks
both input hashes before extracting the primary table rows. Source images are
available from DTU. The numerical Java test uses only the retained CSV, writes
the calculated results to `target/hv-frost-native.csv` and asserts a 5e-8
relative endpoint parity limit with a 1e-13 mole-fraction floor. It checks
closure on each two-phase result and applies the native identities and
numerical checker at their established 1e-8 and 1e-6 limits.

The 2016 PR water-first tuple is (-1500 K, 11, 4050 K, -5.2, 0.16),
with the two directions expressed as tau = D/T + DT. The pure inputs are the
declared library values (water 647.30 K, 220.89 bara, acentric factor 0.344,
methane 190.56 K, 45.99 bara, acentric factor 0.0115) and ordinary PR alpha.
The SRK MEG case reuses the full [Folas/Derawi fixture][meg] and its Soave control.
Original author pure inputs are unresolved. The executable sensitivity matches
IAPWS pure-water pressure locally at each temperature with mixture coefficients
frozen. The local alpha values do not define a new profile.

| Source version / configuration | Liquid methane AARD | Gas polar AARD |
|---|---:|---:|
| Thesis MEG, SRK-HV Mathias–Copeman | 1.991% (11) | 33.668% (8) |
| Thesis MEG, SRK-HV Soave control | 1.679% (11) | 37.684% (8) |
| Thesis water, PR-HV | 97.884% (23) | 22.546% (23) |
| Journal compiler water, PR-HV | 97.878% (22) | 19.915% (22) |
| Journal compiler water, local pure-pressure sensitivity | 97.821% (22) | 16.036% (22) |

The PR liquid methane discrepancy persists across compiler pressure intervals
at 323.56 K. Gas water improves on average when pure vapor pressure is matched,
but the compiler band count decreases from seven to six out of 22. These
comparisons document model error on the assessed coordinates. They establish
neither parameter acceptance nor oil partition, hydrate, ice, brine or
extrapolation accuracy.

The existing `HuronVidalMegMethanePerformanceBenchmark` accepts an optional
fifth argument `water_journal` or `meg_mc` for these data coordinates.
Run one rule/scenario/profile per JVM. The two rules use the same pure inputs,
synthetic feed, temperature and pressure. Inspect phase labels, compositions,
amounts and equilibrium residuals with the timings because changing the rule
can change the equilibrium and solver work.

[thesis]: https://backend.orbit.dtu.dk/ws/portalfiles/portal/107595496/Michael_Frost_978_87_93054_63_9_pdf.pdf
[thermoml]: https://trc.nist.gov/ThermoML/10.1021/je400684k.json
[meg]: ../hv-meg-methane/README.md

## Retained native and timing evidence

The native CSV contains 67 results. Maximum component balance, normalization
and log-fugacity residuals were 7.61e-15, 1.54e-16 and 9.17e-11. Both modeled
directions agreed with the independent calculations to 9.23e-11 relative.
The phase amounts are calculated for the synthetic feed.

The timing CSV contains 360 measured rounds in 36 sequential JVMs on Linux
x86-64, Temurin 21.0.13+9, with 256 MiB initial and 768 MiB maximum heap.
Per profile/scenario, rule order was 2/4, 4/2, 2/4 across three paired forks.
Warmups were 100,000 operations per initialization worker and 3,000 per flash
worker. Each measured round ran 10,000 initializations or 100 full flashes.
Flash calls alternate between the listed base temperature and base + 0.2 K.
PR uses 323.56 K and 84.8 bara. SRK uses 323 K and 85 bara. Base
74ba4a29f7c2acc40232fe6c75ed0c59d8fd8e0e and candidate use identical production
code. The measured change between rules is model cost on a shared host.

| Profile | init1 ratio range | init3 ratio range | Full-flash ratio range |
|---|---:|---:|---:|
| PR water/methane | 1.89–2.85 | 3.08–5.46 | 1.50–1.92 |
| SRK MEG/methane | 0.84–6.19 | 3.73–6.45 | 1.67–2.13 |

Ratios are rule4/rule2 per-fork medians. The scattered SRK init1 values do
not resolve a reliable initialization cost ratio. Every retained endpoint
was GAS/AQUEOUS (the SRK liquid is MEG-rich). Max balance, normalization and
log-fugacity errors were 1.12e-16, 2.23e-16 and 2.01e-11. The PR flash example
at 323.76 K and 84.8 bara had gas water 0.001513 under rule2 and 0.001803
under rule4, and liquid methane 2.89e-7 and 2.15e-5 respectively. Flash timing
therefore includes differences in the equilibrium and solver work.

## Dilute methane and measured-state fugacity

The 2016 PR-HV source chose its parameters to reproduce **water in the vapor**.
Its fit emphasized 323.15–423.15 K and 25–700 bara. A fit with that objective
does not establish the dilute liquid methane chemical potential. The authors'
exact pure-component inputs have yet to be established.

At the water saturation pressure given by [IAPWS G7-04][henry], the staged
model's limiting methane fugacity divided by its liquid mole fraction is
43–57 times the IAPWS Henry constant across the four Frost journal
temperatures. The factor rises to 94 at 423.15 K. The calculation checks
the IAPWS Table 6 values and successively lowers methane mole fraction from
1e-5 to 1e-7. At the 22 measured Frost phase compositions the model
log(f_gas/f_liquid) ranges from -4.05 to -3.59 for methane and -0.048 to
0.428 for water. This comparison evaluates the measured compositions directly
on the two cubic roots, without a flash or measured overall feed. It identifies
a large liquid-direction mismatch within the declared profile.

An additional 18-point VLE dataset from DOI 10.1021/je9007958 measures dissolved
methane at 303.2–323.2 K and 1.115–6.383 bara. These pressures are below the
original fitting emphasis. The staged profile gives 98.48% liquid methane AARD
and falls outside all 18 compiler expanded uncertainty bands. The maximum
calculated equilibrium log-fugacity residual is 3.74e-14. The dataset has no
gas-water observations, so the predicted vapor composition is retained only
as a calculated value. The IAPWS comparison is a correlation check at solvent
saturation, with no implied uncertainty interval for the Frost data.

Run `python3 devtools/audit_huron_vidal_frost_dilute.py PATH_TO_NIST_JSON`
using the [10.1021/je9007958 JSON][low-p]. The executable validates the source
hash and dataset, reports both measured-state residuals and the dilute
benchmark, and reproduces [the archived output](dilute-assessment.txt). Treat
these checks as evidence about the declared inputs and fit objective. They
do not identify a replacement tuple.

[henry]: https://iapws.org/technical-guidance/release/HenGuide
[low-p]: https://trc.nist.gov/ThermoML/10.1021/je9007958.json
