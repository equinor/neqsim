# Native DEXPI 2.0 regression package

This directory is a committed, deterministic exchange fixture generated from a branching NeqSim process with a
separator feeding a compressor and a pump. The XML is validated against the official DEXPI 2.0 schema and the NeqSim
semantic profile. `neqsim-native-summary.json` is the exchange-significant round-trip expectation used by tests.

Regenerate deliberately when the supported native DEXPI profile changes. Review XML and semantic-summary changes
together; never update the fixture only to silence a regression.
