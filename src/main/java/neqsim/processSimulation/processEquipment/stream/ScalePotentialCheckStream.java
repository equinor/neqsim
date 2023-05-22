package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ScalePotentialCheckStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ScalePotentialCheckStream extends Stream {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(ScalePotentialCheckStream.class);

  protected SystemInterface reactiveThermoSystem;

  /**
   * <p>
   * Constructor for ScalePotentialCheckStream.
   * </p>
   */
  @Deprecated(forRemoval = true)
  public ScalePotentialCheckStream() {
    super("ScalePotentialCheckStream");
  }

  /**
   * <p>
   * Constructor for ScalePotentialCheckStream.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ScalePotentialCheckStream(SystemInterface thermoSystem) {
    super("ScalePotentialCheckStream", thermoSystem);
  }

  /**
   * <p>
   * Constructor for ScalePotentialCheckStream.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  @Deprecated(forRemoval = true)
  public ScalePotentialCheckStream(StreamInterface stream) {
    super("ScalePotentialCheckStream", stream);
  }

  /**
   * Constructor for ScalePotentialCheckStream.
   *
   * @param name name of unit operation
   */
  public ScalePotentialCheckStream(String name) {
    super(name);
  }

  /**
   * Constructor for ScalePotentialCheckStream.
   *
   * @param name name of stream
   * @param stream input stream
   */
  public ScalePotentialCheckStream(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * <p>
   * Constructor for ScalePotentialCheckStream.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public ScalePotentialCheckStream(String name, SystemInterface thermoSystem) {
    super(name, thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public ScalePotentialCheckStream clone() {
    ScalePotentialCheckStream clonedSystem = null;
    try {
      clonedSystem = (ScalePotentialCheckStream) super.clone();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return clonedSystem;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    System.out.println("start flashing stream... " + streamNumber);
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }
    if (stream != null) {
      reactiveThermoSystem = this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
    thermoOps.TPflash();
    reactiveThermoSystem.init(3);

    System.out.println("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
    System.out.println("beta: " + reactiveThermoSystem.getBeta());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    reactiveThermoSystem.display(name);
  }
}
