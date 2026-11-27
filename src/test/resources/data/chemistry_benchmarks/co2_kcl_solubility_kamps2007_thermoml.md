# CO2 + KCl + water total-pressure data from Kamps et al. (2007)

## Primary source and retained artifact

- A. P.-S. Kamps, E. Meyer, R. Rumpf and G. Maurer, “Solubility of CO2 in
  Aqueous Solutions of KCl and in Aqueous Solutions of K2CO3,” *Journal of
  Chemical & Engineering Data* **52** (2007), 817–832.
- DOI: <https://doi.org/10.1021/je060430q>
- Machine-readable source: NIST ThermoML,
  <https://trc.nist.gov/ThermoML/10.1021/je060430q.xml>, retrieved 2026-11-27.
- Retained source checksum: SHA-256
  `9206e6280e36be4a6ee3a15405e53d568df6ee7eaf4cd57cba875e05c8578446`
  (MD5 `b7d7e174b96a9089dee21a0bba2b619b`).
- The NIST ThermoML archive is public United States Government data distributed
  under the [NIST Standard Reference Data terms](https://www.nist.gov/srd/public-law).
  This fixture transcribes numeric Dataset 1 values and descriptive metadata;
  it does not reproduce publisher prose, tables or figures.

## Exact semantics

ThermoML Dataset 1 contains 102 liquid-phase records for CO2 (registry record
1), water (record 2) and KCl (record 4). Variables are temperature, KCl
molality and CO2 molality. The property is **total equilibrium vapor pressure**
in kPa, measured by a closed-cell static method. It is not CO2 partial
pressure. Each record carries a compiler-evaluated 95% expanded pressure
uncertainty of 100 kPa.

The CSV therefore retains source total pressure without subtracting water
vapor pressure. The NeqSim assessment imposes that total pressure, represents
the reported KCl and CO2 compositions on a one-kilogram final-aqueous-water
basis, and lets the flash calculate water vapor, gas composition and CO2
fugacity. Source zero-CO2 rows are retained as vapor-pressure controls but are
not used in logarithmic solubility residuals.

The KCl sample is identified in ThermoML as commercial material with 99.5 mass
percent purity and vacuum-heat drying. The retained KCl series spans about
1.942–4.050 mol/kg water, 313.1–433.1 K and 0.3078–9.395 MPa total pressure.

## Frozen transfer selection

No Kamps value is used to fit or select the He–Morse residual-law coefficients.
Before evaluating NeqSim, the transfer subset was frozen by this deterministic
source-only rule:

1. assign each record to the nearest nominal temperature in 313.15, 333.15,
   353.15, 373.15, 393.15, 413.15 and 433.15 K;
2. split each temperature into the source's low-KCl (`m < 3`) and high-KCl
   (`m >= 3`) families;
3. discard zero-CO2 records for log-residual evaluation; and
4. select the lower median positive CO2 molality in each of the fourteen
   temperature/salt blocks.

The `transfer_holdout` column records the resulting fourteen states. This
balanced subset limits slow dense-flash cost while preserving every source
temperature and both independently prepared salt families. All 102 source
rows remain in the fixture so that later bounded fitting or uncertainty work
can declare new calibration and holdout partitions without silently replacing
the evidence base.

## Qualification boundary

This source is an independent single-salt aqueous holdout. It is not hydrate
dissociation data and cannot qualify hydrate equilibrium by itself. Candidate
corrections are applied only as post-processed log-solubility diagnostics; no
database parameter, residual-Helmholtz term, solver tolerance or empirical
temperature offset is changed. Table A.24 mixed brine and Dholabhai/Burgass
hydrate families remain frozen downstream holdouts.
