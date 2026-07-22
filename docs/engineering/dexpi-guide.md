---
title: "DEXPI Engineering Guide"
description: "Practical guide to selecting, generating, validating, qualifying, and governing DEXPI exchanges from NeqSim process and engineering models."
keywords: "DEXPI, DEXPI 2.0, Proteus, P&ID, PFD, BFD, pyDEXPI, engineering graph, conformance, round trip"
---

# DEXPI Engineering Guide

This guide helps you choose the correct NeqSim DEXPI workflow. DEXPI is an engineering information-exchange format:
it can preserve process, plant, connectivity, instrumentation, and diagram information, but it is not itself a process
simulation, an approved P&ID, or a construction release.

## Choose the right DEXPI path

| Need | NeqSim entry point | Detailed documentation |
| --- | --- | --- |
| Import an existing Proteus-compatible P&ID and build a process scaffold | `DexpiXmlReader`, `DexpiSimulationBuilder` | [DEXPI import, export, and visualization](../integration/dexpi-reader) |
| Export a quick Proteus-compatible P&ID | `DexpiXmlWriter.write(...)` | [DEXPI import, export, and visualization](../integration/dexpi-reader#exporting-to-dexpi-xml) |
| Render through pyDEXPI | `DexpiXmlWriter.writeForPyDexpi(...)` or `DexpiDiagramBridge.exportForPyDexpi(...)` | [pyDEXPI-friendly export](../integration/dexpi-reader#pydexpi-friendly-export-namespace-omitted) |
| Export a native DEXPI 2.0 P&ID Plant model | `Dexpi20XmlWriter.writeAndAssess(...)` | [DEXPI 2.0 conformance](../integration/dexpi-20-conformance) |
| Export a native DEXPI 2.0 PFD/BFD Process model | `Dexpi20ProcessModelWriter.writeAndAssess(...)` | [DEXPI 2.0 conformance](../integration/dexpi-20-conformance#process-model-export) |
| Generate a governed engineering package with DEXPI, registers, cases, and evidence | `EngineeringDeliverableCompiler` | [Standards-based DEXPI engineering generation](../integration/dexpi-engineering-generation) |
| Generate a review-required P&ID proposal | `PidDesignSynthesizer`, `PidEngineeringPackageExporter` | [Governed P&ID design synthesis](../pid-design-synthesis) |

## Understand the exchange profiles

### Proteus-compatible exchange

`DexpiXmlWriter` and `DexpiXmlReader` support the established Proteus 4.1.1-oriented path used by existing tools and
examples. `writeForPyDexpi` emits the same content without the default XML namespace for consumers that perform
unqualified tag lookup.

Use this path for compatibility, visualization, and existing Proteus exchanges. Do not present it as native DEXPI 2.0
by changing the file header or namespace.

### Native DEXPI 2.0 Plant model

`Dexpi20XmlWriter` exports the official Core plus Plant information models for P&ID content: plant items, piping,
nozzles, instruments, safeguards, boundaries, and diagram representations.

Use it when the exchange purpose is plant and instrumentation design rather than process-step semantics.

### Native DEXPI 2.0 Process model

`Dexpi20ProcessModelWriter` exports the official Core plus Process information models for PFD/BFD content: sources,
sinks, process steps, material ports, streams, and physical state quantities.

Use it when the exchange purpose is process topology and state data. A Process exchange is not a less-detailed Plant
file; it is a different official information model.

## Recommended engineering workflow

1. **Define the exchange purpose.** State whether the recipient needs a P&ID Plant model, PFD/BFD Process model,
   Proteus compatibility, pyDEXPI rendering, or a complete governed package.
2. **Freeze the source revision.** Retain the process-model revision, thermodynamic basis, design-case basis, and
   package identity used for the export.
3. **Validate the source model.** Require stable tags, explicit physical connections, converged relevant cases, finite
   quantities, and controlled instrument/safeguard identities.
4. **Select the writer explicitly.** Do not infer the exchange profile from the filename.
5. **Generate sidecar evidence.** Store conformance, validation, graph, case, and package-manifest artifacts with the
   XML rather than relying on the drawing alone.
6. **Run internal validation.** Check schema, supported semantic profile, unique identities, references, units, and
   structural export/reimport consistency.
7. **Qualify the receiving tool.** Import into the named CAE product and version, export again, compare semantics and
   identities, and obtain accountable review of differences.
8. **Control revisions.** Use the canonical graph and model-change impact analysis to identify stale exchanges,
   calculations, validations, and approvals.

## Native DEXPI 2.0 export

Export and assess the Plant and Process models separately:

```java
Dexpi20ConformanceAssessment.Report plantReport =
    Dexpi20XmlWriter.writeAndAssess(process, new File("plant.dexpi.xml"));

Dexpi20ConformanceAssessment.Report processReport =
    Dexpi20ProcessModelWriter.writeAndAssess(process, new File("process.dexpi.xml"));

if (!plantReport.isSchemaAndProfileConformant()
    || !processReport.isSchemaAndProfileConformant()) {
  throw new IllegalStateException("DEXPI 2.0 schema or supported-profile validation failed");
}
```

Keep each conformance JSON beside the exact assessed XML. The report pins the official schema fingerprint, imported
model versions, supported semantic profile, file digest, object counts, references, and findings.

## Validation and qualification ladder

| Level | Question answered | NeqSim status |
| --- | --- | --- |
| XML well-formedness | Can the file be parsed safely? | Automated |
| Official XSD validation | Does the native DEXPI 2.0 structure satisfy the bundled official schema? | Automated |
| Supported-profile semantics | Are required imports, identities, references, ports, streams, and supported mappings coherent? | Automated |
| Internal structural round trip | Can NeqSim reparse the generated representations without identity/reference loss? | Automated in the coordinated package |
| Named-tool round trip | Does the exact recipient CAE product/version preserve the required semantics? | External qualification required |
| Discipline acceptance | Are the exchanged engineering content and differences acceptable? | Accountable review required |
| Construction release | Is the detailed design approved for construction? | Outside automatic DEXPI generation |

`isSchemaAndProfileConformant()` does not mean DEXPI EV certification or commercial-tool qualification. The native
report deliberately retains `NOT_A_DEXPI_EV_CERTIFICATE` and a named-CAE status of `QUALIFICATION_REQUIRED`.

## From a process model to a governed DEXPI package

Use `EngineeringDeliverableCompiler` when DEXPI must remain consistent with engineering cases, calculated values,
registers, datasheets, safety evidence, approvals, and revision impact. The coordinated package includes the canonical
engineering graph, calculation dependencies, connectivity, validation findings, DEXPI representations, and manifests.

The canonical graph is the source of identity and provenance. DEXPI is an exchange representation of selected graph
content; it is not the only engineering database.

See [Engineering Deliverables and Handover](deliverables-and-handover) for the package layers and issue workflow.

## Importing a P&ID into simulation

A P&ID normally lacks enough information to run a credible process model. Import can establish equipment, piping,
nozzles, tags, and topology, but the resulting scaffold still needs:

- fluid composition and thermodynamic method;
- feed rates, pressure, temperature, and phase state;
- equipment specifications, efficiencies, geometry, and pressure losses;
- recycle and convergence configuration;
- controller tuning, actuator dynamics, and initial conditions; and
- evidence for operating, design, and accidental cases.

Treat OCR, PDF extraction, STID metadata, and manually normalized drawing content as evidence sources. Do not silently
promote unreviewed drawing interpretation into approved line routing, interlocks, setpoints, or safeguard credit.

## DEXPI, the engineering graph, and CFIHOS

| Representation | Principal role |
| --- | --- |
| Canonical engineering graph | Stable identity, provenance, dependencies, revisions, and cross-discipline relationships |
| DEXPI Plant | P&ID plant, piping, instrumentation, safeguarding, and diagram exchange |
| DEXPI Process | PFD/BFD process-step, material-port, stream, and state exchange |
| Proteus/pyDEXPI profile | Compatibility and visualization path for existing consumers |
| CFIHOS staging | Project information handover mapped to controlled RDL identifiers and Principal requirements |

DEXPI and CFIHOS are complementary. A DEXPI file does not replace project information requirements, document
classification, target-system loading, or final information acceptance.

## Common problems

| Symptom | Likely cause | Action |
| --- | --- | --- |
| pyDEXPI loads an empty or incomplete model | Default XML namespace is not handled by unqualified lookup | Use `writeForPyDexpi` for that consumer |
| A file claims DEXPI 2.0 but has a Proteus `PlantModel` structure | Only the header or namespace was changed | Regenerate with `Dexpi20XmlWriter` or `Dexpi20ProcessModelWriter` |
| Physical quantities are absent | The process was not run, values are non-finite, or the wrong information model was selected | Converge the source case and check the Plant/Process purpose |
| Equipment export fails as unmapped | The generated profile has no reviewed mapping for the Java equipment type | Add and test an explicit supported mapping; do not substitute a generic type silently |
| Validation reports dangling references | Connections, ports, nozzles, or identities are incomplete or inconsistent | Fix the source topology and regenerate the entire package |
| Internal round trip passes but project import fails | The named receiving tool/version has not been qualified | Perform the controlled named-tool round trip and semantic-difference review |
| Drawing looks complete but approval is blocked | Structural completeness is not discipline or safety-lifecycle approval | Close accountable review findings and retain the approval ledger |

## Executable examples

- [`professional_process_flow_diagrams.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/professional_process_flow_diagrams.ipynb) —
  Proteus/pyDEXPI export and rendering.
- [`dexpi_engineering_full_processsystem.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_full_processsystem.ipynb) —
  full `ProcessSystem` engineering export.
- [`dexpi_engineering_processmodel.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_processmodel.ipynb) —
  multi-area `ProcessModel` engineering packages.
- [`complete_pid_design_synthesis.ipynb`](https://github.com/equinor/neqsim/blob/master/examples/notebooks/complete_pid_design_synthesis.ipynb) —
  governed P&ID proposal and package.

## Related documentation

- [DEXPI import, export, and visualization](../integration/dexpi-reader)
- [DEXPI 2.0 native exchange and conformance](../integration/dexpi-20-conformance)
- [Standards-based DEXPI engineering generation](../integration/dexpi-engineering-generation)
- [Governed P&ID design synthesis](../pid-design-synthesis)
- [Engineering Deliverables and Handover](deliverables-and-handover)
