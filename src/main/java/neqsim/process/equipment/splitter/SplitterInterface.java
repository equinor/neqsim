package neqsim.process.equipment.splitter;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

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
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *        object
   */
  public void setInletStream(StreamInterface inletStream);

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getSplitStream(int i);
}
