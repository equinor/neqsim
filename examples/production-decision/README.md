# Two-well restart into a constrained host

The study asks whether a platform inlet choke should stay at 58% or restrict flow temporarily as a second well restarts. It returns numerical evidence for each decision quantity and can withhold a ranking. The integration acceptance work is tracked in [#5114](https://github.com/equinor/neqsim/issues/5114).

## Physical case

Two pressure-dependent NeqSim `WellFlow` units use the squared-pressure productivity law. Synthetic reservoir pressures are 120 and 117 bara. The installed indices are 8.74018595e-5 and 4.04655479e-5 MSm³/(day·bar²). They reproduce 14 and 6 kg/s at the 69.52643 bara inlet pressure of the SRK reference case. Both wells have the same fluid and a prescribed 308.15 K inlet temperature. Each well sees the previous accepted inlet-face pressure. The well pressure loss is lumped in the effective index. Detailed tubing hydraulics, skin evolution and reservoir depletion lie outside this fixture.

The 5.4 km three-phase `TwoFluidPipe` terminates at the platform choke. A forward-flow resistance gives 2 bar at 20 kg/s, 171.53793 kg/m³ and 58% opening. The installed case solves the well-rate and choke-pressure residuals to absolute tolerances of 1e-10 kg/s and 1e-10 bar. This is inside the native pipe's first-step steady-boundary identity test for this fixture. The receipt records the iteration count and both signed residuals. A genuine boundary disturbance still reaches the pipe unchanged. Each step applies the previous separator pressure plus the resistance drop, filtered with a 1 s time constant. The `ThrottlingValve` flashes the accepted stream at the separator pressure. Pressure, total liquid and oil–water interface PI loops act on the three vessel outlet fractions. Well B shuts in at 4 s and restarts at 12 s. The ramp case moves to 54% at restart and returns to 58% over 16 s. The pressure-controller setpoint rises 0.25 bar at 20 s.

At one-second intervals a NeqSim compressor raises the withdrawn gas to 100 bara at 78% isentropic efficiency. A splitter returns 15% of discharge through a valve and cooler to the suction mixer. Each snapshot solves the recycle and checks export against fresh gas. Host power is sampled. The 325 actual m³/h surge-flow screen assumes constant surge flow over the sampled duty. Compressor rotor dynamics, a vendor map and a dynamic anti-surge valve require separate evidence. Host constraints are evaluated on the calculated trajectory. A power violation does not automatically reduce the separator withdrawal.

## Run

Use the NeqSim checkout being qualified, JDK 17, Python 3.10 or later and JPype1. From the repository root, create a classpath with the current compiled classes and the Maven runtime dependencies. On POSIX systems:

```sh
./mvnw -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/restart-classpath.txt
export NEQSIM_CLASSPATH="$PWD/target/classes:$(cat target/restart-classpath.txt)"
python examples/production-decision/matrix.py restart-runs --revision "$(git rev-parse HEAD)"
```

The output directory must be new. The default matrix runs SRK and PR, two choke policies and three discretizations per combination. The mesh/timestep pairs are 60 cells/0.25 s, 120/0.25 and 60/0.125. By default every case uses the same installed well indices, choke resistance, separator outlet capacities and controller targets. A rejected trajectory has a reason and no accepted result; it remains in the manifest and prevents a supported ranking. Run the matrix with `--basis matched` to repeat the sensitivity experiment in which each discretization and fluid is instead calibrated at its own initial operating point. The evaluator rejects mixed equipment bases. `tieback.py --help` exposes individual runs, 240-cell checks, longer horizons and conservative-energy recovery. Backflow clamping rejects the forward-only case. A structured rejection records the interval, imposed face and separator pressures, opening, terminal-cell velocities and actual phase mass transferred during the interval. A positive interval transfer can coexist with a momentary phase clamp within an internal substep. The first affected phase and internal stage cannot be inferred from end-cell velocities alone. Compare only receipts from the same handoff protocol and event. Native named-component and phase residuals come from accepted pipe steps. The optional energy audit combines pipe and vessel inventories at the three separator outlets. The compressor recycle has a separate steady first-law residual. A missing facility-wide residual is encoded as null.

### Agent use and blind evaluation

The native MCP `runPipeline` analysis `wellToTopsideSlugControl` describes its fixed-boundary evidence in `quantityEvidence`. The local study server exposes the two-well comparison. Install the optional `mcp>=1.26,<2` Python package and launch the following command from an MCP stdio client:

```sh
NEQSIM_RESTART_MANIFEST=/absolute/path/to/restart-runs/manifest.json \
NEQSIM_RESTART_MODE=challenge python examples/production-decision/mcp_server.py
```

The challenge server exposes only `restart_challenge`. Give it a power limit, optionally omit the surge map, and save the agent's choice. Keep the raw files and review server outside the blind agent's workspace. Launch a separate server with `NEQSIM_RESTART_MODE=review` to call `score_restart_choice` or `restart_quantity_evidence`. The scorer checks held-out constraints, worst-case production regret and the appropriateness of abstaining. The numerical holdouts are alternative discretizations of the synthetic case. They supply no field ground truth. The CLI exposes the same operations through `decision.py --help`.

### Fluids and produced water

The default feed has methane/ethane/propane/n-pentane/n-heptane/n-decane/water mole fractions of 0.42/0.04/0.03/0.07/0.07/0.05/0.32. SRK and PR use classic mixing. To replay a frozen candidate, pass `--fluid-deck` with an E300 deck containing its composition and water. The receipt includes the deck hash. A separate `--density-evidence` JSON can bind measured phase-density observations to the reported feed composition hash. Provide `fluid_sha256` and an `observations` list. Each observation has `id`, `source`, `phase`, `temperature_K`, `pressure_bara`, `density_kg_m3`, `tolerance_kg_m3` and a `role` of `calibration` or `holdout`. Freeze and independently review the observations and tolerances before evaluating candidates. Acceptance covers only the supplied density observations. The upstream [PVT study workflow](../../docs/pvtsimulation/pvt_fluid_study_report.md) owns laboratory calibration and export fidelity.

The restart run also exercises the native PHREEQC/Pitzer parameter preflight for a separately defined charge-balanced Na–Ba–Cl–SO4 brine. Its missing interactions appear in the receipt. A barite precipitation mass requires the complete qualified parameter set.

### Observed production and injection

`field_history.py volve.csv` evaluates the public [Volve daily production history](https://www.equinor.com/energy/volve-data-sharing). The [input CSV snapshot](https://github.com/Nshakib30/Oil_production_ML/blob/c7ecca11a0c43118bbfef7e46774ca49f6589ce0/Volve%20production%20data-Daily%20Production%20Data.csv) has Git blob `50fd03d46c117105fd634aaea262c2056a86452e`. Retain the source column names. The workflow trains from June through July 2008 and scores August. It selects positive production/injection and at least 23.5 on-stream hours per day. Liquid rate is daily stock-tank oil plus water corrected for on-stream hours. For producers, the NeqSim linear IPR takes measured flowing pressure as input. The fitted pressure intercept uses the same pressure basis as the source gauge. It is an effective intercept, not a reservoir-pressure measurement. Dropping successive blocks of seven training observations shows how sensitive the fitted productivity index is to the period.

F-4 has rates but lacks paired downhole pressure in this extract. The evaluator reports a mean-rate baseline and the missing-measurement count. For separate measured single-zone injection data, use `--paired-injection path.csv` with columns `date,role,bottomhole_bara,reservoir_bara,water_kg_s,fracture_bara,source`. Use ISO dates and the same two role labels as above. This path fits mass injectivity from earlier pressure differences, replays withheld dates through `WellFlow.addLiquidInjectionZone`, and returns prediction errors and fracture margin. Flowing pressure and rate observations alone cannot qualify host manifold pressure, thermal arrival, or coupled injector/producer interference.

Run the decision/validation regressions with `python -m unittest discover -s examples/production-decision`. See [the executed benchmark](../../docs/benchmarks/two-well-restart-decision.md) for results and outstanding qualification.


### Comparing a held-out restart event

The runner records end-of-step gathering-manifold pressure (the pipe inlet face, bara) and arrival temperature (the pipe outlet stream before throttling, K), in addition to vessel states and interval-average riser rates. Assign the event a stable identifier using `--event-id`. Match the physical geometry, fluid, boundary history, actuator trajectory and clocks to that event before comparing observations. Here the feed temperature is prescribed and the default fixture has no calibrated thermal model. A measured temperature departure tests that assumption.

Use `restart_observations.py RECEIPT.json OBSERVATIONS.json` on a frozen run. The observation JSON has `event_id`, `policy`, `origin` (`field`, `laboratory` or `synthetic`), `source`, and an `observations` list. Each row has `tag`, `quantity`, `unit`, `start_s`, `end_s`, `value` and a positive absolute `tolerance` in the stated unit. Choose tolerances from instrument accuracy and study requirements before viewing predictions. Supported end states are `manifold_bara`, `separator_bara`, `arrival_K`, `level_m` and `interface_m`. Give these equal start and end times. For `riser_gas_kg_s`, `riser_oil_kg_s` and `riser_water_kg_s`, give the original averaging interval in seconds and kg/s. The comparator integrates the modeled mass rates across that window and divides by its duration. It preserves off-grid or unavailable samples as missing and reports paired counts, bias, MAE and individual tolerance exceedances. The source-file hash identifies the observations used. Agreement applies only to the supplied channels and windows.

Configure `NEQSIM_RESTART_OBSERVATIONS` on the **review** MCP server to enable `restart_observation_evidence`. It compares each matching run and retains numerical rejections. The blind challenger receives coarse modeled evidence. Keep the independent observation manifest, review server and raw receipts outside its workspace. To run the native Peng–Robinson handoff regression too, set `NEQSIM_CLASSPATH` before invoking unittest discovery.

### Speed-solved recompression

Pass `--compressor-chart PATH` to the case or matrix to solve for compressor speed at the 100 bara discharge duty with the native NeqSim compressor chart and closed recycle. The [example chart](synthetic_host_chart.json) is synthetic. Replace the speed lines with approved data for the installed machine. Use the NeqSim JSON `speedCurves` format with at least three distinct RPM curves and three ascending actual suction flows per curve. Use kJ/kg head and percent polytropic efficiency. Include `origin` (`vendor` or `synthetic`), `source`, `flowBasis: actual_suction_m3_h`, `minimumFlowBasis: surge` and `suction_envelope` bounds for absolute pressure in bara, temperature in K and molar mass in kg/mol. The user supplying a chart must establish its gas applicability and confirm that each first flow point represents surge. Corrected-flow maps require conversion to this basis before use.

For every sampled duty, the study reports speed, native head, suction conditions, actual volume flow and the surge flow interpolated between adjacent speed lines. It rejects extrapolated speed, flow outside either bracketing line, suction conditions outside the stated envelope or a missed discharge duty. The map hash and origin travel with the case. The 325 m³/h screen remains available when a chart is absent. Both routes evaluate steady host snapshots with 15% recycle. Dynamic surge response requires machine/driver inertia and an anti-surge control loop.

For injection holdouts, the linear single-zone replay retains observations at or above fracture pressure as outside its domain. The error statistics identify how many eligible rows contributed. Predictions outside the calibration pressure span carry an extrapolation flag.
