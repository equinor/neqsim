package neqsim.processSimulation.processEquipment.pipeline;

import java.util.UUID;
import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * SimpleTPoutPipeline class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleTPoutPipeline extends Pipeline {
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  protected double temperatureOut = 0, pressureOut = 0.0;
  double dH = 0.0;

  /**
   * <p>
   * Constructor for SimpleTPoutPipeline.
   * </p>
   */
  @Deprecated
  public SimpleTPoutPipeline() {}

  /**
   * <p>
   * Constructor for SimpleTPoutPipeline.
   * </p>
   *
   * @param inStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public SimpleTPoutPipeline(StreamInterface inStream) {
    this("SimpleTPoutPipeline", inStream);
  }

  /**
   * Constructor for SimpleTPoutPipeline.
   *
   * @param name name of pipeline
   */
  public SimpleTPoutPipeline(String name) {
    super(name);
  }

  /**
   * Constructor for SimpleTPoutPipeline.
   *
   * @param name name of pipeline
   * @param inStream the inlet stream
   */
  public SimpleTPoutPipeline(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutTemperature(double temperature) {
    this.temperatureOut = temperature;
  }

  /**
   * <p>
   * setOutPressure.
   * </p>
   *
   * @param pressure a double
   */
  public void setOutPressure(double pressure) {
    this.pressureOut = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    // system.setMultiPhaseCheck(true);
    system.setTemperature(this.temperatureOut);
    system.setPressure(this.pressureOut);
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    // system.setMultiPhaseCheck(false);
    outStream.setThermoSystem(system);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    outStream.getThermoSystem().display(name);
    outStream.getThermoSystem().initPhysicalProperties();
    System.out.println("Superficial velocity out gas : " + getSuperficialVelocity(0, 1));
    System.out.println("Superficial velocity out condensate : " + getSuperficialVelocity(1, 1));
    System.out.println("Superficial velocity out MEG/water : " + getSuperficialVelocity(2, 1));
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {}
}
