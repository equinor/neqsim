package neqsim.thermo.phase;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Independent source-group holdout validation for binary volumetric Pitzer regression.
 *
 * <p>
 * Calibration and validation observations are supplied separately. Their source-lineage identifiers must be disjoint,
 * preventing observations from the same declared laboratory lineage from appearing on both sides of the validation
 * boundary. The caller remains responsible for assigning identifiers that represent genuine experimental independence.
 * </p>
 *
 * <p>
 * This class fits no validation observation and installs no fitted parameter in a phase. It reports standardized
 * residual diagnostics for the untouched holdout and does not define an acceptance threshold.
 * </p>
 */
public final class PitzerBinaryVolumetricGroupedValidation implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final PitzerBinaryVolumetricModel model;
  private final PitzerBinaryVolumetricRegression regression;

  /**
   * Construct grouped holdout validation for one binary electrolyte.
   *
   * @param model parameter-neutral binary volumetric Pitzer model
   */
  public PitzerBinaryVolumetricGroupedValidation(PitzerBinaryVolumetricModel model) {
    if (model == null) {
      throw new IllegalArgumentException("Binary volumetric Pitzer model must not be null");
    }
    this.model = model;
    regression = new PitzerBinaryVolumetricRegression(model);
  }

  /**
   * Validate repository-distributed provenance before fitting and holdout evaluation.
   *
   * <p>
   * The provenance manifest must assign every observation to its declared calibration or validation role and establish
   * repository redistribution permission, file checksum, explicit absolute-one-sigma uncertainty qualification, row
   * count, and state envelope. The original
   * caller-supplied {@link #validate(List, List, double, double, double)} path remains available for private or
   * in-memory data that are not distributed with NeqSim.
   * </p>
   *
   * @param provenance repository dataset provenance manifest
   * @param calibrationObservations observations used by the weighted regression
   * @param validationObservations observations used only after the fit is complete
   * @param temperatureK common temperature in K
   * @param pressurePa common absolute pressure in Pa
   * @param debyeHuckelVolumeSlope caller-supplied Debye-Huckel volume slope in SI units
   * @return immutable calibration fit and held-out residual diagnostics
   */
  public ValidationResult validateRepositoryDatasets(PitzerBinaryVolumetricDatasetProvenance provenance,
      List<PitzerBinaryVolumetricRegression.Observation> calibrationObservations,
      List<PitzerBinaryVolumetricRegression.Observation> validationObservations, double temperatureK, double pressurePa,
      double debyeHuckelVolumeSlope) {
    if (provenance == null) {
      throw new IllegalArgumentException("Volumetric Pitzer dataset provenance must not be null");
    }
    provenance.validateRepositoryObservations(calibrationObservations,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.CALIBRATION, temperatureK, pressurePa);
    provenance.validateRepositoryObservations(validationObservations,
        PitzerBinaryVolumetricDatasetProvenance.DatasetRole.VALIDATION, temperatureK, pressurePa);
    return validate(calibrationObservations, validationObservations, temperatureK, pressurePa, debyeHuckelVolumeSlope);
  }

  /**
   * Fit calibration observations and evaluate an untouched, lineage-disjoint holdout.
   *
   * @param calibrationObservations observations used by the weighted regression
   * @param validationObservations observations used only after the fit is complete
   * @param temperatureK common temperature in K
   * @param pressurePa common absolute pressure in Pa
   * @param debyeHuckelVolumeSlope caller-supplied Debye-Huckel volume slope in SI units
   * @return immutable calibration fit and held-out residual diagnostics
   */
  public ValidationResult validate(List<PitzerBinaryVolumetricRegression.Observation> calibrationObservations,
      List<PitzerBinaryVolumetricRegression.Observation> validationObservations, double temperatureK, double pressurePa,
      double debyeHuckelVolumeSlope) {
    if (calibrationObservations == null) {
      throw new IllegalArgumentException("Calibration observations must not be null");
    }
    if (validationObservations == null || validationObservations.isEmpty()) {
      throw new IllegalArgumentException("Validation observations must not be null or empty");
    }

    Set<String> calibrationGroups = sourceGroups(calibrationObservations, "Calibration");
    Set<String> validationGroups = sourceGroups(validationObservations, "Validation");
    Set<String> overlappingGroups = new HashSet<String>(calibrationGroups);
    overlappingGroups.retainAll(validationGroups);
    if (!overlappingGroups.isEmpty()) {
      List<String> sortedOverlap = new ArrayList<String>(overlappingGroups);
      Collections.sort(sortedOverlap);
      throw new IllegalArgumentException("Calibration and validation source lineages overlap: " + sortedOverlap);
    }

    PitzerBinaryVolumetricRegression.FitResult calibrationFit = regression.fit(calibrationObservations, temperatureK,
        pressurePa, debyeHuckelVolumeSlope);
    double limitingVolume = calibrationFit.getLimitingApparentMolarVolume();
    PitzerBinaryVolumetricModel.StateParameters parameters = calibrationFit.getStateParameters();

    double chiSquare = 0.0;
    double maximumAbsoluteStandardizedResidual = 0.0;
    Map<String, MutableGroupStatistics> mutableGroups = new TreeMap<String, MutableGroupStatistics>();
    for (PitzerBinaryVolumetricRegression.Observation observation : validationObservations) {
      double predictedVolume = model.calculateApparentMolarVolume(observation.getMolality(), limitingVolume,
          parameters);
      double residual = observation.getApparentMolarVolume() - predictedVolume;
      double standardizedResidual = residual / observation.getStandardUncertainty();
      if (!Double.isFinite(standardizedResidual)) {
        throw new IllegalArgumentException("Holdout evaluation produced a non-finite standardized residual");
      }
      chiSquare += standardizedResidual * standardizedResidual;
      maximumAbsoluteStandardizedResidual = Math.max(maximumAbsoluteStandardizedResidual,
          Math.abs(standardizedResidual));

      MutableGroupStatistics group = mutableGroups.get(observation.getSourceGroup());
      if (group == null) {
        group = new MutableGroupStatistics(observation.getSourceGroup());
        mutableGroups.put(observation.getSourceGroup(), group);
      }
      group.add(residual, standardizedResidual);
    }

    List<GroupStatistics> groups = new ArrayList<GroupStatistics>();
    for (MutableGroupStatistics group : mutableGroups.values()) {
      groups.add(group.toImmutable());
    }
    return new ValidationResult(calibrationFit, validationObservations.size(), chiSquare,
        maximumAbsoluteStandardizedResidual, groups);
  }

  private static Set<String> sourceGroups(List<PitzerBinaryVolumetricRegression.Observation> observations,
      String role) {
    Set<String> groups = new HashSet<String>();
    for (PitzerBinaryVolumetricRegression.Observation observation : observations) {
      if (observation == null) {
        throw new IllegalArgumentException(role + " observations must not contain null");
      }
      groups.add(observation.getSourceGroup());
    }
    return groups;
  }

  /** Immutable calibration fit and untouched-holdout diagnostics. */
  public static final class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final PitzerBinaryVolumetricRegression.FitResult calibrationFit;
    private final int observationCount;
    private final double chiSquare;
    private final double maximumAbsoluteStandardizedResidual;
    private final List<GroupStatistics> groupStatistics;

    private ValidationResult(PitzerBinaryVolumetricRegression.FitResult calibrationFit, int observationCount,
        double chiSquare, double maximumAbsoluteStandardizedResidual, List<GroupStatistics> groupStatistics) {
      this.calibrationFit = calibrationFit;
      this.observationCount = observationCount;
      this.chiSquare = chiSquare;
      this.maximumAbsoluteStandardizedResidual = maximumAbsoluteStandardizedResidual;
      this.groupStatistics = Collections.unmodifiableList(new ArrayList<GroupStatistics>(groupStatistics));
    }

    /** @return weighted fit obtained from calibration observations only */
    public PitzerBinaryVolumetricRegression.FitResult getCalibrationFit() {
      return calibrationFit;
    }

    /** @return number of untouched validation observations */
    public int getObservationCount() {
      return observationCount;
    }

    /** @return sum of squared standardized holdout residuals */
    public double getChiSquare() {
      return chiSquare;
    }

    /** @return root-mean-square standardized holdout residual */
    public double getWeightedRootMeanSquareResidual() {
      return Math.sqrt(chiSquare / observationCount);
    }

    /** @return maximum absolute standardized holdout residual */
    public double getMaximumAbsoluteStandardizedResidual() {
      return maximumAbsoluteStandardizedResidual;
    }

    /** @return immutable holdout diagnostics sorted by source-lineage identifier */
    public List<GroupStatistics> getGroupStatistics() {
      return groupStatistics;
    }
  }

  /** Immutable held-out residual diagnostics for one source lineage. */
  public static final class GroupStatistics implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String sourceGroup;
    private final int count;
    private final double meanResidual;
    private final double weightedRootMeanSquareResidual;
    private final double maximumAbsoluteStandardizedResidual;

    private GroupStatistics(String sourceGroup, int count, double meanResidual, double weightedRootMeanSquareResidual,
        double maximumAbsoluteStandardizedResidual) {
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

    /** @return number of held-out observations in this source group */
    public int getCount() {
      return count;
    }

    /** @return arithmetic mean held-out volume residual in m3/mol */
    public double getMeanResidual() {
      return meanResidual;
    }

    /** @return root-mean-square standardized holdout residual for this source group */
    public double getWeightedRootMeanSquareResidual() {
      return weightedRootMeanSquareResidual;
    }

    /** @return maximum absolute standardized holdout residual for this source group */
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
      maximumAbsoluteStandardizedResidual = Math.max(maximumAbsoluteStandardizedResidual,
          Math.abs(standardizedResidual));
    }

    private GroupStatistics toImmutable() {
      return new GroupStatistics(sourceGroup, count, residualSum / count,
          Math.sqrt(standardizedResidualSquareSum / count), maximumAbsoluteStandardizedResidual);
    }
  }
}
