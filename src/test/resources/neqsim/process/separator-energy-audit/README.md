# V101 separator cumulative-energy characterization

This public synthetic fixture reproduces the cumulative-energy issue in #4859.
It contains no plant data, drawing export, external task modules or glyph assets.
The two Python modules depend only on JPype and the repository's existing
`devtools/neqsim_dev_setup.py`. Run from a checkout compiled with the candidate
separator energy-diagnostic API; a released JAR without that API is insufficient.

From the repository root, compile/package using the normal development workflow,
then use the repository's locked Python environment:

```sh
./mvnw -DskipTests package
uv run --project devtools --locked python src/test/resources/neqsim/process/separator-energy-audit/run_energy_audit.py --case control --duration 60 --dt 0.5 --output target/separator-energy-audit/control-smoke
uv run --project devtools --locked python src/test/resources/neqsim/process/separator-energy-audit/run_energy_audit.py --case shutdown --duration 12 --dt 0.1 --output target/separator-energy-audit/shutdown-smoke
```

Alternatively use the selected Python interpreter with JPype installed. The
fixture locates this checkout's helper; `NEQSIM_PROJECT_ROOT` can explicitly
select another checkout. It has no historical task-directory fallback. A new
Python/JVM process is required after recompiling Java. On Windows use `mvnw.cmd`.

Both commands run tracking disabled and enabled. `--tracking off` or `on`
supports individual observations. Outputs include actual `off.json`/`on.json`,
full CSV trajectories and `pair.json`. The latter records the hashes of fixture
sources, actual loaded class bytes and corresponding Java sources, JVM/Python
versions, and the Git commit only when Git identifies the actual repository
root. A materialized source tree instead reports file-hash provenance; an
unrelated enclosing repository commit is never treated as the checkout revision.

## Frozen model and criteria

The SRK/classic-mixing model uses the declared nine-component composition in
`v101_model.py`, 20,000 kg/h at35 degrees C, 45 bara supply, initially40 bara
horizontal separator, and gas/liquid sinks at35/20 bara. Vessel dimensions are
1 m diameter by3 m length with initial liquid fraction0.5. Native valves are
auto-sized at50% opening. PIC gain/Ti are16 percent/bar and20 s; LIC gain/Ti are
4 percent/percent and60 s, with3 s outlet-valve travel. The inlet ramps50 to60%
over30..60 s in the control case.

The shutdown blocks the gas valve at1 s. Only the pressure SIF is implemented:
held PT-002 signal,41 bara trip,0.1 s channel qualification,0.1 s logic delay,
native1oo1 voting and a1 s fail-closed inlet actuator. Level is only a declared
response envelope, not an implemented level trip. `full-recompute.json` records
all response limits, time steps and the unchanged cumulative-energy policy.

The response and energy verdicts are separate. The energy limit is
`max(abs(U - U0 - integrated_boundary_energy)) / max(abs(U0), 1 J) <= 1e-5`.
A local VU acceptance tolerance does not replace this cumulative criterion. The
60 s control smoke is deliberately shorter than the300 s settling study and can
FAIL the frozen final-response criterion while its numerical energy check passes.
An expected numerical FAIL is retained as FAIL; successful script execution does
not mean engineering acceptance.

## Independent consumed-boundary audit

The ordered scan uses the same native objects and held measurement/PI behavior:
advance process clock, sample measurements, feed, numerical pressure-equality
relation, inlet valve, separator, outlet valves, then unrun registered controllers.
The independent audit samples current inlet and previous outlet mass/component/
enthalpy rates **immediately before** `Separator.runTransient`. It does not read
the ledger's captured fluxes to construct its oracle. Zero molar flow carries
zero boundary energy; nonfinite nonzero fluxes are errors.

The native `ClosedLoopSafetyFunction` is activated at the gas-block event and
executes after each subsequent physical step, matching the existing native
scenario runner's event/end-of-step semantics. A configured native strict
capability preflight is required. The energy epoch and independent integral
reset at the event, so shutdown accounting covers1..12 s. The public audit mode
does not itself call `DynamicSafetyScenarioRunner`; its full physical trajectory
must be compared with that strict reference before accepting parity evidence.

Post-step endpoint sampling is not an exact substitute: later native updates
can change inlet enthalpy after the vessel consumed it. The earlier endpoint-only
shutdown energy estimates are superseded, not benchmark truth. The native ledger
distinguishes accepted VU state, published inventory, local defects, cumulative
debt and non-VU differences; tracking off/on must leave the physical trajectory
unchanged. Ledger/oracle agreement does not remove a failed energy gate or prove
physical-model, safety or standards qualification.

## Full recomputation

Run the same command with these case/duration/dt combinations:

| Case | Duration s | dt s |
|---|---:|---:|
| control |300|0.5|
| control |300|0.25|
| shutdown |12|0.1|
| shutdown |12|0.05|

`expected-results.json` contains compact observed reference metrics, not imposed
answers or altered tolerances. In particular the refined300 s control case
retains its frozen energy FAIL despite successful process response. Inspect the
actual reports and complete trajectories when evaluating another runtime.
