package neqsim.processSimulation.processEquipment.distillation;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * TrayInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface TrayInterface extends ProcessEquipmentInterface {
  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void addStream(StreamInterface newStream);

  /**
   * <p>
   * setHeatInput.
   * </p>
   *
   * @param heatinp a double
   */
  public void setHeatInput(double heatinp);
}
