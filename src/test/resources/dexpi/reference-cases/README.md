# Engineering-diagram reference cases

These synthetic, public fixtures pin the shared Phase 0 acceptance baseline for the
professional PFD and DEXPI/P&ID campaigns. The executable definitions live in
`EngineeringDiagramReferenceFixtures`; `EngineeringDiagramReferenceCasesTest`
validates them from fresh models.

The three cases are:

1. a simple feed, valve, separator, compressor and cooler train;
2. a separator with gas-compression and liquid-pumping branches; and
3. an inlet, compression, export and flare-boundary `ProcessModel`.

Each case checks material balance in kg/h, deterministic canonical topology and
stable identities. The two `ProcessSystem` cases also prove deterministic native
DEXPI 2.0 Process and Plant exchange, distinct material ports, supported-profile
validation, Proteus compatibility output and governed P&ID proposal status. The
multi-area case proves plant-wide canonical topology, legacy combined/per-area DOT,
and deterministic combined/per-area Proteus sheets.

The fixtures contain no project or proprietary data. They are regression evidence,
not approved engineering deliverables. Current native DEXPI Process multi-area,
controlled document/sheet, drawing graphics, professional native SVG/PDF, named CAE
round-trip and accountable approval gaps stay explicit in
`reference-case-manifest.json`.
