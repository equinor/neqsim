package neqsim.process.equipment.pipeline;

import java.util.UUID;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Simple pipeline model with specified outlet temperature and pressure.
 *
 * <p>
 * This class provides a simple pipeline model where outlet conditions are directly specified rather
 * than calculated from flow correlations. Useful for preliminary calculations or when detailed
 * pressure drop modeling is not required.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * SimpleTPoutPipeline pipe = new SimpleTPoutPipeline("simple_pipe", inletStream);
 * pipe.setOutTemperature(300.0); // 300 K
 * pipe.setOutPressure(50.0); // 50 bara
 * pipe.run();
 * }</pre>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class SimpleTPoutPipeline extends Pipeline {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  protected double temperatureOutValue = 0;
  protected double pressureOutValue = 0.0;
  double dH = 0.0;

  /**
   * Constructor for SimpleTPoutPipeline.
   *
   * @param name name of pipeline
   */
  public SimpleTPoutPipeline(String name) {
    super(name);
    this.adiabatic = false;
  }

  /**
   * Constructor for SimpleTPoutPipeline.
   *
   * @param name name of pipeline
   * @param inStream the inlet stream
   */
  public SimpleTPoutPipeline(String name, StreamInterface inStream) {
    super(name, inStream);
    this.adiabatic = false;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletTemperature(double temperature) {
    this.temperatureOutValue = temperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressureOutValue = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    double inletPressure = system.getPressure();
    system.setTemperature(this.temperatureOutValue);
    system.setPressure(this.pressureOutValue);
    ThermodynamicOperations testOps = new ThermodynamicOperations(system);
    testOps.TPflash();
    outStream.setThermoSystem(system);
    outStream.getThermoSystem().initProperties();

    // Calculate pressure drop
    pressureDrop = inletPressure - this.pressureOutValue;

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    outStream.getThermoSystem().display(name);
    outStream.getThermoSystem().initPhysicalProperties();
    System.out.println("Superficial velocity out gas : " + getSuperficialVelocity(0));
    System.out.println("Superficial velocity out condensate : " + getSuperficialVelocity(1));
    System.out.println("Superficial velocity out MEG/water : " + getSuperficialVelocity(2));
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    // Not applicable for simple T-P out pipeline
  }
}
