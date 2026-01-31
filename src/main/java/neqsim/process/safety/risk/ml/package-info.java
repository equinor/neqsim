/**
 * Machine Learning Integration Package for Risk Assessment.
 *
 * <p>
 * This package provides a standardized interface for integrating external machine learning models
 * with the NeqSim risk framework. It enables data-driven risk assessment and prediction.
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link neqsim.process.safety.risk.ml.RiskMLInterface} - Main interface for ML model
 * management and prediction</li>
 * </ul>
 *
 * <h2>ML Use Cases</h2>
 * <ul>
 * <li><strong>Failure Prediction:</strong> Predict equipment failures before they occur</li>
 * <li><strong>Anomaly Detection:</strong> Identify unusual patterns indicating potential
 * problems</li>
 * <li><strong>RUL Prediction:</strong> Estimate remaining useful life of equipment</li>
 * <li><strong>Risk Scoring:</strong> ML-based risk scoring for complex scenarios</li>
 * <li><strong>Optimization:</strong> Optimize operations under risk constraints</li>
 * </ul>
 *
 * <h2>Integration Example</h2>
 * 
 * <pre>
 * // Create ML interface
 * RiskMLInterface mlInterface = new RiskMLInterface("Platform Risk ML");
 *
 * // Register a failure prediction model
 * RiskMLInterface.MLModel model =
 *     mlInterface.createFailurePredictionModel("pump-failure-v1", "Pump Failure Predictor");
 * model.setVersion("1.0.0");
 * model.setAccuracy(0.92);
 *
 * // Set up predictor (e.g., calling external Python/TensorFlow service)
 * model.setPredictor(features -> {
 *   // Call ML service
 *   RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
 *   pred.setPrediction(callMLService(features)); // Your ML service
 *   pred.setConfidence(0.85);
 *   return pred;
 * });
 *
 * // Register feature extractor
 * mlInterface.registerFeatureExtractor("process", processData -> {
 *   Map&lt;String, Double&gt; features = new HashMap&lt;&gt;();
 *   features.put("pressure", (Double) processData.get("PT-001"));
 *   features.put("temperature", (Double) processData.get("TT-001"));
 *   features.put("vibration", (Double) processData.get("VT-001"));
 *   return features;
 * });
 *
 * // Make prediction
 * Map&lt;String, Object&gt; processData = getLatestProcessData();
 * RiskMLInterface.MLPrediction prediction =
 *     mlInterface.predictWithExtraction("pump-failure-v1", "process", processData);
 *
 * if (prediction.getPrediction() > 0.7) {
 *   // High failure probability - trigger alert
 *   generateMaintenanceWorkOrder();
 * }
 * </pre>
 *
 * <h2>Python Integration</h2>
 * <p>
 * The ML interface is designed for easy integration with Python-based ML models via REST API or
 * direct JPype/Py4J bridging.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see neqsim.process.safety.risk.condition.ConditionBasedReliability
 */
package neqsim.process.safety.risk.ml;
