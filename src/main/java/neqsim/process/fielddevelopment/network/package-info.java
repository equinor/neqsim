/**
 * Network solver package for multi-well gathering systems.
 *
 * <p>
 * This package provides classes for solving pressure-flow equilibrium in gathering networks with
 * multiple wells, flowlines, and manifolds. It supports the "Production Network Optimization" topic
 * from NTNU's TPG4230 course.
 * </p>
 *
 * <h2>TPG4230 Topic: Production Network Optimization</h2>
 * <p>
 * The network solver addresses:
 * </p>
 * <ul>
 * <li>Multi-well inflow aggregation at common manifolds</li>
 * <li>Flowline pressure drop calculation</li>
 * <li>Rate allocation under network constraints</li>
 * <li>Backpressure effects on individual well deliverability</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.fielddevelopment.network.NetworkSolver} - Solves network
 * equilibrium</li>
 * <li>{@link neqsim.process.fielddevelopment.network.NetworkResult} - Result container</li>
 * </ul>
 *
 * <h2>Solution Modes</h2>
 * <ul>
 * <li>FIXED_MANIFOLD_PRESSURE - Given manifold pressure, calculate individual well rates</li>
 * <li>FIXED_TOTAL_RATE - Find manifold pressure that achieves a target total rate</li>
 * <li>OPTIMIZE_ALLOCATION - Optimize rate allocation among wells</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * NetworkSolver network = new NetworkSolver("Gathering System");
 * network.addWell(well1, 3.0); // 3 km flowline
 * network.addWell(well2, 5.5); // 5.5 km flowline
 * network.setManifoldPressure(60.0);
 * NetworkResult result = network.solve();
 * System.out.println("Total rate: " + result.getTotalRate("MSm3/day"));
 * }</pre>
 *
 * @see neqsim.process.fielddevelopment.workflow.FieldDevelopmentWorkflow
 * @see neqsim.process.equipment.reservoir.WellSystem
 */
package neqsim.process.fielddevelopment.network;
