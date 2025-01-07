/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * <p>
 * OnePhasePipeLine class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhasePipeLine extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for OnePhasePipeLine.
   * </p>
   *
   * @param inStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public OnePhasePipeLine(StreamInterface inStream) {
    this("OnePhasePipeLine", inStream);
  }

  /**
   * Constructor for OnePhasePipeLine.
   *
   * @param name name of pipe
   */
  public OnePhasePipeLine(String name) {
    super(name);
  }

  /**
   * Constructor for OnePhasePipeLine.
   *
   * @param name name of pipe
   * @param inStream input stream
   */
  public OnePhasePipeLine(String name, StreamInterface inStream) {
    super(name, inStream);
    pipe = new PipeFlowSystem();
  }

  /**
   * <p>
   * createSystem.
   * </p>
   */
  public void createSystem() {}

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    UUID oldid = getCalculationIdentifier();
    super.run(id);
    setCalculationIdentifier(oldid);
    pipe.solveSteadyState(10, id);
    // pipe.print();
    outStream
        .setThermoSystem(pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().clone());
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
