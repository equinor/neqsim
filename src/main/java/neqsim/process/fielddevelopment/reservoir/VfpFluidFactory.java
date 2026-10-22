package neqsim.process.fielddevelopment.reservoir;

import neqsim.thermo.system.SystemInterface;

/**
 * Creates the flowing well stream for one VFP table coordinate.
 *
 * <p>
 * The returned fluid must carry composition and total molar flow rate for the requested surface volumes. Its pressure
 * and temperature are overwritten by the VLP model. {@link RecombinationFluidFactory} is the standard implementation; a
 * lambda can be used when the study has its own recombination.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
@FunctionalInterface
public interface VfpFluidFactory {
  /**
   * Creates the well stream for a table coordinate.
   *
   * @param rate surface rate in Sm3/day of the table rate basis
   * @param waterRatio water ratio in the unit of the selected water ratio type
   * @param gasRatio gas ratio in the unit of the selected gas ratio type
   * @param alq artificial lift quantity (gas lift rate in Sm3/day)
   * @return new fluid with composition and total flow rate set; the caller may modify it
   */
  SystemInterface createWellStream(double rate, double waterRatio, double gasRatio, double alq);
}
