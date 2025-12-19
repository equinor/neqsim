package neqsim.process.ml.surrogate;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry for managing trained surrogate (machine learning) models.
 *
 * <p>
 * Surrogate models are fast approximations of computationally expensive physics models. This
 * registry provides:
 * <ul>
 * <li><b>Model Caching:</b> Keep frequently-used models in memory</li>
 * <li><b>Persistence:</b> Save/load models to disk for reuse</li>
 * <li><b>Version Management:</b> Track model versions and validity</li>
 * <li><b>Fallback:</b> Automatic fallback to physics model if surrogate fails</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * // Register a surrogate model for flash calculations
 * SurrogateModelRegistry registry = SurrogateModelRegistry.getInstance();
 *
 * registry.register("flash-separator-1", new SurrogateModel() {
 *   &#64;Override
 *   public double[] predict(double[] input) {
 *     // Neural network inference
 *     return neuralNet.forward(input);
 *   }
 * });
 *
 * // Use with automatic fallback to physics
 * double[] result =
 *     registry.predictWithFallback("flash-separator-1", input, physicsModel::calculate);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class SurrogateModelRegistry implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static volatile SurrogateModelRegistry instance;

  private final Map<String, SurrogateModelEntry> models;
  private boolean enableFallback = true;
  private String persistenceDirectory = ".neqsim/surrogates";

  /**
   * Private constructor for singleton pattern.
   */
  private SurrogateModelRegistry() {
    this.models = new ConcurrentHashMap<>();
  }

  /**
   * Gets the singleton instance of the registry.
   *
   * @return the global registry instance
   */
  public static SurrogateModelRegistry getInstance() {
    if (instance == null) {
      synchronized (SurrogateModelRegistry.class) {
        if (instance == null) {
          instance = new SurrogateModelRegistry();
        }
      }
    }
    return instance;
  }

  /**
   * Registers a surrogate model.
   *
   * @param modelId unique identifier for the model
   * @param model the surrogate model implementation
   */
  public void register(String modelId, SurrogateModel model) {
    register(modelId, model, new SurrogateMetadata());
  }

  /**
   * Registers a surrogate model with metadata.
   *
   * @param modelId unique identifier for the model
   * @param model the surrogate model implementation
   * @param metadata model metadata (training info, validity, etc.)
   */
  public void register(String modelId, SurrogateModel model, SurrogateMetadata metadata) {
    SurrogateModelEntry entry = new SurrogateModelEntry(model, metadata);
    models.put(modelId, entry);
  }

  /**
   * Gets a registered surrogate model.
   *
   * @param modelId the model identifier
   * @return the model, or empty if not found
   */
  public Optional<SurrogateModel> get(String modelId) {
    SurrogateModelEntry entry = models.get(modelId);
    return entry != null ? Optional.of(entry.model) : Optional.empty();
  }

  /**
   * Checks if a model is registered.
   *
   * @param modelId the model identifier
   * @return true if registered
   */
  public boolean hasModel(String modelId) {
    return models.containsKey(modelId);
  }

  /**
   * Removes a model from the registry.
   *
   * @param modelId the model identifier
   * @return true if removed
   */
  public boolean unregister(String modelId) {
    return models.remove(modelId) != null;
  }

  /**
   * Predicts using a surrogate model with automatic fallback.
   *
   * <p>
   * If the surrogate model fails or is outside its validity range, the physics model will be used
   * as a fallback.
   * </p>
   *
   * @param modelId the surrogate model identifier
   * @param input input vector
   * @param physicsFallback fallback physics calculation
   * @return prediction result
   */
  public double[] predictWithFallback(String modelId, double[] input,
      Function<double[], double[]> physicsFallback) {

    SurrogateModelEntry entry = models.get(modelId);

    if (entry == null) {
      // No surrogate registered - use physics
      return physicsFallback.apply(input);
    }

    // Check if input is within surrogate validity range
    if (!entry.metadata.isInputValid(input)) {
      entry.metadata.recordExtrapolation();
      if (enableFallback) {
        return physicsFallback.apply(input);
      }
    }

    try {
      double[] prediction = entry.model.predict(input);
      entry.metadata.recordPrediction();
      return prediction;
    } catch (Exception e) {
      entry.metadata.recordFailure();
      if (enableFallback) {
        return physicsFallback.apply(input);
      }
      throw new RuntimeException("Surrogate model failed and fallback is disabled", e);
    }
  }

  /**
   * Saves a model to disk.
   *
   * @param modelId the model identifier
   * @param filePath output file path
   * @throws IOException if save fails
   */
  public void saveModel(String modelId, String filePath) throws IOException {
    SurrogateModelEntry entry = models.get(modelId);
    if (entry == null) {
      throw new IllegalArgumentException("Model not found: " + modelId);
    }

    try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filePath))) {
      out.writeObject(entry);
    }
  }

  /**
   * Loads a model from disk.
   *
   * @param modelId identifier to register the model under
   * @param filePath input file path
   * @throws IOException if load fails
   * @throws ClassNotFoundException if model class not found
   */
  public void loadModel(String modelId, String filePath)
      throws IOException, ClassNotFoundException {
    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
      SurrogateModelEntry entry = (SurrogateModelEntry) in.readObject();
      models.put(modelId, entry);
    }
  }

  /**
   * Gets statistics for a registered model.
   *
   * @param modelId the model identifier
   * @return metadata with usage statistics
   */
  public Optional<SurrogateMetadata> getMetadata(String modelId) {
    SurrogateModelEntry entry = models.get(modelId);
    return entry != null ? Optional.of(entry.metadata) : Optional.empty();
  }

  /**
   * Gets all registered model IDs.
   *
   * @return map of model IDs to their metadata
   */
  public Map<String, SurrogateMetadata> getAllModels() {
    Map<String, SurrogateMetadata> result = new HashMap<>();
    for (Map.Entry<String, SurrogateModelEntry> e : models.entrySet()) {
      result.put(e.getKey(), e.getValue().metadata);
    }
    return result;
  }

  /**
   * Clears all registered models.
   */
  public void clear() {
    models.clear();
  }

  public boolean isEnableFallback() {
    return enableFallback;
  }

  public void setEnableFallback(boolean enableFallback) {
    this.enableFallback = enableFallback;
  }

  public String getPersistenceDirectory() {
    return persistenceDirectory;
  }

  public void setPersistenceDirectory(String directory) {
    this.persistenceDirectory = directory;
  }

  /**
   * Interface for surrogate model implementations.
   */
  public interface SurrogateModel extends Serializable {
    /**
     * Makes a prediction using the surrogate model.
     *
     * @param input input vector (normalized)
     * @return output vector (predictions)
     */
    double[] predict(double[] input);

    /**
     * Gets the expected input dimension.
     *
     * @return number of input features
     */
    default int getInputDimension() {
      return -1; // Unknown
    }

    /**
     * Gets the expected output dimension.
     *
     * @return number of output values
     */
    default int getOutputDimension() {
      return -1; // Unknown
    }
  }

  /**
   * Metadata for a surrogate model.
   */
  public static class SurrogateMetadata implements Serializable {
    private static final long serialVersionUID = 1000L;

    private String modelType = "unknown";
    private String trainingDataSource;
    private Instant trainedAt;
    private Instant lastUsed;
    private int predictionCount = 0;
    private int failureCount = 0;
    private int extrapolationCount = 0;
    private double[] inputMin;
    private double[] inputMax;
    private double expectedAccuracy = Double.NaN;

    public SurrogateMetadata() {
      this.trainedAt = Instant.now();
    }

    /**
     * Sets the valid input range for the model.
     *
     * @param min minimum values for each input
     * @param max maximum values for each input
     */
    public void setInputBounds(double[] min, double[] max) {
      this.inputMin = min.clone();
      this.inputMax = max.clone();
    }

    /**
     * Checks if an input is within the model's validity range.
     *
     * @param input input vector
     * @return true if within range
     */
    public boolean isInputValid(double[] input) {
      if (inputMin == null || inputMax == null) {
        return true; // No bounds defined
      }

      for (int i = 0; i < Math.min(input.length, inputMin.length); i++) {
        if (input[i] < inputMin[i] || input[i] > inputMax[i]) {
          return false;
        }
      }
      return true;
    }

    void recordPrediction() {
      predictionCount++;
      lastUsed = Instant.now();
    }

    void recordFailure() {
      failureCount++;
    }

    void recordExtrapolation() {
      extrapolationCount++;
    }

    /**
     * Gets the failure rate of this model.
     *
     * @return failure rate (0-1)
     */
    public double getFailureRate() {
      if (predictionCount == 0) {
        return 0.0;
      }
      return (double) failureCount / predictionCount;
    }

    /**
     * Gets the extrapolation rate (predictions outside training range).
     *
     * @return extrapolation rate (0-1)
     */
    public double getExtrapolationRate() {
      if (predictionCount == 0) {
        return 0.0;
      }
      return (double) extrapolationCount / predictionCount;
    }

    // Getters and setters

    public String getModelType() {
      return modelType;
    }

    public void setModelType(String modelType) {
      this.modelType = modelType;
    }

    public String getTrainingDataSource() {
      return trainingDataSource;
    }

    public void setTrainingDataSource(String trainingDataSource) {
      this.trainingDataSource = trainingDataSource;
    }

    public Instant getTrainedAt() {
      return trainedAt;
    }

    public void setTrainedAt(Instant trainedAt) {
      this.trainedAt = trainedAt;
    }

    public Instant getLastUsed() {
      return lastUsed;
    }

    public int getPredictionCount() {
      return predictionCount;
    }

    public int getFailureCount() {
      return failureCount;
    }

    public double getExpectedAccuracy() {
      return expectedAccuracy;
    }

    public void setExpectedAccuracy(double accuracy) {
      this.expectedAccuracy = accuracy;
    }
  }

  /**
   * Internal entry combining model and metadata.
   */
  private static class SurrogateModelEntry implements Serializable {
    private static final long serialVersionUID = 1000L;

    final SurrogateModel model;
    final SurrogateMetadata metadata;

    SurrogateModelEntry(SurrogateModel model, SurrogateMetadata metadata) {
      this.model = model;
      this.metadata = metadata;
    }
  }
}
