# Stratified pressure-moment coupling: analytical operator record

This record qualifies the default-off coupling of the existing public circular-stratified
pressure moments into `TwoFluidConservationEquations` momentum traction and total-energy work.
The published remote base is `0ed75c2f10d9030ec8d9270c51e4ade28f5603f8`. Local validation began on
`a049b0c147a7dc27999f4182fe8f7c5041614daa`; the two intervening default-branch commits changed
only plant-layout tooling and top-level compressor navigation, so every source, test, and model
document touched by this record is byte-identical on the published base.

For phase $k$, the independently qualified geometry supplies signed moment $J_k$ in m3 and
area $A_k$ in m2. The operator applies

$$F_k=\rho_k g\cos(\theta)J_k\quad[\mathrm N],\qquad
  \dot W_k=\frac{\dot m_k}{\rho_{k,donor}}\frac{F_k}{A_k}\quad[\mathrm W].$$

The work uses the exact signed mass donor selected by advection. The thermodynamic pressure is
the interface pressure. The reference is analytical geometry and finite-volume conservation;
it is not a proprietary trace or fitted dataset.

## Frozen acceptance matrix

`StratifiedPressureMomentCouplingTest` executes six tests:

| Gate | Control | Acceptance |
| --- | --- | --- |
| Traction and work | Uniform 40% gas / 60% oil at -2 and +2 m/s | Every inlet/internal/outlet force matches `rho*g*J`; enabled-minus-disabled energy flow matches `velocity*(Fg+Fl)` within 1e-7 W |
| Conservation | Three closed cells at 20/50/80% oil | Length-integrated phase momentum rate equals inlet force minus outlet force within 1e-8 N; mass and stationary energy rates are zero |
| Phase limits | All gas, all oil, all water; vertical mixed section | Present-phase full-section moment matches `+/-rho*g*pi*D^3/8`; absent phases are exactly zero; normal-gravity control is below 1e-11 N |
| Model boundary | Gas/oil/water and a diameter step | Both reject explicitly; no second interface or face geometry is invented |
| Transaction | Valid and rejected trial evaluations | Accepted force ledger is restored bit for bit |
| Compatibility/API | Disable after one enabled evaluation; `TwoFluidPipe` switch | Disabled rates return bit for bit; force ledger becomes empty; public state is explicit and default false |

The focused command passed all six tests on the working tree above that exact base:

```bash
./mvnw -q -Dtest=StratifiedPressureMomentCouplingTest test
```

The disabled branch returns before geometry evaluation and allocates no force-face arrays. The
existing retained geometry timing bounds only the explicit moment query; no end-to-end timestep
speed claim is made. Broader test, formatting, documentation and hook results belong in the PR
ledger and exact-head CI because those results can change with the final published tree.

## Limits

The qualified face has one gas/liquid interface and one constant circular diameter. Simultaneous
positive oil and water and diameter steps are unsupported. This record has no elapsed time,
pool-level history, cooldown, phase-change rate, restart ramp, slug frequency, moving pig, receiver
capacity, field trace or commercial-simulator comparison. It therefore closes a conservative
traction/work prerequisite only and cannot qualify retained pools, severe slugging, restart or
pigging by itself.
