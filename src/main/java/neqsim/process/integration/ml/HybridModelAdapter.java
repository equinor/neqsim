package neqsim.process.integration.ml;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Adapter for integrating external ML models with NeqSim hybrid physics simulations.
 *
 * <p>
 * This class provides a bridge between NeqSim's physics-based models and external ML frameworks. It
 * supports various correction strategies and can be extended for specific ML platforms.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class HybridModelAdapter implements MLCorrectionInterface, Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * Strategy for combining physics and ML predictions.
   */
  public enum CombinationStrategy {
    /** ML provides additive correction: y = y_physics + y_ml */
    ADDITIVE,
    /** ML provides multiplicative correction: y = y_physics * (1 + y_ml) */
    MULTIPLICATIVE,
    /** ML provides replacement value when confident */
    REPLACEMENT,
    /** Weighted average based on confidence */
    WEIGHTED_AVERAGE
  }

  private String[] featureNames;
  private String modelVersion;
  private CombinationStrategy strategy;
  private boolean ready;

  // Simple linear model parameters (for demonstration)
  private double[] weights;
  private double bias;
  private double confidenceThreshold;

  /**
   * Creates a new hybrid model adapter.
   *
   * @param featureNames names of input features
   * @param strategy combination strategy
   */
  public HybridModelAdapter(String[] featureNames, CombinationStrategy strategy) {
    this.featureNames = featureNames.clone();
    this.strategy = strategy;
    this.modelVersion = "1.0.0";
    this.weights = new double[featureNames.length];
    this.bias = 0.0;
    this.confidenceThreshold = 0.7;
    this.ready = false;
  }

  /**
   * Creates an additive correction adapter.
   *
   * @param featureNames names of input features
   * @return configured adapter
   */
  public static HybridModelAdapter additive(String[] featureNames) {
    return new HybridModelAdapter(featureNames, CombinationStrategy.ADDITIVE);
  }

  /**
   * Creates a multiplicative correction adapter.
   *
   * @param featureNames names of input features
   * @return configured adapter
   */
  public static HybridModelAdapter multiplicative(String[] featureNames) {
    return new HybridModelAdapter(featureNames, CombinationStrategy.MULTIPLICATIVE);
  }

  @Override
  public double correct(double physicsPrediction, double[] features) {
    if (!ready || features.length != weights.length) {
      return physicsPrediction;
    }

    double mlOutput = calculateMLOutput(features);
    double confidence = getConfidence(features);

    switch (strategy) {
      case ADDITIVE:
        return physicsPrediction + mlOutput;

      case MULTIPLICATIVE:
        return physicsPrediction * (1.0 + mlOutput);

      case REPLACEMENT:
        if (confidence >= confidenceThreshold) {
          return mlOutput;
        }
        return physicsPrediction;

      case WEIGHTED_AVERAGE:
        return confidence * mlOutput + (1.0 - confidence) * physicsPrediction;

      default:
        return physicsPrediction;
    }
  }

  /**
   * Calculates the ML model output using the configured linear surrogate.
   *
   * @param features feature vector provided by the process context
   * @return ML prediction corresponding to the supplied features
   */
  private double calculateMLOutput(double[] features) {
    double output = bias;
    for (int i = 0; i < Math.min(features.length, weights.length); i++) {
      output += weights[i] * features[i];
    }
    return output;
  }

  @Override
  public String[] getFeatureNames() {
    return featureNames.clone();
  }

  @Override
  public int getFeatureCount() {
    return featureNames.length;
  }

  @Override
  public boolean isReady() {
    return ready;
  }

  @Override
  public void onModelUpdate(byte[] modelPayload) {
    // Simple protocol: weights as comma-separated doubles, then bias
    try {
      String payload = new String(modelPayload);
      String[] parts = payload.split(",");

      if (parts.length >= weights.length + 1) {
        for (int i = 0; i < weights.length; i++) {
          weights[i] = Double.parseDouble(parts[i].trim());
        }
        bias = Double.parseDouble(parts[weights.length].trim());

        if (parts.length > weights.length + 1) {
          modelVersion = parts[weights.length + 1].trim();
        }

        ready = true;
      }
    } catch (Exception e) {
      ready = false;
    }
  }

  @Override
  public String getModelVersion() {
    return modelVersion;
  }

  @Override
  public double getConfidence(double[] features) {
    // Simple confidence model based on feature range
    // In practice, this would use prediction intervals or ensemble variance
    double confidence = 1.0;

    for (double feature : features) {
      if (Double.isNaN(feature) || Double.isInfinite(feature)) {
        return 0.0;
      }
      // Reduce confidence for extreme values
      if (Math.abs(feature) > 100) {
        confidence *= 0.9;
      }
    }

    return Math.max(0.0, Math.min(1.0, confidence));
  }

  @Override
  public double getUncertainty(double physicsPrediction, double[] features) {
    // Simple uncertainty model
    double baseUncertainty = Math.abs(physicsPrediction) * 0.05; // 5% base
    double confidence = getConfidence(features);
    return baseUncertainty / Math.max(0.1, confidence);
  }

  /**
   * Sets the model weights directly (for testing or simple models).
   *
   * @param weights array of weights
   * @param bias bias term
   */
  public void setLinearModel(double[] weights, double bias) {
    if (weights.length == this.weights.length) {
      this.weights = weights.clone();
      this.bias = bias;
      this.ready = true;
    }
  }

  /**
   * Gets the combination strategy.
   *
   * @return the strategy
   */
  public CombinationStrategy getStrategy() {
    return strategy;
  }

  /**
   * Sets the combination strategy.
   *
   * @param strategy the strategy
   */
  public void setStrategy(CombinationStrategy strategy) {
    this.strategy = strategy;
  }

  /**
   * Sets the confidence threshold for REPLACEMENT strategy.
   *
   * @param threshold confidence threshold (0-1)
   */
  public void setConfidenceThreshold(double threshold) {
    this.confidenceThreshold = Math.max(0.0, Math.min(1.0, threshold));
  }

  /**
   * Trains the linear model on provided data.
   *
   * <p>
   * This is a simple least-squares regression for demonstration. Production implementations should
   * use proper ML libraries.
   * </p>
   *
   * @param featureMatrix training features (samples x features)
   * @param targets target correction values
   */
  public void trainLinear(double[][] featureMatrix, double[] targets) {
    int n = featureMatrix.length;
    int m = featureNames.length;

    if (n == 0 || targets.length != n || featureMatrix[0].length != m) {
      return;
    }

    // Simple gradient descent
    Arrays.fill(weights, 0.0);
    bias = 0.0;

    double learningRate = 0.01;
    int iterations = 1000;

    for (int iter = 0; iter < iterations; iter++) {
      double[] gradientW = new double[m];
      double gradientB = 0.0;

      for (int i = 0; i < n; i++) {
        double pred = bias;
        for (int j = 0; j < m; j++) {
          pred += weights[j] * featureMatrix[i][j];
        }
        double error = pred - targets[i];

        for (int j = 0; j < m; j++) {
          gradientW[j] += error * featureMatrix[i][j] / n;
        }
        gradientB += error / n;
      }

      for (int j = 0; j < m; j++) {
        weights[j] -= learningRate * gradientW[j];
      }
      bias -= learningRate * gradientB;
    }

    ready = true;
  }

  /**
   * Gets the current model weights.
   *
   * @return array of weights
   */
  public double[] getWeights() {
    return weights.clone();
  }

  /**
   * Gets the current bias term.
   *
   * @return bias value
   */
  public double getBias() {
    return bias;
  }
}
