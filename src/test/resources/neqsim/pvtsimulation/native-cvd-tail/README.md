# Independent dry CVD gas-only continuation

This author-generated synthetic benchmark checks numerical SRK/protocol consistency,
not measured laboratory or field accuracy. It qualifies the gas-only CVD limit
after liquid disappearance; no absent oil PVT is assigned.

One mole methane/n-butane0.95/0.05 at275K, classic SRK, no volume translation.
Critical data, acentric factors and the actual database interaction are exported
in `native-states.json`; the independent EOS uses R=8.3144621J/(molK).
Pressures are500/200/100/50/20/5/2bara. Gas/oil coexist at50bara; only gas remains
at20/5/2bara. Pre-boundary single-phase rows retain the closed sample. Below the
upper dew reference, gas is removed to the fixed cell volume, including after
the final liquid evaporates. At fixed T/P/composition, single-phase volume is
extensive in moles, so removed moles equal volume excess divided by gas molar volume.

`independent_srk_tail.py` reuses the independent cubic roots, fugacity equality,
lever rule and algebraic two-phase withdrawal from the native-depletion benchmark.
It adds the single-phase extensivity limit. Forty declared dew starts locate
separated equilibria and select the highest root in1..1000bara. The two roots
include41.24435334891582 and74.95956303780969bara; a single-start lower-root solve
does not define the upper cell reference. This is a bounded binary reference,
not a general stability or phase-envelope solver. No EOS parameter or tolerance
is fitted to the production result. Equation provenance is retained in the script.

The volume error metric is `abs(V_reference/V_native-1)`. Freeze volume relative, composition absolute and inventory/withdrawal absolute
errors at1e-6 (mol for inventory), component closure1e-10mol and dew-pressure
error1e-5bara. The corrected maximum errors are3.0303e-9 relative volume,
2.4614e-12 phase composition,1.7706e-9mol inventory/withdrawal and1.0062e-16mol
closure. The unchanged prior implementation fails:0.965813 relative volume error
and0.564562mol inventory/withdrawal error, because it leaves the tail gas in the cell.

Compile `TailBenchmarkProbe.java` using the exact project classes/dependencies
with `javac --release 8 -cp <classpath> -d <probe-directory> TailBenchmarkProbe.java`.
Run `java -cp <probe-directory-and-classpath> TailBenchmarkProbe <native-output>`.
Then run `python3 independent_srk_tail.py --states <native-output> --output <reference-output>`.
Normal and optimized Python must give the same verdict. Full reference and native
outputs are retained here. Java tests cover three sample scales, original/tuned
handoff, named cell/separator conservation and gas-only pressure-step refinement.

The default direct CVD path also receives this protocol correction, relative to
its existing scan reference, only after a gas/oil stage is sampled in the
pressure schedule. A pure supercritical regression checks that an unbracketed
single-phase schedule preserves its inventory; this is a compatibility guard,
not qualification of the legacy reference or a pure-fluid CVD protocol. Native
replay uses the independently qualified refined upper dew. Modified-black-oil conversion still requires both phases.
Wet/critical paths, near-critical field accuracy and the full Tran method remain
outside this benchmark. In-place volumes are not recoverable reserves.
