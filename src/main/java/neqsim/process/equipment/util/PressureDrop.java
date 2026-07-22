package neqsim.process.equipment.util;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * PressureDrop class. The pressure drop unit is used to simulate pressure drops in process plant. The proessure drop is
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
   * Constructor for PressureDrop.
   *
   * @param name the name of the pressure drop unit
   */
  public PressureDrop(String name) {
    super(name);
  }

  /**
   * Setter for the field <code>pressureDrop</code>.
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
   * Constructor for PressureDropUnit.
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

    // A zero (or negligible) pressure drop leaves the fluid state unchanged, so the
    // constant-enthalpy flash is unnecessary and is skipped.
    if (Math.abs(pressureDrop) > 1e-10) {
      double outletPressure = thermoSystem.getPressure() - pressureDrop;
      if (outletPressure <= 0.0) {
        throw new RuntimeException(new neqsim.util.exception.InvalidInputException(this, "run", "pressureDrop",
            "results in a non-physical outlet pressure (" + outletPressure
                + " bara). The pressure drop must be smaller than the inlet pressure (" + thermoSystem.getPressure()
                + " bara)."));
      }
      thermoSystem.setPressure(outletPressure);
      thermoOps.PHflash(enthalpy);
    }

    outStream.setFluid(thermoSystem);
  }
}
