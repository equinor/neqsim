package neqsim.process.equipment.reactor.digestion;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.characterization.BioFeedstock;

/**
 * Engineering screening model with first-order hydrolysis and a bounded temperature correction.
 *
 * <p>
 * The model calculates volatile-solids destruction as {@code X = Xmax * (1 - exp(-k * theta)) * fT}. It represents
 * residence-time and feed-preparation effects without claiming the acid-base, inhibition, or population detail of a
 * full anaerobic-digestion state model.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class FirstOrderHydrolysisDigestionModel extends EmpiricalYieldDigestionModel {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference temperature in Kelvin. */
  private double referenceTemperatureK = 308.15;
  /** Q10 temperature coefficient. */
  private double q10 = 2.0;

  /**
   * Sets the temperature-correction basis.
   *
   * @param referenceTemperatureK reference temperature in Kelvin
   * @param q10Factor Q10 coefficient
   */
  public void setTemperatureCorrection(double referenceTemperatureK, double q10Factor) {
    if (referenceTemperatureK <= 0.0 || q10Factor <= 0.0) {
      throw new IllegalArgumentException("Temperature-correction values must be positive");
    }
    this.referenceTemperatureK = referenceTemperatureK;
    this.q10 = q10Factor;
  }

  /** {@inheritDoc} */
  @Override
  public AnaerobicDigestionResult calculate(AnaerobicDigestionInput input) {
    BioFeedstock feedstock = getEffectiveFeedstock(input);
    double feedKgPerDay = input.getWetFeedRateKgPerHr() * 24.0;
    double totalSolids = feedKgPerDay * feedstock.getTotalSolidsFraction();
    double volatileSolids = totalSolids * feedstock.getVolatileSolidsFraction();

    double temperatureFactor = Math.pow(q10, (input.getTemperatureK() - referenceTemperatureK) / 10.0);
    temperatureFactor = Math.max(0.25, Math.min(2.0, temperatureFactor));
    double kineticConversion = 1.0
        - Math.exp(-feedstock.getHydrolysisRatePerDay() * input.getHydraulicRetentionTimeDays());
    double maximumDestruction = Double.isNaN(input.getVsDestructionOverride()) ? feedstock.getMaximumVsDestruction()
        : input.getVsDestructionOverride();
    double destruction = Math.max(0.0,
        Math.min(maximumDestruction, maximumDestruction * kineticConversion * temperatureFactor));
    double destroyedVs = volatileSolids * destruction;

    double methaneYield = Double.isNaN(input.getMethaneYieldOverride()) ? feedstock.getMethaneYieldNm3PerKgDestroyedVs()
        : input.getMethaneYieldOverride();
    double methaneFraction = Double.isNaN(input.getMethaneFractionOverride()) ? feedstock.getMethaneFraction()
        : input.getMethaneFractionOverride();
    methaneFraction = Math.max(1.0e-6, Math.min(0.999999, methaneFraction));
    double methane = destroyedVs * methaneYield;
    double carbonDioxide = methane * (1.0 - methaneFraction) / methaneFraction;
    double h2s = calculateHydrogenSulfide(input, totalSolids);
    double feedCarbon = totalSolids * feedstock.getCarbonFraction();

    List<String> warnings = getCalculationWarnings(input);
    return new AnaerobicDigestionResult(getModelIdentifier(), getFidelity(), getEvidenceReference(), feedKgPerDay,
        totalSolids, volatileSolids, destroyedVs, methane, carbonDioxide, h2s, methaneFraction, feedCarbon, warnings);
  }

  /**
   * Returns the feedstock parameters used by the kinetic calculation.
   *
   * <p>
   * Subclasses may return an independent copy carrying fitted parameters. The caller's characterization must not be
   * mutated.
   * </p>
   *
   * @param input digestion input
   * @return effective feedstock characterization
   */
  protected BioFeedstock getEffectiveFeedstock(AnaerobicDigestionInput input) {
    return input.getFeedstock();
  }

  /**
   * Returns qualification warnings attached to a calculation.
   *
   * @param input digestion input
   * @return mutable warning list
   */
  protected List<String> getCalculationWarnings(AnaerobicDigestionInput input) {
    List<String> warnings = new ArrayList<String>();
    warnings.add("First-order model requires calibration of hydrolysis rate and maximum degradability");
    return warnings;
  }

  /** {@inheritDoc} */
  @Override
  public ModelFidelity getFidelity() {
    return ModelFidelity.ENGINEERING;
  }

  /** {@inheritDoc} */
  @Override
  public String getModelIdentifier() {
    return "anaerobic-digestion-first-order-hydrolysis-v1";
  }
}
