package neqsim.process.equipment.distillation;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

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
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface}
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
