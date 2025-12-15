package neqsim.process.integration.ml;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Utility class for extracting ML features from NeqSim process streams.
 *
 * <p>
 * Provides standardized feature extraction for hybrid physics-ML models, ensuring consistent input
 * formatting for external ML platforms.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FeatureExtractor {

  /**
   * Standard feature set for stream properties.
   */
  public static final String[] STANDARD_STREAM_FEATURES =
      {"pressure", "temperature", "totalFlowRate", "gasFlowRate", "oilFlowRate", "waterFlowRate",
          "gor", "waterCut", "density", "viscosity", "z-factor"};

  /**
   * Minimal feature set for basic streams.
   */
  public static final String[] MINIMAL_STREAM_FEATURES =
      {"pressure", "temperature", "totalFlowRate", "density"};

  private FeatureExtractor() {
    // Utility class
  }

  /**
   * Extracts standard features from a stream.
   *
   * @param stream the stream to extract features from
   * @return array of feature values in standard order
   */
  public static double[] extractStandardFeatures(StreamInterface stream) {
    return extractFeatures(stream, STANDARD_STREAM_FEATURES);
  }

  /**
   * Extracts minimal features from a stream.
   *
   * @param stream the stream to extract features from
   * @return array of feature values in minimal order
   */
  public static double[] extractMinimalFeatures(StreamInterface stream) {
    return extractFeatures(stream, MINIMAL_STREAM_FEATURES);
  }

  /**
   * Extracts specified features from a stream.
   *
   * @param stream the stream to extract features from
   * @param featureNames names of features to extract
   * @return array of feature values
   */
  public static double[] extractFeatures(StreamInterface stream, String[] featureNames) {
    double[] features = new double[featureNames.length];

    for (int i = 0; i < featureNames.length; i++) {
      features[i] = extractFeature(stream, featureNames[i]);
    }

    return features;
  }

  /**
   * Extracts a single feature from a stream.
   *
   * @param stream the stream
   * @param featureName the feature name
   * @return feature value or NaN if not available
   */
  public static double extractFeature(StreamInterface stream, String featureName) {
    if (stream == null || stream.getFluid() == null) {
      return Double.NaN;
    }

    try {
      switch (featureName.toLowerCase()) {
        case "pressure":
          return stream.getPressure("bara");

        case "temperature":
          return stream.getTemperature("K");

        case "totalflowrate":
          return stream.getFlowRate("kg/hr");

        case "gasflowrate":
          if (stream.getFluid().hasPhaseType("gas")) {
            return stream.getFluid().getPhase("gas").getFlowRate("kg/hr");
          }
          return 0.0;

        case "oilflowrate":
          if (stream.getFluid().hasPhaseType("oil")) {
            return stream.getFluid().getPhase("oil").getFlowRate("kg/hr");
          }
          return 0.0;

        case "waterflowrate":
          if (stream.getFluid().hasPhaseType("aqueous")) {
            return stream.getFluid().getPhase("aqueous").getFlowRate("kg/hr");
          }
          return 0.0;

        case "gor":
          return calculateGOR(stream);

        case "watercut":
          return calculateWaterCut(stream);

        case "density":
          return stream.getFluid().getDensity("kg/m3");

        case "viscosity":
          return stream.getFluid().getViscosity("cP");

        case "z-factor":
          if (stream.getFluid().hasPhaseType("gas")) {
            return stream.getFluid().getPhase("gas").getZ();
          }
          return 1.0;

        case "entropy":
          return stream.getFluid().getEntropy();

        case "enthalpy":
          return stream.getFluid().getEnthalpy();

        case "mw":
          return stream.getFluid().getMolarMass("kg/mol") * 1000.0;

        default:
          return Double.NaN;
      }
    } catch (Exception e) {
      return Double.NaN;
    }
  }

  private static double calculateGOR(StreamInterface stream) {
    double gasRate = 0.0;
    double oilRate = 0.0;

    if (stream.getFluid().hasPhaseType("gas")) {
      gasRate = stream.getFluid().getPhase("gas").getFlowRate("Sm3/day");
    }
    if (stream.getFluid().hasPhaseType("oil")) {
      oilRate = stream.getFluid().getPhase("oil").getFlowRate("Sm3/day");
    }

    return (oilRate > 0) ? gasRate / oilRate : 0.0;
  }

  private static double calculateWaterCut(StreamInterface stream) {
    double waterRate = 0.0;
    double oilRate = 0.0;

    if (stream.getFluid().hasPhaseType("aqueous")) {
      waterRate = stream.getFluid().getPhase("aqueous").getFlowRate("Sm3/day");
    }
    if (stream.getFluid().hasPhaseType("oil")) {
      oilRate = stream.getFluid().getPhase("oil").getFlowRate("Sm3/day");
    }

    double totalLiquid = waterRate + oilRate;
    return (totalLiquid > 0) ? (waterRate / totalLiquid) * 100.0 : 0.0;
  }

  /**
   * Normalizes features using z-score normalization.
   *
   * @param features raw features
   * @param means feature means
   * @param stds feature standard deviations
   * @return normalized features
   */
  public static double[] normalizeZScore(double[] features, double[] means, double[] stds) {
    double[] normalized = new double[features.length];
    for (int i = 0; i < features.length; i++) {
      if (stds[i] > 1e-10) {
        normalized[i] = (features[i] - means[i]) / stds[i];
      } else {
        normalized[i] = 0.0;
      }
    }
    return normalized;
  }

  /**
   * Normalizes features using min-max normalization.
   *
   * @param features raw features
   * @param mins feature minimums
   * @param maxs feature maximums
   * @return normalized features (0-1 range)
   */
  public static double[] normalizeMinMax(double[] features, double[] mins, double[] maxs) {
    double[] normalized = new double[features.length];
    for (int i = 0; i < features.length; i++) {
      double range = maxs[i] - mins[i];
      if (range > 1e-10) {
        normalized[i] = (features[i] - mins[i]) / range;
      } else {
        normalized[i] = 0.5;
      }
    }
    return normalized;
  }
}
