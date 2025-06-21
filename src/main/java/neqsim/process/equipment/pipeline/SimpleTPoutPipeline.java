package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SimpleTPoutPipeline class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleTPoutPipeline extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  protected double temperatureOut = 0;
  protected double pressureOut = 0.0;
  double dH = 0.0;

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
    outStream.getThermoSystem().initProperties();
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
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
