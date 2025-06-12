package neqsim.process.equipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.exception.InvalidInputException;

/**
 * <p>
 * NeqStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqStream extends Stream {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(NeqStream.class);

  /**
   * Constructor for NeqStream.
   *
   * @param name name of stream
   */
  public NeqStream(String name) {
    super(name);
  }

  /**
   * Constructor for NeqStream.
   *
   * @param name name of stream
   * @param stream input stream
   */
  public NeqStream(String name, StreamInterface stream) {
    super(name, stream);
  }

  /**
   * <p>
   * Constructor for NeqStream.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public NeqStream(String name, SystemInterface thermoSystem) {
    super(name, thermoSystem);
  }

  /** {@inheritDoc} */
  @Override
  public NeqStream clone() {
    NeqStream clonedStream = null;

    try {
      clonedStream = (NeqStream) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    thermoSystem = thermoSystem.clone();

    return clonedStream;
  }

  /** {@inheritDoc} */
  @Override
  public NeqStream clone(String name) {
    if (this.getName() == name) {
      throw new RuntimeException(
          new InvalidInputException(this, "clone", "name", "- Same name as in original object"));
    }
    NeqStream s = this.clone();
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
    this.thermoSystem.init_x_y();
    this.thermoSystem.initBeta();
    this.thermoSystem.init(3);
    // thermoOps = new ThermodynamicOperations(thermoSystem);
    // thermoOps.TPflash();
    logger.info("number of phases: " + thermoSystem.getNumberOfPhases());
    logger.info("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }
}
