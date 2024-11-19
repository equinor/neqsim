package neqsim.process.equipment.heatexchanger;

/**
 * <p>
 * HeatExchangerInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeatExchangerInterface extends HeaterInterface {
  /**
   * <p>
   * getOutStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public neqsim.process.equipment.stream.StreamInterface getOutStream(int i);
}
