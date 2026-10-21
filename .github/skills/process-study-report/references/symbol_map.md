# Equipment category to symbol map (psr_diagrams)

Symbols are schematic and follow the ISO 10628-2 / ISO 14617 conventions in spirit. Conformance is not
claimed and every drawing says so. Categories come from `psr_extract.classify` (class-name keywords).

| Category | Symbol | Notes |
|---|---|---|
| separator (2-phase) | horizontal vessel (capsule) | outlets ordered by dominant phase: gas top, hydrocarbon liquid middle, aqueous bottom |
| separator (three-phase) | horizontal vessel with weir | class name contains `ThreePhase` |
| separator (scrubber) | vertical vessel with demister marks | class name contains `Scrubber` |
| compressor | circle with converging lines (centrifugal), dashed shaft-power stub, "driver (not modelled)" | efficiency and power on the data lines |
| expander | mirrored compressor symbol | |
| pump | circle with triangle | |
| heater / cooler / exchanger | circle with zig-zag coil, dashed utility stubs "heating utility" / "cooling utility" | the medium is never named unless modelled |
| valve | bow-tie | |
| mixer | vertical header bar that grows with the number of inlets | |
| splitter | filled dot (tee), header bar above three outlets | |
| ejector | nozzle and diffuser double trapezoid | |
| column | tall rectangle with tray lines | |
| recycle | small dashed circle "R" | numerical convergence element, not plant equipment |
| other | rectangle | |

Line and label conventions: process streams solid with arrowheads and a numbered diamond (`S001`) on the
longest segment; boundary feeds/products carry the stream label; off-page connectors carry sheet number, other
drawing number and stream number; utility and shaft-power interfaces dashed; recycles and back flows routed in
lanes above/below the drawing; legend lists only the symbols used on the sheet; notes state the unmodelled
systems and that pressures are absolute.
