/*
 * OnePhasePipeLine.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.processSimulation.processEquipment.pipeline;

import java.util.UUID;
import neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem.PipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * OnePhasePipeLine class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class OnePhasePipeLine extends Pipeline {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for OnePhasePipeLine.
   * </p>
   */
  @Deprecated(forRemoval = true)
  public OnePhasePipeLine() {
    this("OnePhasePipeLine");
  }

  /**
   * <p>
   * Constructor for OnePhasePipeLine.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
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
    outStream.setThermoSystem((SystemInterface)pipe.getNode(pipe.getTotalNumberOfNodes() - 1).getBulkSystem().clone());
    outStream.setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }
}
