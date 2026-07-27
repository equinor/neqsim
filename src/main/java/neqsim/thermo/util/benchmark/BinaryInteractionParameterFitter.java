package neqsim.thermo.util.benchmark;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Dataset;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Point;

/**
 * Bounded one-dimensional regression of a constant cubic-EOS binary interaction parameter.
 *
 * <p>The objective is the mean squared relative error. This gives bubble- and dew-point
 * pressures comparable influence even when their absolute pressure levels differ.</p>
 */
public final class BinaryInteractionParameterFitter {
  /** Creates model predictions for a trial binary interaction parameter. */
  public interface PredictionFactory {
    /**
     * Creates a prediction function for one trial parameter.
     *
     * @param binaryInteractionParameter trial dimensionless binary interaction parameter
     * @return model prediction function
     */
    ThermodynamicBenchmark.Prediction create(double binaryInteractionParameter);
  }

  /** Immutable scalar-regression result. */
  public static final class Result {
    private final double binaryInteractionParameter;
    private final double rootMeanSquareRelativeErrorPercent;
    private final int objectiveEvaluations;

    private Result(double binaryInteractionParameter, double rootMeanSquareRelativeErrorPercent,
        int objectiveEvaluations) {
      this.binaryInteractionParameter = binaryInteractionParameter;
      this.rootMeanSquareRelativeErrorPercent = rootMeanSquareRelativeErrorPercent;
      this.objectiveEvaluations = objectiveEvaluations;
    }

    /** @return fitted dimensionless binary interaction parameter */
    public double getBinaryInteractionParameter() {
      return binaryInteractionParameter;
    }

    /** @return in-sample root-mean-square relative error in percent */
    public double getRootMeanSquareRelativeErrorPercent() {
      return rootMeanSquareRelativeErrorPercent;
    }

    /** @return number of objective evaluations */
    public int getObjectiveEvaluations() {
      return objectiveEvaluations;
    }
  }

  private final Dataset dataset;
  private final PredictionFactory predictionFactory;

  /**
   * Creates a scalar binary-interaction-parameter regression.
   *
   * @param dataset experimental calibration dataset
   * @param predictionFactory model adapter factory
   */
  public BinaryInteractionParameterFitter(Dataset dataset, PredictionFactory predictionFactory) {
    if (dataset == null || predictionFactory == null) {
      throw new IllegalArgumentException("Dataset and prediction factory are required");
    }
    this.dataset = dataset;
    this.predictionFactory = predictionFactory;
  }

  /**
   * Fits within a closed interval using golden-section minimization.
   *
   * @param lowerBound inclusive lower parameter bound
   * @param upperBound inclusive upper parameter bound
   * @param tolerance parameter-space convergence tolerance
   * @param maximumEvaluations maximum objective evaluations
   * @return fitted parameter and in-sample objective
   * @throws Exception when a model prediction fails
   */
  public Result fit(double lowerBound, double upperBound, double tolerance, int maximumEvaluations)
      throws Exception {
    if (!Double.isFinite(lowerBound) || !Double.isFinite(upperBound)
        || lowerBound >= upperBound || !Double.isFinite(tolerance) || tolerance <= 0.0
        || maximumEvaluations < 3) {
      throw new IllegalArgumentException(
          "Valid bounds, tolerance, and evaluation limit are required");
    }

    double left = lowerBound;
    double right = upperBound;
    double inverseGoldenRatio = (Math.sqrt(5.0) - 1.0) / 2.0;
    double innerLeft = right - inverseGoldenRatio * (right - left);
    double innerRight = left + inverseGoldenRatio * (right - left);
    double objectiveLeft = objective(innerLeft);
    double objectiveRight = objective(innerRight);
    int evaluations = 2;

    while (right - left > tolerance && evaluations < maximumEvaluations) {
      if (objectiveLeft <= objectiveRight) {
        right = innerRight;
        innerRight = innerLeft;
        objectiveRight = objectiveLeft;
        innerLeft = right - inverseGoldenRatio * (right - left);
        objectiveLeft = objective(innerLeft);
      } else {
        left = innerLeft;
        innerLeft = innerRight;
        objectiveLeft = objectiveRight;
        innerRight = left + inverseGoldenRatio * (right - left);
        objectiveRight = objective(innerRight);
      }
      evaluations++;
    }

    double fittedParameter;
    double fittedObjective;
    if (objectiveLeft <= objectiveRight) {
      fittedParameter = innerLeft;
      fittedObjective = objectiveLeft;
    } else {
      fittedParameter = innerRight;
      fittedObjective = objectiveRight;
    }
    return new Result(fittedParameter, 100.0 * Math.sqrt(fittedObjective), evaluations);
  }

  /**
   * Returns a dataset containing only selected point indexes.
   *
   * @param source source dataset
   * @param indexes zero-based point indexes
   * @param name descriptive subset name
   * @return immutable dataset subset retaining source provenance
   */
  public static Dataset subset(Dataset source, List<Integer> indexes, String name) {
    if (source == null || indexes == null || indexes.isEmpty() || name == null
        || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Source, indexes, and subset name are required");
    }
    List<Point> points = new ArrayList<Point>();
    for (Integer index : indexes) {
      if (index == null || index < 0 || index >= source.getPoints().size()) {
        throw new IllegalArgumentException("Subset index is outside the source dataset");
      }
      points.add(source.getPoints().get(index));
    }
    return new Dataset(name, source.getCitation(), source.getDoi(), source.getLicense(), points);
  }

  private double objective(double binaryInteractionParameter) throws Exception {
    ThermodynamicBenchmark.Prediction prediction =
        predictionFactory.create(binaryInteractionParameter);
    double squaredRelativeErrorSum = 0.0;
    for (Point point : dataset.getPoints()) {
      double predictedValue = prediction.predict(point);
      if (!Double.isFinite(predictedValue)) {
        throw new IllegalStateException("Non-finite prediction during parameter regression");
      }
      double relativeError =
          (predictedValue - point.getExperimentalValue()) / point.getExperimentalValue();
      squaredRelativeErrorSum += relativeError * relativeError;
    }
    return squaredRelativeErrorSum / dataset.getPoints().size();
  }
}
