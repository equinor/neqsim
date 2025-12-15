/**
 * Closure relations for two-fluid multiphase pipe flow model.
 *
 * <p>
 * This package provides closure correlations required by the two-fluid model to close the system of
 * conservation equations. These include:
 * </p>
 * <ul>
 * <li>Wall friction correlations for each phase (τ_wg, τ_wL)</li>
 * <li>Interfacial friction correlations (τ_i)</li>
 * <li>Geometry calculations for stratified flow (wetted perimeters, liquid level)</li>
 * <li>Entrainment and deposition models</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction} - Phase-specific
 * wall friction</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction} -
 * Gas-liquid interface friction</li>
 * <li>{@link neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator} -
 * Stratified flow geometry</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 * <li>Taitel, Y. and Dukler, A.E. (1976) - AIChE Journal 22(1)</li>
 * <li>Andritsos, N. and Hanratty, T.J. (1987) - Int. J. Multiphase Flow</li>
 * <li>Wallis, G.B. (1969) - One-dimensional two-phase flow</li>
 * </ul>
 *
 * @since 1.0
 */
package neqsim.process.equipment.pipeline.twophasepipe.closure;
