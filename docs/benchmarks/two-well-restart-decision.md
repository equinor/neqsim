---
title: Two-well restart into a constrained host
description: Executed NeqSim restart matrix, pressure-coupled platform choke, constrained host, Volve holdouts and evidence needed for an operating decision.
---

# Two-well restart into a constrained host

**Decision.** Both choke policies now complete the 40 s installed-equipment restart on every tested fluid/grid combination. At 550 kW the sampled power limit is exceeded. At 600 kW the predicted production difference is below 0.004 kg, while the SRK post-restart oil peak remains mesh-sensitive. The evidence supports withholding a field recommendation.

The [runnable study and input definitions](../../examples/production-decision/README.md) use native NeqSim classes. The underlying physics revision was `26caacd2523667c701226cb9a181bfe8d448f8b9`. Two policies, two fluid descriptions and three discretizations produced 12 complete trajectories **when each case was matched to its own initial operating point**. This recalibration changes the well indices, choke resistance and separator capacities between cases. Those comparisons characterize sensitivity under matched starting conditions. The default installed-equipment workflow freezes those quantities and uses a coupled steady initialization. The reference SRK trajectory was repeated in a fresh JVM with identical time-series values. Each case includes shut-in, restart and a separator backpressure setpoint step. One-second compressor/recycle snapshots resolve the sampled loads only.

| Quantity over 40 s | SRK, fixed | SRK, ramp | PR, fixed | PR, ramp |
|---|---:|---:|---:|---:|
| Well inflow integrated over time, kg, 60 cells at 0.25 s | 753.721 | 753.718 | 753.807 | 753.805 |
| Peak sampled compressor power, kW, 60 cells at 0.25 s | 577.09 | 577.09 | 550.21 | 550.21 |
| Minimum screening surge margin across the three runs, % | 13.35 | 13.03 | 10.37 | 10.08 |
| Peak post-restart riser oil rate, kg/s, 60 cells at 0.25 s | 12.46 | 12.48 | 12.62 | 12.67 |
| Same peak, 120 cells at 0.25 s | 14.81 | 13.05 | 12.43 | 12.49 |
| Same peak, 60 cells at 0.125 s | 12.45 | 12.47 | 12.62 | 12.66 |

The coarse SRK case initially drew 534.58 kW at the first one-second sample. Its peak of 577.09 kW occurred at the end of the shut-in interval, before the two policies diverged. The choke trajectory after restart cannot resolve this earlier constraint. With 600 kW available, all twelve cases passed the sampled power, liquid-level, interface and nominal surge limits. The difference between the two policies was under 0.004 kg of cumulative inflow over 40 s. This is below the stated 2 kg comparison tolerance.

The maximum per-step named-component pipe residual across the matrix was below 4.7 × 10⁻¹¹ kg. The steady recycle energy residual was below 4.5 × 10⁻⁹ W. These checks have different physical boundaries. Neither establishes full-facility transient energy closure. A fourth, 240-cell fixed SRK case gave a 12.80 kg/s post-restart oil peak, after 12.46 and 14.81 kg/s on 60 and 120 cells. The sequence is nonmonotonic.

## Installed-equipment replay

The first installed-equipment implementation completed four of twelve cases. Its 1e-5 bar and kg/s outer-loop tolerances could move the inlet or outlet past the native pipe's first-step boundary identity threshold. For the 60-cell PR case, the original initialization stopped after eight iterations with a 2.13e-6 bar choke residual and a 6.37e-7 kg/s well residual. A direct inspection after the first step confirmed that the native steady momentum correction was inactive. Tightening the installed network solve to 1e-10 in both units took sixteen iterations. The residuals fell to 9.64e-13 bar and 4.31e-11 kg/s and the correction was active. The same forward-only boundary and installed ratings were used in both tests.

All twelve corrected installed cases reached 40 s. At 60 cells and 0.25 s, peak sampled power was 577.09 kW with SRK and 551.03 kW with PR. The fixed SRK post-restart oil peak moved from 12.46 to 13.52 kg/s at 120 cells. A 240-cell fixed-opening run gave 12.63 kg/s, so the spatial sequence is nonmonotonic. For the ramp the peak moved from 12.48 to 14.45 kg/s on 60 and 120 cells. Both exceed the 0.5 kg/s refinement tolerance. PR policy gains remained unresolved. The [machine-readable results](data/two_well_restart_decisions.json) preserve both handoff versions, separate matched-basis cases and the rejected conservative-energy control. [Moving-boundary qualification issue](https://github.com/equinor/neqsim/issues/5117)

## Longer runs and rejected physics

With the standard energy settings, eight of the twelve installed-equipment cases reached 300 s. All six PR runs completed. Their post-restart oil peaks spanned 13.21–13.35 kg/s. On the 120-cell grid the synthetic surge screen reached 9.980% at 50 s for fixed opening and 9.971% at 47 s for the ramp, against a 10% minimum. This marginal breach appears after the 40 s screening window. It measures sensitivity to an assumed limit and requires an installed map to interpret. With SRK, the 120-cell fixed case and the 60-cell, 0.125 s ramp completed. The 60-cell fixed case reached the outlet guard at 173.50 s with a 0.25 s step and at 173.625 s with a 0.125 s step. The 60-cell ramp reached it at 174.25 s with a 0.25 s step. All three ended with negative terminal gas velocity and positive net transfers of all three phases over the flagged interval. At 228 s the 120-cell SRK ramp delivered only 5.35e-27 kg/s of fresh gas to recompression and modeled zero gas export. The recycle itself reported convergence. The earlier relative mass check labeled this a balance failure; the corrected receipt identifies an unavailable online compressor duty and records both rates. A gas-starved snapshot cannot establish a surge margin or simulate a machine trip. None of these rejected cases was used to rank policies.

On the corrected installed SRK 60-cell conservative-energy case, the first flagged interval was 3.50–3.75 s. Terminal water velocity was −0.01034 m/s while net water transfer through the outlet over that interval was positive (0.003087 kg). Interval transport and end-cell velocity describe different quantities. This mode still lacks a qualified coupled energy balance.

For the matched SRK case on 60 cells at 0.25 s, fixed opening completed 300 s with a peak riser oil rate of 18.72 kg/s; the ramp tripped the forward-only outlet-backflow check at 174.25 s. The matched 120-cell fixed case also completed 300 s at 0.25 s. Halving the timestep on 60 cells caused the fixed case to reach the same guard at 181.5 s. The conservative-energy variant rejected the baseline at 3.75 s, before either well was shut in. The study retains these failures. They require inspection of phase-specific outlet transport, physically defined reverse-flow donors or valve boundary behavior, and synchronized energy exchange at the choke. A completed 40 s trajectory cannot substitute for long-horizon qualification.

## Production observations

The June–July 2008 Volve fit was frozen before evaluating August. The held-out well-rate calculation uses measured bottomhole pressure as an input. It therefore tests a conditional inflow model.

| Well or aggregate | Training days | Held-out days | Liquid-rate MAE, Sm³/day | Training-mean baseline MAE, Sm³/day |
|---|---:|---:|---:|---:|
| F-12 | 52 | 27 | 1,058 | 1,238 |
| F-14 | 18 | 16 | 319 | 851 |
| Both producers on matched producing dates | — | 16 | 875 | — |
| F-4 injection | 41 | 24 | unavailable | 929 |

The F-12 fit overpredicted on average by 1,058 Sm³/day. F-4 has zero positive training downhole pressures in this extract. Its injection-rate history alone cannot identify injectivity or fracture margin. The optional paired-injection route recovered 5 kg/s at a 50 bar driving pressure on a separate synthetic withheld point. Across the entire source CSV, F-4 had 2,830 positive injection-volume rows and F-5 had 2,558. Neither well had a paired positive downhole or wellhead pressure on those rows. Independent manifold pressure, arrival temperature and injector pressure measurements remain necessary for network qualification. The event holdout tool now accepts source-tagged pressure, temperature, level and interval-averaged phase-rate measurements. An analytical test exercised point and interval alignment and missing samples. No independent field restart event has been supplied.

## Agent and fluid evidence

The blind MCP session received only coarse results. With a 550 kW limit a deterministic challenger chose to withhold. A separate scoring session found a power violation for each policy on withheld refinements. The corrected installed case gives the same abstention at 550 kW. The largest absolute coarse-to-holdout power change was 2.07 kW with fixed equipment and 3.22 kW on the matched basis. Separate regressions cover an apparently feasible high-production candidate that violates a withheld load limit and a missing surge map.

An optional native compressor map now solves for the speed needed to deliver 100 bara in each sampled host calculation. The bundled three-speed synthetic chart completed a separate 21 s SRK case, giving 9,882–10,080 rpm and 477.65–555.47 kW. These are sampled results for the example machine. The two-fluid matrices above use the original fixed-efficiency host. A negative control excluded the actual suction pressure from the declared map envelope and the runner rejected the first sampled duty. Site application requires the installed map and its suction-condition basis.

SRK and PR use the same synthetic composition. They are sensitivity cases. Laboratory qualification requires measured observations for the feed and separate validation of the relevant phase behavior and transport properties. The produced-water preflight returned six missing Ba/SO4 and mixed-ion entries. The resulting evidence supports requesting parameters and measurements before predicting precipitated mass.

[Integration acceptance and remaining work](https://github.com/equinor/neqsim/issues/5114)

### First outlet reversal on the conservative-energy route

On master `0eacf4bac9646a0c2839aa95392e38d67ddb51b2`, the installed SRK fixed-opening case
with 60 cells, a 0.25 s outer step and conservative energy recovery rejected the interval
(3.50, 3.75] s. Read-only diagnostic instrumentation at the first transition in
`calcOutletFlux` and the substep acceptance point identified the only Euler stage in the
accepted substep starting at 3.688795864 s (length 0.015725680 s). The terminal face
pressure was 56.406713 bara and the water velocity was −0.000823 m/s. The gas and oil
velocities were +7.51484 and +0.58670 m/s. The water area fraction of 0.058949 and
density of 993.55 kg/m³ give a signed water flux of −0.001562 kg/s before the one-way
rule and a pressure traction of 10.783 kN. The net water transfer reported by the pipe
over the full outer interval was +0.003087 kg. The separator did not advance across
this rejected handoff.

| Outlet pressure used in the pre-event diagnostic | First rejected outer interval (s) |
| --- | --- |
| Live separator plus lagged choke loss | (3.50, 3.75] |
| Held initial separator and initial loss | (3.75, 4.00] |
| Live separator plus held initial loss | (2.75, 3.00] |
| Held initial separator plus lagged choke loss | Reached 4.00 without rejection |

Only the pressure imposed on the pipe was changed in the three diagnostic replays.
All stopped by the first well event at 4 s. Holding the outlet face delays reversal
for this case. Its persistence with a fixed face warrants examining the internal
handoff and terminal pressure/momentum balance before changing the donor model.
