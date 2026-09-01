---
title: Controlled CFIHOS 2.0 engineering handover
description: Deterministic staging files, verified RDL mappings, gap registers, and fail-closed readiness evidence for CFIHOS 2.0 handover.
---

# Controlled CFIHOS 2.0 engineering handover

See [Engineering Deliverables and Handover](../engineering/deliverables-and-handover) for how CFIHOS staging fits with
the canonical engineering graph, DEXPI exchanges, registers, package validation, approvals, and revision control.

CFIHOS 2.0 was published as a major release in November 2025. It provides Core and
Extended Reference Data Library deliveries, an updated data model and dictionary, implementation
guides, and supporting templates. The standard separates the information requirements, data model,
implementation guidance, RDL, and software requirements used to specify, validate, stage, map, and
transfer project information.

NeqSim implements the controlled staging part of that workflow. It does not bundle CFIHOS RDL
content, invent RDL identifiers, reproduce a Principal information requirements, or claim CFIHOS
conformance. Cfihos20HandoverExporter maps the canonical EngineeringGraph through an explicit
project-owned RDL mapping and produces deterministic CSV and JSON evidence for target-system
transformation by the Principal.

## Required mapping controls

Cfihos20ReferenceDataMapping records:

- whether the project uses the CFIHOS 2.0 Core or Extended RDL;
- a URI for the exact controlled RDL delivery;
- a SHA-256 calculated from the supplied RDL bytes;
- the accountable mapping authority and mapping revision;
- exact tag and equipment class identifiers per canonical node;
- exact property and applicable unit-of-measure identifiers; and
- exact document-type identifiers.

Use verifiedSource, not a copied digest, for release evidence:

~~~java
Cfihos20ReferenceDataMapping mapping = Cfihos20ReferenceDataMapping
    .builder(Cfihos20ReferenceDataMapping.Edition.CORE)
    .verifiedSource("urn:company:cfihos:2.0:core:project-rev-c", controlledRdlFile)
    .approvedBy("Project Information Manager", "MAPPING-REV-C")
    .mapNode("equipment:20-vg-001", exactTagClassId, exactEquipmentClassId)
    .mapProperty("designPressure", exactPropertyId, exactUomId)
    .mapDocument("20-VG-001-datasheet.pdf", exactDocumentTypeId)
    .build();

Cfihos20HandoverExporter.Result result =
    Cfihos20HandoverExporter.export(graph, mapping, outputDirectory);
if (!result.getReport().isReadyForPrincipalTransformation()) {
  throw new IllegalStateException(result.getReport().getFindings().toString());
}
~~~

The API validates identity presence and mapping controls, but it cannot decide whether an identifier
is semantically correct for a project. That decision belongs to the approved RDL mapping and the
Principal information-management review.

## Staging package

| File | Purpose |
|---|---|
| cfihos-tags.csv | Functional tag identity, description, exact tag class, project, and revision |
| cfihos-equipment.csv | Physical equipment identity, exact equipment class, and realized tag |
| cfihos-properties.csv | Scalar values mapped to exact property and UOM identifiers |
| cfihos-documents.csv | Document identity, type, title, and controlled file reference |
| cfihos-relationships.csv | Canonical graph relationships plus equipment-realizes-tag links |
| cfihos-unmapped.csv | Every unclassified node, document, or optional scalar property |
| cfihos-20-assessment.json | Counts, findings, mapping evidence, file digests, and readiness decision |
| cfihos-20-manifest.json | Ordered inventory and SHA-256 digest for every staged payload |

The CSV column set is the versioned NEQSIM_CONTROLLED_CFIHOS_2_0_STAGING_V1 profile. It is
intentionally a neutral staging shape, not an official or vendor-specific bulk-loader template. The
Principal must transform it to the contracted CFIHOS exchange templates and target-system schema.

## Fail-closed decisions

The assessment is INCOMPLETE when:

- the RDL digest was declared rather than verified from controlled bytes;
- the mapping revision lacks project approval;
- a tag-bearing graph node lacks an exact tag-class mapping;
- physical equipment lacks an exact equipment-class mapping;
- a mapped numeric property lacks an exact unit-of-measure mapping;
- a handover document lacks an exact document-type mapping; or
- the graph contains no transferable tag-like nodes.

Unmapped scalar properties are retained in the gap register as warnings because the Principal
contractual data requirements determine whether each property is required. A production release
must reconcile those warnings against the project requirement matrix; absence of a blocker is not
proof of contractual completeness.

Both JSON files state:

- cfihosConformanceClaim=false;
- principalAcceptanceRequired=true; and
- targetSystemTransformationRequired=true.

These controls prevent a deterministic export from being mistaken for information acceptance,
operational readiness, or construction authorization.

## Industrial handover sequence

1. Freeze the CFIHOS 2.0 Core or Extended RDL delivery and calculate its digest.
2. Derive the project information requirement matrix and contract deliverable scope.
3. Approve exact node, property, UOM, and document mappings.
4. Compile the canonical engineering graph and export the staging package.
5. Close every blocker and reconcile every optional-property warning with the requirement matrix.
6. Transform to the Principal templates and validate in the named staging/target system.
7. Record review comments, load receipts, rejected records, accepted revisions, and final handover
   authority outside NeqSim.

Official references: [CFIHOS 2.0 standards and downloads](https://www.jip36-cfihos.org/cfihos-standards/)
and [CFIHOS implementation workflow](https://www.jip36-cfihos.org/how-it-works/).
