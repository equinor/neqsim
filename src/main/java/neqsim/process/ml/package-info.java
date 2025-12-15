/**
 * Machine Learning and AI integration for NeqSim.
 *
 * <p>
 * This package provides infrastructure for integrating NeqSim with modern AI/ML systems:
 * <ul>
 * <li>Reinforcement Learning environments (Gym-compatible)</li>
 * <li>Standardized state/action vectors for neural networks</li>
 * <li>Constraint management for safe exploration</li>
 * <li>Surrogate model training data export</li>
 * </ul>
 *
 * <h2>Key Components:</h2>
 * <ul>
 * <li>{@link neqsim.process.ml.StateVector} - Normalized state representation</li>
 * <li>{@link neqsim.process.ml.ActionVector} - Bounded action representation</li>
 * <li>{@link neqsim.process.ml.Constraint} - Physical/safety constraints</li>
 * <li>{@link neqsim.process.ml.ConstraintManager} - Unified constraint handling</li>
 * <li>{@link neqsim.process.ml.RLEnvironment} - Base RL environment</li>
 * </ul>
 *
 * <h2>Design Principles:</h2>
 * <ol>
 * <li><b>Physics First</b> - ML augments, never replaces, thermodynamic rigor</li>
 * <li><b>Safety by Design</b> - Constraints enforced before any action execution</li>
 * <li><b>Explainability</b> - All decisions traceable to physical constraints</li>
 * <li><b>Multi-fidelity</b> - Fast surrogates for training, full physics for deployment</li>
 * </ol>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.ml;
