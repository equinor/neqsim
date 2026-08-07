package neqsim.process.equipment.pipeline.twophasepipe.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Utilities for comparing a severe-slugging classifier with a public flow-regime map. */
public final class SevereSluggingBenchmarkHarness {
  private SevereSluggingBenchmarkHarness() {
  }

  /** Experimentally observed flow-regime category. */
  public enum ObservedRegime {
    /** A cyclic severe-slugging observation. */
    SEVERE_SLUG,
    /** A transition observation, excluded from the binary confusion matrix. */
    TRANSITION,
    /** Stable operation. */
    STABLE
  }

  /** Binary prediction used by a system stability screen. */
  public enum Prediction {
    /** Severe slugging is possible. */
    SEVERE_SLUG,
    /** The screened operating point is stable. */
    STABLE
  }

  /** Classifier callback for one experimental operating point. */
  public interface Classifier {
    /**
     * Classify an operating point.
     *
     * @param point experimental operating point
     * @return binary model prediction
     */
    Prediction classify(FlowMapPoint point);
  }

  /** Read a public experimental flow map from CSV. */
  public static List<FlowMapPoint> readCsv(Path csvPath) throws IOException {
    List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
    if (lines.isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, Integer> header = parseHeader(lines.get(0));
    List<FlowMapPoint> points = new ArrayList<>();
    for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber).trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] fields = line.split(",", -1);
      points.add(new FlowMapPoint(getString(fields, header, "case", ""), (int) getDouble(fields, header, "run", -1.0),
          getDouble(fields, header, "vsl_m_s", Double.NaN), getDouble(fields, header, "vsg_std_m_s", Double.NaN),
          getDouble(fields, header, "vsl_uncertainty_m_s", Double.NaN),
          getDouble(fields, header, "vsg_uncertainty_m_s", Double.NaN),
          ObservedRegime.valueOf(getString(fields, header, "observed_regime", "").toUpperCase(Locale.ROOT)),
          getString(fields, header, "classification_digitization_uncertainty", ""),
          getString(fields, header, "source", "")));
    }
    return Collections.unmodifiableList(points);
  }

  /** Compare a binary classifier with stable and severe observations, keeping transition counts separate. */
  public static ConfusionMatrix compare(List<FlowMapPoint> points, Classifier classifier) {
    if (points == null || classifier == null) {
      throw new IllegalArgumentException("points and classifier must not be null");
    }
    int truePositive = 0;
    int falseNegative = 0;
    int falsePositive = 0;
    int trueNegative = 0;
    int transitionPredictedSevere = 0;
    int transitionPredictedStable = 0;
    for (FlowMapPoint point : points) {
      Prediction prediction = classifier.classify(point);
      if (prediction == null) {
        throw new IllegalArgumentException("classifier must not return null");
      }
      switch (point.getObservedRegime()) {
      case SEVERE_SLUG:
        if (prediction == Prediction.SEVERE_SLUG) {
          truePositive++;
        } else {
          falseNegative++;
        }
        break;
      case STABLE:
        if (prediction == Prediction.SEVERE_SLUG) {
          falsePositive++;
        } else {
          trueNegative++;
        }
        break;
      case TRANSITION:
        if (prediction == Prediction.SEVERE_SLUG) {
          transitionPredictedSevere++;
        } else {
          transitionPredictedStable++;
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported observed regime: " + point.getObservedRegime());
      }
    }
    return new ConfusionMatrix(truePositive, falseNegative, falsePositive, trueNegative, transitionPredictedSevere,
        transitionPredictedStable);
  }

  private static Map<String, Integer> parseHeader(String line) {
    String[] fields = line.split(",", -1);
    Map<String, Integer> header = new HashMap<>();
    for (int i = 0; i < fields.length; i++) {
      header.put(normalize(fields[i]), i);
    }
    return header;
  }

  private static String getString(String[] fields, Map<String, Integer> header, String key, String defaultValue) {
    Integer index = header.get(normalize(key));
    if (index == null || index >= fields.length) {
      return defaultValue;
    }
    return fields[index].trim();
  }

  private static double getDouble(String[] fields, Map<String, Integer> header, String key, double defaultValue) {
    String value = getString(fields, header, key, "");
    return value.isEmpty() ? defaultValue : Double.parseDouble(value);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  /** One digitized point from a public experimental flow-regime map. */
  public static final class FlowMapPoint {
    private final String caseName;
    private final int runNumber;
    private final double superficialLiquidVelocityMPerS;
    private final double superficialGasVelocityAtStandardConditionsMPerS;
    private final double superficialLiquidVelocityUncertaintyMPerS;
    private final double superficialGasVelocityUncertaintyMPerS;
    private final ObservedRegime observedRegime;
    private final String classificationDigitizationUncertainty;
    private final String source;

    public FlowMapPoint(String caseName, int runNumber, double superficialLiquidVelocityMPerS,
        double superficialGasVelocityAtStandardConditionsMPerS, double superficialLiquidVelocityUncertaintyMPerS,
        double superficialGasVelocityUncertaintyMPerS, ObservedRegime observedRegime,
        String classificationDigitizationUncertainty, String source) {
      if (runNumber < 0 || !positiveFinite(superficialLiquidVelocityMPerS)
          || !positiveFinite(superficialGasVelocityAtStandardConditionsMPerS)
          || !nonNegativeFinite(superficialLiquidVelocityUncertaintyMPerS)
          || !nonNegativeFinite(superficialGasVelocityUncertaintyMPerS) || observedRegime == null) {
        throw new IllegalArgumentException("Flow-map point contains invalid values");
      }
      this.caseName = caseName;
      this.runNumber = runNumber;
      this.superficialLiquidVelocityMPerS = superficialLiquidVelocityMPerS;
      this.superficialGasVelocityAtStandardConditionsMPerS = superficialGasVelocityAtStandardConditionsMPerS;
      this.superficialLiquidVelocityUncertaintyMPerS = superficialLiquidVelocityUncertaintyMPerS;
      this.superficialGasVelocityUncertaintyMPerS = superficialGasVelocityUncertaintyMPerS;
      this.observedRegime = observedRegime;
      this.classificationDigitizationUncertainty = classificationDigitizationUncertainty;
      this.source = source;
    }

    public String getCaseName() {
      return caseName;
    }

    public int getRunNumber() {
      return runNumber;
    }

    public double getSuperficialLiquidVelocityMPerS() {
      return superficialLiquidVelocityMPerS;
    }

    public double getSuperficialGasVelocityAtStandardConditionsMPerS() {
      return superficialGasVelocityAtStandardConditionsMPerS;
    }

    public double getSuperficialLiquidVelocityUncertaintyMPerS() {
      return superficialLiquidVelocityUncertaintyMPerS;
    }

    public double getSuperficialGasVelocityUncertaintyMPerS() {
      return superficialGasVelocityUncertaintyMPerS;
    }

    public ObservedRegime getObservedRegime() {
      return observedRegime;
    }

    public String getClassificationDigitizationUncertainty() {
      return classificationDigitizationUncertainty;
    }

    public String getSource() {
      return source;
    }

    private static boolean positiveFinite(double value) {
      return Double.isFinite(value) && value > 0.0;
    }

    private static boolean nonNegativeFinite(double value) {
      return Double.isFinite(value) && value >= 0.0;
    }
  }

  /** Binary confusion matrix with transition observations reported but not scored. */
  public static final class ConfusionMatrix {
    private final int truePositive;
    private final int falseNegative;
    private final int falsePositive;
    private final int trueNegative;
    private final int transitionPredictedSevere;
    private final int transitionPredictedStable;

    private ConfusionMatrix(int truePositive, int falseNegative, int falsePositive, int trueNegative,
        int transitionPredictedSevere, int transitionPredictedStable) {
      this.truePositive = truePositive;
      this.falseNegative = falseNegative;
      this.falsePositive = falsePositive;
      this.trueNegative = trueNegative;
      this.transitionPredictedSevere = transitionPredictedSevere;
      this.transitionPredictedStable = transitionPredictedStable;
    }

    public int getTruePositive() {
      return truePositive;
    }

    public int getFalseNegative() {
      return falseNegative;
    }

    public int getFalsePositive() {
      return falsePositive;
    }

    public int getTrueNegative() {
      return trueNegative;
    }

    public int getTransitionPredictedSevere() {
      return transitionPredictedSevere;
    }

    public int getTransitionPredictedStable() {
      return transitionPredictedStable;
    }

    public int getScoredCount() {
      return truePositive + falseNegative + falsePositive + trueNegative;
    }

    public int getTransitionCount() {
      return transitionPredictedSevere + transitionPredictedStable;
    }

    public double getAccuracy() {
      return safeDivide(truePositive + trueNegative, getScoredCount());
    }

    public double getSevereSlugRecall() {
      return safeDivide(truePositive, truePositive + falseNegative);
    }

    public double getStableRecall() {
      return safeDivide(trueNegative, trueNegative + falsePositive);
    }

    private static double safeDivide(int numerator, int denominator) {
      return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }
  }
}
