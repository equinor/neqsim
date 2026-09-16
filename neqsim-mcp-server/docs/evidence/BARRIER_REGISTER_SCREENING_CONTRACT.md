# Bounded barrier-register screening contract

This note records the qualification boundary for MCP `runBarrierRegister`. The
tool constructs only NeqSim's existing `BarrierRegister`, `SafetyBarrier`,
`PerformanceStandard`, `SafetyCriticalElement`, and `DocumentEvidence`
objects. It does not create a parallel safety model.

## Qualified software behavior

The qualification covers:

- at most 65,536 UTF-8 request bytes, 100 items in any collection, 256 members
  in an object, 4,096 characters in a text value, and 12 nested levels;
- fail-closed malformed JSON, wrong collection or item types, non-numeric or
  non-finite quantitative fields, and exceeded admission bounds;
- deterministic caller order, summary accounting, validation findings, impaired
  barrier reporting, and equipment-to-barrier mapping;
- quantitative LOPA/SIL handoff eligibility only when an available barrier has
  a finite PFD in `(0,1]`, a linked performance standard, and traceable source
  evidence;
- explicit exclusion reasons for impaired, unqualified, or untraceable barriers;
- catalog schema/example discovery, normal MCP access enforcement, standard
  response evidence, and real packaged-STDIO transport.

The returned `screeningOnly=true`, `standardConformanceClaimed=false`, and
`advisoryBoundary` fields are part of the qualified response. Standards names
are context references, not conformance claims.

LOPA exclusion records use the existing `lopaHandoff.excluded` array. The
qualification fixture supplies a safety-function description and links both
available and impaired barriers to an SCE, a performance standard, and evidence.
An impaired barrier remains excluded even when its documentation is complete.
Traceability can come from direct barrier evidence or the linked performance
standard. The missing-evidence scenario first verifies inherited traceability,
then removes both routes before requiring exclusion and validation findings.

## Safety and engineering boundary

The tool does not identify hazards; validate document extraction, scenario
completeness, PFD, effectiveness, availability, independence, common-cause
failure, proof testing, or lifecycle evidence; select or verify SIL; decide
tolerability or risk acceptance; demonstrate NORSOK S-001, IEC 61511, ISO 31000,
or regulatory compliance; authorize plant action; certify design; or replace
qualified process-safety review and accountable approval.

It reads caller-supplied data and returns advisory screening only. It performs
no plant write, controller action, external data retrieval, or automatic approval.

## Qualification and promotion boundary

Focused Java tests cover the catalog example, compact documentation example,
validation findings, deterministic screening fields, impaired/unqualified
exclusion, and fail-closed structural bounds. The focused
`test_barrier_register_protocol.py` runs seven scenarios through the packaged
MCP server.

Merged PR #3711 left Phase 0 inventory version `1.39` at
`20 EXPLICIT_TRUST + 39 CONTRACT_TESTED + 12 CONFIRMED_GAP` while the
qualification completed exact-head review.

Inventory `1.40 / 20 explicit + 40 contract-tested + 11 confirmed gaps`
atomically promotes `runBarrierRegister=CONTRACT_TESTED`. The canonical
machine-readable evidence record, Java assertions, focused packaged protocol,
authoritative comprehensive protocol accounting, and current-state documentation
move together on one exact head. No promotion candidate remains queued,
`scientificValidationComplete=false`, and every safety and engineering
limitation above remains controlling.
