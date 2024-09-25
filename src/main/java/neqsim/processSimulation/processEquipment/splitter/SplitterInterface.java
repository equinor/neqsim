package neqsim.processSimulation.processEquipment.splitter;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

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
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public void setInletStream(StreamInterface inletStream);

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public StreamInterface getSplitStream(int i);
}
