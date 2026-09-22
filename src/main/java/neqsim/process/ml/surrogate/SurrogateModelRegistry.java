package neqsim.process.ml.surrogate;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
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
 * Surrogate models are fast approximations of computationally expensive physics models. This registry provides:
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
 * double[] result = registry.predictWithFallback("flash-separator-1", input, physicsModel::calculate);
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
    if (modelId == null || modelId.trim().isEmpty() || model == null || metadata == null) {
      throw new IllegalArgumentException("Model ID, model and metadata must be provided");
    }
    validateSchema(model, metadata);
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
   * Malformed inputs (null, empty, nonfinite or inconsistent with the declared input dimension) are rejected before
   * either model runs. Valid inputs outside the surrogate's training bounds, missing models, prediction exceptions and
   * invalid predictions use physics when fallback is enabled. Disabling fallback rejects these cases rather than
   * extrapolating. Both paths must return a nonempty finite vector with the declared output dimension, when known.
   * Range and numeric checks do not establish thermodynamic validity or conservation.
   * </p>
   *
   * @param modelId the surrogate model identifier
   * @param input nonempty finite input vector in the model's documented feature order and units
   * @param physicsFallback fallback physics calculation, required only when fallback is needed
   * @return validated prediction result
   * @throws IllegalArgumentException if the input or model schema is malformed
   * @throws IllegalStateException if fallback is needed but disabled/unavailable, or the fallback fails validation
   */
  public double[] predictWithFallback(String modelId, double[] input, Function<double[], double[]> physicsFallback) {
    if (modelId == null || modelId.trim().isEmpty()) {
      throw new IllegalArgumentException("Model ID must be provided");
    }
    validateInput(input, -1);
    SurrogateModelEntry entry = models.get(modelId);

    if (entry == null) {
      return predictPhysics(modelId, input, physicsFallback, -1, null);
    }

    validateSchema(entry.model, entry.metadata);
    boolean withinBounds = entry.metadata.validateRequestInput(input, entry.model.getInputDimension());
    int outputDimension = entry.model.getOutputDimension();

    if (!withinBounds) {
      entry.metadata.recordExtrapolation();
      return predictPhysics(modelId, input, physicsFallback, outputDimension, null);
    }

    try {
      // A failed surrogate must not corrupt the request passed to physics or the caller's array.
      double[] prediction = entry.model.predict(input.clone());
      validateOutput(prediction, outputDimension);
      entry.metadata.recordPrediction();
      return prediction;
    } catch (Exception e) {
      entry.metadata.recordFailure();
      return predictPhysics(modelId, input, physicsFallback, outputDimension, e);
    }
  }

  private double[] predictPhysics(String modelId, double[] input, Function<double[], double[]> physicsFallback,
      int outputDimension, Exception surrogateFailure) {
    if (!enableFallback) {
      throw new IllegalStateException("Surrogate unavailable or invalid for " + modelId + " and fallback is disabled",
          surrogateFailure);
    }
    if (physicsFallback == null) {
      throw new IllegalStateException("Physics fallback is required for " + modelId, surrogateFailure);
    }
    try {
      double[] prediction = physicsFallback.apply(input.clone());
      validateOutput(prediction, outputDimension);
      return prediction;
    } catch (Exception e) {
      IllegalStateException failure = new IllegalStateException("Physics fallback failed for " + modelId, e);
      if (surrogateFailure != null) {
        failure.addSuppressed(surrogateFailure);
      }
      throw failure;
    }
  }

  private static void validateSchema(SurrogateModel model, SurrogateMetadata metadata) {
    int inputDimension = model.getInputDimension();
    int outputDimension = model.getOutputDimension();
    if (inputDimension < -1 || inputDimension == 0 || outputDimension < -1 || outputDimension == 0) {
      throw new IllegalArgumentException("Model dimensions must be positive or -1 (unknown)");
    }
    int boundsDimension = metadata.getInputDimension();
    if (inputDimension > 0 && boundsDimension > 0 && inputDimension != boundsDimension) {
      throw new IllegalArgumentException("Model input dimension does not match input bounds");
    }
  }

  private static void validateInput(double[] input, int expectedDimension) {
    if (!isFiniteVector(input)) {
      throw new IllegalArgumentException("Input must be a nonempty vector of finite values");
    }
    if (expectedDimension > 0 && input.length != expectedDimension) {
      throw new IllegalArgumentException("Input dimension " + input.length + " does not match " + expectedDimension);
    }
  }

  private static void validateOutput(double[] output, int expectedDimension) {
    if (!isFiniteVector(output)) {
      throw new IllegalStateException("Prediction must be a nonempty vector of finite values");
    }
    if (expectedDimension > 0 && output.length != expectedDimension) {
      throw new IllegalStateException("Output dimension " + output.length + " does not match " + expectedDimension);
    }
  }

  private static boolean isFiniteVector(double[] values) {
    if (values == null || values.length == 0) {
      return false;
    }
    for (double value : values) {
      if (!Double.isFinite(value)) {
        return false;
      }
    }
    return true;
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
  public void loadModel(String modelId, String filePath) throws IOException, ClassNotFoundException {
    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filePath))) {
      SurrogateModelEntry entry = (SurrogateModelEntry) in.readObject();
      register(modelId, entry.model, entry.metadata);
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
     * @return positive number of input features, or -1 if unknown; must agree with metadata bounds when provided
     */
    default int getInputDimension() {
      return -1; // Unknown
    }

    /**
     * Gets the expected output dimension.
     *
     * @return positive number of output values, or -1 if unknown; applies to both surrogate and physics results
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
     * Sets finite inclusive training bounds and the input dimension. Bounds are copied and validated before being
     * installed. Unbounded metadata is represented by never setting bounds; infinite per-feature bounds are
     * unsupported.
     *
     * @param min minimum values for each input
     * @param max maximum values for each input
     * @throws IllegalArgumentException if arrays are null, empty, unequal in size, nonfinite or unordered
     */
    public synchronized void setInputBounds(double[] min, double[] max) {
      double[] minCopy = min == null ? null : min.clone();
      double[] maxCopy = max == null ? null : max.clone();
      validateBounds(minCopy, maxCopy);
      this.inputMin = minCopy;
      this.inputMax = maxCopy;
    }

    private static void validateBounds(double[] min, double[] max) {
      if (!isFiniteVector(min) || !isFiniteVector(max) || min.length != max.length) {
        throw new IllegalArgumentException("Bounds must be nonempty finite vectors of equal size");
      }
      for (int i = 0; i < min.length; i++) {
        if (min[i] > max[i]) {
          throw new IllegalArgumentException("Minimum bound exceeds maximum at input " + i);
        }
      }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      if (inputMin != null || inputMax != null) {
        try {
          validateBounds(inputMin, inputMax);
        } catch (IllegalArgumentException e) {
          InvalidObjectException invalid = new InvalidObjectException("Invalid serialized surrogate bounds");
          invalid.initCause(e);
          throw invalid;
        }
      }
    }

    private synchronized int getInputDimension() {
      return inputMin == null ? -1 : inputMin.length;
    }

    private synchronized boolean validateRequestInput(double[] input, int modelDimension) {
      int boundsDimension = getInputDimension();
      if (modelDimension > 0 && boundsDimension > 0 && modelDimension != boundsDimension) {
        throw new IllegalArgumentException("Model input dimension does not match input bounds");
      }
      validateInput(input, modelDimension);
      validateInput(input, boundsDimension);
      return isInputValid(input);
    }

    /**
     * Checks if an input is within the model's validity range.
     *
     * @param input input vector
     * @return true only for a nonempty finite vector matching the bounds dimension and inclusive range, when defined;
     * this check alone does not establish physical validity
     */
    public synchronized boolean isInputValid(double[] input) {
      if (!isFiniteVector(input)) {
        return false;
      }
      if (inputMin == null) {
        return true; // No bounds defined
      }
      if (input.length != inputMin.length) {
        return false;
      }
      for (int i = 0; i < input.length; i++) {
        if (input[i] < inputMin[i] || input[i] > inputMax[i]) {
          return false;
        }
      }
      return true;
    }

    synchronized void recordPrediction() {
      predictionCount++;
      lastUsed = Instant.now();
    }

    synchronized void recordFailure() {
      failureCount++;
    }

    synchronized void recordExtrapolation() {
      extrapolationCount++;
    }

    /**
     * Gets failed surrogate attempts divided by all surrogate attempts. Physics fallbacks and malformed requests do not
     * count as surrogate attempts.
     *
     * @return failure rate (0-1)
     */
    public synchronized double getFailureRate() {
      double attempts = (double) predictionCount + failureCount;
      if (attempts == 0.0) {
        return 0.0;
      }
      return failureCount / attempts;
    }

    /**
     * Gets out-of-range requests divided by all well-formed requests to this registered model. Out-of-range requests
     * never run the surrogate, even if fallback is disabled. Malformed requests do not affect this rate.
     *
     * @return extrapolation rate (0-1)
     */
    public synchronized double getExtrapolationRate() {
      double requests = (double) predictionCount + failureCount + extrapolationCount;
      if (requests == 0.0) {
        return 0.0;
      }
      return extrapolationCount / requests;
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

    public synchronized Instant getLastUsed() {
      return lastUsed;
    }

    /**
     * Gets the number of surrogate predictions accepted after output validation, excluding physics fallbacks.
     *
     * @return successful surrogate prediction count
     */
    public synchronized int getPredictionCount() {
      return predictionCount;
    }

    /**
     * Gets the number of surrogate attempts that threw or returned invalid output, whether or not physics succeeded.
     *
     * @return failed surrogate attempt count
     */
    public synchronized int getFailureCount() {
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
