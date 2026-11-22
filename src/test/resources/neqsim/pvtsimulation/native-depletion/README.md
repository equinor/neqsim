# Independent native depletion qualification

This is an analytical implementation check of a **declared synthetic SRK model**,
not independent measured PVT/field accuracy. The historical equilibrium-reference CVD
check below **fails** its frozen tolerance on the legacy scan. The refined native
path now passes; see the continuation below. Do not interpret the conditional
scan-pressure check as a pass for independently predicted saturation pressure.

## Provenance and frozen question

The native snapshot producer is PR #4781 head
`14fbd3e612519859e9aff075a59af2d20532f8a8`, based on
`ab66ca997b22cf965ee7d6d9f63a8fc7b8559d92`. The production blobs used are:

- `DepletionPvtHistoryGenerator.java`: `7775f045605a405e54d26c243172f07636d11546`
- `DifferentialLiberation.java`: `b02a2b11e5a20c2a42e01faf18180d558cd6be80`
- `ConstantVolumeDepletion.java`: `6dbf4288b1ea8146b0a52b7c01a54181f2220ef5`

The last blob also matches fresh master
`d24fa0f81538257027c1dce4cad5a98c37e4a268`. Its only change relative to the
tested base is compressor documentation and its documentation test; the
thermodynamic implementation is unchanged. No production source or parameter
is changed by this qualification artifact.

Freeze before comparison: relative volume error at most `1e-6`, absolute phase
mole-fraction and component-inventory/withdrawal errors at most `1e-6` (mol for
inventories), and component closure at most `1e-10` mol for the one-mole feed.
These are numerical acceptance bounds, not measurement uncertainties. The older
`1e-8` CVD test checks constancy against its own first retained cell; it does not
independently establish that cell's saturation reference.

Component order is methane, n-decane. Both cases use unshifted SRK, classic mixing,
the declared library R `8.3144621 J/(mol K)` and database `kij=0.041099999`.
Critical parameters `(Tc K, Pc bara, omega)` are `(190.56,45.99,0.0115)` and
`(617.7,21.1,0.4923)`. These shared inputs are explicit; no parameter is fitted.

| Protocol | T (K) | Initial methane fraction | Pressures (bara) |
| --- | ---: | ---: | --- |
| DLE, remove all equilibrium gas after each row | 323.15 | 0.70 | 500, 300, 200, 100, 50 |
| CVD, restore the cell by gas withdrawal | 373.15 | 0.95 | 500, 300, 200, 100 |

The producer also runs the fixed separator train at `10 bara/303.15 K`, then
`1.0133 bara/288.71 K`; native inventory/volume comparisons here are at reservoir
conditions. Existing Java tests qualify separator component closure. This audit
does not independently validate the separator EOS or its measured accuracy.

## Independent method and reproducibility

`NativeDepletionBenchmarkProbe.java` exports all nine native states into
`native-states.json`. Compile it with the project's compiled classes/dependencies,
using `javac --release 8 -cp <classpath> -d <probe-classes> NativeDepletionBenchmarkProbe.java` and run
`NativeDepletionBenchmarkProbe <output-json>` with the same classpath. In a JDK
whose compiler launcher is absent, `java -m jdk.compiler/com.sun.tools.javac.Main`
can supply the compiler on JDK 9+. The probe itself uses Java 8 APIs. Local
execution was OpenJDK 17.0.20; this is not a local Java 8 JVM claim.

`independent_srk.py` makes **no NeqSim call**. It uses independently assembled
classical SRK cubic roots and departure-function fugacity, quadratic attraction
and linear covolume mixing. Solve binary coexistence compositions from equality
of both component log fugacities; exclude trivial equal-composition solutions.
Use the lever rule for phase amounts. DLE removes the calculated equilibrium gas.
At fixed T/P a binary tie line is fixed, allowing CVD gas withdrawal to be solved
algebraically from the gas molar volume and cell-volume excess, without copying
the production iterative removal loop. The dew calculation fixes gas composition
to the original feed and solves incipient liquid composition and pressure.

The generalized cubic/mixing/fugacity equations are documented in the
[IDAES cubic EOS documentation](https://idaes-pse.readthedocs.io/en/2.0.0/explanations/components/property_package/general/eos/cubic.html).
Use the exact SRK critical coefficients and standard Soave square-root alpha
explicitly implemented in the script, rather than rounded tabular coefficients.
This small reference is qualified only for these dry, nondegenerate binary cases;
it is not a general stability solver or a new production EOS framework.

The tested reference environment is Python 3.12.14, NumPy 2.3.5 and SciPy 1.17.0.
From this directory run:

```sh
python3 independent_srk.py --states native-states.json --output equilibrium-reference.json
python3 independent_srk.py --states native-states.json --output scan-pressure-reference.json --scan-pressure-reference
```

The first command **exits 1** after writing the full evidence because CVD exceeds
the frozen bounds. The second **exits 0**, conditional on the producer's explicitly
supplied scan pressure. It independently calculates the mixture cell volume at
that pressure; it does not fit the target to a retained volume. Both commands
give the same verdict in optimized Python (`python3 -O`); checks use explicit
exceptions. Missing/reordered pressures, nonfinite input or incompatible phase
identities are rejected. Preserve the first failed result when running the
second diagnostic. All result files are included for inspection.

## Results and unresolved acceptance

| Maximum error | DLE | CVD, independent dew | CVD, supplied scan pressure |
| --- | ---: | ---: | ---: |
| Relative retained volume | 1.066e-13 | **6.530e-6** | 4.832e-12 |
| Absolute phase mole fraction | 2.033e-13 | 3.253e-14 | 3.253e-14 |
| Component inventory/withdrawal (mol) | 1.015e-13 | **5.620e-6** | 4.154e-12 |
| Component closure (mol) | 4.164e-17 | 6.939e-18 | 5.552e-17 |

Independent dew pressure is `359.2450710350704 bara`, versus the existing
phase-count scan's `359.2417392730713 bara` (difference `-0.0033317619991 bar`).
The independently predicted one-mole dew cell volume is
`9.332000912961158e-5 m3`. The constant CVD volume differs by about 6.53 ppm.
The conditional diagnostic isolates the discrepancy to saturation-reference
initialization, rather than binary tie-line equilibrium or gas-removal accounting.

This result adds a **CVD saturation-reference precision blocker**. Neither the
frozen acceptance bounds nor the production flash/stability tolerance has been
loosened. Shared phase-boundary/saturation changes need coordination with #2937
and qualification of the existing saturation APIs; substituting an unqualified
dew API is not a repair. No `READY TO MERGE` or full #4740 completion is established
by the earlier self-reference CVD volume test or passing CI.

Two reference invocations take approximately 1 second total in the shared local
runtime (including interpreter/import overhead). No production simulation calls
this audit and no simulation speed claim is made. Wet, critical-degenerate and
phase-disappearance paths remain outside this artifact and the native API's
qualified domain; measured laboratory-to-field acceptance remains separate.


## Checked upper-dew refinement continuation

Merged #4784 supplies the shared constant-duty pressure convergence repair;
this continuation uses that API rather than copying its solver. The native
handoff now enables checked refinement of the coarse scan-selected upper dew.
The direct CVD default and modified-black-oil route retain the legacy scan.
No equation, independent input, tolerance or legacy artifact above is changed.

`refined-native-states.json` and `refined-equilibrium-reference.json` retain the
new complete nine-state export and independent comparison. Reproduce with the
same `NativeDepletionBenchmarkProbe.java` command, choosing the refined filename,
then run `independent_srk.py --states refined-native-states.json --output <output>`.
The independent script is unchanged and exits zero, including under `python3 -O`.
Its legacy `--scan-pressure-reference` mode diagnoses only the historical states;
it is not the reference used to accept the refined result.

The native CVD maximum errors are `4.7298e-11` relative volume,
`4.0698e-11 mol` inventory/withdrawal, `4.5297e-14` phase composition and
`5.5512e-17 mol` closure. DLE outputs are unchanged. `RefinedCvdHandoffTest`
compares independent algebraic depletion at 0.001/1/1000 mol and verifies the
upper reference from three initial pressures, legacy-default preservation and
explicit rejection of missing brackets. Original/tuned native handoff and fixed
separator conservation remain covered by `NativeDepletionHandoffTest`.

The phase-count scan is a branch locator, not the accepted equilibrium boundary.
Refinement validates the coarse pressure bracket, nontrivial normalized phase
compositions, feed reconstruction and component log-fugacity equality before
reflashing the original inventory at the accepted pressure. Unsupported domains
are rejected, not extrapolated. This resolves the declared binary native-CVD
precision blocker only; wet/critical states, CVD disappearance tails, measured
lab/field accuracy and the full #4740 acceptance remain open.
