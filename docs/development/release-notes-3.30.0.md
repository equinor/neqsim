---
title: "NeqSim 3.30.0 release and migration notes"
description: "Changes since 3.29.0, including Aspen IP.21 removal, cryogenic EOS and PVT corrections, transient well-to-export and DEXPI workflows, and publication requirements."
---

# NeqSim 3.30.0

**Status: prepared release candidate.** Prepared on 2026-11-26 from master
`16acfdcd70193e35252ee29fe885473cc645823e`, covering **259 commits** after
[v3.29.0](https://github.com/equinor/neqsim/releases/tag/v3.29.0).

[Complete change list for this snapshot](https://github.com/equinor/neqsim/compare/v3.29.0...16acfdcd70193e35252ee29fe885473cc645823e).

## Breaking changes

### Legacy Aspen IP.21 online-signal integration removed

`neqsim.util.database.AspenIP21Database`,
`neqsim.process.measurementdevice.online.OnlineSignal` and the legacy signal
methods on `MeasurementDeviceInterface` / `MeasurementDeviceBaseClass`
(`getOnlineSignal()`, `setOnlineSignal(OnlineSignal)`, `isOnlineSignal()`,
`setIsOnlineSignal(boolean, String, String)`, `getOnlineValue()`) are removed.
They were deprecated in 3.25.0 (first published in 3.26.0, issue
[#4186](https://github.com/equinor/neqsim/issues/4186), removal in
[#5060](https://github.com/equinor/neqsim/pull/5060)) and relied on the
`sun.jdbc.odbc` bridge driver, which no JDK since Java 8 ships, with a
placeholder connection string.

#### Historian migration

- Acquire historian samples with an application-managed client and pass the
  checked value and unit to
  `MeasurementDeviceBaseClass.setOnlineMeasurementValue(double, String)`. Read it
  with `getOnlineMeasurementValue()`.
- Custom `MeasurementDeviceInterface` implementations must drop their
  `getOnlineSignal()` and `isOnlineSignal()` overrides.
- `ProcessSystem.reportMeasuredValues()` no longer prints an "Online value" line.
- `ModelPredictiveController` and all measurement devices no longer report an
  "external I/O" transient-state coverage blocker because that mode was removed.
- P&ID engineering packages (`PidNativeControlSystem`) no longer emit the
  `onlineSignal` key in native instrument configurations. Packages exported
  earlier with `onlineSignal: false` still validate and apply. The importer rejects
  `onlineSignal: true`.
- `serialVersionUID` values are unchanged. Serialized measurement devices whose
  `onlineSignal` field was `null` still deserialize. A stream containing a
  non-null `OnlineSignal` instance fails with `ClassNotFoundException` and must be
  recreated from its model inputs.

See the [API lifecycle guidance](api-lifecycle.md#legacy-aspen-ip21-historian-integration)
and [measurement devices](../process/equipment/measurement_devices.md#historian-measurements).

### Legacy CPA phase entry points and severe-slugging subclasses

- `PhaseElectrolyteCPAOld` and the legacy methods `initCPAMatrixOld`, `initOld2`,
  `molarVolumeOld`, `solveX2Old` and `solveXOld` on `PhaseSrkCPA` and
  `PhaseUMRCPA` were removed
  ([#4995](https://github.com/equinor/neqsim/pull/4995)). Migrate direct callers
  to the appropriate current CPA fluid system and its initialization and flash
  APIs. Recreate archived models containing the removed phase class from their
  model inputs and compare the results.
- `FluidSevereSlug`, `Pipe` and `SevereSlugAnalyser` in
  `neqsim.process.measurementdevice.simpleflowregime` are now final
  ([#5218](https://github.com/equinor/neqsim/pull/5218)). Replace custom subclasses
  with composition and recompile against the candidate.

The candidate's public Java binary comparison against the published 3.29.0
artifact identified these changes in addition to the historian removal.
Resolve their compatibility disposition under the
[API lifecycle policy](api-lifecycle.md) before publication.

## Review before upgrading

### `TwoFluidPipe`: annular wall friction and the feed split with a prescribed outlet pressure

This release updates the feed calculation and adds a steady annular friction option
(issue [#5266](https://github.com/equinor/neqsim/issues/5266)). No method is removed or renamed.

- **Optional flowing-density annular friction.** With `setAnnularFlowingDensityFriction(true)`, sections
  classified as annular return a lower friction gradient than before: about 5 per cent lower on
  a lean high-pressure gas-condensate line and 20 to 30 per cent lower on a low-pressure line
  with a large liquid mass fraction. Against OLGA 2025.1.0 on four cases the pressure drop is
  now 3 to 7 per cent below the reference, where it was 0.6 to 33 per cent above. Stratified,
  slug and dispersed sections are unchanged, as is the holdup closure.
  New pipes and older saved models use the holdup-weighted density by default.
- **With a prescribed outlet pressure the feed split is flashed at the solved inlet pressure.**
  The pressure of the inlet stream used to set the gas, oil and aqueous rates carried down the
  line. The solver now uses that pressure as its starting value. Results change wherever the stream pressure differed
  from the solved inlet pressure. During a transient the connected feed uses the current
  inlet-cell pressure, feed temperature and composition to set the admitted phase rates.
  For separately metered phases, select `setUseStreamPhaseSplit(true)` to take the
  source-free inlet split from the connected stream in steady and transient operation.

#### Pipeline migration

- When enabling flowing-density friction, compare both pressure drops. All four reference cases
  fall on the low side of OLGA with the option enabled.
- Outer loops that iterated the inlet stream pressure to the solved inlet pressure are no longer
  needed for a steady solve and can be removed.
- Serialized pipes written before this release load with the holdup-weighted density.

See [TwoFluidPipe friction model](../process/TWOFLUIDPIPE_MODEL.md#friction-model).

### Thermodynamics and fluid data

- The Leachman normal-hydrogen Joule–Thomson coefficient now uses the public
  getter's K/bar convention, and accepted bubble points publish initialized
  phase fugacities. GERG-2008 preserves the requested pressure when the
  vapor-root trial fails and checks pressure closure and mechanical stability
  before accepting a dense root. Rerun cryogenic hydrogen and subcooled LNG
  cases against the [cryogenic EOS validation guide](../thermo/cryogenic_eos_validation.md)
  and its labelled reference and measurement cases
  ([#5293](https://github.com/equinor/neqsim/pull/5293)).
- Reference-EOS liquid-density calculations use faster density solves, and
  independently owned GERG-2008 and EOS-CG fluids use thread-confined kernels.
  Continue to use a separate fluid instance per worker
  ([#5009](https://github.com/equinor/neqsim/pull/5009),
  [#5198](https://github.com/equinor/neqsim/pull/5198)).
- Rerun studies that use glycerol or R134a. Glycerol now has its own component
  identity and properties, and R134a has an ideal-gas heat-capacity correlation
  relevant to refrigeration compressor work
  ([#5007](https://github.com/equinor/neqsim/pull/5007),
  [#5291](https://github.com/equinor/neqsim/pull/5291)).
  Recheck fits using the Mathias–Copeman UMR-PRU variants against the updated
  group and component parameters
  ([#5215](https://github.com/equinor/neqsim/pull/5215)).

### PVT laboratory simulations and wax

- Differential liberation now uses the molar mass of dry air for relative gas
  gravity and corrected reservoir volumes for Z and Bg. The default standard
  gas volume for Rs assumes Z = 1. Choose
  `setUseIdealGasStandardVolume(false)` when the laboratory basis uses
  real-gas Z at standard conditions. Constant composition expansion uses
  volume-corrected properties, and the `VOLUME_SHIFT_C7PLUS` tuning parameter
  now scales the effective volume shift of characterized fractions
  ([#5197](https://github.com/equinor/neqsim/pull/5197)).
- CCE and differential liberation anchor saturation volume on the single-phase
  side of the bracket and use whole-cell corrected volume. Multistage separator
  reports read the initialized stage oil properties and flash final carried oil
  at stock-tank conditions. Named wax formers with placeholder fusion data use
  Pedersen properties, and wax flashes test relative convergence of each phase
  ([#5212](https://github.com/equinor/neqsim/pull/5212),
  [#5232](https://github.com/equinor/neqsim/pull/5232)).
  Rebaseline against the laboratory stage sequence and documented basis in the
  [PVT laboratory guide](../pvtsimulation/pvt_lab_tests.md).

### Hydrates and electrolyte chemistry

- Electrolyte CPA can resolve dense CO₂ and aqueous MEG/brine phases during
  hydrate searches. CPA variants now select cavity parameters by fluid family.
  Pitzer adds MEG–water and MEG–NaCl interactions with documented composition
  limits. Recheck inhibitor concentration denominators and distinguish the
  onset equilibrium from hydrate formation kinetics
  ([#5027](https://github.com/equinor/neqsim/pull/5027),
  [#5196](https://github.com/equinor/neqsim/pull/5196),
  [Pitzer hydrate guide](../thermo/pitzer_hydrate_equilibrium.md)).
- The opt-in electrolyte CPA molal carbonate mode uses an aqueous-water mass
  basis and conserves reaction-adjusted inventories during phase reordering.
  Reinitialize chemistry when switching basis and compare pH using the same
  reference convention. The caloric water alpha is a separate opt-in.
  Chloride-brine and divalent-ion diagnostic studies retain experimental and
  model-identifiability limits
  ([#5242](https://github.com/equinor/neqsim/pull/5242),
  [#5243](https://github.com/equinor/neqsim/pull/5243),
  [#5253](https://github.com/equinor/neqsim/pull/5253),
  [#5290](https://github.com/equinor/neqsim/pull/5290)).

### Relief and depressurization results

- `ReliefValveSizing.calculateMassFlowCapacity` uses the molar gas constant
  with molar mass in kg/mol and returns zero forward flow at equal or higher
  backpressure. Rerun earlier forward gas-capacity studies that used the
  erroneous gas-constant basis. Dynamic PSV sizing also requires complete,
  pressure-compliant trajectories
  ([#5111](https://github.com/equinor/neqsim/pull/5111),
  [#5112](https://github.com/equinor/neqsim/pull/5112),
  [relief sizing guide](../safety/relief_valve_sizing_api.md)).
- `VesselDepressurization` uses vapour properties and vents vapour from the
  top of a vessel with liquid inventory. Recheck pressure, inventory and vent
  temperature histories for prior gas–liquid blowdown cases
  ([#5193](https://github.com/equinor/neqsim/pull/5193),
  [#5200](https://github.com/equinor/neqsim/pull/5200)).

## Main additions since 3.29.0

### Wells, networks, control and performance

- `WellToExportDynamics` provides a fixed-step reference plant from producing
  wells and gathering lines through a finite separator, oil export and parallel
  gas compression. The examples exercise speed and anti-surge control,
  independent trips, plant ESD, relief and blowdown. Their synthetic
  component-balance and time-step evidence applies to the tested reference
  plant
  ([#5275](https://github.com/equinor/neqsim/pull/5275)).
- Hybrid production modeling couples Eclipse/OPM-derived reservoir state tables
  to NeqSim well inflow, lift and network backpressure. Related additions cover
  VLP correlations, VFP tables, gas lift and OLGA GENKEY well export
  ([#5227](https://github.com/equinor/neqsim/pull/5227),
  [#5233](https://github.com/equinor/neqsim/pull/5233),
  [#5238](https://github.com/equinor/neqsim/pull/5238),
  [#5246](https://github.com/equinor/neqsim/pull/5246)).
- Two-fluid transport adds conservative phase-specific face fluxes, experimental
  tracked-slug forces and additional mesh-refinement evidence. Use the
  [two-fluid evidence matrix](../process/twofluidpipe-evidence-matrix.md)
  to distinguish benchmark cases from operating ranges still under evaluation.
  The failed-primary-PSD tie-back example retains a numerical hold on the
  120-to-240-cell peak-pressure comparison
  ([#5093](https://github.com/equinor/neqsim/pull/5093),
  [#5249](https://github.com/equinor/neqsim/pull/5249),
  [#5257](https://github.com/equinor/neqsim/pull/5257),
  [#5288](https://github.com/equinor/neqsim/pull/5288),
  [#5289](https://github.com/equinor/neqsim/pull/5289)).
- Process-linked MPC adds constrained receding-horizon plans and controlled
  variable zones. Ideal-stage ratio-specified distillation under `AUTO`
  prefers rigorous Naphtali–Sandholm or MESH residual probes before the
  existing solver sequence. Compare accepted solver, product splits and
  residuals when upgrading saved column cases
  ([#5089](https://github.com/equinor/neqsim/pull/5089),
  [#5099](https://github.com/equinor/neqsim/pull/5099),
  [#5276](https://github.com/equinor/neqsim/pull/5276),
  [distillation guide](../process/equipment/distillation.md)).

### Safety, DEXPI and engineering deliverables

- Steady flare studies solve local valve backpressures with simultaneous
  branches. The V-101 reference case couples PSV and BDV flows to a finite
  header and knockout inventory. Review the worked cases for their transient,
  phase and backpressure assumptions. The opt-in pure-CO₂ dry-ice adapter has
  bounded equilibrium-model evidence and an unqualified engineering status
  ([#5143](https://github.com/equinor/neqsim/pull/5143),
  [#5160](https://github.com/equinor/neqsim/pull/5160),
  [#5162](https://github.com/equinor/neqsim/pull/5162),
  [#5292](https://github.com/equinor/neqsim/pull/5292)).
- Native DEXPI 2.0.1 intake and round-trip reconstruction cover more connectivity
  and control bindings. P&IDs and DEXPI exports carry calculated line and
  equipment sizes. Preliminary equipment and mechanical datasheets report
  traceable weights and explicit holds for missing inputs. Check receiving-tool
  behavior and project-specific engineering inputs before relying on an export
  ([#4989](https://github.com/equinor/neqsim/pull/4989),
  [#5081](https://github.com/equinor/neqsim/pull/5081),
  [#5116](https://github.com/equinor/neqsim/pull/5116),
  [#5159](https://github.com/equinor/neqsim/pull/5159),
  [#5191](https://github.com/equinor/neqsim/pull/5191),
  [DEXPI bridge evaluation](../integration/dexpi-physics-bridge-evaluation.md)).
- New or expanded design and fluid-screening tools cover subsea umbilicals,
  silica in commingled produced water, a calibrated solid asphaltene model and
  the NORSOK M-506 Rev. 2 unmitigated water-wet carbon-steel rate. Select the
  intended corrosion equation explicitly if comparing with earlier screening
  calculations
  ([#4971](https://github.com/equinor/neqsim/pull/4971),
  [#5091](https://github.com/equinor/neqsim/pull/5091),
  [#5142](https://github.com/equinor/neqsim/pull/5142),
  [#5204](https://github.com/equinor/neqsim/pull/5204)).

## Runtime and planned distributions

| Distribution | Version or expected asset | Runtime |
| --- | --- | --- |
| Core Maven artifact | `com.equinor.neqsim:neqsim:3.30.0` | Java 17+ |
| Core standalone library | `neqsim-3.30.0.jar` | Java 17+ |
| Java 8 Maven artifact | `com.equinor.neqsim:neqsim:3.30.0-Java8` | Java 8+ |
| Java 8 standalone library | `neqsim-3.30.0-Java8.jar` | Java 8+ |
| MCP runner and checksum | `neqsim-mcp-server-3.30.0-runner.jar` and `.jar.sha256` | Java 21+ |
| MCP container | `ghcr.io/equinor/neqsim-mcp-server:3.30.0` | Container runtime |

The MCP module retains its own `1.0.0-SNAPSHOT` Maven identity and uses 3.30.0
for its NeqSim core dependency. Development tooling requires Python 3.11+
([#5177](https://github.com/equinor/neqsim/pull/5177)).
Python and plugin-marketplace packages have separate release processes.

The release preparation restores Maven **3.9.16**, the publishing wrapper used
for 3.29.0. The cycle's Dependabot updates advanced the wrapper to 3.10.2
([#5054](https://github.com/equinor/neqsim/pull/5054),
[#5220](https://github.com/equinor/neqsim/pull/5220)). The Java 8 release profile
still uses Sonatype central-publishing **0.11.0**. The local-metadata bundle
failure previously observed with that plugin and Maven 3.10.0 is documented in
[#4318](https://github.com/equinor/neqsim/pull/4318). The standard profile uses
**0.12.0** and both profiles wait for publication.

## Release completion requirements

1. Review API and saved-model compatibility under the
   [API lifecycle policy](api-lifecycle.md). Load representative earlier
   `.neqsim` models and verify retained configuration and rerun outputs,
   especially archived instruments, PVT studies and transient pipes.
   See the [3.29.0 migration notes](release-notes-3.29.0.md) for earlier changes.
2. Check the final candidate's core and slow tests, model specifications,
   code health, documentation/Javadoc, security and packaged Java 21 MCP
   protocol qualification.
3. Inspect the Java 17 and Java 8 libraries on their supported runtimes,
   including embedded versions, class-file targets, source/Javadoc archives,
   dependencies and both signing/publication bundles. Verify the MCP runner
   checksum.
4. Merge the reviewed version PR to let the default-branch workflow build the
   draft release. Include these migration notes with the generated change list.
5. After release-owner approval, publish through the repository workflow and
   verify both Maven Central versions reach `PUBLISHED`, the GitHub assets
   and checksums are available, and the versioned MCP container is available.
