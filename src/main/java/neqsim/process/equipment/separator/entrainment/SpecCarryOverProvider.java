package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;

/**
 * Entrainment model that assumes a fixed liquid carry-over per unit of standard gas volume.
 *
 * <p>
 * Registered as {@code "spe-0.1gal-mmscf"}. The default figure is <b>13.4 L per MSm3</b> of gas, which is the SI
 * equivalent of the long-standing 0.1 US gallon per MMscf rule of thumb described in <i>The Savvy Separator: A Century
 * of Carry-Over - 0.1 gal/MMscf</i> (SPE / Journal of Petroleum Technology). The conversion is 0.1 US gal = 0.37854 L
 * against 1 MMscf = 28 262 Sm3 (scf referenced to 60 F, Sm3 to 15 C), giving 13.39 L/MSm3.
 * </p>
 *
 * <p>
 * <b>What it computes.</b> The total liquid carry-over volume is proportional to the standard gas rate leaving the
 * separator. That total is split between oil and water using the <i>feed</i> water cut, so a high-water-cut separator
 * reports mostly water carry-over, and each part is converted to a mass rate with its own phase density. With no liquid
 * in the feed the result is zero rather than a nominal 13.4 L of nothing.
 * </p>
 *
 * <p>
 * <b>This is a specification, not a prediction.</b> The figure does not respond to gas load factor, mesh pad selection
 * or overload: a separator running at twice its capacity still reports the same litres per MSm3. It is intended as a
 * defensible default when nothing better is known, not as a substitute for a performance model. A capacity constraint
 * fed from this model can never trip on carry-over, which is by design and worth stating in any report that uses it.
 * </p>
 *
 * <p>
 * The specific carry-over figure can be changed with {@link #setCarryOverLitrePerMSm3(double)} for operators whose own
 * basis differs from the SPE figure.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SpecCarryOverProvider implements EnhancedEntrainmentProvider {

  /** Stable id used to look this model up through the registry. */
  public static final String ID = "spe-0.1gal-mmscf";

  /** Version of this model. */
  public static final String VERSION = "1.0.0";

  /**
   * Default carry-over figure [litre of liquid per MSm3 of gas], the SI equivalent of 0.1 US gal/MMscf.
   */
  public static final double DEFAULT_CARRY_OVER_LITRE_PER_MSM3 = 13.4;

  /** Carry-over figure actually used by this instance [litre per MSm3]. */
  private double carryOverLitrePerMSm3 = DEFAULT_CARRY_OVER_LITRE_PER_MSM3;

  /** Public no-args constructor required by {@link java.util.ServiceLoader}. */
  public SpecCarryOverProvider() {
  }

  /** {@inheritDoc} */
  @Override
  public String getId() {
    return ID;
  }

  /** {@inheritDoc} */
  @Override
  public String getVersion() {
    return VERSION;
  }

  /**
   * Returns the carry-over figure used by this instance.
   *
   * @return carry-over [litre of liquid per MSm3 of gas]
   */
  public double getCarryOverLitrePerMSm3() {
    return carryOverLitrePerMSm3;
  }

  /**
   * Sets the carry-over figure used by this instance.
   *
   * @param carryOverLitrePerMSm3 carry-over [litre of liquid per MSm3 of gas]; must not be negative
   * @throws IllegalArgumentException if the value is negative or not a number
   */
  public void setCarryOverLitrePerMSm3(double carryOverLitrePerMSm3) {
    if (Double.isNaN(carryOverLitrePerMSm3) || carryOverLitrePerMSm3 < 0.0) {
      throw new IllegalArgumentException("carryOverLitrePerMSm3 must be zero or positive");
    }
    this.carryOverLitrePerMSm3 = carryOverLitrePerMSm3;
  }

  /**
   * Always applicable; a fixed specification makes no claim that can fall out of range.
   *
   * @param separator the separator whose entrainment is requested; unused
   * @return {@link EntrainmentApplicability#ok()}
   */
  @Override
  public EntrainmentApplicability checkApplicability(Separator separator) {
    return EntrainmentApplicability.ok();
  }

  /**
   * Computes carry-over as a fixed liquid volume per standard gas volume, split by the feed water cut.
   *
   * @param separator the separator whose entrainment is requested; must not be null
   * @return the carry-over result; all values are 0.0 when the feed carries no liquid or the gas rate is zero
   * @throws IllegalArgumentException if {@code separator} is null
   */
  @Override
  public EntrainmentResult compute(Separator separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator must not be null");
    }

    double gasMSm3PerHr = SeparatorFeedBasis.gasOutStandardFlowMSm3PerHr(separator);
    if (!(gasMSm3PerHr > 0.0)) {
      return new EntrainmentResult(ID, VERSION, 0.0, 0.0, 0.0, 0.0);
    }

    // litre/MSm3 -> m3 of liquid per hour
    double totalLiquidM3PerHr = carryOverLitrePerMSm3 * 1.0e-3 * gasMSm3PerHr;

    double waterCut = SeparatorFeedBasis.feedWaterCut(separator);
    double oilDensity = SeparatorFeedBasis.feedPhaseDensityKgPerM3(separator, "oil");
    double waterDensity = SeparatorFeedBasis.feedPhaseDensityKgPerM3(separator, "aqueous");

    double oilKgPerHr = totalLiquidM3PerHr * (1.0 - waterCut) * oilDensity;
    double waterKgPerHr = totalLiquidM3PerHr * waterCut * waterDensity;

    return new EntrainmentResult(ID, VERSION, oilKgPerHr, waterKgPerHr, 0.0, Double.NaN);
  }
}
