package neqsim.processsimulation.processequipment.splitter;

import neqsim.processsimulation.processequipment.ProcessEquipmentInterface;
import neqsim.processsimulation.processequipment.stream.StreamInterface;

/**
 * <p>
 * SplitterInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SplitterInterface extends ProcessEquipmentInterface {
  /**
   * <p>
   * setSplitNumber.
   * </p>
   *
   * @param i a int
   */
  public void setSplitNumber(int i);

  /**
   * <p>
   * setInletStream.
   * </p>
   *
   * @param inletStream a {@link neqsim.processsimulation.processequipment.stream.StreamInterface}
   *        object
   */
  public void setInletStream(StreamInterface inletStream);

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.processsimulation.processequipment.stream.Stream} object
   */
  public StreamInterface getSplitStream(int i);
}
