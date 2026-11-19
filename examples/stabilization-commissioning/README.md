# Oil stabilization and recompression commissioning case

Run a synthetic 100 t/h wet wellstream through three pressure stages (26, 8 and
1.6 bara), a 78 °C stabilization heater and three stages of flash-gas
recompression to 60 bara. The steady model uses SRK equilibrium with water and
discrete hydrocarbons through C20. Six native Recycle units close the
scrubber/aftercooler condensate returns. At 55% feed, three additional native
recycles return cooled gas across the anti-surge valves to the corresponding
suction mixers. Only exported oil, gas and water cross the external balance.

## Run from the repository root

Use Java 17, the Maven wrapper, and Python with JPype1 and NumPy. Each script
uses the checkout's compiled Java classes. Run the commands in order.

```sh
./mvnw -DskipTests compile
python examples/stabilization-commissioning/steady.py
python examples/stabilization-commissioning/dynamic.py
python examples/stabilization-commissioning/matched_replay.py
python examples/stabilization-commissioning/hipps.py
python examples/stabilization-commissioning/flare.py
python examples/stabilization-commissioning/procedures.py
python examples/stabilization-commissioning/drawings.py
python examples/stabilization-commissioning/verify.py
```

Results go to `target/stabilization-commissioning/`. Open the PDF under
`<package_directory>/drawings/pid-drawing-set.pdf`; `drawing-intake.json`
gives the package directory for the latest export and any re-import losses.
Each export preserves the previous drawing revision in its own directory.
The package contains the DEXPI XML, vector sheets, cause/effect records,
instrument register, simulation bindings and drawing completeness report.
The evaluated 55% turndown drawing inserts a real zero-flow gas branch for
the closed `20-BDV-001` while preserving the live pressure-control path. A
real protective `20-PT-002` measures that zero-pressure-drop live branch at
V-101 pressure, and the design model explicitly identifies its physical
sensing location as the vessel. Their explicit bindings make the generated
`20-PSHH-001 -> 20-BDV-001` demand and the BDV
final-element identity re-importable without creating an ambiguous second
consumer of the vessel outlet. The complete package intentionally remains a
non-strict intake because other synthesized proposals still require
reconciliation with executable logic before they can be credited.

## What the commissioning results measure

| Case | Model and evidence |
| --- | --- |
| Normal, turndown and lower heater temperature | Native three-phase flowsheet with converged recycle streams. Check each tear, external mass and enthalpy balances, component residuals, compressor suction volumes and oil bubble pressure at 37.8 °C. |
| Feed step and anti-surge | Whole-train reduced gas/liquid inventories and PI pressure/level control. Three native anti-surge controllers operate finite-stroke valves using a synthetic single-speed characteristic. Compare turndown with the same disturbance and the anti-surge control disabled. |
| Trip and isolation | Three blocked outlets, single faulty pressure transmitter, scrubber liquid slug and three machinery signal injections. A native 2oo3 voter with a one-second persistence timer drives a latched, simulated shutdown. Test actual modeled positions and distinguish injected signals from physical process disturbances. |
| V-101 matched replay | Initialize a native constant-volume/energy separator and a frozen-phase comparator with the same phase masses and volumes. Both receive the same feed, condensate return and three phase withdrawals at every step. The native trip drives the common valve strokes. The return stays at its converged rate. CSV traces expose every boundary. |
| Flare handoff | Use the first sample with latched trip and inlet, transfer and export valve positions all zero. Initialize two native gas-cap depressurizations using the reduced train's HP/MP pressures and gas volumes at isolation. Preserve steady gas compositions and temperatures. Open both restrictions together; sum the time-dependent discharges into a fixed 1.5 bara header. |
| Operating procedures | Execute turndown, controlled shutdown and operator-authorized restart against the closed native flowsheet. `CHV-100` uses a four-second rate-limited actuator and measured stem feedback. Stuck-shutdown and inhibited-restart cases must time out without falsely completing. A repeated shutdown uses the same deterministic physical-step identities. |

At a 0.25 s step, the native V-101 trip occurred **22.25 s after blockage**,
versus **21.00 s** with frozen phases. At 0.5 s the times were 22.50 and 21.00 s.
The largest paired total-mass difference was below 3 × 10⁻¹⁰ kg. Matched
forcing narrows the previously observed timing discrepancy while leaving
different pressure responses from the two thermodynamic models.

In the 12-case integrated run the minimum K-203 surge margin during turndown
was 9.60%. In the blocked HP case, all three isolation positions reached zero
at 49 s plant time. The simultaneous HP/MP gas-cap discharge peaked at
2.55 kg/s at 50 s. This is a screening load for the assumed restriction sizes,
gas inventories and header state. Header linepack and pressure feedback, liquid
flashing from the separator inventories, flare KO carryover, vendor compressor
maps, and independent SAS emulator testing require additional models and data.

## Executable operating procedures

`procedures.py` applies the revisioned `OperatingProcedure` contract to the
same closed 100 t/h reference train. It records command ownership, measured
`CHV-100` target and position, procedure events, admitted feed, product flows,
position IAE, completion time, and the external mass residual at every physical
scan. The healthy turndown, shutdown and authorized restart cases use the same
four-second full-stroke actuator. The stuck-shutdown case applies 150 percentage
points of stiction and must issue its recovery command; the inhibited restart
must fail before issuing an opening command.

The 0.50 s and 0.25 s turndown replays provide scan-size sensitivity, while a
second 0.50 s shutdown must reproduce the complete JSON record. These are
synthetic deterministic commissioning tests, not an approved operating
procedure or site acceptance test. Most equipment in this reference flowsheet
remains algebraic during `runTransient`; the reported external residual and its
time integral are therefore explicit model-closure diagnostics, not a physical
vessel-inventory calculation. Cold-start thermal inventory, trip recovery,
operator-display integration, and coupled flare-header feedback remain outside
this batch.


## HIPPS pressure-protection replay

`hipps.py` takes three readings from the native V-101 vessel at each scan.
A synthetic 29 bara 2oo3 trip commands two independent stem models with 2 s
and 3 s stroke times. Every scan uses the canonical deterministic physical-step
identity derived from the case, scan interval and zero-based step index. Both act on the same feed. Each has linear Cv versus
travel and equal nominal resistance. The series-flow calculation is a
normalized resistance approximation for the wet feed. The condensate return
continues at its steady rate. Persistent pressure voting starts a separate
four-second inlet ESD stroke after a five-second escalation delay.

Inspect `hipps.json` and the five CSV traces for the healthy case at two scan
sizes, a single false-high channel under normal throughput, one stuck-open
valve, and two stuck-open valves. The traces separate trip demand, both actual
positions, feed flow, ESD travel, and protected pressure. Valve positions here
are simulated feedback; the case has no independent field switch, vendor
closure data, wave propagation or probabilistic SIL verification.
