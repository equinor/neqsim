package neqsim.process.equipment.subsea;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * SimpleFlowLine class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SimpleFlowLine extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double height = 100.0;
  public double length = 520.0;
  double outletTemperature = 313.15;
  AdiabaticTwoPhasePipe pipeline;

  /**
   * Constructor for SimpleFlowLine.
   *
   * @param name Name of SimpleFlowLine object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SimpleFlowLine(String name, StreamInterface inStream) {
    super(name);
    setInletStream(inStream);
    pipeline = new AdiabaticTwoPhasePipe("pipeline", inStream);
  }

  /**
   * <p>
   * Getter for the field <code>pipeline</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe} object
   */
  public AdiabaticTwoPhasePipe getPipeline() {
    return pipeline;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return getOutletStream().getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    pipeline.run(id);
    getOutletStream().setFluid(pipeline.getOutletStream().getFluid());

    /*
     * System.out.println("stary P " );
     *
     * SystemInterface fluidIn = (inStream.getFluid()).clone(); fluidIn.initProperties();
     *
     * double density = fluidIn.getDensity("kg/m3");
     *
     * double deltaP = density*getHeight()*neqsim.thermo.ThermodynamicConstantsInterface.gravity/1.
     * 0e5;
     *
     * System.out.println("density " +density + " delta P " + deltaP);
     *
     * fluidIn.setPressure(fluidIn.getPressure("bara")-deltaP);
     * fluidIn.setTemperature(outletTemperature);
     *
     * ThermodynamicOperations ops = new ThermodynamicOperations(fluidIn); ops.TPflash();
     *
     * getOutletStream().setFluid(fluidIn);
     */
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * Getter for the field <code>height</code>.
   * </p>
   *
   * @return a double
   */
  public double getHeight() {
    return height;
  }

  /**
   * <p>
   * Setter for the field <code>height</code>.
   * </p>
   *
   * @param height a double
   */
  public void setHeight(double height) {
    this.height = height;
  }
}
