# Connected PSV response study

`AdvancedV101PsvStudy` is an opt-in extension of `AdvancedV101SafetyStudy`. The original 23-case study is unchanged. The extension keeps the same actual V100/V101 inventories, nine-component SRK/classic feed, PI loops, native ESD/HIPPS candidate/manual functions and physical boundary assumptions.

Run the repository Maven wrapper with `-Dtest=AdvancedV101PsvStudyTest test`. Set `-Dneqsim.psvStudy.output=target/advanced-psv-study` to export the exact tested native result JSON and physical CSV. The public `defaultSettings`, `buildSource`, `configureAfterSteady` and `createCase` APIs are used by these tests and the engineering/P&ID example. No previous task or Python installation is needed by the Java fixture.

## Actual relief branch

The protected V101 gas outlet feeds native splitter `20-TEE-PSV-001`. `20-PCV-FEED` supplies the existing pressure-control valve; `20-PSV-FEED` supplies actual `SafetyReliefValve` `20-PSV-001`. Its outlet `20-PSV-DISPOSAL` is a 1.2 bara fixed-pressure sink. This is not a dynamic flare/header model. PCV and PSV never share one mutable inlet stream.

Each physical interval uses a runtime-only algebraic helper to refresh both branches from current vessel pressure, temperature and composition while preserving their previous total mol/s demand, including zero. This is an initial hydraulic guess, not conserved component amounts or prescribed branch mass flow. Native PCV and PSV then calculate their real flows. The native splitter aggregates both demands before V101 consumes its one gas withdrawal. The helper has no material ports or inventory and is excluded from physical P&ID equipment. The source model retains ordinary material topology for steady initialization and engineering export.

The final steady source assigns actual throughput to the PCV and zero relief. Native steady initialization first evaluates the closed PSV from a seeded branch, then solves the final zero-relief split; no seed or epsilon is recorded as transient discharge. The PSV pressure-boundary implementation can also open from an initially zero specified flow using only temporary intensive-property normalization. Native Cv hydraulics publishes the actual flow, or both ports return to exact zero on closure, absent forward pressure difference or a failed calculation. The ordinary `ThrottlingValve` dry-feed behavior is unchanged. A connected model must provide actual upstream inventory or an explicitly imposed pressure source; this response model does not create or size an inventory.

## Synthetic mechanics and deliberate corrections

The healthy PSV has set pressure 42.2 bara, absolute-pressure overpressure fraction 0.10, blowdown fraction 0.03, synthetic full-lift Cv 80 (US), Kd 0.975, maximum backpressure factor 1, and conventional sensitivity 0.15. Opening/closing time constants are 0.15/0.4 s, open/closed dwell 0.5/0.2 s, and maximum lift rate 3/s. The minimum stable command is 0.03; it does not force physical lift through a rate limit. The declared terminal seat cutoff is 1e-8 of lift; below-reseat downward motion reaches exact zero only when the remaining motion also respects the rate limit. This is not a relaxation of the numerical energy criterion.

All gas-path valves explicitly enable native choked-flow limiting: ESD inlet, HIPPS inlet, PCV and PSV. The existing gas valves are natively recalibrated at the same specified normal flow/P/T, 50% opening and sizing margin 1.0 after enabling choking. Liquid-valve policy is unchanged. PSV Cv is not auto-sized or fitted continuously to a pressure target. The frozen 40/80/120 synthetic alternatives use the same initiation, 20 s horizon, 0.1 s timestep and 43 bara pressure limit:

| Synthetic PSV Cv (US) | Peak V101 pressure (bara) | Peak actual relief (kg/s) | Required response |
| ---: | ---: | ---: | --- |
| 40 | 43.799111 | 2.868736 | FAIL |
| 80 | 42.363686 | 5.495518 | PASS |
| 120 | 42.364786 | 8.120014 | PASS |

Cv 80 is the smallest passing tested candidate, not a global sizing optimum or vendor selection. Its half-step run also passes, with peak 42.389673 bara at 0.05 s. Native gas-valve calibration inputs and before/after Cv values are recorded separately. Earlier uncapped-capacity checkpoints remain preserved but their pressure-response PASS results are superseded and receive no design credit. No pressure, response deadline or energy criterion was relaxed.

`SNAP` is an ideal open command at set pressure, latched until the declared reseat pressure. It does not claim a real PSV reaches rated capacity at set. `MODULATING` uses unlatched set-to-full and latched reseat-to-full linear command curves; these are not pressure-direction sensing or a vendor hysteresis curve. The parent native trim applies lift once to full-lift Cv. The qualified example uses exact linear trim and no competing external controller, parent actuator travel or secondary opening limits/stiction.

Both UUID and convenience steady/transient methods now execute the same PSV law. A repeated non-null physical UUID and zero-dt reevaluation do not advance response, dwell, cycles or clocks twice. Negative/nonfinite dt is rejected before response mutation. Complete PSV-owned snapshots include configuration, lift, latch, dwell, availability, counters and UUID. Inlet streams remain separate graph participants. Legacy UID1 serialized models explicitly migrate new defaults and the corrected capacity basis; this is a model migration, not exact historical numerical replay.

The upstream intensive state must be initialized with finite phase fractions in [0,1] summing to one. Invalid phase flags are rejected before motion or capacity changes; this PSV does not repair or reflash an invalid upstream model. Valid zero and differing positive throughput seeds give the same native pressure/Cv capacity. A separately identified repeated-zero Stream setter anomaly is outside this PSV correction.

The public immutable `getPressureReliefConfiguration()` readback identifies units, absolute-pressure fraction basis, actual trim/sizing class and inherited hydraulic settings. `SYNTHETIC_CV_ASSUMPTION` is explicit. These inputs are not a vendor rated-capacity curve, discharge coefficient certification, API 520/521 or ISO 4126 approval, relief sizing approval, or installed-device validation.

## Cases and gates

Ten profiles run at 0.1 s: `PSV_NORMAL`, `SIS_STANDBY`, `PSV_RELIEF`, `HIPPS_FAILED_PSV`, `PSV_FAILED_CLOSED`, `PSV_STUCK_OPEN`, `PSV_UNDERSIZED`, `PSV_HIGH_BACKPRESSURE`, `PSV_SLOW`, and `PSV_CYCLE`. `PSV_NORMAL`, `PSV_RELIEF`, `PSV_SLOW` and `PSV_CYCLE` also run at 0.05 s. Ordinary cases last 20 s with the initiating event at 1 s; the cycle case lasts 30 s. The physical trace covers the full event window, retaining actual accepted dt/UUID, held-input freshness, both vessel states/components, pressure, actual lift/flow, demand, command, effective capacity, dwell, pop and exact-seat counts.

SIS standby credits no PSV discharge where functioning shutdown contains pressure before set. The failed-HIPPS case retains actual unavailable HIPPS hardware while native backup ESD and PSV respond. PSV-only capacity/response cases explicitly inhibit automatic protection. Failed-closed freezes initialized lift zero; stuck-open freezes initialized lift one; undersized Cv is 4; slow opening uses 4 s and 0.2/s; elevated backpressure is 44 bara. Healthy and fault inputs remain separate governed configurations.

Whole-window pressure and no-unwanted-relief criteria use native `MAINTAIN_BOUNDS`; initial normal values cannot give later violations credit. Pop requires actual positive lift and discharge. Reseat/reopening requires observed pop, later exact zero seat below the reseat threshold, and another actual opening; an initially closed sample is not reseat evidence. Stuck-open fails unwanted relief even when pressure remains below its upper bound. The expected failures remain native simulation FAIL results, not thrown engine errors.

Keep pressure/functional response, per-vessel numerical coverage and engineering qualification separate. The cumulative energy limit remains 1e-5 of absolute initialized internal energy. A successful pressure response does not erase a local energy FAIL, and whole-train cancellation does not qualify either vessel. Qualification stays HOLD.

The trace's relieved mass is an integral of the actual PSV outlet rate over its recorded event window only. Report the independent full-horizon pre-consumption boundary integral separately. Independent conservation must sample raw source, branch and vessel-consumed mass, component and enthalpy fluxes in actual equipment order and compare the reference replay with the strict trace; zero material flow contributes exactly zero extensive enthalpy flux regardless of cached raw fluid enthalpy.

Transaction tests cover the complete connected branch subgraph, including the helper, splitter, streams and valves, with identities and replay preserved. The original two-vessel SetPoint helpers still make whole-train adaptive/transactional coverage unsupported; this study does not waive that limitation or claim vent-to-empty/phase-disappearance, header dynamics, SIL or construction approval.
