package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import neqsim.util.exception.InvalidInputException;

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

  /**
   * Clone ScalePotentialCheckStream object and give it a new name.
   *
   * @param name Name to set for the cloned object
   * @return Cloned ScalePotentialCheckStream object
   */
  @Override
  public ScalePotentialCheckStream clone(String name) {
    if (this.getName() == name) {
      throw new RuntimeException(
          new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
    }
    ScalePotentialCheckStream s = this.clone();
    s.setName(name);
    return s;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    logger.info("start flashing stream... " + streamNumber);
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }
    if (stream != null) {
      reactiveThermoSystem = this.stream.getThermoSystem().setModel("Electrolyte-CPA-EOS-statoil");
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(reactiveThermoSystem);
    thermoOps.TPflash();
    reactiveThermoSystem.init(3);

    logger.info("number of phases: " + reactiveThermoSystem.getNumberOfPhases());
    logger.info("beta: " + reactiveThermoSystem.getBeta());
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void displayResult() {
    reactiveThermoSystem.display(name);
  }
}
