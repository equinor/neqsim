# Safety Shutdown prescribed valve travel — SS-02a

Experimental single-line acoustic boundary, campaign [#4707](https://github.com/equinor/neqsim/issues/4707).
Source baseline: `8b89915c2465b1b27cd8e4fd2ffd0a2bc620860f`. Exact delivered head and commands are recorded in the campaign ledger and draft PR. No asset or vendor measurements are used.

## Reproduce

Run `./mvnw -q -Dtest=AcousticValveBoundaryTest,WaterHammerKernelTest,WaterHammerPipeTest test` after the repository Maven bootstrap. `AcousticValveBoundaryTest.studyInputAndOutput` reads [input.json](input.json), executes `WaterHammerStudy.run`, checks synchronized actual position/effective Kv and retains output. The other tests write the full grid/time/closure matrix and a separate physical-volume diagnostic under `target/ss-physical-valve-*.json`. Committed results preserve one executed run; wall times depend on host/JVM and are not portable performance assertions.

## Public synthetic case and provenance

SRK/classic pure water, 298.15 K, 20 bara, 36000 kg/hour, physical L=1000 m, ID=0.2 m, 101 nodes, declared wave speed 1000 m/s. The independent receiver is 1 bara. Capacity is a synthetic selection from the steady liquid law, not a fit to manufacturer or plant data: full Kv=8.261300311152434, half-position Kv=20% of full capacity. Position/capacity uses the existing strict monotone cubic table. Actual travel is linearly interpolated through (0 s,1), (0.04 s,0.5), (0.1 s,0), following command at0.013 s plus0.01 s delay. The measured-delay input API is implemented; the example delay is synthetic.

Kv uses reference water density1000 kg/m3 in Q=Kv/3600 sqrt(deltaP_bar/(rho/1000)). US Cv=1.156 Kv retains the library conversion. The [Spirax Sarco capacity definition](https://www.spiraxsarco.com/learn-about-steam/control-hardware-electric-pneumatic-actuation/control-valve-capacity) gives1.156099; the library rounding is about0.0086%. Its [water sizing guide](https://www.spiraxsarco.com/learn-about-steam/control-hardware-electric-pneumatic-actuation/control-valve-sizing-for-water-systems) discusses the liquid law and physical cavitation limitations. These are definitions, not independent valve test data. No project-adopted IEC/ASME/DNV edition or sizing/pressure-recovery coefficient is applied; no standards compliance claim.

## Frozen verification and explicit physical verdicts

| Check | Criterion / interpretation | Verdict |
| --- | --- | --- |
| Initial capacity consistency | 1e-9 +1e-6 abs(Q) m3/s; inconsistent declared capacity rejects | PASS |
| Capacity law / passivity | flow residual <=1e-10 m3/s; Q times pressure drop nonnegative; zero capacity exactly closes | PASS |
| Command/delay/travel knots | exact physical knot within1e-12 s; internal versus explicit interval pressure1e-8 Pa / flow1e-12 m3/s | PASS |
| Local serialization/replay | identical next pressure/flow arrays | PASS |
| Rapid complete closure | Joukowsky amplitude within1%; 51/101/201/401/801 nodes, full/half Courant | PASS |
| Physical nodal storage versus boundary exchange | original1e-9 m3 target with smooth travel | **FAIL** |
| Zero-friction discretization identity | independently derived defect matches within1e-9 m3 | PASS; this does not pass conservation |
| Slow-closure waveform | grid/time characterization; no fitted acceptance or experimental data | NOT_EVALUATED as converged physical prediction |
| Integrated detection/logic/actuator, pump trip/disc slam, junctions | separate dependencies / owners | NOT_EVALUATED |
| Global rollback, physical UUID idempotency, cavitation, support stress | not supplied by the prescribed boundary | NOT_EVALUATED |
| Asset/SIL/SRS/proof-test/support approval | actual data and accountable assessment absent | NOT_EVALUATED |

The first smooth-travel check with frozen friction and half Courant produced boundary volume0.005474144007596436 m3 and stored volume0.005499092514930641 m3; residual2.4948507334205e-5 m3 (about25 mL). Its1e-9 m3 target was not relaxed. It exposed an existing nodal MOC continuity limitation that the prior instantaneous-closure test did not reveal.

For the independent ideal diagnostic only, the test sets frozen friction exactly zero after consistent initialization; the resulting nonuniform initial pressure is a valid linear-PDE initial condition. This is private test-state seeding, not an operational fluid/property fallback. Summing the nodal MOC continuity update gives per-step defect `(dx/a-dt)/2 * delta(Qin-Qout)`. The test compares this independently derived identity with measured storage/exchange, and writes the unchanged physical target and **FAIL** verdict. No state, pressure, inventory or boundary flux is corrected. A passing JUnit identity check is not a physical conservation pass. A conservative kernel increment remains required before coupled-inventory qualification.

Frozen density/viscosity/wave speed/friction and interpolation diffusion remain. No large-decompression, choking/pressure recovery, transient phase boundary, signed receiver, pump/disc, branch, vector force or structural model is qualified here. Keep this mode experimental and the campaign open.

## Executed numerical results

OpenJDK17.0.20; Java8 language/API compilation is a separate check. One executed diagnostic run. The matrix fraction is the requested fraction of the maximum stable step; command/delay/travel knots can shorten individual substeps below unit Courant.

| Nodes | Requested full-Courant 0.02 s closure surge (bar) | Requested full-Courant 4 s closure surge (bar) | Half-Courant physical volume defect (mL) |
| ---: | ---: | ---: | ---: |
| 51 | 3.19538327 | 2.66953956 | 50.373911 |
| 101 | 3.19532617 | 2.69609194 | 25.186270 |
| 201 | 3.19530462 | 2.73179750 | 12.592830 |
| 401 | 3.19523651 | 2.74980201 | not evaluated |
| 801 | 3.19517527 | 2.75853239 | not evaluated |

The declared Joukowsky limit is3.1830988618bar. The rapid-closure peak includes finite-friction evolution; it remains within the frozen1% target. Slow-closure peak changes remain visible at the finest grids, so a converged slow waveform is NOT_EVALUATED. Full and half-Courant raw values are retained in [refinement.json](refinement.json); smaller time alone is not an accuracy guarantee. Physical volume fails the unchanged1e-9m3 target at all three evaluated grids, despite an approximately twofold reduction per grid doubling. The ideal defect agrees with the independent discrete identity to roundoff.

All30 matrix runs include complete requested intervals. Diagnostic wall times range 2.983–783.406 ms in this run, with setup outside the timed region and outlet thermodynamic updates included. These are single-fixture timings, not warmed paired baseline comparisons or a speedup claim. See [output.json](output.json) for the executed public short case, synchronized pressure/Kv/flow with frozen density and explicit NOT_EVALUATED qualification fields, and [volume-defect.json](volume-defect.json) for physical FAIL values.
