/*
 * TwoPhasePipeLine.java
 *
 * Created on 21. august 2001, 20:45
 */

package neqsim.processSimulation.processEquipment.pipeline;

import java.util.UUID;
import neqsim.fluidMechanics.flowSystem.twoPhaseFlowSystem.twoPhasePipeFlowSystem.TwoPhasePipeFlowSystem;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * TwoPhasePipeLine class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class TwoPhasePipeLine extends Pipeline {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for TwoPhasePipeLine.
   * </p>
   */
  @Deprecated
  public TwoPhasePipeLine() {
    this("TwoPhasePipeLine");
  }

  /**
   * <p>
   * Constructor for TwoPhasePipeLine.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  @Deprecated
  public TwoPhasePipeLine(StreamInterface inStream) {
    this("TwoPhasePipeLine", inStream);
  }

  /**
   * Constructor for TwoPhasePipeLine.
   * 
   * @param name name of pipeline
   */
  public TwoPhasePipeLine(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for TwoPhasePipeLine.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.Stream} object
   */
  public TwoPhasePipeLine(String name, StreamInterface inStream) {
    super(name, inStream);
    pipe = new TwoPhasePipeFlowSystem();
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
    pipe.solveSteadyState(2, id);
    setCalculationIdentifier(id);
    pipe.print();
  }
}
