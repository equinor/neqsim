package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;

/**
 * Entrainment model that exposes NeqSim's open-source 7-stage physics chain through the provider interface.
 *
 * <p>
 * Registered as {@code "neqsim-7stage"}. The numerical work is done by {@link SeparatorPerformanceCalculator}; this
 * class evaluates it through {@link Separator#computeSeparationPerformance()}, which is read-only and does not disturb
 * the entrainment applied during {@link Separator#run()}.
 * </p>
 *
 * <p>
 * <b>Conversion.</b> The calculator reports entrainment as the fraction of each incoming liquid phase that is not
 * separated ({@code 1 - overallGasLiquidEfficiency}), so a mass rate follows directly from the feed phase mass flow
 * without any density assumption:
 * </p>
 *
 * <pre>
 * oil carried over [kg/h] = oilInGasFraction x feed oil mass flow [kg/h]
 * </pre>
 *
 * <p>
 * <b>Confidence band.</b> The chain is deterministic and produces no statistical band, so the band is reported as
 * {@link Double#NaN} rather than as a fabricated zero.
 * </p>
 *
 * <p>
 * <b>Status.</b> The underlying chain has not been validated against field or rig data in this repository. It is
 * offered as one selectable model among several, not as a reference result.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class BuiltInSevenStageProvider implements EnhancedEntrainmentProvider {

  /** Stable id used to look this model up through the registry. */
  public static final String ID = "neqsim-7stage";

  /** Version of the adapter; bumped when this adapter changes, not when the chain changes. */
  public static final String VERSION = "1.0.0";

  /** Public no-args constructor required by {@link java.util.ServiceLoader}. */
  public BuiltInSevenStageProvider() {}

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
   * The 7-stage chain is predictive and carries no hard validity envelope.
   *
   * @param separator the separator whose entrainment is requested; unused
   * @return {@link EntrainmentApplicability#ok()}
   */
  @Override
  public EntrainmentApplicability checkApplicability(Separator separator) {
    return EntrainmentApplicability.ok();
  }

  /**
   * Evaluates the 7-stage chain and converts its entrainment fractions to mass rates.
   *
   * @param separator the separator whose entrainment is requested; must not be null
   * @return the carry-over result; values are 0.0 for phases absent from the feed
   * @throws IllegalArgumentException if {@code separator} is null
   */
  @Override
  public EntrainmentResult compute(Separator separator) {
    if (separator == null) {
      throw new IllegalArgumentException("separator must not be null");
    }

    SeparatorPerformanceCalculator calc = separator.computeSeparationPerformance();
    if (calc == null) {
      return new EntrainmentResult(ID, VERSION, 0.0, 0.0, 0.0, Double.NaN);
    }

    double feedOil = SeparatorFeedBasis.feedPhaseMassFlowKgPerHr(separator, "oil");
    double feedWater = SeparatorFeedBasis.feedPhaseMassFlowKgPerHr(separator, "aqueous");
    double feedGas = SeparatorFeedBasis.feedPhaseMassFlowKgPerHr(separator, "gas");

    double oilKgPerHr = calc.getOilInGasFraction() * feedOil;
    double waterKgPerHr = calc.getWaterInGasFraction() * feedWater;
    double gasKgPerHr = (calc.getGasInOilFraction() + calc.getGasInWaterFraction()) * feedGas;

    return new EntrainmentResult(ID, VERSION, oilKgPerHr, waterKgPerHr, gasKgPerHr, Double.NaN);
  }
}
