package neqsim.process.equipment.heatexchanger;

/**
 * HeatExchangerInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeatExchangerInterface extends HeaterInterface {
  /**
   * getOutStream.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public neqsim.process.equipment.stream.StreamInterface getOutStream(int i);
}
