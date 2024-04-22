package neqsim.processSimulation.processEquipment.expander;



import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.UUID;




import neqsim.processSimulation.processEquipment.TwoPortEquipment;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ExpanderOld class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ExpanderOld extends TwoPortEquipment implements ExpanderInterface {
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem;
  ThermodynamicOperations thermoOps;

  // TODO: neither subclass of Compressor nor TwoPortEquipment, nor used. remove class?
  double dH = 0.0;
  double pressure = 0.0;

  /**
   * <p>
   * Constructor for ExpanderOld.
   * </p>
   */
  @Deprecated
  public ExpanderOld() {
    this("ExpanderOld");
  }

  /**
   * <p>
   * Constructor for ExpanderOld.
   * </p>
   *
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  @Deprecated
  public ExpanderOld(StreamInterface inletStream) {
    this("ExpanderOld", inletStream);
  }

  /**
   * Constructor for ExpanderOld.
   *
   * @param name name of expander
   */
  public ExpanderOld(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for ExpanderOld.
   * </p>
   *
   * @param name name of expander
   * @param inletStream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *        object
   */
  public ExpanderOld(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;

    thermoSystem = inletStream.getThermoSystem().clone();
    outStream = inletStream.clone();
  }

  /** {@inheritDoc} */
  @Override
  public void setOutletPressure(double pressure) {
    this.pressure = pressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getEnergy() {
    return dH;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    System.out.println("expander running..");
    thermoSystem = inStream.getThermoSystem().clone();
    thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    double entropy = thermoSystem.getEntropy();
    System.out.println("entropy inn.." + entropy);
    double hinn = thermoSystem.getEnthalpy();
    thermoSystem.setPressure(pressure);
    thermoOps.PSflash(entropy);
    dH = thermoSystem.getEnthalpy() - hinn;
    outStream.setThermoSystem(thermoSystem);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {}
}
