package neqsim.thermo.util.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable, uncertainty-aware comparison of thermodynamic model predictions with experimental data.
 *
 * <p>
 * Experimental values, units, composition, uncertainty, and provenance remain attached to every point so benchmark
 * results are auditable and suitable for regression testing.
 * </p>
 */
public final class ThermodynamicBenchmark {
  private ThermodynamicBenchmark() {
  }

  /** Thermodynamic properties supported by the benchmark framework. */
  public enum Property {
    /** Bubble-point pressure. */
    BUBBLE_POINT_PRESSURE,
    /** Dew-point pressure. */
    DEW_POINT_PRESSURE,
    /** Mass density. */
    DENSITY,
    /** Speed of sound. */
    SPEED_OF_SOUND,
    /** Dynamic viscosity. */
    VISCOSITY,
    /** Thermal conductivity. */
    THERMAL_CONDUCTIVITY,
    /** Isobaric heat capacity. */
    HEAT_CAPACITY_CP,
    /** Water content. */
    WATER_CONTENT
  }

  /** Immutable experimental benchmark point. */
  public static final class Point {
    private final Property property;
    private final double temperatureK;
    private final double pressureBara;
    private final double experimentalValue;
    private final double standardUncertainty;
    private final String unit;
    private final Map<String, Double> composition;

    /**
     * Creates an experimental point.
     *
     * @param property measured property
     * @param temperatureK absolute temperature in K
     * @param pressureBara absolute pressure in bara
     * @param experimentalValue measured value in {@code unit}
     * @param standardUncertainty standard uncertainty in {@code unit}, or {@link Double#NaN}
     * @param unit unit of the measured value
     * @param composition mole-fraction composition
     */
    public Point(Property property, double temperatureK, double pressureBara, double experimentalValue,
        double standardUncertainty, String unit, Map<String, Double> composition) {
      if (property == null || unit == null || unit.trim().isEmpty() || composition == null || composition.isEmpty()) {
        throw new IllegalArgumentException("Property, unit, and composition are required");
      }
      if (!Double.isFinite(temperatureK) || temperatureK <= 0.0 || !Double.isFinite(experimentalValue)) {
        throw new IllegalArgumentException("Temperature and experimental value must be finite");
      }
      double compositionSum = 0.0;
      Map<String, Double> compositionCopy = new LinkedHashMap<String, Double>();
      for (Map.Entry<String, Double> entry : composition.entrySet()) {
        if (entry.getKey() == null || entry.getKey().trim().isEmpty() || entry.getValue() == null
            || !Double.isFinite(entry.getValue()) || entry.getValue() < 0.0) {
          throw new IllegalArgumentException("Composition entries must be named and non-negative");
        }
        compositionCopy.put(entry.getKey(), entry.getValue());
        compositionSum += entry.getValue();
      }
      if (Math.abs(compositionSum - 1.0) > 1.0e-8) {
        throw new IllegalArgumentException("Mole fractions must sum to one");
      }
      this.property = property;
      this.temperatureK = temperatureK;
      this.pressureBara = pressureBara;
      this.experimentalValue = experimentalValue;
      this.standardUncertainty = standardUncertainty;
      this.unit = unit;
      this.composition = Collections.unmodifiableMap(compositionCopy);
    }

    /** @return measured property */
    public Property getProperty() {
      return property;
    }

    /** @return absolute temperature in K */
    public double getTemperatureK() {
      return temperatureK;
    }

    /** @return absolute pressure in bara */
    public double getPressureBara() {
      return pressureBara;
    }

    /** @return experimental value */
    public double getExperimentalValue() {
      return experimentalValue;
    }

    /** @return reported standard uncertainty, or NaN when unavailable */
    public double getStandardUncertainty() {
      return standardUncertainty;
    }

    /** @return property unit */
    public String getUnit() {
      return unit;
    }

    /** @return immutable mole-fraction composition */
    public Map<String, Double> getComposition() {
      return composition;
    }
  }

  /** Immutable experimental dataset with source provenance. */
  public static final class Dataset {
    private final String name;
    private final String citation;
    private final String doi;
    private final String license;
    private final List<Point> points;

    /**
     * Creates a dataset.
     *
     * @param name dataset name
     * @param citation full source citation
     * @param doi source DOI
     * @param license reuse statement for the encoded data
     * @param points experimental points
     */
    public Dataset(String name, String citation, String doi, String license, List<Point> points) {
      if (name == null || name.trim().isEmpty() || citation == null || doi == null || license == null || points == null
          || points.isEmpty()) {
        throw new IllegalArgumentException("Dataset metadata and points are required");
      }
      this.name = name;
      this.citation = citation;
      this.doi = doi;
      this.license = license;
      this.points = Collections.unmodifiableList(new ArrayList<Point>(points));
    }

    /** @return dataset name */
    public String getName() {
      return name;
    }

    /** @return source citation */
    public String getCitation() {
      return citation;
    }

    /** @return source DOI */
    public String getDoi() {
      return doi;
    }

    /** @return data reuse statement */
    public String getLicense() {
      return license;
    }

    /** @return immutable experimental points */
    public List<Point> getPoints() {
      return points;
    }
  }

  /** Supplies a model prediction for one experimental point. */
  public interface Prediction {
    /**
     * Predicts the property represented by a point.
     *
     * @param point experimental point defining state and composition
     * @return predicted value in the point's unit
     * @throws Exception when the model cannot calculate the point
     */
    double predict(Point point) throws Exception;
  }

  /** Result for one experimental point. */
  public static final class Row {
    private final Point point;
    private final double predictedValue;
    private final double signedRelativeErrorPercent;
    private final double uncertaintyNormalizedResidual;

    private Row(Point point, double predictedValue) {
      this.point = point;
      this.predictedValue = predictedValue;
      this.signedRelativeErrorPercent = 100.0 * (predictedValue - point.getExperimentalValue())
          / point.getExperimentalValue();
      double uncertainty = point.getStandardUncertainty();
      this.uncertaintyNormalizedResidual = Double.isFinite(uncertainty) && uncertainty > 0.0
          ? (predictedValue - point.getExperimentalValue()) / uncertainty
          : Double.NaN;
    }

    /** @return experimental point */
    public Point getPoint() {
      return point;
    }

    /** @return predicted value */
    public double getPredictedValue() {
      return predictedValue;
    }

    /** @return signed relative error in percent */
    public double getSignedRelativeErrorPercent() {
      return signedRelativeErrorPercent;
    }

    /** @return residual divided by standard uncertainty, or NaN when unavailable */
    public double getUncertaintyNormalizedResidual() {
      return uncertaintyNormalizedResidual;
    }
  }

  /** Immutable aggregate benchmark report. */
  public static final class Report {
    private final String modelName;
    private final Dataset dataset;
    private final List<Row> rows;
    private final double averageAbsoluteRelativeDeviationPercent;
    private final double biasPercent;
    private final double rootMeanSquareRelativeErrorPercent;
    private final double maximumAbsoluteRelativeErrorPercent;

    private Report(String modelName, Dataset dataset, List<Row> rows) {
      this.modelName = modelName;
      this.dataset = dataset;
      this.rows = Collections.unmodifiableList(new ArrayList<Row>(rows));
      double absoluteErrorSum = 0.0;
      double signedErrorSum = 0.0;
      double squaredErrorSum = 0.0;
      double maximumError = 0.0;
      for (Row row : rows) {
        double error = row.getSignedRelativeErrorPercent();
        absoluteErrorSum += Math.abs(error);
        signedErrorSum += error;
        squaredErrorSum += error * error;
        maximumError = Math.max(maximumError, Math.abs(error));
      }
      this.averageAbsoluteRelativeDeviationPercent = absoluteErrorSum / rows.size();
      this.biasPercent = signedErrorSum / rows.size();
      this.rootMeanSquareRelativeErrorPercent = Math.sqrt(squaredErrorSum / rows.size());
      this.maximumAbsoluteRelativeErrorPercent = maximumError;
    }

    /** @return model name */
    public String getModelName() {
      return modelName;
    }

    /** @return source dataset */
    public Dataset getDataset() {
      return dataset;
    }

    /** @return immutable point results */
    public List<Row> getRows() {
      return rows;
    }

    /** @return average absolute relative deviation in percent */
    public double getAverageAbsoluteRelativeDeviationPercent() {
      return averageAbsoluteRelativeDeviationPercent;
    }

    /** @return mean signed relative error in percent */
    public double getBiasPercent() {
      return biasPercent;
    }

    /** @return root mean square relative error in percent */
    public double getRootMeanSquareRelativeErrorPercent() {
      return rootMeanSquareRelativeErrorPercent;
    }

    /** @return maximum absolute relative error in percent */
    public double getMaximumAbsoluteRelativeErrorPercent() {
      return maximumAbsoluteRelativeErrorPercent;
    }
  }

  /**
   * Runs a model over all points in a dataset.
   *
   * @param modelName auditable model name
   * @param dataset experimental dataset
   * @param prediction prediction implementation
   * @return aggregate report
   * @throws Exception when any prediction fails or is non-finite
   */
  public static Report run(String modelName, Dataset dataset, Prediction prediction) throws Exception {
    if (modelName == null || modelName.trim().isEmpty() || dataset == null || prediction == null) {
      throw new IllegalArgumentException("Model name, dataset, and prediction are required");
    }
    List<Row> rows = new ArrayList<Row>();
    for (Point point : dataset.getPoints()) {
      double predictedValue = prediction.predict(point);
      if (!Double.isFinite(predictedValue)) {
        throw new IllegalStateException("Non-finite prediction for " + point.getProperty());
      }
      rows.add(new Row(point, predictedValue));
    }
    return new Report(modelName, dataset, rows);
  }
}
