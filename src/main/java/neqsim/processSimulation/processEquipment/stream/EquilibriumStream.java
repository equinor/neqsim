package neqsim.processSimulation.processEquipment.stream;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * EquilibriumStream class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class EquilibriumStream extends Stream {
  private static final long serialVersionUID = 1000;
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
      ex.printStackTrace();
    }

    thermoSystem = thermoSystem.clone();
    return clonedStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    System.out.println("start flashing stream... " + streamNumber);
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.TPflash();
    System.out.println("number of phases: " + thermoSystem.getNumberOfPhases());
    System.out.println("beta: " + thermoSystem.getBeta());
    setCalculationIdentifier(id);
  }
}
