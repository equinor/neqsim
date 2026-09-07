package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * Weighted linear regression for one-state binary-electrolyte volumetric Pitzer parameters.
 *
 * <p>
 * The regression is parameter and dataset neutral. Callers supply apparent-molar-volume
 * observations, one-sigma uncertainties, source-group identifiers, and the Debye-Huckel volume
 * slope at a common temperature and pressure. The fit estimates the limiting apparent molar
 * volume and the three binary interaction pressure derivatives used by
 * {@link PitzerBinaryVolumetricModel}.
 * </p>
 *
 * <p>
 * Weighted design columns are normalized before singular-value decomposition. Rank-deficient and
 * excessively ill-conditioned designs fail closed. Returned covariance assumes that the supplied
 * standard uncertainties are absolute one-sigma uncertainties.
 * </p>
 */
public final class PitzerBinaryVolumetricRegression implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final int PARAMETER_COUNT = 4;
  private static final double RELATIVE_RANK_TOLERANCE = 1.0e-12;
  private static final double MAXIMUM_CONDITION_NUMBER = 1.0e10;

  private final PitzerBinaryVolumetricModel model;

  /**
   * Construct a regression for one binary-electrolyte model.
   *
   * @param model parameter-neutral binary volumetric Pitzer model
   */
  public PitzerBinaryVolumetricRegression(PitzerBinaryVolumetricModel model) {
    if (model == null) {
      throw new IllegalArgumentException("Binary volumetric Pitzer model must not be null");
    }
    this.model = model;
  }

  /**
   * Fit one common temperature-pressure state.
   *
   * @param observations apparent-molar-volume observations with absolute one-sigma uncertainties
   * @param temperatureK common temperature in K
   * @param pressurePa common absolute pressure in Pa
   * @param debyeHuckelVolumeSlope caller-supplied Debye-Huckel volume slope in SI units
   * @return immutable fit result and diagnostics
   */
  public FitResult fit(List<Observation> observations, double temperatureK, double pressurePa,
      double debyeHuckelVolumeSlope) {
    requirePositive(temperatureK, "Regression temperature");
    requirePositive(pressurePa, "Regression pressure");
    requireFinite(debyeHuckelVolumeSlope, "Debye-Huckel volume slope");
    if (observations == null || observations.size() <= PARAMETER_COUNT) {
      throw new IllegalArgumentException(
          "Volumetric Pitzer regression requires at least five observations");
    }

    int observationCount = observations.size();
    double[][] weightedDesign = new double[observationCount][PARAMETER_COUNT];
    double[] weightedResponse = new double[observationCount];
    double[][] unweightedDesign = new double[observationCount][PARAMETER_COUNT];
    double[] debyeContributions = new double[observationCount];

    PitzerBinaryVolumetricModel.StateParameters debyeParameters =
        new PitzerBinaryVolumetricModel.StateParameters(temperatureK, pressurePa,
            debyeHuckelVolumeSlope, 0.0, 0.0, 0.0);
    PitzerBinaryVolumetricModel.StateParameters beta0Unit =
        new PitzerBinaryVolumetricModel.StateParameters(temperatureK, pressurePa, 0.0, 1.0, 0.0,
            0.0);
    PitzerBinaryVolumetricModel.StateParameters beta1Unit =
        new PitzerBinaryVolumetricModel.StateParameters(temperatureK, pressurePa, 0.0, 0.0, 1.0,
            0.0);
    PitzerBinaryVolumetricModel.StateParameters cphiUnit =
        new PitzerBinaryVolumetricModel.StateParameters(temperatureK, pressurePa, 0.0, 0.0, 0.0,
            1.0);

    for (int row = 0; row < observationCount; row++) {
      Observation observation = observations.get(row);
      if (observation == null) {
        throw new IllegalArgumentException("Volumetric Pitzer observations must not contain null");
      }
      double molality = observation.getMolality();
      double debyeContribution =
          model.calculateApparentMolarVolume(molality, 0.0, debyeParameters);
      debyeContributions[row] = debyeContribution;

      double[] designRow = unweightedDesign[row];
      designRow[0] = 1.0;
      designRow[1] = model.calculateApparentMolarVolume(molality, 0.0, beta0Unit);
      designRow[2] = model.calculateApparentMolarVolume(molality, 0.0, beta1Unit);
      designRow[3] = model.calculateApparentMolarVolume(molality, 0.0, cphiUnit);

      double inverseUncertainty = 1.0 / observation.getStandardUncertainty();
      weightedResponse[row] =
          (observation.getApparentMolarVolume() - debyeContribution) * inverseUncertainty;
      for (int column = 0; column < PARAMETER_COUNT; column++) {
        weightedDesign[row][column] = designRow[column] * inverseUncertainty;
      }
    }

    double[] columnNorms = calculateColumnNorms(weightedDesign);
    double[][] scaledDesign = new double[observationCount][PARAMETER_COUNT];
    for (int row = 0; row < observationCount; row++) {
      for (int column = 0; column < PARAMETER_COUNT; column++) {
        scaledDesign[row][column] = weightedDesign[row][column] / columnNorms[column];
      }
    }

    RealMatrix designMatrix = new Array2DRowRealMatrix(scaledDesign, false);
    SingularValueDecomposition decomposition = new SingularValueDecomposition(designMatrix);
    double[] singularValues = decomposition.getSingularValues();
    double largestSingularValue = singularValues[0];
    double rankTolerance = RELATIVE_RANK_TOLERANCE * largestSingularValue;
    int rank = 0;
    double smallestRetainedSingularValue = Double.POSITIVE_INFINITY;
    for (double singularValue : singularValues) {
      if (singularValue > rankTolerance) {
        rank++;
        smallestRetainedSingularValue =
            Math.min(smallestRetainedSingularValue, singularValue);
      }
    }
    if (rank < PARAMETER_COUNT) {
      throw new IllegalArgumentException(
          "Volumetric Pitzer regression design is rank deficient: rank " + rank + " of "
              + PARAMETER_COUNT);
    }

    double conditionNumber = largestSingularValue / smallestRetainedSingularValue;
    if (!Double.isFinite(conditionNumber) || conditionNumber > MAXIMUM_CONDITION_NUMBER) {
      throw new IllegalArgumentException(
          "Volumetric Pitzer regression design is ill-conditioned: scaled condition number "
              + conditionNumber);
    }

    RealVector scaledSolution =
        decomposition.getSolver().solve(new ArrayRealVector(weightedResponse, false));
    double[] coefficients = new double[PARAMETER_COUNT];
    for (int index = 0; index < PARAMETER_COUNT; index++) {
      coefficients[index] = scaledSolution.getEntry(index) / columnNorms[index];
      requireFinite(coefficients[index], "Fitted volumetric Pitzer coefficient");
    }

    double[][] covariance = calculateCovariance(decomposition, singularValues, columnNorms);
    double[] standardUncertainties = new double[PARAMETER_COUNT];
    for (int index = 0; index < PARAMETER_COUNT; index++) {
      double variance = covariance[index][index];
      if (!Double.isFinite(variance) || variance < 0.0) {
        throw new IllegalArgumentException(
            "Volumetric Pitzer regression produced an invalid coefficient variance");
      }
      standardUncertainties[index] = Math.sqrt(variance);
    }

    PitzerBinaryVolumetricModel.StateParameters fittedParameters =
        new PitzerBinaryVolumetricModel.StateParameters(temperatureK, pressurePa,
            debyeHuckelVolumeSlope, coefficients[1], coefficients[2], coefficients[3]);

    double chiSquare = 0.0;
    double maximumAbsoluteStandardizedResidual = 0.0;
    Map<String, MutableGroupStatistics> mutableGroups =
        new TreeMap<String, MutableGroupStatistics>();
    for (int row = 0; row < observationCount; row++) {
      Observation observation = observations.get(row);
      double fittedVolume = debyeContributions[row];
      for (int column = 0; column < PARAMETER_COUNT; column++) {
        fittedVolume += unweightedDesign[row][column] * coefficients[column];
      }
      double residual = observation.getApparentMolarVolume() - fittedVolume;
      double standardizedResidual = residual / observation.getStandardUncertainty();
      requireFinite(standardizedResidual, "Standardized volumetric Pitzer residual");
      chiSquare += standardizedResidual * standardizedResidual;
      maximumAbsoluteStandardizedResidual =
          Math.max(maximumAbsoluteStandardizedResidual, Math.abs(standardizedResidual));

      MutableGroupStatistics group = mutableGroups.get(observation.getSourceGroup());
      if (group == null) {
        group = new MutableGroupStatistics(observation.getSourceGroup());
        mutableGroups.put(observation.getSourceGroup(), group);
      }
      group.add(residual, standardizedResidual);
    }

    int degreesOfFreedom = observationCount - PARAMETER_COUNT;
    List<GroupStatistics> groups = new ArrayList<GroupStatistics>();
    for (MutableGroupStatistics group : mutableGroups.values()) {
      groups.add(group.toImmutable());
    }

    return new FitResult(coefficients[0], fittedParameters, standardUncertainties, covariance,
        observationCount, degreesOfFreedom, chiSquare, conditionNumber,
        maximumAbsoluteStandardizedResidual, groups);
  }

  private static double[] calculateColumnNorms(double[][] design) {
    double[] norms = new double[PARAMETER_COUNT];
    for (double[] row : design) {
      for (int column = 0; column < PARAMETER_COUNT; column++) {
        norms[column] = Math.hypot(norms[column], row[column]);
      }
    }
    for (int column = 0; column < PARAMETER_COUNT; column++) {
      if (!Double.isFinite(norms[column]) || norms[column] <= 0.0) {
        throw new IllegalArgumentException(
            "Volumetric Pitzer regression has an empty or non-finite design column");
      }
    }
    return norms;
  }

  private static double[][] calculateCovariance(SingularValueDecomposition decomposition,
      double[] singularValues, double[] columnNorms) {
    RealMatrix rightSingularVectors = decomposition.getV();
    double[][] covariance = new double[PARAMETER_COUNT][PARAMETER_COUNT];
    for (int row = 0; row < PARAMETER_COUNT; row++) {
      for (int column = 0; column < PARAMETER_COUNT; column++) {
        double scaledCovariance = 0.0;
        for (int mode = 0; mode < PARAMETER_COUNT; mode++) {
          scaledCovariance += rightSingularVectors.getEntry(row, mode)
              * rightSingularVectors.getEntry(column, mode)
              / (singularValues[mode] * singularValues[mode]);
        }
        covariance[row][column] =
            scaledCovariance / (columnNorms[row] * columnNorms[column]);
        requireFinite(covariance[row][column], "Volumetric Pitzer coefficient covariance");
      }
    }
    return covariance;
  }

  private static void requirePositive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void requireFinite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static double[][] copyMatrix(double[][] source) {
    double[][] copy = new double[source.length][];
    for (int row = 0; row < source.length; row++) {
      copy[row] = source[row].clone();
    }
    return copy;
  }

  /** One caller-supplied apparent-molar-volume observation. */
  public static final class Observation implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double molality;
    private final double apparentMolarVolume;
    private final double standardUncertainty;
    private final String sourceGroup;

    /**
     * Construct an observation.
     *
     * @param molality formula-unit molality in mol/kg solvent
     * @param apparentMolarVolume apparent molar volume in m3/mol
     * @param standardUncertainty absolute one-sigma uncertainty in m3/mol
     * @param sourceGroup non-empty laboratory or source-lineage identifier
     */
    public Observation(double molality, double apparentMolarVolume, double standardUncertainty,
        String sourceGroup) {
      if (!Double.isFinite(molality) || molality < 0.0) {
        throw new IllegalArgumentException("Observation molality must be finite and non-negative");
      }
      requireFinite(apparentMolarVolume, "Observed apparent molar volume");
      requirePositive(standardUncertainty, "Observed apparent-molar-volume uncertainty");
      if (sourceGroup == null || sourceGroup.trim().isEmpty()) {
        throw new IllegalArgumentException("Observation source group must not be empty");
      }
      this.molality = molality;
      this.apparentMolarVolume = apparentMolarVolume;
      this.standardUncertainty = standardUncertainty;
      this.sourceGroup = sourceGroup.trim();
    }

    /** @return formula-unit molality in mol/kg solvent */
    public double getMolality() {
      return molality;
    }

    /** @return apparent molar volume in m3/mol */
    public double getApparentMolarVolume() {
      return apparentMolarVolume;
    }

    /** @return absolute one-sigma uncertainty in m3/mol */
    public double getStandardUncertainty() {
      return standardUncertainty;
    }

    /** @return laboratory or source-lineage identifier */
    public String getSourceGroup() {
      return sourceGroup;
    }
  }

  /** Immutable weighted-regression result. */
  public static final class FitResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double limitingApparentMolarVolume;
    private final PitzerBinaryVolumetricModel.StateParameters stateParameters;
    private final double[] parameterStandardUncertainties;
    private final double[][] parameterCovariance;
    private final int observationCount;
    private final int degreesOfFreedom;
    private final double chiSquare;
    private final double conditionNumber;
    private final double maximumAbsoluteStandardizedResidual;
    private final List<GroupStatistics> groupStatistics;

    private FitResult(double limitingApparentMolarVolume,
        PitzerBinaryVolumetricModel.StateParameters stateParameters,
        double[] parameterStandardUncertainties, double[][] parameterCovariance,
        int observationCount, int degreesOfFreedom, double chiSquare, double conditionNumber,
        double maximumAbsoluteStandardizedResidual, List<GroupStatistics> groupStatistics) {
      this.limitingApparentMolarVolume = limitingApparentMolarVolume;
      this.stateParameters = stateParameters;
      this.parameterStandardUncertainties = parameterStandardUncertainties.clone();
      this.parameterCovariance = copyMatrix(parameterCovariance);
      this.observationCount = observationCount;
      this.degreesOfFreedom = degreesOfFreedom;
      this.chiSquare = chiSquare;
      this.conditionNumber = conditionNumber;
      this.maximumAbsoluteStandardizedResidual = maximumAbsoluteStandardizedResidual;
      this.groupStatistics =
          Collections.unmodifiableList(new ArrayList<GroupStatistics>(groupStatistics));
    }

    /** @return fitted limiting apparent molar volume in m3/mol */
    public double getLimitingApparentMolarVolume() {
      return limitingApparentMolarVolume;
    }

    /** @return fitted state parameters including the caller-supplied Debye-Huckel slope */
    public PitzerBinaryVolumetricModel.StateParameters getStateParameters() {
      return stateParameters;
    }

    /**
     * Return parameter standard uncertainties.
     *
     * @return defensive copy ordered as V0, beta0 derivative, beta1 derivative, C-phi derivative
     */
    public double[] getParameterStandardUncertainties() {
      return parameterStandardUncertainties.clone();
    }

    /**
     * Return parameter covariance.
     *
     * @return defensive covariance copy in the same parameter order as the uncertainties
     */
    public double[][] getParameterCovariance() {
      return copyMatrix(parameterCovariance);
    }

    /** @return number of fitted observations */
    public int getObservationCount() {
      return observationCount;
    }

    /** @return observation count minus four fitted parameters */
    public int getDegreesOfFreedom() {
      return degreesOfFreedom;
    }

    /** @return sum of squared standardized residuals */
    public double getChiSquare() {
      return chiSquare;
    }

    /** @return chi-square divided by degrees of freedom */
    public double getReducedChiSquare() {
      return chiSquare / degreesOfFreedom;
    }

    /** @return root-mean-square standardized residual */
    public double getWeightedRootMeanSquareResidual() {
      return Math.sqrt(chiSquare / observationCount);
    }

    /** @return maximum absolute standardized residual */
    public double getMaximumAbsoluteStandardizedResidual() {
      return maximumAbsoluteStandardizedResidual;
    }

    /** @return condition number of the column-scaled weighted design matrix */
    public double getConditionNumber() {
      return conditionNumber;
    }

    /** @return immutable source-group diagnostics sorted by source identifier */
    public List<GroupStatistics> getGroupStatistics() {
      return groupStatistics;
    }
  }

  /** Immutable residual diagnostics for one source lineage. */
  public static final class GroupStatistics implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String sourceGroup;
    private final int count;
    private final double meanResidual;
    private final double weightedRootMeanSquareResidual;
    private final double maximumAbsoluteStandardizedResidual;

    private GroupStatistics(String sourceGroup, int count, double meanResidual,
        double weightedRootMeanSquareResidual, double maximumAbsoluteStandardizedResidual) {
      this.sourceGroup = sourceGroup;
      this.count = count;
      this.meanResidual = meanResidual;
      this.weightedRootMeanSquareResidual = weightedRootMeanSquareResidual;
      this.maximumAbsoluteStandardizedResidual = maximumAbsoluteStandardizedResidual;
    }

    /** @return laboratory or source-lineage identifier */
    public String getSourceGroup() {
      return sourceGroup;
    }

    /** @return number of observations in the source group */
    public int getCount() {
      return count;
    }

    /** @return arithmetic mean volume residual in m3/mol */
    public double getMeanResidual() {
      return meanResidual;
    }

    /** @return root-mean-square standardized residual for the group */
    public double getWeightedRootMeanSquareResidual() {
      return weightedRootMeanSquareResidual;
    }

    /** @return maximum absolute standardized residual for the group */
    public double getMaximumAbsoluteStandardizedResidual() {
      return maximumAbsoluteStandardizedResidual;
    }
  }

  private static final class MutableGroupStatistics {
    private final String sourceGroup;
    private int count;
    private double residualSum;
    private double standardizedResidualSquareSum;
    private double maximumAbsoluteStandardizedResidual;

    private MutableGroupStatistics(String sourceGroup) {
      this.sourceGroup = sourceGroup;
    }

    private void add(double residual, double standardizedResidual) {
      count++;
      residualSum += residual;
      standardizedResidualSquareSum += standardizedResidual * standardizedResidual;
      maximumAbsoluteStandardizedResidual =
          Math.max(maximumAbsoluteStandardizedResidual, Math.abs(standardizedResidual));
    }

    private GroupStatistics toImmutable() {
      return new GroupStatistics(sourceGroup, count, residualSum / count,
          Math.sqrt(standardizedResidualSquareSum / count),
          maximumAbsoluteStandardizedResidual);
    }
  }
}
