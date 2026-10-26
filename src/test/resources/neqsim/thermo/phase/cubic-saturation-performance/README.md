# Pure cubic saturation: stop exhausted floating-point bisections

Reviewed baseline: PR #4577 head `87e3410f384f2560095ff8095949b224d54d0b50`.
Candidate changes only the two inner `CubicPureSaturation` bisections: return the
midpoint when rounding makes it equal either bracket endpoint. The remaining
iterations would return that same midpoint. No pressure, fugacity, root separation
or convergence tolerance changes; no new cache, dependency or allocation.

Measured 2026-10-26 with OpenJDK 17.0.20+8 on Linux amd64, `--release 8`
compilation and fresh JVMs with `-Xms128m -Xmx768m`. This is not a Java 8 runtime
measurement. Both versions use the exact same current-head classes/dependencies,
except a separately compiled baseline/candidate `CubicPureSaturation` is first on
the classpath. Timing processes ran sequentially. Pair order was baseline/candidate,
candidate/baseline, baseline/candidate. Host/JIT variability remains significant.

`timings.csv` retains all measured samples. Each batch cycles over SRK then PR
for hydrogen 20.369 K, CO2 302 K, methane 150 K and water 373.15 K, all with one
mole, initial pressure 10 bara and mixing rule 2. Fixtures are initialized once
outside timing. The helper scenario evaluates the pressure without changing the
fixture (100 cycles/batch, 25 warmup batches, 12 measured). The full scenario
clones the fixture and calls the public `bubblePointPressureFlash()` (50
cycles/batch, 150 warmup batches, 30 measured). Both include a checksum; each
scenario's checksum is identical across all versions and samples.

Candidate/baseline independent process median ratios:

| Scenario | Pair 1 | Pair 2 | Pair 3 |
| --- | ---: | ---: | ---: |
| Pressure helper | 0.623 | 0.591 | 0.689 |
| Complete public bubble-pressure call, including clone | 0.837 | 0.865 | 0.756 |

The helper is 31–41% faster in these runs. The complete operation is 13–24%
faster, with substantial variation; this is not a universal speedup claim.
Mixture TP, PH, VU, process and distillation throughput are outside this timing
experiment. This change is not called by mixture TP flashes.

`pressure-bits.csv` records an additional 48-state comparison: each of the eight
EOS/component fixtures at T/Tc = 0.45, 0.60, 0.75, 0.90, 0.98 and 0.995.
All baseline/candidate results are bit-identical, including near-critical states;
all 48 calculations succeeded. Every timing JVM repeats this comparison outside
its timed section. These are numerical EOS comparisons, not physical validation
of extrapolated low-temperature fluid states.

`Benchmark.java.txt` is the exact harness, with warmup/measured/cycle counts made
command-line arguments. Copy it to a temporary `Benchmark.java`; compile using
`javac --release 8 -cp "$CP" -d "$HARNESS" Benchmark.java`. Compile each version's
`CubicPureSaturation.java` separately against the same `$CP` into `$VARIANT`.
`$CP` contains current-head target/classes and Maven dependencies. Then run:

```bash
java -Xms128m -Xmx768m -cp "$VARIANT:$HARNESS:$CP" Benchmark baseline-1 helper.csv helper 25 12 100
java -Xms128m -Xmx768m -cp "$VARIANT:$HARNESS:$CP" Benchmark baseline-1 full.csv full 150 30 50
```

Select the matching label and variant directory and alternate order as above.
If the JDK has no `javac` launcher, `java com.sun.tools.javac.Main` is equivalent.
The harness appends raw timings and `.states` pressure bits. Compare independent
process medians, checksums and state rows. Never add wall-clock CI assertions.

Focused validation: 71 tests pass with zero failures/errors/skips across
CubicPureSaturationTest, PureComponentSaturationPressureTest,
PureComponentSaturationPointTest, CubicAnalyticalRootPressureTest,
TPflashCubicRootSelectionTest, HuronVidalThermodynamicModelConsistencyTest,
HuronVidalModelConsistencyTest and HuronVidalMultiphaseQualificationTest.
The existing independent saturation references, distinct-root/EOS-pressure checks
and source-state serialization assertions remain unchanged.
