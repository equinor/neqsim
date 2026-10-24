# Reservoir-to-products interfaces and specialist routing

Use only the rows needed for the requested system. Paths are relative to the
core NeqSim checkout unless a repository is named. Verify current source and
matching tests; these are navigation anchors, not a blanket model qualification.

| Stage | Inputs and boundaries to preserve | Core API anchors | Workflow/skill route |
| --- | --- | --- | --- |
| Reservoir and PVT | Composition/pseudo-components, reservoir T/P, volumes, depletion/aquifer/injection basis; external deck/PVT/VFP mapping | `neqsim.process.equipment.reservoir.SimpleReservoir`; `neqsim.process.fielddevelopment.integrated.IntegratedProductionModel`; `neqsim.blackoil.BlackOilConverter` | `field-development`, `pvt-simulation`; `neqsim-integrated-production-and-lifecycle`; community `reservoir-simulator-agent`, `near-well-injectivity-agent`, `reservoir-history-match-agent` when installed |
| Production/injection wells | IPR/injectivity/PI provenance, BHP/THP and rate basis, tubing/completion, gas lift, water cut/GOR, constraints | `neqsim.process.equipment.reservoir.WellFlow`; `neqsim.process.equipment.subsea.SubseaWell` | `neqsim-subsea-and-wells`, `neqsim-vfp-lift-curves`; installed community near-well and VFP specialists |
| SURF gathering | Trees/templates/PLEMs/manifolds, daisy-chain or branched topology, live streams, routing, chokes, equipment pressure limits | `neqsim.process.equipment.network.FieldNetworkTopology`; `neqsim.process.equipment.network.LoopedPipeNetwork`; `neqsim.process.equipment.network.FieldWellNetworkCoupler`; `neqsim.process.equipment.manifold.Manifold` | `network-energy-systems`, `flow-assurance`; `neqsim-pipeline-and-terminal-networks`; community `surf-layout-design-agent` for layout |
| Flowlines and risers | Diameter/roughness/elevation profile, heat transfer/seabed/ambient, boundary conditions, gas-oil-water state, selected steady/transient closure | `neqsim.process.equipment.pipeline.PipeBeggsAndBrills`; `neqsim.process.equipment.pipeline.TwoFluidPipe` | `flow-assurance`; `neqsim-flow-assurance`, `neqsim-dynamic-simulation`; community multiphase specialist when installed |
| Host process | Battery limits, separation stages, compression/pumps, heat exchange, recycles, water treatment, operability and capacity | `neqsim.process.processmodel.ProcessSystem`; `neqsim.process.processmodel.ProcessModel` | `process-model`; `neqsim-process-modeling`, `neqsim-platform-modeling` |
| Utilities | Required duty/temperature level, power/fuel, steam/condensate, cooling/heating/refrigeration, supply/capacity constraints and bus signs | `neqsim.process.equipment.energy.EnergyNetworkSolver`; `neqsim.process.util.utilitydesign.SteamNetwork`; `neqsim.process.util.utilitydesign.RefrigerationCycle` | `utility-design`, `network-energy-systems`; `neqsim-utility-design`, `neqsim-energy-systems-and-electrification`, `neqsim-heat-integration` |
| Downstream processing | Receiving pressure/T, product specifications, treating/dehydration, NGL/LNG/refinery topology, export/storage constraints | Select actual equipment from `process.equipment` and its tested builders | `gas-processing-refining`, `lng-value-chain`, `gas-quality`; the corresponding treating/column/LNG/quality skills |

## Read executable usage before constructing the model

- Canonical graph and live well coupling: `src/test/java/neqsim/process/equipment/network/FieldWellNetworkCouplerTest.java`
  and `FieldWellNetworkProcessUnitTest.java` in that directory. Inspect `FieldNetworkTopology`
  and edge-local hydraulic model selection; do not infer transient/thermal qualification
  from a steady graph solve.
- Legacy reservoir/wells/network example: `src/test/java/neqsim/process/equipment/network/WellFlowlineNetworkTest.java`
  and `src/test/java/neqsim/process/equipment/reservoir/WellFlowLiquidInjectionTest.java`.
- Deliverability/depletion scope: `src/test/java/neqsim/process/fielddevelopment/integrated/IntegratedProductionTest.java`.
- Multiphase/transient validity: `docs/process/dynamic-capability-contract.md`,
  `src/test/java/neqsim/process/equipment/pipeline/TwoFluidPipeMassBalanceTest.java`
  and the selected regime's benchmark tests. Follow current open qualification gaps.
- Area composition: `src/test/java/neqsim/process/processmodel/ProcessModelRunUntilConvergedTest.java`
  and `ProcessModelSerializationTest.java` in the same directory.
- Utility links: `src/test/java/neqsim/process/equipment/energy/UtilityEnergyNetworkTest.java`,
  `src/test/java/neqsim/process/util/utilitydesign/UtilityComponentsTest.java`
  and the selected supply-demand balance tests.
- Black-oil exchange: `src/test/java/neqsim/blackoil/BlackOilPackageDocumentationTest.java`.
- Property models: `docs/physical_properties/README.md` and its verified examples;
  match EOS, transport and interfacial models to each relevant fluid/phase pair.
- Downstream equipment: read the selected `neqsim-acid-gas-treating`,
  `neqsim-distillation-design`, `neqsim-lng-liquefaction`, `neqsim-refinery-crude-processing`
  or `neqsim-produced-water-and-solids-separation` skill, then inspect its test anchors.

## Reservoir feedback and stage ownership

An imported OPM forecast is a replay boundary, not a responsive reservoir model.
Backpressure/utility-constrained forecasting requires a verified control exchange
and reservoir rerun/restart contract, or an explicitly defined response model.
Do not assume an automatic forecast importer or cosimulation adapter exists.
Never advance reservoir inventory during candidate process evaluations; commit
one accepted timestep and define rollback on failure. Phase-rate forecasts alone
do not determine a unique compositional feed.

Assign each tubing, flowline and riser loss exactly once when using VFP tables
and explicit network models. Keep arrival-temperature information separately;
VFP pressure tables do not carry a thermal handoff. Preserve raw turning points
in lift curves. Any operating-branch restriction or regularization needs a
stated physical/numerical basis and comparison with the raw hydraulic response.

For LNG use the verified public builder contract in `neqsim-lng-liquefaction`:
no-argument builder, cycle enum, public `build()` returning `LNGProcessModel`,
and `run()` returning its result. Preserve its live feed and use
`getProcessSystem()` for area composition. The refrigerant circulation ratio is
on a mass basis. Do not duplicate ownership when extending an upstream process.

## Cross-repository composition

Discover, do not hard-code, the complete installed catalog. Core skills provide
Java/Python API workflows; community skills provide reusable public methods and
specialists. Preserve the established community
`reservoir-to-facility-screening-agent` and `neqsim-reservoir-facility-value-chain`
for screening/history-match use cases. Their gradients/tank/capacity surrogates
must not silently replace requested multiphase thermal models, detailed facilities
or utilities. Hand off the full-chain contract to the core `network-energy-systems`
and process/utility/downstream specialists when those stages are requested.

For a company installation, resolve authorized enterprise agents/skills through
the existing discovery registry and plugin catalogs. Use distinct `enterprise-*`
IDs or explicit schema-supported overlays; retain core/community dependencies,
public units and model boundaries. Do not copy private content into core/community
or make public examples depend on private data/services. Plugin packaging must
preserve skill references/resources and validate all declared skill dependencies.

## Acceptance prompts

Use natural-language variations, not only class names:

- Build a Python gas-condensate reservoir-to-LNG model with two templates, a
  daisy-chain gathering line, a riser, host separation/compression and limited
  steam/cooling/power; identify missing data and use synthetic inputs explicitly.
- Build the same production system in Java, retaining the same case specification,
  fluids, connection table, constraints and validation targets.
- Couple OPM Flow forecasts and PVT/VFP tables to three production wells and a
  water injector, then include host backpressure and utility capacity in rate selection.
- Update water cut/GOR and reservoir pressure over time; compare a steady
  Beggs-Brill flowline/riser case with a requested two-fluid transient case,
  preserving each model's qualification limits.

A successful response covers every requested stage, selects a justified model
and runtime per stage, names verified API/test anchors, retains both directions
of pressure/rate/capacity coupling and delivers the requested language. It must
identify missing critical data and unsupported regimes, rather than claim a
validated full-chain result from unexecuted code or screening placeholders.
