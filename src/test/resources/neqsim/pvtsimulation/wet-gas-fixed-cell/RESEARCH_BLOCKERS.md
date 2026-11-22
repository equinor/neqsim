# Independent-data and research boundaries (2026-11-05)

This batch is original synthetic classic-SRK numerical evidence. It does not
admit the training reports or any measurements into calibration/holdouts.
The constants in independent_reference.py are declared shared physical/model
inputs; no parameter was fitted to NeqSim outputs or reservoir volume. The
code and generated numerical outputs are repository deliverables under the
repository license, not a reproduction of a copyrighted paper.

The latest #4740 run43 ledger preserves the complete numerical acquisition,
source hashes and encoding audit for May et al. (2015), DOI
[10.1021/acs.jced.5b00610](https://doi.org/10.1021/acs.jced.5b00610), from
[NIST ThermoML](https://trc.nist.gov/ThermoML/10.1021/acs.jced.5b00610.html).
Its frozen source-admission gate remains failed: 20 occurrences contain 19
unique state keys, three reported pressure records exceed the advertised
9 MPa range, and the coverage factor/variable uncertainties/covariance and
full experimental pressure basis require reconciliation. No row is dropped,
expanded uncertainty converted to sigma, or static VLE relabeled as
CCE/CVD/DLE/separator or field production. NS1 basis discrepancies also remain
quarantined. Neither source supplies the complete laboratory-to-field chain.

Tran et al. (2025), DOI
[10.1016/j.jgsce.2025.205551](https://doi.org/10.1016/j.jgsce.2025.205551),
was rechecked through the public publisher record
[PII S2949908925000159](https://www.sciencedirect.com/science/article/pii/S2949908925000159).
The DOI reader failed; direct publisher retrieval returned HTTP 403. Search
returned the publisher preview with organizational-access/purchase controls,
not the complete article or benchmark. No payment, login, access bypass or
author contact was attempted. Missing material is the full coupled RCVD/FPM
and density-based equations/definitions, numerical fluid/EOS and relative
permeability inputs, initialization/grid/boundary specifications, variable-BHP
pressure/rate/time schedules, and complete independent numerical output for
the cases. Full-paper/table and benchmark recovery are prerequisites; this
batch implements none of that method from a preview. Public preview support
for investigating RCVD is not numerical acceptance.

Remaining #4740 acceptance includes whole-experiment laboratory calibration
and holdouts, independently measured laboratory-to-field pressure/GOR/
composition validation, wet upper-dew/oil-containing/critical paths, finite
aquifer and Bayesian sensitivity/uncertainty qualification, and subsequent
multi-tank matching. In-place quantities are not recoverable reserves.
