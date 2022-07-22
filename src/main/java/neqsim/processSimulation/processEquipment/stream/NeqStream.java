package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * NeqStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NeqStream extends Stream {
  private static final long serialVersionUID = 1000;
  static Logger logger = LogManager.getLogger(NeqStream.class);

  /**
   * <p>
   * Constructor for NeqStream.
   * </p>
   */
  @Deprecated
  public NeqStream() {
    super("NeqStream");
  }

  /**
   * <p>
   * Constructor for NeqStream.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  @Deprecated
  public NeqStream(SystemInterface thermoSystem) {
    super(thermoSystem);
  }

  /**
   * <p>
   * Constructor for NeqStream.
   * </p>
   *
   * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
   */
  @Deprecated
  public NeqStream(StreamInterface stream) {
    super(stream);
  }

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
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }

    thermoSystem = thermoSystem.clone();

    return clonedStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    System.out.println("start flashing stream... " + streamNumber);
    if (stream != null) {
      thermoSystem = this.stream.getThermoSystem().clone();
    }
    this.thermoSystem.init_x_y();
    this.thermoSystem.initBeta();
    this.thermoSystem.init(3);
    // thermoOps = new ThermodynamicOperations(thermoSystem);
    // thermoOps.TPflash();
    System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
    System.out.println("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }
}
