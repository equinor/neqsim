# Public gas-density consistency fixture

Source: Schlumberger DBR Technology Center, report **2012-10037190**, May 14,
2012, Encana Corporation, Duvernay well 13-05-043-06W5; 51 printed pages.
Publicly accessible primary laboratory report hosted in the Whitson training data:

https://manual.pvt.whitson.com/files/Duvernay-PVT-Data/SLB-Gas-Condensate-SEP-REC-Sample%20%282.08-2.09%29.pdf

Source PDF SHA-256:
`c72c3cb3be80a1ffcc535504f68b9ac6311cc813aafc98ffefa4a3ddc3095725`.
Access and text/rendered-page recheck: 2026-10-31. This directory contains a limited
factual numerical extract for reproducible consistency checks, an independent
arithmetic script and its output. It does not redistribute the PDF, figures or
report prose. Public access does not imply a license to redistribute the full report.
No proprietary data, asset production history or fitted model is included.

`duvernay-table18.csv` preserves the nine pressure/gas-Z/vapor-MW/vapor-density
tuples from **printed page 37, table 18**, at 106 °C. Units remain psia (absolute),
dimensionless, g/mol and g/cc. Converted units: Pa = psia × 6894.757293168;
kg/mol = g/mol ÷ 1000; kg/m³ = g/cc × 1000; K = °C + 273.15.
The printed final pressure is **1300 psia**, verified in extracted text and the
rendered page. It is not 1250 psia, as an earlier #4740 ledger incorrectly stated.

The protocol is recombined-fluid CVD: lower cell pressure, equilibrate/sample gas,
then withdraw further gas to restore the initial reservoir volume (printed p50).
The vapor columns refer to each equilibrium gas state; they are not bulk two-phase
properties. This small check does not replay the CVD experiment or qualify depletion
composition/volume conservation. The saturation row is retained despite its failure.

Bounds frozen before comparison: ±0.5 psia, ±0.5 K, ±0.0005 Z, ±0.005 g/mol,
±0.0005 g/cc, from printed precision. These are not instrument uncertainties.
Printed p50 specifies pressure accuracy ±0.1% of gauge full scale; the actual
selected gauge range is not established by this extract. Maximum system operating
pressure is not proof of the gauge's calibrated full scale. Temperature indication
accuracy is stated as 0.1 °C. The rounding audit must not be described as an
experimental-confidence test.

Other relevant source context, not mixed into these vapor tuples:

- Printed p3 summary saturation Z=0.992 versus detailed CCE p34 Z=0.922. No source
  correction or preferred-value selection is assumed.
- Table 18 saturation density 0.339 g/cc is incompatible with its p/T/Z/MW under
  the stated printing bounds (implied 0.333688435 g/cc).
- Printed final row at 1300 psia implies 0.075278163 g/cc, compatible with 0.075.
  Eight of nine rows overlap; the complete set fails the gate.
- The mini separator uses an equilibrium liquid feed at 800 kPa gauge and 10 °C;
  its GOR is not a bulk-reservoir-fluid GOR. The report's standard basis is
  15.56 °C/101.33 kPa, which differs from the existing 15 °C/1.01325 bara handoff.

`audit_reference.py` uses 50-digit Decimal arithmetic directly in source units,
independent of Java/NeqSim. Run it to reproduce `reference-results.json`; it never
fits hidden parameters or repairs source observations. The Java comparison tolerance
is 1e-9 kg/m³ for nominal/endpoints and exact equality for compatibility decisions.
The script intentionally retains the failing row. Numerical consistency is not
evidence of laboratory measurement accuracy or a measured field-chain validation.

Guide: [laboratory gas-density consistency](../../../../../../docs/pvtsimulation/laboratory_gas_density_consistency.md).
