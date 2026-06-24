package neqsim.process.equipment.distillation;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * TrayInterface interface.
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface TrayInterface extends ProcessEquipmentInterface {
  /**
   * addStream.
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream);

  /**
   * setHeatInput.
   *
   * @param heatinp a double
   */
  public void setHeatInput(double heatinp);
}
