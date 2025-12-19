/**
 * Advisory systems for real-time process operations.
 *
 * <p>
 * This package provides infrastructure for operator advisory systems that predict future plant
 * behavior and suggest optimal actions. Key capabilities:
 * </p>
 *
 * <ul>
 * <li><b>Look-ahead Predictions:</b> Predict process behavior minutes to days ahead</li>
 * <li><b>Uncertainty Quantification:</b> Confidence intervals on all predictions</li>
 * <li><b>Constraint Monitoring:</b> Early warning for limit violations</li>
 * <li><b>Actionable Advice:</b> Suggested operator actions with explanations</li>
 * </ul>
 *
 * <h2>Advisory System Use Cases:</h2>
 * <ul>
 * <li>Compressor surge margin prediction</li>
 * <li>Slug arrival forecasting in pipelines</li>
 * <li>Separator flooding risk assessment</li>
 * <li>Energy consumption optimization</li>
 * <li>Production rate forecasting</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * // Create prediction engine for a process
 * AdvisoryEngine engine = new AdvisoryEngine(processSystem);
 *
 * // Run 2-hour look-ahead
 * PredictionResult prediction = engine.predict(Duration.ofHours(2));
 *
 * // Check for predicted issues
 * if (prediction.hasViolations()) {
 *   for (ConstraintViolation v : prediction.getViolations()) {
 *     System.out.println("WARNING: " + v.getDescription());
 *     System.out.println("Suggested: " + v.getSuggestedAction());
 *   }
 * }
 *
 * // Get specific value prediction
 * PredictedValue pressure = prediction.getValue("separator.pressure");
 * System.out.println("Expected pressure in 2h: " + pressure);
 * </pre>
 *
 * @see neqsim.process.advisory.PredictionResult
 * @author ESOL
 * @version 1.0
 */
package neqsim.process.advisory;
