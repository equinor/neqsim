# Well-to-platform pressure protection

This executable synthetic case uses NeqSim 3.29.0 Java equipment from a compiled source checkout. One 120 bara pressure source supplies a native WellFlow, DHSV, subsea tree, choke, production manifold and two series HIPPS valves. A 5.4 km gas–oil–water TwoFluidPipe ends at the platform inlet and a three-phase separator. The steady SRK feed composition and 60-cell route geometry are defined in the adjacent tieback.py. The source delivers 20 kg/s at the design point. SubseaTree publishes zero production through a closed master valve, wing valve or choke. SubseaManifold updates existing production and test outlet objects when routing changes or all contributing wells shut in.

## Inputs and scope

| Location or function | Synthetic basis |
| --- | --- |
| Fortified spool, 0–800 m | Design and allowable 130 bara |
| LC1, 800–3650 m | Design 72 bara, transient allowable 73 bara |
| LC2 and riser, 3650–5400 m | Design 80 bara, transient allowable 82 bara |
| Subsea PSD | PST-101 at the protected inlet face, 70 bara, 3 s to tree seat and 6 s DHSV travel |
| HIPPS | Separate PST-201A/B/C tags at the same inlet face, 70.6 bara, live 2oo3 vote, nominal 2 s and 3 s strokes |
| Riser | PST-301 at the outlet, 58.5 bara. Host communication case requests subsea shutdown on a riser pressure demand |
| Receiving vessel | 54.4 bara for route blockage. Gas-export blockage starts from 48 bara with ideal upstream-pressure control, a 49.5 bara high-high switch, 2 s to inlet isolation and 50.5 bara gas-space PSV |
| PSV-302 to flare | 500 mm² area, discharge coefficient 0.95, 3% blowdown, 3 bara fixed header. Gas-space relief only |

Pressure limits are input on a common absolute-pressure basis. The model records the highest cell-centre or terminal-face pressure, location, time and margin in each zone. The simple fully rated flag compares the specified source and design pressures at the same datum. Engineering design needs local hydrostatic and external pressure corrections, credible sources from both ends, installation/transient loads and mechanical resistance. No burst strength is inferred from a line class.

The well and choke are represented dynamically by a squared-pressure IPR and a quadratic series resistance frozen at the 60-cell design point. Restriction varies with measured valve travel. Tree isolation retains full potential throughput until its seating deadline, and actuating the DHSV can independently reduce throughput during travel. The native pipe integrates distributed mass and momentum. Blockage cases impose a seated platform valve at 2 s. A native host is advanced only in the two gas-export blockage cases. Those cases transfer the accepted interval discharge with the outlet composition, check host mass closure and evaluate the opening required for ideal riser pressure regulation. Liquid outlets continue at their initial capacities until host isolation. The numerical flash carrier on an isolated inlet is 1e-9 kg/s.

Taps are on the **protected side** of the HIPPS in this fixture. The layout in which a detector remains exposed to the high-pressure source after a valve seats needs a separate upstream inventory and tap. Independent tags and logic do not establish physical or functional independence. Riser multiphase PSV capacity, common utilities, leaking seats, flare-network dynamics, manual reset/restart, SIL assessment and external field validation remain qualification work under [#5124](https://github.com/equinor/neqsim/issues/5124).

## Run and interpret

Use the checkout classpath instructions in [the companion study](README.md#run) and Python with JPype1. The following commands are run from the repository root.

```sh
python examples/production-decision/tieback_protection.py --case blocked --output blocked.json
python examples/production-decision/tieback_protection.py --case all_isolation_failed --fully-rated --output rated.json
python examples/production-decision/tieback_pid.py new-design-package
```

The default duration is 90 s. Use 45 s for host_gas_blocked, host_psv_only and choke_failed_open when reproducing the regression matrix. Set --basis to a JSON object with any of psd_bara, hipps_bara, host_riser_bara, tree_seat_s, dhsv_stroke_s, hipv_a_stroke_s, hipv_b_stroke_s and zones. Each zone must specify name, end_m, design_bara and allowable_bara. End chainages must coincide with mesh faces and cover the route. The drawing exporter takes the same basis. The fully rated comparison changes the mechanical limits only. Choose all_isolation_failed to assess the same trajectory with no upstream isolation credit.

To replay all ten scenarios, the time and grid refinements, and the fully rated comparison, use a new output directory. Omit --run to verify existing receipts.

```sh
python examples/production-decision/verify_tieback_protection.py --run tieback-cases > qualification.json
```

The coupled well rate is initialized independently on each mesh with the same installed resistance. Both meshes run without a normal-operation trip and both detect an LC1 exceedance with isolation unavailable. The verifier reports separate functional and numerical verdicts. A **numerical HOLD** means the 0.5 bar zone-peak or 0.5 s timing tolerance is exceeded. The LC2 peak remains sensitive to the outer time step. Selecting a reinforced length or final setpoints requires a time/space-converged, independently benchmarked pressure envelope. The 90 s results cover only the simulated interval.

The DEXPI 2.0.1 physical design projects the well as an inlet boundary, the tree and active manifold branch as individual valves, the hydraulic route as the named FL-201 line, and each PSV as a side branch. The output contains a tag register, cause/effect connections, XML and read-back evidence. The P&ID sheets trace the series valves and each relief takeoff through numbered continuations. The receipt reports material drawing coverage separately and lists the attachment and signal continuations that keep overall drawing coverage on HOLD. Alternative rate/phase models, measured response curves, verified hydraulic boundaries and the project SRS are required for asset use.
