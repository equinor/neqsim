/**
 * Network solver package for multi-well gathering systems.
 *
 * <p>
 * This package provides classes for solving pressure-flow equilibrium in gathering networks with
 * multiple wells, flowlines, and manifolds.
 * </p>
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
 * <li>FIXED_MANIFOLD_PRESSURE - Given manifold pressure, calculate well rates</li>
 * <li>FIXED_TOTAL_RATE - Find manifold pressure for target total rate</li>
 * <li>OPTIMIZE_ALLOCATION - Allocate rates optimally among wells</li>
 * </ul>
 *
 * @see neqsim.process.fielddevelopment.workflow
 * @see neqsim.process.equipment.reservoir.WellSystem
 */
package neqsim.process.fielddevelopment.network;
