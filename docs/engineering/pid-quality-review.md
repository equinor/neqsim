---
title: "P&ID Quality and Standards Review"
description: "Recheck coordinated native P&ID packages and retain independent drawing and controlled standards evidence without assigning engineering approval."
---

# P&ID quality and standards review

Use `@pid-quality-review` after generation or regeneration. The reviewer combines
the source model and registers with `@pfd-layout-review` and `@standards-review`.
Keep the generating owner and independent review role distinct. A successful
export or a geometry PASS does not establish standards conformance.

Run `neqsim pid-review PACKAGE --output REVIEW_DIR` from a source checkout or an
installation containing the review module. The source equivalent is the selected
interpreter followed by `devtools/pid_quality_review.py`. Keep REVIEW_DIR outside
PACKAGE so review does not change the artifacts it checks.

The command writes `pid-quality-review.json`, a readable Markdown report and
`visual-review.template.json` / `standards-review.template.json`. Templates start
at HOLD. Fill them only after actual review, identify the reviewer as a person or
AI, and rerun with `--visual-review FILE --standards-review FILE`.

| Dimension | Evidence and limits |
| --- | --- |
| Automatic checks | Actual file hashes/sizes, final native-assessment hash, project/revision and source-graph identities, native object references, actual SVG tag coverage, native/layout/register auxiliary line IDs, vessel sensing strokes against actual drawn hulls, and a fresh deterministic layout check |
| Engineering inputs | Existing native HOLDs, register completeness, sizing/classes, failure actions, driver data and unresolved physical attachments; proposed values remain proposed |
| Visual review | Drawing-set PDF and every P&ID SVG, at readable scale, with observed symbol meaning, physical connections, sensing locations, utility separation, continuations and readability |
| Standards review | Exact applicable editions and controlled-reference evidence for content, symbols, PCE and drafting; public catalogue descriptions and generated claims cannot replace normative review |
| Receiving-tool qualification | Named CAE product/version and round-trip evidence, kept separate from native XML and drawing review |

The automatic checks verify consistency within the retained package. They do not
independently reconstruct the entire live process, execute the official Java
assessment again, authenticate a reviewer, or determine compliance with licensed
standards. The source/physical review must still trace actual ports, nozzles,
signals, utility circuits and simulation bindings. A receipt binds content; it
does not prove competence, independence or that the referenced review occurred.

## Evidence and revision control

Both review records use SHA-256 of the raw `package-manifest.json` bytes in
`packageManifestSha256`. Every visual artifact additionally records its actual
file SHA-256; the exporter's visual fingerprint is not substituted for that hash.
Regeneration invalidates previous package receipts. Preserve earlier exports
and findings, fix defects in the source/generator, and review the new artifacts.

The visual schema is `neqsim_pid_visual_review.v1`. It requires reviewer/time,
artifact path/hash/disposition and evidence for each emitted checklist item. The
standards schema is `neqsim_pid_standards_review.v1`; it requires reviewer/time,
the exact standard basis, scope, controlled references and observed evidence.
Inspect the generated templates and workflow before preparing records.

Native P&ID registers use the shared physical projection for auxiliary material
line tags. The native XML, drawn routes and register rows use the same identity.
Configured process/product boundary lines retain their source-stream provenance.
Supplied `LineDesignInput` values/evidence are retained as REVIEW_REQUIRED; absent
auxiliary sizing/class remains HOLD. Series devices share one line row and retain
their declared order, endpoints and source proposal tags.

## Disposition

| Result | Meaning |
| --- | --- |
| FAIL (exit 2) | A technical inconsistency, corrupted/stale evidence, failed drawing check or observed review defect |
| HOLD (exit 1) | Missing design input, controlled edition/reference, applicable review, qualification or explained exception |
| PASS_FOR_ENGINEERING_REVIEW (exit 0) | All scoped automatic and supplied review checks pass without open blockers; it remains a bounded review disposition |

FAIL takes precedence over HOLD. No result assigns engineering approval,
construction fitness, SIL, HAZOP/LOPA acceptance, device certification or field
operating authority. The report always retains `fitnessForConstruction=false`.
Deliberate schematic whitespace remains visible in the layout evidence; other
geometry waivers cannot produce an unattended automatic pass.

## Standards sources

Use the project's controlled editions and applicability decisions. Public
publisher pages help locate the right documents; they are not clause or symbol
verification evidence:

- [ISO 10628-1 catalogue](https://www.iso.org/standard/51840.html)
- [ISO 14617-2 catalogue](https://www.iso.org/standard/83364.html)
- [IEC 62424 catalogue](https://webstore.iec.ch/en/publication/25442)
- [ISA5.1 standards committee](https://www.isa.org/standards-and-publications/isa-standards/isa-standards-committees/isa5-1)

Do not select an edition solely because it is newest. Record controlled editions
for ISO 5457/7200 and the applicable ISO 14617 parts as well as the process/PCE
basis. Missing or conflicting edition evidence stays HOLD. Follow the
[professional P&ID generation workflow](../pid-design-synthesis.md) for synthesis,
binding, native export and separate steady-state/dynamic tests.
