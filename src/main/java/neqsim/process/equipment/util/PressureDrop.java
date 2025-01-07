package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * PressureDrop class.
 * </p>
 * The pressure drop unit is used to simulate pressure drops in process plant. The proessure drop is
 * simulated using a constant enthalpy flash.
 *
 * @author esol
 * @version $Id: $Id
 */
public class PressureDrop extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PressureDrop.class);

  SystemInterface thermoSystem;
  double pressureDrop = 0.1;

  /**
   * <p>
   * Constructor for PressureDrop.
   * </p>
   *
   * @param name the name of the pressure drop unit
   */
  public PressureDrop(String name) {
    super(name);
  }

  /**
   * <p>
   * Setter for the field <code>pressureDrop</code>.
   * </p>
   *
   * @param pressureDrop a double
   * @param unit a {@link java.lang.String} object
   */
  public void setPressureDrop(double pressureDrop, String unit) {
    if (unit.equals("bara")) {
      this.pressureDrop = pressureDrop;
    } else if (unit.equals("Pa")) {
      this.pressureDrop = pressureDrop / 1e5;
    } else {
      throw new RuntimeException("pressure drop unit not supported: " + unit);
    }
  }

  /**
   * <p>
   * Constructor for PressureDropUnit.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public PressureDrop(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = getInletStream().getThermoSystem().clone();
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();

    thermoSystem.setPressure(thermoSystem.getPressure() - pressureDrop);
    thermoOps.PHflash(enthalpy);

    outStream.setFluid(thermoSystem);
  }
}
