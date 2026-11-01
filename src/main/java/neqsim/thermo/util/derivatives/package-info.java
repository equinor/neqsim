/**
 * Implicit equilibrium sensitivities and semi-analytical property gradients for thermodynamic calculations.
 *
 * <p>
 * This package provides classes for computing and storing derivatives of thermodynamic properties with respect to
 * temperature, pressure, and composition. These gradients enable:
 * </p>
 * <ul>
 * <li>Gradient-based optimization of process conditions</li>
 * <li>Integration with machine learning frameworks (JAX, PyTorch via custom backward passes)</li>
 * <li>Sensitivity analysis of thermodynamic calculations</li>
 * <li>Physics-informed neural network training</li>
 * </ul>
 *
 * <h2>Key Classes:</h2>
 * <ul>
 * <li>{@link neqsim.thermo.util.derivatives.PropertyGradient} - Gradients of scalar properties (density, enthalpy,
 * etc.)</li>
 * <li>{@link neqsim.thermo.util.derivatives.FlashGradients} - Gradients of flash calculation results (K-values, phase
 * fractions)</li>
 * <li>{@link neqsim.thermo.util.derivatives.FugacityJacobian} - Jacobian matrix of fugacity coefficients</li>
 * </ul>
 *
 * <p>
 * {@link neqsim.thermo.util.derivatives.DifferentiableFlash} operates on detached current snapshots. It uses one
 * factorized equilibrium Jacobian and local property evaluations rather than differentiating iterative solver history.
 * Composition columns follow normalized additions (z + h e_j)/(1+h), and pressure derivatives are per bar. Batched
 * properties share the local states. The interface requires a smooth supported fluid equilibrium; reactions, ions,
 * liquid-liquid and three-phase splits are not supported. Machine-learning integrations must retain the returned
 * forward-state gradients and handle unsupported states explicitly.
 * </p>
 *
 * @author ESOL
 * @since 3.0
 */
package neqsim.thermo.util.derivatives;
