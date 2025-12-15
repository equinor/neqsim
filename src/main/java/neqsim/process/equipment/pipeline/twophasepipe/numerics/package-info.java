/**
 * Numerical methods for two-fluid multiphase pipe flow solver.
 *
 * <p>
 * This package provides numerical algorithms for solving the two-fluid conservation equations:
 * </p>
 * <ul>
 * <li>Flux calculators (AUSM+, HLL) for spatial discretization</li>
 * <li>Reconstruction schemes (MUSCL) for higher-order accuracy</li>
 * <li>Time integrators (RK4, IMEX) for temporal discretization</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator} -
 * Advection Upstream Splitting Method</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.numerics.MUSCLReconstructor} -
 * Second-order spatial reconstruction</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator} - Time
 * stepping algorithms</li>
 * </ul>
 *
 * <h2>Numerical Scheme</h2>
 * <p>
 * The default scheme uses AUSM+ flux splitting, which is well-suited for multiphase flows with
 * large density ratios. Time integration uses explicit RK4 with CFL-based adaptive stepping.
 * </p>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Liou, M.S. (1996) - A sequel to AUSM: AUSM+, J. Comp. Physics</li>
 * <li>van Leer, B. (1979) - Towards the ultimate conservative difference scheme</li>
 * </ul>
 *
 * @since 1.0
 */
package neqsim.process.equipment.pipeline.twophasepipe.numerics;
