---
title: Two-well restart into a constrained host
description: Executed NeqSim restart matrix, pressure-coupled platform choke, constrained host, Volve holdouts and evidence needed for an operating decision.
---

# Two-well restart into a constrained host

**Decision.** With fixed installed equipment, both policies encounter outlet-backflow rejection on one or more fluid/grid combinations. There is no supported field choke recommendation. At a synthetic 550 kW power limit, neither policy is feasible across both fluid models. Some peak phase rates vary substantially with mesh. The evidence supports withholding an operating recommendation.

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

Using the same well indices, choke coefficient, separator capacities and controller targets on every run changes the verdict. PR at 60 cells and 0.25 s reached the forward-only outlet guard at 3.0 s. SRK at 120 cells and 0.25 s reached it at 2.75 s. Both times precede the well shut-in at 4 s, so the choice of restart policy cannot explain those rejections. Four of the twelve installed-equipment cases reached 40 s. Eight were rejected between 2.50 and 3.00 s. All six PR cases and both 120-cell SRK cases were rejected. The [machine-readable results](data/two_well_restart_decisions.json) retain each run outcome, the blind scores and the Volve holdouts. [Moving-boundary qualification issue](https://github.com/equinor/neqsim/issues/5117)

## Longer runs and rejected physics

For the matched SRK case on 60 cells at 0.25 s, fixed opening completed 300 s with a peak riser oil rate of 18.72 kg/s; the ramp tripped the forward-only outlet-backflow check at 174.25 s. The matched 120-cell fixed case also completed 300 s at 0.25 s. Halving the timestep on 60 cells caused the fixed case to reach the same guard at 181.5 s. The conservative-energy variant rejected the baseline at 3.75 s, before either well was shut in. The study retains these failures. They require inspection of phase-specific outlet transport, physically defined reverse-flow donors or valve boundary behavior, and synchronized energy exchange at the choke. A completed 40 s trajectory cannot substitute for long-horizon qualification.

## Production observations

The June–July 2008 Volve fit was frozen before evaluating August. The held-out well-rate calculation uses measured bottomhole pressure as an input. It therefore tests a conditional inflow model.

| Well or aggregate | Training days | Held-out days | Liquid-rate MAE, Sm³/day | Training-mean baseline MAE, Sm³/day |
|---|---:|---:|---:|---:|
| F-12 | 52 | 27 | 1,058 | 1,238 |
| F-14 | 18 | 16 | 319 | 851 |
| Both producers on matched producing dates | — | 16 | 875 | — |
| F-4 injection | 41 | 24 | unavailable | 929 |

The F-12 fit overpredicted on average by 1,058 Sm³/day. F-4 has zero positive training downhole pressures in this extract. Its injection-rate history alone cannot identify injectivity or fracture margin. The optional paired-injection route recovered 5 kg/s at a 50 bar driving pressure on a separate synthetic withheld point. Independent manifold pressure, arrival temperature and injector pressure measurements remain necessary for network qualification.

## Agent and fluid evidence

The blind MCP session received only coarse results. With a 550 kW limit a deterministic challenger chose to withhold. A separate scoring session found a power violation for each policy on withheld refinements. The largest absolute coarse-to-holdout power change was 3.22 kW. Separate regressions cover an apparently feasible high-production candidate that violates a withheld load limit and a missing surge map.

SRK and PR use the same synthetic composition. They are sensitivity cases. Laboratory qualification requires measured observations for the feed and separate validation of the relevant phase behavior and transport properties. The produced-water preflight returned six missing Ba/SO4 and mixed-ion entries. The resulting evidence supports requesting parameters and measurements before predicting precipitated mass.

[Integration acceptance and remaining work](https://github.com/equinor/neqsim/issues/5114)
