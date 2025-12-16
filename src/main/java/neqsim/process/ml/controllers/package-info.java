/**
 * Simple control algorithms for testing RL environments from Java.
 *
 * <p>
 * This package provides baseline controllers that can be used to:
 * <ul>
 * <li>Test RL environment implementations without Python</li>
 * <li>Provide baselines for comparison with trained RL agents</li>
 * <li>Quick prototyping and debugging</li>
 * </ul>
 *
 * <h2>Available Controllers:</h2>
 * <ul>
 * <li>{@link neqsim.process.ml.controllers.ProportionalController} - P controller</li>
 * <li>{@link neqsim.process.ml.controllers.PIDController} - PID controller</li>
 * <li>{@link neqsim.process.ml.controllers.BangBangController} - On-off with hysteresis</li>
 * <li>{@link neqsim.process.ml.controllers.RandomController} - Random baseline</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.ml.controllers;
