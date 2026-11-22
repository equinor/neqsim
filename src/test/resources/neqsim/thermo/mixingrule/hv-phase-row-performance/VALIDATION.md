# Qualification checkpoint before the full matrix

2026-11-18, base `bd8a56f694ad095f87f0f990eb4772700a126ccd`.
This is a draft checkpoint. Full-matrix performance acceptance and exact-head
hosted CI, including actual Java 8, remain pending.

- Native Temurin 17.0.21+9: 147 HV-focused/adjacent tests passed, none failed,
  errored or skipped. Selector:
  `-Dtest=HuronVidal*Test,ComponentGENRTLmodifiedHV*Test,PhaseGENRTLmodifiedHVTest`.
  The suite asserts the existing ThermodynamicModelTest checks, independent
  finite differences, complete flashes, clone/serialization/history behavior,
  parameter selection and retained experimental assessments. Tolerances and
  model/profile selections are unchanged.
- JaCoCo 0.8.16 generated fresh XML for 6,657 classes. The modified component has
  302/302 lines and 76/76 branches covered; the activity phase has 61/69 lines and
  21/26 branches covered. A temporary validation POM changed only the coverage
  output format to XML and scoped Javadoc to the two changed production files;
  production POMs and coverage exclusions were unchanged.
- Pinned analysis Temurin 17.0.21+9: fresh clean compile, PMD 7.17.0,
  7,233-class canonical archive and SpotBugs 4.10.5 completed. Reviewed master
  baseline blob `b4a357ea7e76f791889b2e6c2c864e660346681b`: zero new selected source,
  PMD or SpotBugs finding groups (existing selected counts 1,027/8/11,006).
  No baseline refresh, detector suppression or heap retry was used.
- Direct current Spotless apply/check passed, followed by regenerated engineering
  coverage inventory and documentation-search audit (855 Markdown/one HTML).
  Changed-file Checkstyle passed on Microsoft Java 21.0.13 with zero violations;
  the initial Java 17 tooling attempt failed because Checkstyle 14.4 needs Java 21.
  Javadoc passed with existing warnings. Pre-commit executable/module is absent;
  direct repository gates were run.
- The two changed production classes and new test compile with `--release 8`
  (class version 52). This does not verify the actual Java 8 dependency graph;
  the repository's Java 8 Ubuntu/Windows jobs remain required.
- Independent source/contract review found no blocking issue in B3 or these docs.

`pilot-and-held.zip` retains every attempted pilot process, including adverse
observations, plus the held constructor repair/probes and gate logs. `index.json`
records its bytes/hash. The component-local trial is rejected, not pooled with
B3. The 24-JVM B3 pilot had zero same-case endpoint difference, maximum balance
4.17e-15, normalization 2.23e-16 and log-fugacity residual 6.81e-13. Its large
PR/Twu init1 candidate/baseline ratios were 0.742/0.866; complete-flash ratios were
0.708/0.942. Allocation rose from 0.024 to 504.024 B/init1 and from 1,517,944.96
to 1,553,896.96 B/flash. These bounded preliminary observations do not replace
the full 216-JVM matrix or establish a universal speedup.

The retained Kruger measurements reproduce the existing assessment: vapor MEG
AARD 88.472981% SRK / 171.180445% PR over ten published measurements, vapor water
28.989540% / 20.389645% and liquid methane 59.149340% / 50.686063% over sixteen.
The six missing high-temperature MEG observations stay missing; uncertainty
semantics and raw measured feeds are preserved. These are errors, not acceptance
tolerances. This equivalent runtime change is not a physical-model improvement.
