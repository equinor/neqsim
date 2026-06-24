package neqsim.process.equipment.splitter;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * SplitterInterface interface.
 *
 * @author esol
 * @version $Id: $Id
 */
public interface SplitterInterface extends ProcessEquipmentInterface {
  /**
   * setSplitNumber.
   *
   * @param i a int
   */
  public void setSplitNumber(int i);

  /**
   * setInletStream.
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface inletStream);

  /**
   * getSplitStream.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getSplitStream(int i);
}
