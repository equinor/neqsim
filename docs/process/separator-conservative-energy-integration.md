---
title: Conservative separator transient-energy integration
description: Opt-in accumulated-target energy integration, lifecycle contract, and V101 validation procedure for dynamic Separator calculations.
---

# Conservative separator transient-energy integration

`Separator` provides an explicit, opt-in accumulated-target policy for dynamic
energy integration:

```java
separator.setConservativeTransientEnergyIntegrationEnabled(true);
```

The default is `false` and preserves the legacy equation and trajectory. The
policy is currently qualified only for the concrete two-outlet `Separator`;
enabling it on an unqualified subclass, including `ThreePhaseSeparator`, throws
`UnsupportedOperationException`.

## Discrete contract

At the first accepted step after enable, reset, explicit inventory
initialization, or steady-state reevaluation, the policy takes the published
vessel internal energy as the epoch baseline:

\[
U^*_0 = U_0.
\]

For accepted physical step \(n\), it forms one target from the exact prior
target and the same native boundary and heat rate already consumed by the
separator:

\[
U^*_{n+1} = U^*_n + \Delta t_n
\left(\dot H_{in,n} - \dot H_{gas,n} - \dot H_{liq,n} + \dot Q_n\right).
\]

The existing single VU flash receives \(U^*_{n+1}\). Its accepted local
numerical defect remains visible in the published inventory and optional
`SeparatorTransientEnergyBalanceReport`, but that defect is not used as the
next integration baseline. The policy adds no solver iteration, changes no VU
acceptance tolerance, and performs no clipping. Call
`getConservativeTransientEnergyTargetJ()` to read the last accepted exact target;
it returns `NaN` while disabled or before the epoch is baselined.

`resetConservativeTransientEnergyIntegration()` starts a new unbaselined epoch
without disabling the policy. Empty/depleted or property-initialization skips
also invalidate the target so a later step cannot continue from a stale
reference. The enabled flag and exact target participate in native transient
snapshots, rollback, replay, cloning, and Java serialization.

## Diagnostics and validity

Energy tracking is an independent observational option. Enabling or disabling
`setTransientEnergyBalanceTrackingEnabled` must not change a conservative-policy
trajectory. Keep the tracker enabled when numerical evidence is required: it
reports the local VU defect, published energy, cumulative debt, maximum debt,
and non-VU correction separately. The independent ordered-boundary audit remains
the acceptance oracle.

The public V101 fixture accepts `--conservative` and retains separate process
response and energy verdicts. Recompute both tracker states at the frozen
timesteps:

```sh
uv run --project devtools --locked python \
  src/test/resources/neqsim/process/separator-energy-audit/run_energy_audit.py \
  --case control --duration 300 --dt 0.25 --tracking both --conservative \
  --output target/separator-energy-audit/conservative-control-025
```

Repeat for control `dt=0.5 s` and shutdown `dt=0.1 s` and `0.05 s` as declared
in `full-recompute.json`. Do not relax the frozen `1e-5` maximum-relative
cumulative-energy criterion. Require exact tracker off/on physical equality,
native-ledger/independent-oracle agreement, deterministic repetition, physical
bounds, response criteria, and measured runtime.

This is a numerical integration option, not a process-design or safety approval.
It does not establish hazard completeness, SIL, independence, voting, detector
coverage, relief adequacy, or construction/operating acceptance. Those decisions
retain their separate reviewed evidence requirements.
