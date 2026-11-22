# Independent VFP contract fixtures

These files were hand-authored for issue #3600. They are synthetic encoding
fixtures, not measured well-performance data and not exporter-generated output.
Each pressure identifies its full coordinate, so dropped or transposed axes
change the expected result. Body rows contain explicit one-based indices.

The production fixtures exercise every axis with two entries. The FIELD fixture
uses STB/day for liquid, psia for both pressure axes/body, ft for depth,
Mscf/STB for GOR, and Mscf/day for gas lift. One Mscf is 1000 standard ft³,
not one standard ft³. The Java test supplies the corresponding canonical metric
inputs and independently compares all decoded header fields, axes, and body rows.

Schema and units were checked against these primary OPM sources on 2026-09-10:

- [VFPPROD keyword definition](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/share/keywords/000_Eclipse100/V/VFPPROD)
- [VFPINJ keyword definition](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/share/keywords/000_Eclipse100/V/VFPINJ)
- [Production table interpretation](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/Schedule/VFPProdTable.cpp)
- [Injection table interpretation](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/Schedule/VFPInjTable.cpp)
- [OPM unit constants](https://github.com/OPM/opm-common/blob/master/opm/input/eclipse/Units/Units.hpp)

`EclipseVFPExporterContractTest` uses its own slash-record reader with explicit
schema, dimension, bounds, uniqueness, finiteness, and completeness checks. It
does not call any exporter parsing or conversion helper. This checks the
documented OPM-compatible subset; it is not execution of OPM Flow or Eclipse.
