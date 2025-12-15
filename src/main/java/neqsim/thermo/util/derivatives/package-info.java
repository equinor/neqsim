/**
 * Automatic differentiation and gradient computation for thermodynamic calculations.
 *
 * <p>
 * This package provides classes for computing and storing derivatives of thermodynamic properties
 * with respect to temperature, pressure, and composition. These gradients enable:
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
 * <li>{@link neqsim.thermo.util.derivatives.PropertyGradient} - Gradients of scalar properties
 * (density, enthalpy, etc.)</li>
 * <li>{@link neqsim.thermo.util.derivatives.FlashGradients} - Gradients of flash calculation
 * results (K-values, phase fractions)</li>
 * <li>{@link neqsim.thermo.util.derivatives.FugacityJacobian} - Jacobian matrix of fugacity
 * coefficients</li>
 * </ul>
 *
 * <h2>Usage with Python/JAX:</h2>
 * 
 * <pre>
 * {@code
 * # Define custom VJP for JAX
 * # Use @jax.custom_vjp decorator
 * def flash_density(T, P, z):
 *     # Forward: call NeqSim
 *     system = create_system(z)
 *     system.setTemperature(T)
 *     system.setPressure(P)
 *     ops.TPflash()
 *     return system.getDensity()
 *
 * def flash_density_bwd(res, g):
 *     # Backward: use NeqSim's analytical gradients
 *     grads = DifferentiableFlash(system).computePropertyGradient("density")
 *     return (g * grads.dT, g * grads.dP, g * grads.dz)
 * }
 * </pre>
 *
 * @author ESOL
 * @since 3.0
 */
package neqsim.thermo.util.derivatives;
