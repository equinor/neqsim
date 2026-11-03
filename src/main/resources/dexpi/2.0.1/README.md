# Official DEXPI 2.0.1 definitions

DEXPI e.V. (2026), **DEXPI Specification 2.0.1**, released 30 September 2026.
The files below are unmodified copies retrieved from the official published specification.
License: [Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/).
No endorsement by DEXPI e.V. is implied.

Specification: https://dexpi.org/specification/2.0.1/html/
Changelog: https://dexpi.org/specification/2.0.1/html/appendix/changelog.html

| Resource | Official source | SHA-256 |
| --- | --- | --- |
| DEXPI_XML_Schema.xsd | https://dexpi.org/specification/2.0.1/html/_static/DEXPI_XML_Schema.xsd | `0bd6568b7c52fd59ac4ef6d8547ab2b2aa890c009a3a1bfbc5d7cc18c53b36e6` |
| Core.xml | https://dexpi.org/specification/2.0.1/html/_static/Core.xml | `7136f8440c08c8bf7759c4e9eb614b046cc3196f2f45695122629a2020e61e4a` |
| Plant.xml | https://dexpi.org/specification/2.0.1/html/_static/Plant.xml | `0a5175535c1263464fee653c7b99f38eb810c15824f84155748eac165f50edbf` |
| Process.xml | https://dexpi.org/specification/2.0.1/html/_static/Process.xml | `b4d6a7e35fe62e6fd81153cf1f4c54e3f08718802d96ac21cdd4167d5753d341` |
| Builtin.xml | https://dexpi.org/specification/2.0.1/html/_static/Builtin.xml | `75bb247a5f1ddcd12e5df74b6acf6877496b8962d1332ec4184e0aaabdfa485e` |

The schema published with 2.0.1 is byte-identical to the published 2.0.0 schema.
Upstream model annotations contain trailing spaces. Keep those bytes unchanged
so the recorded source hashes remain reproducible; the scoped Git whitespace
attribute excludes only these vendored XML files from end-of-line whitespace checks.
The model definitions differ; an unchanged XSD does not establish 2.0.1 semantic compatibility.
Core/Plant/Process declare exact `https://data.dexpi.org/models/2.0.1/` model URIs.
Builtin retains its official metamodel URI `https://data.dexpi.org/meta/1.0.0/Builtin`.
The official definitions retain references to MetaData annotations. Those specification-description
annotations are not emitted instance data or inputs to the supported exchange validation.

`Dexpi201ModelValidator` checks the reviewed byte fingerprints once, then validates supported
instance classes, inherited properties, property kinds, multiplicities, enumeration identifiers
and typed object relationships offline. It does not qualify every data-value constraint,
project drawing convention, commercial CAE implementation or engineering approval.

Legacy `dexpi/2.0/` resources and exporter overloads remain available unchanged.
