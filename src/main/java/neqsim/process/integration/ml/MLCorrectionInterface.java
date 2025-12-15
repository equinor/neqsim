package neqsim.process.integration.ml;

/**
 * Interface for integrating machine learning corrections with physics-based models.
 *
 * <p>
 * This interface enables hybrid AI approaches where ML models augment first-principles physics
 * predictions. It is designed for integration with AI-based production optimization platforms.
 * </p>
 *
 * <p>
 * Typical use cases:
 * </p>
 * <ul>
 * <li>Bias correction of thermodynamic model predictions</li>
 * <li>Data-driven enhancement of equipment models</li>
 * <li>Real-time model adaptation based on production data</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public interface MLCorrectionInterface {

  /**
   * Applies ML correction to a physics-based prediction.
   *
   * @param physicsPrediction the prediction from the physics model
   * @param features input features for the ML model
   * @return corrected prediction
   */
  double correct(double physicsPrediction, double[] features);

  /**
   * Applies ML correction to multiple predictions (batch mode).
   *
   * @param physicsPredictions array of physics predictions
   * @param featureMatrix matrix of features (one row per prediction)
   * @return array of corrected predictions
   */
  default double[] correctBatch(double[] physicsPredictions, double[][] featureMatrix) {
    double[] corrected = new double[physicsPredictions.length];
    for (int i = 0; i < physicsPredictions.length; i++) {
      corrected[i] = correct(physicsPredictions[i], featureMatrix[i]);
    }
    return corrected;
  }

  /**
   * Gets the expected feature names for this ML model.
   *
   * @return array of feature names in expected order
   */
  String[] getFeatureNames();

  /**
   * Gets the number of features expected by this model.
   *
   * @return number of features
   */
  int getFeatureCount();

  /**
   * Checks if the model is ready for inference.
   *
   * @return true if the model can make predictions
   */
  boolean isReady();

  /**
   * Updates the ML model with new weights/parameters.
   *
   * <p>
   * This method is called when an external AI platform pushes updated model parameters after
   * retraining.
   * </p>
   *
   * @param modelPayload serialized model parameters
   */
  void onModelUpdate(byte[] modelPayload);

  /**
   * Gets the model version or identifier.
   *
   * @return model version string
   */
  String getModelVersion();

  /**
   * Gets the confidence level for a prediction.
   *
   * @param features input features
   * @return confidence score between 0 and 1
   */
  default double getConfidence(double[] features) {
    return 1.0; // Default: full confidence
  }

  /**
   * Gets the uncertainty (standard deviation) for a prediction.
   *
   * @param physicsPrediction the physics prediction
   * @param features input features
   * @return predicted standard deviation
   */
  default double getUncertainty(double physicsPrediction, double[] features) {
    return 0.0; // Default: no additional uncertainty
  }
}
