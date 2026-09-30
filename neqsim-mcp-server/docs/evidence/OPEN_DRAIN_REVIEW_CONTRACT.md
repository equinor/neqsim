# Open-drain review software contract

## Qualified surface

Inventory 1.49 promotes `runOpenDrainReview` from `CONFIRMED_GAP` to
`CONTRACT_TESTED`. The public MCP tool delegates to the existing
`OpenDrainReviewRunner` and canonical `OpenDrainReviewEngine`; it does not introduce a
parallel simulator or an MCP-only engineering model.

The executable contract covers:

- discovery text that identifies the NORSOK S-001 Clause 9 and caller-normalized evidence boundary;
- the `open-drain-review/norsok-s001-stid` catalog example;
- deterministic two-item `PASS` reporting with per-item results, standards attribution, and provenance;
- normal access enforcement and the standard MCP status, validation, quality-gate, data, warning, and provenance fields;
- fail-closed empty input behavior; and
- direct Java, comprehensive protocol, and focused packaged-MCP qualification.

## Evidence

- `src/main/java/neqsim/mcp/runners/OpenDrainReviewRunner.java`
- `src/main/java/neqsim/process/safety/opendrain/OpenDrainReviewEngine.java`
- `src/test/java/neqsim/mcp/runners/OpenDrainReviewRunnerTest.java`
- `neqsim-mcp-server/src/main/java/neqsim/mcp/server/NeqSimTools.java`
- `neqsim-mcp-server/test_open_drain_review_protocol.py`
- `neqsim-mcp-server/test_mcp_server.py`
- this contract document

## Engineering and advisory boundary

The tool consumes normalized STID/P&ID records and optional tagreader or historian evidence
supplied by the caller. Neither the runner nor this contract establishes direct STID/tagreader
connectivity, source-document or tag fidelity, complete area/drain coverage, drainage,
fire-water, or leak-rate design-basis accuracy, hydraulic or CFD performance, segregation,
backflow, seal, vent, or utility adequacy, or governing-standard applicability or conformance.

Results are advisory screening evidence. They do not define safe operating limits, authorize
plant or control action, certify a design, or replace accountable process-safety review.
