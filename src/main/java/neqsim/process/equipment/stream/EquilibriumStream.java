package neqsim.process.equipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * EquilibriumStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class EquilibriumStream extends Stream {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(EquilibriumStream.class);

  /**
   * Constructor for EquilibriumStream.
   *
   * @param name name of stream
   */
  public EquilibriumStream(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for EquilibriumStream.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public EquilibriumStream(String name, SystemInterface thermoSystem) {
    super(name, thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public EquilibriumStream clone() {
    EquilibriumStream clonedStream = null;

    try {
      clonedStream = (EquilibriumStream) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    thermoSystem = thermoSystem.clone();
    return clonedStream;
  }

  /** {@inheritDoc} */
  @Override
  public EquilibriumStream clone(String name) {
    if (this.getName() == name) {
      throw new RuntimeException(
          new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
    }
    EquilibriumStream s = this.clone();
    s.setName(name);
    return s;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    logger.info("start flashing stream... " + streamNumber);
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    logger.info("number of phases: " + thermoSystem.getNumberOfPhases());
    logger.info("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }
}
