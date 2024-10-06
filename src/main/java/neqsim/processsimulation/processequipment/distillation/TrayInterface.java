package neqsim.processsimulation.processequipment.distillation;

import neqsim.processsimulation.processequipment.ProcessEquipmentInterface;
import neqsim.processsimulation.processequipment.stream.StreamInterface;

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
   * @param newStream a {@link neqsim.processsimulation.processequipment.stream.StreamInterface}
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
