/**
 * Surrogate models and physics constraint validation for AI/ML integration.
 *
 * <p>
 * This package provides infrastructure for hybrid physics-ML systems:
 * </p>
 *
 * <ul>
 * <li><b>Surrogate Registry:</b> Cache and manage trained ML models</li>
 * <li><b>Physics Validation:</b> Verify AI actions against physical constraints</li>
 * <li><b>Fallback Support:</b> Automatic fallback to rigorous physics</li>
 * </ul>
 *
 * <h2>Design Principles:</h2>
 * <ol>
 * <li><b>Physics First:</b> ML augments, never replaces, thermodynamic rigor</li>
 * <li><b>Safety by Design:</b> Constraints enforced before action execution</li>
 * <li><b>Explainability:</b> All decisions traceable to physical constraints</li>
 * </ol>
 *
 * <h2>Usage Pattern:</h2>
 * 
 * <pre>
 * // Register surrogate
 * SurrogateModelRegistry.getInstance().register("flash-model", myNeuralNet);
 *
 * // Use with physics fallback
 * double[] result = registry.predictWithFallback("flash-model", input, physicsModel::calculate);
 *
 * // Validate AI actions
 * PhysicsConstraintValidator validator = new PhysicsConstraintValidator(process);
 * ValidationResult check = validator.validate(proposedAction);
 * if (!check.isValid()) {
 *   System.out.println("Rejected: " + check.getRejectionReason());
 * }
 * </pre>
 *
 * @see neqsim.process.ml.surrogate.SurrogateModelRegistry
 * @see neqsim.process.ml.surrogate.PhysicsConstraintValidator
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.ml.surrogate;
