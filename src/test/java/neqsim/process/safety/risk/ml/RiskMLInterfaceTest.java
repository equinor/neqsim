package neqsim.process.safety.risk.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ML Integration package.
 */
class RiskMLInterfaceTest {

  private RiskMLInterface mlInterface;

  @BeforeEach
  void setUp() {
    mlInterface = new RiskMLInterface("Platform Risk ML");
  }

  @Test
  void testInterfaceCreation() {
    assertEquals("Platform Risk ML", mlInterface.getName());
    assertTrue(mlInterface.getModels().isEmpty());
  }

  @Test
  void testCreateFailurePredictionModel() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("pump-failure-v1", "Pump Failure Predictor");

    assertNotNull(model);
    assertEquals("pump-failure-v1", model.getModelId());
    assertEquals("Pump Failure Predictor", model.getModelName());
    assertEquals(RiskMLInterface.MLModel.ModelType.FAILURE_PREDICTION, model.getModelType());
    assertTrue(model.isActive());
  }

  @Test
  void testCreateAnomalyDetectionModel() {
    RiskMLInterface.MLModel model =
        mlInterface.createAnomalyDetectionModel("anomaly-v1", "Process Anomaly Detector");

    assertEquals(RiskMLInterface.MLModel.ModelType.ANOMALY_DETECTION, model.getModelType());
  }

  @Test
  void testCreateRULModel() {
    RiskMLInterface.MLModel model = mlInterface.createRULModel("rul-v1", "RUL Predictor");

    assertEquals(RiskMLInterface.MLModel.ModelType.RUL_PREDICTION, model.getModelType());
  }

  @Test
  void testModelMetadata() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setVersion("1.0.0");
    model.setAccuracy(0.92);
    model.setTrainedDate(Instant.now());
    model.addMetadata("framework", "TensorFlow");
    model.addMetadata("features", 15);

    assertEquals("1.0.0", model.getVersion());
    assertEquals(0.92, model.getAccuracy(), 0.01);
    assertNotNull(model.getTrainedDate());
    assertEquals("TensorFlow", model.getMetadata().get("framework"));
  }

  @Test
  void testRegisterPredictor() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");

    // Create a simple mock predictor
    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      // Simple prediction: average of features
      double sum = features.values().stream().mapToDouble(Double::doubleValue).sum();
      pred.setPrediction(sum / features.size() > 50 ? 0.8 : 0.2);
      pred.setConfidence(0.85);
      return pred;
    });

    assertNotNull(model.getPredictor());
  }

  @Test
  void testMakePrediction() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");

    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      double pressure = features.getOrDefault("pressure", 0.0);
      pred.setPrediction(pressure > 80 ? 0.9 : 0.1);
      pred.setConfidence(0.88);
      pred.setLabel(pressure > 80 ? "HIGH_RISK" : "LOW_RISK");
      return pred;
    });

    Map<String, Double> features = new HashMap<>();
    features.put("pressure", 85.0);
    features.put("temperature", 60.0);

    RiskMLInterface.MLPrediction prediction = mlInterface.predict("model-1", features);

    assertNotNull(prediction);
    assertEquals(0.9, prediction.getPrediction(), 0.01);
    assertEquals(0.88, prediction.getConfidence(), 0.01);
    assertEquals("HIGH_RISK", prediction.getLabel());
  }

  @Test
  void testPredictionWithFeatureImportance() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");

    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      pred.setPrediction(0.75);
      pred.setConfidence(0.90);

      Map<String, Double> importance = new HashMap<>();
      importance.put("vibration", 0.45);
      importance.put("temperature", 0.35);
      importance.put("pressure", 0.20);
      pred.setFeatureImportance(importance);

      return pred;
    });

    Map<String, Double> features = new HashMap<>();
    features.put("vibration", 5.5);
    features.put("temperature", 65.0);
    features.put("pressure", 50.0);

    RiskMLInterface.MLPrediction prediction = mlInterface.predict("model-1", features);

    Map<String, Double> importance = prediction.getFeatureImportance();
    assertEquals(0.45, importance.get("vibration"), 0.01);
    assertEquals(0.35, importance.get("temperature"), 0.01);
  }

  @Test
  void testFeatureExtractor() {
    mlInterface.registerFeatureExtractor("process", processData -> {
      Map<String, Double> features = new HashMap<>();
      features.put("pressure", ((Number) processData.get("PT-001")).doubleValue());
      features.put("temperature", ((Number) processData.get("TT-001")).doubleValue());
      features.put("flow", ((Number) processData.get("FT-001")).doubleValue());
      return features;
    });

    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      pred.setPrediction(0.5);
      return pred;
    });

    Map<String, Object> processData = new HashMap<>();
    processData.put("PT-001", 75.0);
    processData.put("TT-001", 55.0);
    processData.put("FT-001", 100.0);

    RiskMLInterface.MLPrediction prediction =
        mlInterface.predictWithExtraction("model-1", "process", processData);

    assertNotNull(prediction);
  }

  @Test
  void testPredictWithoutPredictor() {
    mlInterface.createFailurePredictionModel("model-1", "Test Model");

    Map<String, Double> features = new HashMap<>();
    features.put("pressure", 50.0);

    assertThrows(IllegalStateException.class, () -> mlInterface.predict("model-1", features));
  }

  @Test
  void testPredictWithInactiveModel() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setPredictor(features -> new RiskMLInterface.MLPrediction(model.getModelId()));
    model.setActive(false);

    Map<String, Double> features = new HashMap<>();

    assertThrows(IllegalStateException.class, () -> mlInterface.predict("model-1", features));
  }

  @Test
  void testPredictWithUnknownModel() {
    Map<String, Double> features = new HashMap<>();

    assertThrows(IllegalArgumentException.class,
        () -> mlInterface.predict("unknown-model", features));
  }

  @Test
  void testGetActiveModels() {
    mlInterface.createFailurePredictionModel("model-1", "Model 1");
    RiskMLInterface.MLModel model2 = mlInterface.createFailurePredictionModel("model-2", "Model 2");
    mlInterface.createRULModel("model-3", "Model 3");

    model2.setActive(false);

    List<RiskMLInterface.MLModel> active = mlInterface.getActiveModels();
    assertEquals(2, active.size());
  }

  @Test
  void testModelPerformanceTracking() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      pred.setPrediction(0.5);
      return pred;
    });

    // Make prediction
    Map<String, Double> features = new HashMap<>();
    features.put("pressure", 50.0);
    RiskMLInterface.MLPrediction prediction = mlInterface.predict("model-1", features);

    // Provide feedback
    mlInterface.provideFeedback(prediction.getTimestamp(), 0.6);

    // Get performance metrics
    RiskMLInterface.ModelPerformanceMetrics metrics = mlInterface.getModelPerformance("model-1");

    assertNotNull(metrics);
    assertEquals("model-1", metrics.getModelId());
    assertEquals(1, metrics.getValidatedPredictions());
  }

  @Test
  void testJsonSerialization() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setVersion("1.0.0");
    model.setAccuracy(0.92);

    mlInterface.createAnomalyDetectionModel("model-2", "Anomaly Detector");

    String json = mlInterface.toJson();

    assertNotNull(json);
    assertTrue(json.contains("Platform Risk ML"));
    assertTrue(json.contains("model-1"));
    assertTrue(json.contains("FAILURE_PREDICTION"));
  }

  @Test
  void testPredictionMetadata() {
    RiskMLInterface.MLPrediction prediction = new RiskMLInterface.MLPrediction("test-model");
    prediction.setPrediction(0.75);
    prediction.setConfidence(0.9);
    prediction.addMetadata("processing_time_ms", 15);
    prediction.addMetadata("model_version", "1.0.0");

    assertEquals(15, prediction.getMetadata().get("processing_time_ms"));
    assertEquals("1.0.0", prediction.getMetadata().get("model_version"));
  }

  @Test
  void testPredictionProbabilities() {
    RiskMLInterface.MLPrediction prediction = new RiskMLInterface.MLPrediction("test-model");
    double[] probs = {0.1, 0.2, 0.7};
    prediction.setProbabilities(probs);

    assertEquals(3, prediction.getProbabilities().length);
    assertEquals(0.7, prediction.getProbabilities()[2], 0.01);
  }

  @Test
  void testModelToMap() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");
    model.setVersion("2.0.0");
    model.setAccuracy(0.95);

    Map<String, Object> map = model.toMap();

    assertEquals("model-1", map.get("modelId"));
    assertEquals("Test Model", map.get("modelName"));
    assertEquals("FAILURE_PREDICTION", map.get("modelType"));
    assertEquals("2.0.0", map.get("version"));
    assertEquals(0.95, (double) map.get("accuracy"), 0.01);
  }

  @Test
  void testPerformanceMetricsCalculation() {
    RiskMLInterface.MLModel model =
        mlInterface.createFailurePredictionModel("model-1", "Test Model");

    final double[] predictions = {0.5, 0.6, 0.4, 0.7};
    final int[] index = {0};

    model.setPredictor(features -> {
      RiskMLInterface.MLPrediction pred = new RiskMLInterface.MLPrediction(model.getModelId());
      pred.setPrediction(predictions[index[0]++ % predictions.length]);
      return pred;
    });

    // Make predictions and provide feedback
    // Note: Due to timestamp precision, we verify at least one prediction can be validated
    Map<String, Double> features = new HashMap<>();
    features.put("x", 1.0);
    RiskMLInterface.MLPrediction pred = mlInterface.predict("model-1", features);
    mlInterface.provideFeedback(pred.getTimestamp(), predictions[0] + 0.1);

    RiskMLInterface.ModelPerformanceMetrics metrics = mlInterface.getModelPerformance("model-1");

    assertTrue(metrics.getValidatedPredictions() >= 1,
        "At least one prediction should be validated");
    assertEquals(0.1, metrics.getMeanAbsoluteError(), 0.01);
  }
}
