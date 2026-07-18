---
title: DEXPI 2.0 native exchange and conformance
description: Official native DEXPI XML Plant and Process model export, deterministic validation, and auditable conformance evidence.
---

# DEXPI 2.0 native exchange and conformance

DEXPI 2.0.0 was released on 10 October 2025. It combines the Plant/P&ID and Process/PFD/BFD
information models and introduces DEXPI XML as their common serialization. Native DEXPI XML has a
`Model` root containing `Import`, `Object`, `Components`, `Data`, and `References` elements; it is
not Proteus XML with a different namespace.

NeqSim provides two fail-closed native exporters:

| API | Official model imports | Exchange purpose |
|---|---|---|
| `Dexpi20XmlWriter` | Core 2.0.0 + Plant 2.0.0 | P&ID plant items, piping, instruments, safeguards, boundaries, and diagram representations |
| `Dexpi20ProcessModelWriter` | Core 2.0.0 + Process 2.0.0 | PFD/BFD process steps, material ports, streams, and physical state quantities |

The official DEXPI XML V2.0.0 XSD is bundled under CC BY 4.0 for deterministic offline validation.
`Dexpi20ConformanceAssessment` verifies its reviewed SHA-256 fingerprint before using the result as
conformance evidence.

## Process-model export

```java
File exchange = new File("gas-processing-pfd.dexpi.xml");
Dexpi20ConformanceAssessment.Report report =
    Dexpi20ProcessModelWriter.writeAndAssess(process, exchange);
if (!report.isSchemaAndProfileConformant()) {
  throw new IllegalStateException(report.getErrors().toString());
}
Files.write(Paths.get("gas-processing-pfd.conformance.json"),
    report.toJson().getBytes(StandardCharsets.UTF_8));
```

Each process connection has a dedicated source and target `MaterialPort`. The ports and
`Process.Stream` carry reciprocal references, stable identifiers, nominal directions, and explicit
mass-flow, absolute-pressure, and temperature quantities when finite simulation values are
available. The exporter uses kilogram/hour, bar absolute, and degree Celsius references from the
official Core physical-quantity model.

Reviewed NeqSim-to-DEXPI Process mappings are:

| NeqSim equipment | DEXPI 2.0 Process type |
|---|---|
| feed or boundary `Stream` | `Source` |
| unconsumed product outlet | `Sink` |
| compressor | `Compressing` |
| pump | `Pumping` |
| separator | `SeparatingByGravity` |
| distillation column | `Distilling` |
| cooler | `Cooling` |
| heat exchanger | `ExchangingThermalEnergy` |
| heater | `HeatingInFurnace` |
| tank | `StoringFluids` |
| control valve | `RegulatingFlow` |
| mixer | `MixingSimple` |
| splitter | `SplittingMaterial` |
| expander | `TransportingFluids` |
| pipeline or pipe segment | `TransportingFluids` |

An unmapped equipment class aborts export with its Java type and tag. The exporter never substitutes
an unreviewed generic DEXPI type merely to make a file validate.

## Conformance layers

`Dexpi20ConformanceAssessment` records separate decisions for:

1. the bundled official V2.0.0 schema fingerprint;
2. DEXPI XML schema validation;
3. exact versioned Core plus Plant or Process imports;
4. unique identities and resolvable references;
5. NeqSim-supported Plant or Process semantic-profile rules; and
6. the SHA-256 digest and object/reference counts of the assessed file.

Only a report with all five technical gates passing returns
`isSchemaAndProfileConformant() == true`. A Plant file assessed as a Process profile, a version-mixed
model import, dangling reference, missing required port/stream relationship, or unknown generated
profile type fails explicitly.

The report deliberately states `NOT_A_DEXPI_EV_CERTIFICATE` and
`namedCaeRoundTripStatus=QUALIFICATION_REQUIRED`. Schema and supported-profile conformance do not
replace import/export/reimport testing in the exact CAE product and version used by a project.
Record that separate evidence through `DexpiToolQualificationRunner` and
`DexpiToolQualificationEvidence`.

## Release and review controls

- Pin exchange evidence to DEXPI 2.0.0 model URIs and the file digest; never silently reinterpret a
  later DEXPI model release as 2.0.0.
- Store the conformance JSON, source model revision, generated XML, named-tool round-trip, and
  accountable semantic-difference review together.
- Compare the committed native golden fixture and semantic inventory when modifying type mappings,
  topology, units, or serialization order.
- Treat graphics, project standard-library restrictions, vendor extensions, and CAE certification as
  separate qualification scopes.

Official references: [DEXPI Specification 2.0.0](https://dexpi.gitlab.io/-/Specification/-/jobs/11676485644/artifacts/src/.build/html/html/index.html)
and the [DEXPI specification source](https://gitlab.com/dexpi/Specification), licensed CC BY 4.0.
