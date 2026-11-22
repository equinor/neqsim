# Issue #3943 pump inlet fixture

`pump1_inlet_fluid.ser.gz.b64` is the serialized NeqSim 3.22.0 pump inlet
provided in the [issue reproduction gist](https://gist.github.com/EvenSol/3e2679546dc566be9e1d8805afd07e10).
It is gzip-compressed and Base64-encoded to keep the Java serialization payload
as a portable text test resource. The decoded `.ser` has SHA-256
`d7c8f501072abd8ed94912044c0ba209bf1872fa3a38f4773f765e7492d5f66c`.

The inlet reflects a metastable gas/oil flash at 298.15 K and 2.67 bara;
the regression tests cover both that saved state and the equilibrium inlet
obtained by reflashing it before the pump PS calculation.
