package neqsim.process.equipment.reactor.digestion;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.characterization.BioFeedstock;

/**
 * First-order hydrolysis model fitted to a named conversion data set.
 *
 * <p>
 * The fitted hydrolysis rate and maximum volatile-solids destruction replace the corresponding feedstock defaults on
 * an independent characterization copy. Other feedstock properties, including methane yield and elemental analysis,
 * remain unchanged. Calculations outside the observed retention-time or temperature range are retained but explicitly
 * warned as extrapolations.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public final class CalibratedFirstOrderHydrolysisDigestionModel extends FirstOrderHydrolysisDigestionModel {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Fitted maximum volatile-solids destruction. */
  private final double maximumVsDestruction;
  /** Fitted hydrolysis rate in 1/day. */
  private final double hydrolysisRatePerDay;
  /** Traceable calibration evidence. */
  private final String evidenceReference;
  /** Minimum calibrated hydraulic retention time in days. */
  private final double minimumRetentionTimeDays;
  /** Maximum calibrated hydraulic retention time in days. */
  private final double maximumRetentionTimeDays;
  /** Minimum calibrated temperature in Kelvin. */
  private final double minimumTemperatureK;
  /** Maximum calibrated temperature in Kelvin. */
  private final double maximumTemperatureK;

  /**
   * Creates an evidence-bearing calibrated model.
   *
   * @param maximumVsDestruction fitted maximum VS destruction fraction
   * @param hydrolysisRatePerDay fitted first-order rate in 1/day
   * @param evidenceReference calibration data-set or campaign reference
   * @param minimumRetentionTimeDays minimum observed retention time in days
   * @param maximumRetentionTimeDays maximum observed retention time in days
   * @param minimumTemperatureK minimum observed temperature in Kelvin
   * @param maximumTemperatureK maximum observed temperature in Kelvin
   * @param referenceTemperatureK reference temperature for the Q10 correction
   * @param q10 temperature coefficient
   */
  public CalibratedFirstOrderHydrolysisDigestionModel(double maximumVsDestruction, double hydrolysisRatePerDay,
      String evidenceReference, double minimumRetentionTimeDays, double maximumRetentionTimeDays,
      double minimumTemperatureK, double maximumTemperatureK, double referenceTemperatureK, double q10) {
    if (!Double.isFinite(maximumVsDestruction) || maximumVsDestruction <= 0.0 || maximumVsDestruction > 1.0) {
      throw new IllegalArgumentException("Maximum VS destruction must be finite and in (0, 1]");
    }
    if (!Double.isFinite(hydrolysisRatePerDay) || hydrolysisRatePerDay <= 0.0) {
      throw new IllegalArgumentException("Hydrolysis rate must be finite and positive");
    }
    if (evidenceReference == null || evidenceReference.trim().isEmpty()) {
      throw new IllegalArgumentException("A calibration evidence reference must be provided");
    }
    if (minimumRetentionTimeDays <= 0.0 || maximumRetentionTimeDays < minimumRetentionTimeDays
        || minimumTemperatureK <= 0.0 || maximumTemperatureK < minimumTemperatureK) {
      throw new IllegalArgumentException("Calibration ranges are invalid");
    }
    this.maximumVsDestruction = maximumVsDestruction;
    this.hydrolysisRatePerDay = hydrolysisRatePerDay;
    this.evidenceReference = evidenceReference.trim();
    this.minimumRetentionTimeDays = minimumRetentionTimeDays;
    this.maximumRetentionTimeDays = maximumRetentionTimeDays;
    this.minimumTemperatureK = minimumTemperatureK;
    this.maximumTemperatureK = maximumTemperatureK;
    setTemperatureCorrection(referenceTemperatureK, q10);
  }

  /** {@inheritDoc} */
  @Override
  protected BioFeedstock getEffectiveFeedstock(AnaerobicDigestionInput input) {
    BioFeedstock effective = input.getFeedstock().copy();
    effective.setSolidsAnalysis(effective.getTotalSolidsFraction(), effective.getVolatileSolidsFraction(),
        maximumVsDestruction, effective.getAshFraction());
    effective.setDigestionProperties(effective.getMethaneYieldNm3PerKgDestroyedVs(), hydrolysisRatePerDay,
        effective.getMethaneFraction());
    effective.setEvidenceReference(evidenceReference);
    return effective;
  }

  /** {@inheritDoc} */
  @Override
  protected List<String> getCalculationWarnings(AnaerobicDigestionInput input) {
    List<String> warnings = new ArrayList<String>();
    if (!Double.isNaN(input.getVsDestructionOverride())) {
      warnings.add("VS-destruction override supersedes the calibrated maximum-degradability parameter");
    }
    if (input.getHydraulicRetentionTimeDays() < minimumRetentionTimeDays
        || input.getHydraulicRetentionTimeDays() > maximumRetentionTimeDays) {
      warnings.add("Hydraulic retention time is outside the calibration range");
    }
    if (input.getTemperatureK() < minimumTemperatureK || input.getTemperatureK() > maximumTemperatureK) {
      warnings.add("Operating temperature is outside the calibration range");
    }
    return warnings;
  }

  /** {@inheritDoc} */
  @Override
  public ModelFidelity getFidelity() {
    return ModelFidelity.CALIBRATED;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelIdentifier() {
    return "anaerobic-digestion-calibrated-first-order-hydrolysis-v1";
  }

  /** @return fitted maximum volatile-solids destruction fraction */
  public double getMaximumVsDestruction() {
    return maximumVsDestruction;
  }

  /** @return fitted hydrolysis rate in 1/day */
  public double getHydrolysisRatePerDay() {
    return hydrolysisRatePerDay;
  }

  /** {@inheritDoc} */
  @Override
  public String getEvidenceReference() {
    return evidenceReference;
  }

  /** @return minimum calibrated hydraulic retention time in days */
  public double getMinimumRetentionTimeDays() {
    return minimumRetentionTimeDays;
  }

  /** @return maximum calibrated hydraulic retention time in days */
  public double getMaximumRetentionTimeDays() {
    return maximumRetentionTimeDays;
  }

  /** @return minimum calibrated temperature in Kelvin */
  public double getMinimumTemperatureK() {
    return minimumTemperatureK;
  }

  /** @return maximum calibrated temperature in Kelvin */
  public double getMaximumTemperatureK() {
    return maximumTemperatureK;
  }
}
