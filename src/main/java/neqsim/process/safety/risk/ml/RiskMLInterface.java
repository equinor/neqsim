package neqsim.process.safety.risk.ml;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Machine Learning Integration Interface for Risk Assessment.
 *
 * <p>
 * Provides a standardized interface for integrating external machine learning models with the
 * NeqSim risk framework. Supports various ML use cases including:
 * </p>
 * <ul>
 * <li>Failure prediction models</li>
 * <li>Anomaly detection</li>
 * <li>Remaining useful life (RUL) prediction</li>
 * <li>Process optimization under risk constraints</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RiskMLInterface implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Interface name. */
  private String name;

  /** Registered ML models. */
  private Map<String, MLModel> models;

  /** Feature extractors. */
  private Map<String, FeatureExtractor> featureExtractors;

  /** Prediction history. */
  private List<PredictionRecord> predictionHistory;

  /** Maximum history size. */
  private int maxHistorySize = 10000;

  /**
   * Machine learning model wrapper.
   */
  public static class MLModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelId;
    private String modelName;
    private ModelType modelType;
    private String version;
    private Instant trainedDate;
    private double accuracy;
    private boolean active;
    private transient MLPredictor predictor;
    private Map<String, Object> metadata;

    public enum ModelType {
      FAILURE_PREDICTION, ANOMALY_DETECTION, RUL_PREDICTION, RISK_SCORING, OPTIMIZATION, CLASSIFICATION, REGRESSION
    }

    public MLModel(String id, String name, ModelType type) {
      this.modelId = id;
      this.modelName = name;
      this.modelType = type;
      this.active = true;
      this.metadata = new HashMap<>();
    }

    public String getModelId() {
      return modelId;
    }

    public String getModelName() {
      return modelName;
    }

    public ModelType getModelType() {
      return modelType;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String version) {
      this.version = version;
    }

    public Instant getTrainedDate() {
      return trainedDate;
    }

    public void setTrainedDate(Instant date) {
      this.trainedDate = date;
    }

    public double getAccuracy() {
      return accuracy;
    }

    public void setAccuracy(double accuracy) {
      this.accuracy = accuracy;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }

    public void setPredictor(MLPredictor predictor) {
      this.predictor = predictor;
    }

    public MLPredictor getPredictor() {
      return predictor;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public void addMetadata(String key, Object value) {
      metadata.put(key, value);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("modelId", modelId);
      map.put("modelName", modelName);
      map.put("modelType", modelType.name());
      map.put("version", version);
      map.put("trainedDate", trainedDate != null ? trainedDate.toString() : null);
      map.put("accuracy", accuracy);
      map.put("active", active);
      map.put("metadata", metadata);
      return map;
    }
  }

  /**
   * Functional interface for ML model prediction.
   * <p>
   * This is a functional interface (SAM - Single Abstract Method) to allow lambda expressions.
   * </p>
   */
  @FunctionalInterface
  public interface MLPredictor {
    /**
     * Makes a prediction.
     *
     * @param features input features
     * @return prediction result
     */
    MLPrediction predict(Map<String, Double> features);

    /**
     * Makes batch predictions.
     * <p>
     * Default implementation iterates over the batch and calls predict for each.
     * </p>
     *
     * @param featuresBatch list of feature maps
     * @return list of predictions
     */
    default List<MLPrediction> predictBatch(List<Map<String, Double>> featuresBatch) {
      List<MLPrediction> results = new ArrayList<>();
      for (Map<String, Double> features : featuresBatch) {
        results.add(predict(features));
      }
      return results;
    }
  }

  /**
   * ML prediction result.
   */
  public static class MLPrediction implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelId;
    private Instant timestamp;
    private double prediction;
    private double confidence;
    private double[] probabilities;
    private String label;
    private Map<String, Double> featureImportance;
    private Map<String, Object> metadata;

    public MLPrediction(String modelId) {
      this.modelId = modelId;
      this.timestamp = Instant.now();
      this.featureImportance = new HashMap<>();
      this.metadata = new HashMap<>();
    }

    public String getModelId() {
      return modelId;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getPrediction() {
      return prediction;
    }

    public void setPrediction(double prediction) {
      this.prediction = prediction;
    }

    public double getConfidence() {
      return confidence;
    }

    public void setConfidence(double confidence) {
      this.confidence = confidence;
    }

    public double[] getProbabilities() {
      return probabilities;
    }

    public void setProbabilities(double[] probabilities) {
      this.probabilities = probabilities;
    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public Map<String, Double> getFeatureImportance() {
      return featureImportance;
    }

    public void setFeatureImportance(Map<String, Double> importance) {
      this.featureImportance = importance;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public void addMetadata(String key, Object value) {
      metadata.put(key, value);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("modelId", modelId);
      map.put("timestamp", timestamp.toString());
      map.put("prediction", prediction);
      map.put("confidence", confidence);
      map.put("label", label);
      map.put("featureImportance", featureImportance);
      map.put("metadata", metadata);
      return map;
    }
  }

  /**
   * Feature extractor for process data.
   */
  public interface FeatureExtractor {
    /**
     * Extracts features from process data.
     *
     * @param processData raw process data
     * @return extracted features
     */
    Map<String, Double> extractFeatures(Map<String, Object> processData);
  }

  /**
   * Prediction record for history tracking.
   */
  public static class PredictionRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelId;
    private Instant timestamp;
    private Map<String, Double> features;
    private double prediction;
    private Double actualValue;
    private boolean validated;

    /**
     * Creates a prediction record with auto-generated timestamp.
     *
     * @param modelId model ID
     * @param features input features
     * @param prediction predicted value
     */
    public PredictionRecord(String modelId, Map<String, Double> features, double prediction) {
      this(modelId, features, prediction, Instant.now());
    }

    /**
     * Creates a prediction record with specified timestamp.
     *
     * @param modelId model ID
     * @param features input features
     * @param prediction predicted value
     * @param timestamp timestamp of prediction
     */
    public PredictionRecord(String modelId, Map<String, Double> features, double prediction,
        Instant timestamp) {
      this.modelId = modelId;
      this.timestamp = timestamp;
      this.features = new HashMap<>(features);
      this.prediction = prediction;
      this.validated = false;
    }

    public void setActualValue(double actual) {
      this.actualValue = actual;
      this.validated = true;
    }

    public double getPredictionError() {
      return actualValue != null ? Math.abs(prediction - actualValue) : Double.NaN;
    }

    public String getModelId() {
      return modelId;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getPrediction() {
      return prediction;
    }

    public Double getActualValue() {
      return actualValue;
    }

    public boolean isValidated() {
      return validated;
    }
  }

  /**
   * Creates an ML interface.
   *
   * @param name interface name
   */
  public RiskMLInterface(String name) {
    this.name = name;
    this.models = new HashMap<>();
    this.featureExtractors = new HashMap<>();
    this.predictionHistory = new ArrayList<>();
  }

  /**
   * Registers an ML model.
   *
   * @param model ML model
   */
  public void registerModel(MLModel model) {
    models.put(model.getModelId(), model);
  }

  /**
   * Creates and registers a failure prediction model.
   *
   * @param modelId model ID
   * @param modelName model name
   * @return created model
   */
  public MLModel createFailurePredictionModel(String modelId, String modelName) {
    MLModel model = new MLModel(modelId, modelName, MLModel.ModelType.FAILURE_PREDICTION);
    models.put(modelId, model);
    return model;
  }

  /**
   * Creates and registers an anomaly detection model.
   *
   * @param modelId model ID
   * @param modelName model name
   * @return created model
   */
  public MLModel createAnomalyDetectionModel(String modelId, String modelName) {
    MLModel model = new MLModel(modelId, modelName, MLModel.ModelType.ANOMALY_DETECTION);
    models.put(modelId, model);
    return model;
  }

  /**
   * Creates and registers an RUL prediction model.
   *
   * @param modelId model ID
   * @param modelName model name
   * @return created model
   */
  public MLModel createRULModel(String modelId, String modelName) {
    MLModel model = new MLModel(modelId, modelName, MLModel.ModelType.RUL_PREDICTION);
    models.put(modelId, model);
    return model;
  }

  /**
   * Registers a feature extractor.
   *
   * @param name extractor name
   * @param extractor feature extractor
   */
  public void registerFeatureExtractor(String name, FeatureExtractor extractor) {
    featureExtractors.put(name, extractor);
  }

  /**
   * Makes a prediction using a registered model.
   *
   * @param modelId model ID
   * @param features input features
   * @return prediction result
   */
  public MLPrediction predict(String modelId, Map<String, Double> features) {
    MLModel model = models.get(modelId);
    if (model == null) {
      throw new IllegalArgumentException("Model not found: " + modelId);
    }
    if (!model.isActive()) {
      throw new IllegalStateException("Model is not active: " + modelId);
    }
    if (model.getPredictor() == null) {
      throw new IllegalStateException("No predictor registered for model: " + modelId);
    }

    MLPrediction prediction = model.getPredictor().predict(features);

    // Record prediction using the prediction's timestamp for later feedback lookup
    PredictionRecord record = new PredictionRecord(modelId, features, prediction.getPrediction(),
        prediction.getTimestamp());
    synchronized (predictionHistory) {
      predictionHistory.add(record);
      while (predictionHistory.size() > maxHistorySize) {
        predictionHistory.remove(0);
      }
    }

    return prediction;
  }

  /**
   * Makes a prediction with feature extraction.
   *
   * @param modelId model ID
   * @param extractorName feature extractor name
   * @param processData raw process data
   * @return prediction result
   */
  public MLPrediction predictWithExtraction(String modelId, String extractorName,
      Map<String, Object> processData) {
    FeatureExtractor extractor = featureExtractors.get(extractorName);
    if (extractor == null) {
      throw new IllegalArgumentException("Feature extractor not found: " + extractorName);
    }

    Map<String, Double> features = extractor.extractFeatures(processData);
    return predict(modelId, features);
  }

  /**
   * Provides feedback on a prediction (for model improvement).
   *
   * @param predictionTimestamp timestamp of prediction
   * @param actualValue actual observed value
   */
  public void provideFeedback(Instant predictionTimestamp, double actualValue) {
    synchronized (predictionHistory) {
      for (PredictionRecord record : predictionHistory) {
        if (record.getTimestamp().equals(predictionTimestamp)) {
          record.setActualValue(actualValue);
          break;
        }
      }
    }
  }

  /**
   * Gets model performance metrics.
   *
   * @param modelId model ID
   * @return performance metrics
   */
  public ModelPerformanceMetrics getModelPerformance(String modelId) {
    List<PredictionRecord> validated = new ArrayList<>();
    synchronized (predictionHistory) {
      for (PredictionRecord record : predictionHistory) {
        if (record.getModelId().equals(modelId) && record.isValidated()) {
          validated.add(record);
        }
      }
    }

    return new ModelPerformanceMetrics(modelId, validated);
  }

  /**
   * Model performance metrics.
   */
  public static class ModelPerformanceMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelId;
    private int totalPredictions;
    private int validatedPredictions;
    private double meanAbsoluteError;
    private double rootMeanSquareError;
    private double meanAbsolutePercentageError;

    public ModelPerformanceMetrics(String modelId, List<PredictionRecord> validated) {
      this.modelId = modelId;
      this.validatedPredictions = validated.size();

      if (!validated.isEmpty()) {
        double sumAE = 0;
        double sumSE = 0;
        double sumAPE = 0;

        for (PredictionRecord record : validated) {
          double error = record.getPredictionError();
          sumAE += error;
          sumSE += error * error;
          if (record.getActualValue() != 0) {
            sumAPE += Math.abs(error / record.getActualValue());
          }
        }

        meanAbsoluteError = sumAE / validated.size();
        rootMeanSquareError = Math.sqrt(sumSE / validated.size());
        meanAbsolutePercentageError = sumAPE / validated.size() * 100;
      }
    }

    public String getModelId() {
      return modelId;
    }

    public int getValidatedPredictions() {
      return validatedPredictions;
    }

    public double getMeanAbsoluteError() {
      return meanAbsoluteError;
    }

    public double getRootMeanSquareError() {
      return rootMeanSquareError;
    }

    public double getMeanAbsolutePercentageError() {
      return meanAbsolutePercentageError;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("modelId", modelId);
      map.put("validatedPredictions", validatedPredictions);
      map.put("mae", meanAbsoluteError);
      map.put("rmse", rootMeanSquareError);
      map.put("mape", meanAbsolutePercentageError);
      return map;
    }
  }

  // Getters

  public String getName() {
    return name;
  }

  public MLModel getModel(String modelId) {
    return models.get(modelId);
  }

  public List<MLModel> getModels() {
    return new ArrayList<>(models.values());
  }

  public List<MLModel> getActiveModels() {
    List<MLModel> active = new ArrayList<>();
    for (MLModel model : models.values()) {
      if (model.isActive()) {
        active.add(model);
      }
    }
    return active;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("totalModels", models.size());
    map.put("activeModels", getActiveModels().size());
    map.put("featureExtractors", new ArrayList<>(featureExtractors.keySet()));

    List<Map<String, Object>> modelList = new ArrayList<>();
    for (MLModel model : models.values()) {
      modelList.add(model.toMap());
    }
    map.put("models", modelList);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    return String.format("RiskMLInterface[%s, models=%d, active=%d]", name, models.size(),
        getActiveModels().size());
  }
}
