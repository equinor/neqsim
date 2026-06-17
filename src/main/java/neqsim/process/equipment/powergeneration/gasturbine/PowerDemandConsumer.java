package neqsim.process.equipment.powergeneration.gasturbine;

/**
 * Marker interface for any process element that consumes shaft power and can therefore be attached
 * to a {@link GasTurbineUnit} as a load.
 *
 * <p>
 * The unit family includes pumps, compressors, and aggregated machinery blocks. Implementations
 * only need to expose their currently demanded power in Watts; sign convention is positive for
 * power drawn from the driver.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public interface PowerDemandConsumer {
  /**
   * Return the current shaft power demand.
   *
   * @return demanded power in Watts (positive)
   */
  double getDemandedPowerW();

  /**
   * Return a short tag / name for reports.
   *
   * @return name of the consumer
   */
  String getConsumerName();
}
