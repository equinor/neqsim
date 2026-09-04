package neqsim.process.equipment.separator.entrainment;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Package-private helpers that read the feed basis an entrainment provider needs from a {@link Separator}.
 *
 * <p>
 * Kept in one place so the built-in providers agree on what "inlet oil", "inlet water" and "gas rate" mean. All methods
 * are null-safe and return 0.0 when the separator has not been run or the phase is absent, so a provider never throws
 * merely because a model is incompletely set up.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
final class SeparatorFeedBasis {

  /** Static utility class. */
  private SeparatorFeedBasis() {}

  /**
   * Returns the feed fluid of a separator.
   *
   * @param separator the separator to read; may be null
   * @return the feed thermodynamic system, or null when unavailable
   */
  private static SystemInterface feedFluid(Separator separator) {
    if (separator == null) {
      return null;
    }
    StreamInterface feed = separator.getFeedStream();
    return (feed == null) ? null : feed.getFluid();
  }

  /**
   * Returns the mass flow of a named phase in the separator feed.
   *
   * @param separator the separator to read; may be null
   * @param phaseName the NeqSim phase name, for example {@code "oil"}, {@code "aqueous"} or {@code "gas"}
   * @return the phase mass flow [kg/h], or 0.0 when the separator, feed or phase is absent
   */
  static double feedPhaseMassFlowKgPerHr(Separator separator, String phaseName) {
    SystemInterface fluid = feedFluid(separator);
    if (fluid == null || !fluid.hasPhaseType(phaseName)) {
      return 0.0;
    }
    return fluid.getPhase(phaseName).getFlowRate("kg/hr");
  }

  /**
   * Returns the actual volumetric flow of a named phase in the separator feed.
   *
   * @param separator the separator to read; may be null
   * @param phaseName the NeqSim phase name, for example {@code "oil"} or {@code "aqueous"}
   * @return the phase volumetric flow at feed conditions [m3/h], or 0.0 when absent
   */
  private static double feedPhaseVolumeFlowM3PerHr(Separator separator, String phaseName) {
    SystemInterface fluid = feedFluid(separator);
    if (fluid == null || !fluid.hasPhaseType(phaseName)) {
      return 0.0;
    }
    return fluid.getPhase(phaseName).getFlowRate("m3/hr");
  }

  /**
   * Returns the water cut of the separator feed, defined as NeqSim defines it elsewhere: the water volume fraction of
   * the total liquid, evaluated at feed conditions.
   *
   * @param separator the separator to read; may be null
   * @return the water cut [0-1], or 0.0 when the feed carries no liquid
   */
  static double feedWaterCut(Separator separator) {
    double oil = feedPhaseVolumeFlowM3PerHr(separator, "oil");
    double water = feedPhaseVolumeFlowM3PerHr(separator, "aqueous");
    double liquid = oil + water;
    if (liquid <= 0.0) {
      return 0.0;
    }
    return water / liquid;
  }

  /**
   * Returns the standard gas rate leaving the separator, used as the denominator of carry-over specifications expressed
   * per unit of standard gas volume.
   *
   * <p>
   * Note that a standard cubic metre in NeqSim is a molar quantity: {@code Sm3} is evaluated as
   * {@code n * R * T_std / atm} with no density involved.
   * </p>
   *
   * @param separator the separator to read; may be null
   * @return the gas outlet flow [MSm3/h], or 0.0 when unavailable
   */
  static double gasOutStandardFlowMSm3PerHr(Separator separator) {
    if (separator == null) {
      return 0.0;
    }
    StreamInterface gas = separator.getGasOutStream();
    if (gas == null || gas.getFluid() == null) {
      return 0.0;
    }
    return gas.getFluid().getFlowRate("MSm3/hr");
  }

  /**
   * Returns the density of a named phase in the separator feed.
   *
   * @param separator the separator to read; may be null
   * @param phaseName the NeqSim phase name, for example {@code "oil"} or {@code "aqueous"}
   * @return the phase density at feed conditions [kg/m3], or 0.0 when absent
   */
  static double feedPhaseDensityKgPerM3(Separator separator, String phaseName) {
    SystemInterface fluid = feedFluid(separator);
    if (fluid == null || !fluid.hasPhaseType(phaseName)) {
      return 0.0;
    }
    return fluid.getPhase(phaseName).getDensity("kg/m3");
  }
}
