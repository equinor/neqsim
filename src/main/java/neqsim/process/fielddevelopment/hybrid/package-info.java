/**
 * Hybrid reservoir, well and network production model: reservoir simulator results supply the depletion behaviour and
 * NeqSim supplies the well, network and fluid response.
 *
 * <p>
 * A production profile replayed from a reservoir simulator gives the same rate at a given cumulative production
 * whatever the network does. The classes in this package instead keep the reservoir <em>state</em> from the simulator
 * (pressure, water cut, gas-oil ratio, productivity index and optionally composition as functions of the cumulative
 * volumes) and solve the well rates from inflow performance, lift curves and network back-pressure, so the rates
 * respond when a choke, another well, the separator pressure, the routing or a facility capacity changes.
 * </p>
 *
 * <h2>Layers</h2>
 * <ul>
 * <li>Reservoir-state proxy: {@link neqsim.process.fielddevelopment.hybrid.ReservoirStateProxy} (one simulator run),
 * {@link neqsim.process.fielddevelopment.hybrid.ScenarioReservoirState} (interpolation between runs on a second
 * cumulative volume such as cumulative injection).</li>
 * <li>Well: {@link neqsim.process.fielddevelopment.hybrid.HybridWell} (inflow performance, tubing lift curve, choke and
 * rate target).</li>
 * <li>Lift curves: {@link neqsim.process.fielddevelopment.hybrid.VfpTableLookup} (VFPPROD tables read the way a
 * simulator reads them) and {@link neqsim.process.fielddevelopment.hybrid.CorrectedVlpProvider} (fast curve corrected
 * against a live multiphase model).</li>
 * <li>Network: {@link neqsim.process.fielddevelopment.hybrid.GatheringNetwork} (manifolds, flowlines, risers and a
 * fixed-pressure boundary).</li>
 * <li>Fluid: {@link neqsim.process.fielddevelopment.hybrid.MolarFeedAdapter} (well rates to component molar
 * rates).</li>
 * <li>Solver: {@link neqsim.process.fielddevelopment.hybrid.HybridProductionModel} (coupled well and network solve,
 * time stepping, fidelity modes, facility capacities) with
 * {@link neqsim.process.fielddevelopment.hybrid.HybridStepResult} snapshots.</li>
 * </ul>
 *
 * <p>
 * Reading Eclipse or OPM Flow results into proxy tables is done outside this package (Python, {@code resdata}); the
 * tables enter as arrays or comma-separated files.
 * </p>
 *
 * @see neqsim.process.fielddevelopment.integrated.IntegratedProductionModel
 * @see neqsim.process.fielddevelopment.reservoir.VfpTableBuilder
 */
package neqsim.process.fielddevelopment.hybrid;
