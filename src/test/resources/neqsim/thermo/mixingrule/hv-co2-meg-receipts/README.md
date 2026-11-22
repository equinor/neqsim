# Quarantined CO2-in-MEG source receipts

This fixture makes the evidence in campaign receipts
[6534892620](https://github.com/equinor/neqsim/issues/4576#issuecomment-6534892620),
[6537188660](https://github.com/equinor/neqsim/issues/4576#issuecomment-6537188660) and
[6574928348](https://github.com/equinor/neqsim/issues/4576#issuecomment-6574928348)
executable offline. It supplies no interaction parameters, measured overall feed,
reverse inhibitor partition, calibration target or experimental accuracy threshold.
Production SRK/PR-HV, alpha functions, selected pairs and CLASSICAL kij are unchanged.

`source.xml` preserves the complete publisher-permitted NIST ThermoML record for
Wise and Chapoy, DOI10.1021/acs.jced.7b00537. The official XML MD5 anchors the
GitHub transport copy; `provenance.json` records both identities and source routes.
The primary accepted-manuscript images were inspected at PDF indices8/10–13;
no manuscript PDF or artwork is redistributed here.

`manuscript.csv` retains the earlier transcription with ordinal, extracted-line
and zero-based PDF-page locators. All80 numeric observations agree with the
archive, including five repeated pairs. They are not deduplicated or averaged.
The printed manuscript labels rows53–56 VLE; the archive labels them LLE.
Both versions remain visible. Final-publisher reconciliation is unresolved;
neither version's phase label is a solver acceptance condition.

Author standard uncertainties and compiler-expanded95% absolute mole-fraction
uncertainties are different quantities. The XML retains every compiler value;
the audit never doubles a standard uncertainty or converts it into an accuracy
tolerance. Source modeling uses CPA-SRK72; its coefficient is not an HV tuple.

From the repository root, run the standard-library-only audit:

```
python3 devtools/audit_huron_vidal_meg_receipts.py
python3 -m unittest discover -s devtools -p test_audit_huron_vidal_meg_receipts.py -v
```

The audit checks hashes, DOI, compounds, measured-property direction, units,
uncertainty semantics, all observations and multiplicities, source locators and
the exact disagreements. Mutation tests exercise these gates, including with
Python `-O`; checks do not depend on `assert` statements.

This is source-evidence qualification only. A later physical batch still needs
complete EOS-specific directed HV and paired pure-component inputs, original
source/range reconciliation, final measurement interpretation and reverse MEG
partition. It must independently assert native ThermodynamicModelTest identities
and numerical checks, finite differences, gas/oil/water closure and matched
CLASSICAL/HV performance. This fixture does not qualify hydrate dissociation,
ice/brine behavior or extrapolation, and does not resolve campaign4576 or4573.
