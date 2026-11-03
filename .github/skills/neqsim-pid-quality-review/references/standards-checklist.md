# Controlled P&ID standards review checklist

This checklist defines review topics, not clauses or a declaration of compliance.
The project must select applicable parts and exact editions from controlled
sources. Verify publisher/catalog metadata for edition identity; public metadata
does not replace licensed/controlled requirements review. Do not infer a current
edition from this file or silently select the newest edition for an existing
project.

For each applicable standard, retain its designation, part, exact edition/year,
amendments/corrigenda, project adoption basis, controlled document ID/revision,
SHA-256, accessible evidence location, clause/requirement mapping, affected
objects/sheets, observed result, reviewer and review receipt. Use short
paraphrases and references; do not copy licensed tables or symbol collections.
An unverified citation or unavailable controlled requirement is HOLD. An
exclusion requires a scope rationale and accountable project decision.

| Standards family | Review topics against the selected controlled edition | Required package evidence |
| --- | --- | --- |
| ISO 10628, applicable parts | Diagram scope/content and representation conventions, relationship between diagram type and detail, stream/connection identification and selected graphical symbols | Declared drawing type/profile, source-to-sheet coverage, line identity/direction, symbol library provenance and clause mapping |
| ISO 14617, applicable parts | Selected graphical-symbol meaning, combination, connection and orientation where adopted | Symbol-to-device/function mapping, legend, orientation/ports and adopted symbol-source references |
| ISO 5457 | Adopted drawing-sheet size/layout conventions, frame, zones and sheet references | Actual PDF/SVG page dimensions and viewBox/scale, frame/zone details and final-size visual observations |
| ISO 7200 | Adopted document title-block and header data requirements | Drawing identifier/title, revision/status, sheet identity and other applicable controlled fields, reconciled with manifest |
| IEC 62424 | Representation of process-control-engineering requests and applicable exchange semantics | Typed instrument/control functions, actual sensing attachments, signal/final-element relations, identities and source/exchange mapping |
| ANSI/ISA-5.1 | Adopted instrumentation symbols and identification, functions, locations, signal conventions and project extensions | Tag/function mapping, bubbles/location coding, line-style legend, loop and sensing-to-controller-to-final-element evidence |
| Project drawing/instrument/line conventions and additional governing requirements | Selected hierarchy, deviations, required data and jurisdiction-specific interfaces | Approved project basis, convention revision/hash, conflict resolution, accepted exclusions and governed design-data sources |

Do not conflate the ISO 10628 symbol basis with the ISO 14617 family, or silently
combine ISO/IEC and ISA conventions. Record the selected convention and how any
mixed use is governed. A software profile named after a standard is an
implementation claim until controlled requirement review supports its scope.

Before closing the review, verify these interfaces explicitly:

- Every material junction is intentional; crossings are visually unambiguous.
- Process and utility sides remain physically distinct, with named supply and
  return boundaries; sensing or signal lines do not create material continuity.
- Actual pressure/temperature/flow/level/interface sensing locations match the
  process and control basis, rather than merely appearing near equipment.
- Off-page pairs agree on destination, identity, service and direction; external
  boundaries have a documented receiving system.
- Symbol function/orientation, valve direction/fail-action provenance and
  line/device design data agree across sheets, registers and native exchange.
- Final-size observations, exact reviewed artifact hashes and independent review
  receipts are retained for every sheet and applicable standards requirement.

Receiving-CAE import/round-trip results are separate evidence with a named tool,
version and tested scope. Neither this checklist nor the integrated P&ID review
provides construction, safety-lifecycle or final engineering approval.
