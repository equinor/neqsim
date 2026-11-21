---
name: neqsim-dexpi-conformance-validation
version: "1.0.0"
description: "Validate generated or received DEXPI XML: which specification version and profile it is (2.0.0 vs 2.0.1, Plant vs Process vs Proteus 1.x), the NeqSim official-schema and pinned-model check, and an independent pyDEXPI load plus rule-engine check. USE WHEN: asked whether a DEXPI export is 2.0.1 compatible, valid, importable or round-trippable, or before handing a P&ID/PFD exchange to another tool."
last_verified: "2026-11-21"
---

# DEXPI conformance validation

One command, three independent layers. A green layer never stands in for another.

```
neqsim dexpi-check <file.xml> --expect-version 2.0.1 [--pydexpi <exe>] [--output report.json]
```

(source checkout: `python devtools/dexpi_conformance_check.py ...`; without the CLI on PATH:
`<python> -m neqsim_cli dexpi-check ...`). Exit code 0 only for `PASS` or `PASS_NEQSIM_ONLY`.

| Layer | What it proves | Tool |
|---|---|---|
| `identity` | Version and profile from the Core/Plant/Process `Import` URIs, never the file name or a changed namespace | pure Python |
| `neqsimAssessment` | Official XSD (SHA-256 verified), exact model imports, pinned type/property/multiplicity/relationship declarations (2.0.1), reference integrity, NeqSim supported semantic profile | `Dexpi20ConformanceAssessment.assess(path, profile, is201)` through the NeqSim JVM |
| `pyDexpi` | Independent full load plus 37 rules: segment connectivity, composition tree, port/stream direction, mandatory attributes, fractions | pyDEXPI 2.1+ (`pydexpi validate --json`), the community reference implementation of 2.0.1 |

## Verdicts

- `PASS`: 2.0.x identity as expected, NeqSim `CONFORMANT`, pyDEXPI clean.
- `PASS_NEQSIM_ONLY`: pyDEXPI was not available. State this; it is not a full pass.
- `UNVERIFIED`: the NeqSim layer did not run (`--no-java`) or does not apply.
- `FAIL`: wrong version/kind (Proteus 1.x is not DEXPI 2.0.x), `NONCONFORMANT`, or pyDEXPI loader errors / mandatory rule violations.

`--fail-on recommended|optional` tightens pyDEXPI. Findings carry rule id, object id and XML line.

## Setup (never install at run time)

pyDEXPI needs Python 3.12+ and is separate from the NeqSim environment. Install once:
`uv tool install "pydexpi>=2.1"`; the wrapper finds `pydexpi` on PATH, `PYDEXPI`, or `--pydexpi`.
If it is missing the layer reports `NOT_RUN` with this command. Java is located by
`neqsim_dev_setup` (see `neqsim-setup`).

## Measured behaviour (2026-11-21, 17-unit HP/LP train with recompression)

- Plant 2.0.1 (`Dexpi201XmlWriter`): before the fix, NeqSim reported `CONFORMANT` while pyDEXPI found 4 mandatory
  `pydexpi_segment_connectivity` violations (in-line valve segments declared no source/target item). Fixed in
  `Dexpi20XmlWriter` (valve segments now name their item and its inlet/outlet nodes); the same file is now `PASS`
  with 0 pyDEXPI issues. The NeqSim semantic profile did not flag the defect: always run pyDEXPI too.
- Process 2.0.0 (`Dexpi20ProcessModelWriter.write`): NeqSim `CONFORMANT`; pyDEXPI reports 8
  `model_references_in_composition_tree` issues and 4 loader errors; this path is 2.0.0 and lacks
  the mandatory `Method`.
- Process 2.0.1 rejects models with `Recycle`/`Calculator`/`Adjuster` by design.

## Producing a file that can pass

- Plant/P&ID 2.0.1: `Dexpi201XmlWriter.writeAndAssess(process, file, metadata)` or
  `DexpiEngineeringExporter.exportDexpi201(project, file, metadata)` with real
  `Dexpi20PlantExportMetadata`; plain `Dexpi20XmlWriter.write` is 2.0.0.
- Process/PFD 2.0.1: `Dexpi201ProcessModelWriter.writeAndAssess`.
- Round trip to a simulation is Proteus only (`DexpiXmlWriter` + `DexpiSimulationBuilder`);
  native 2.x import into a simulation is not supported and throws `DexpiXmlReaderException`.

## Reporting rules

1. Quote the `identity` version and the SHA-256 from the NeqSim report so the verdict is bound to the file bytes.
2. Report each layer separately with its counts; never write "DEXPI 2.0.1 compatible" on a `PASS_NEQSIM_ONLY`.
3. Fix exporter defects in Java (with a test); never edit the XML or change a URI to claim migration.
4. Not a DEXPI EV certificate and not a named CAE import/round trip: that needs the receiving tool
   (`neqsim-pid-quality-review`, `devtools/validate_dexpi_interoperability.py` for DEXPIViewer/CAE evidence).

## Common mistakes

- Trusting the NeqSim layer alone: it checks pinned declarations and the supported profile, not every
  DEXPI information-model rule.
- Validating a 2.0.0 file and calling it 2.0.1; legacy 2.0.0 assessment does not accept 2.0.1 imports.
- Treating pyDEXPI loader *warnings* (for example cyclic port references) as failures; only loader
  `error`/`critical` entries and rule violations at or above `--fail-on` fail.
- Running the check on a stale export: regenerate, then validate the exact file that is delivered.
