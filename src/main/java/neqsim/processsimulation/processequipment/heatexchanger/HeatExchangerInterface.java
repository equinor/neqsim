package neqsim.processsimulation.processequipment.heatexchanger;

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
   * @return a {@link neqsim.processsimulation.processequipment.stream.StreamInterface} object
   */
  public neqsim.processsimulation.processequipment.stream.StreamInterface getOutStream(int i);
}
