package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemEOSCGEosCO2SoundSpeedTest extends neqsim.NeqSimTest {
  static Logger logger = LogManager.getLogger(SystemEOSCGEosCO2SoundSpeedTest.class);

  @Test
  @DisplayName("Check speed of sound of CO2 with EOS-CG")
  public void testCO2SoundSpeed() {
    double temperature = 298.15; // K
    double pressure = 1.0; // bar

    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent("CO2", 1.0);
    system.setMixingRule("classic"); // EOS-CG might have its own mixing rule handling, but setting
                                     // classic is often safe or ignored if not relevant.
    // Actually EOS-CG is a specific model, maybe I shouldn't set mixing rule if it defaults
    // correctly.
    // Let's check SystemEOSCGEos constructor or init.
    // The existing test SystemEOSCGEosThermodynamicConsistencyTest doesn't set mixing rule.

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);
    system.initProperties();

    double soundSpeed = system.getPhase(0).getSoundSpeed();

    // Expected value approx 268-270 m/s for CO2 at 25C 1 atm.
    // Let's assert it's in a reasonable range for gas phase.
    assertTrue(soundSpeed > 260.0 && soundSpeed < 280.0,
        "Speed of sound for CO2 at 298.15 K and 1 bar should be around 270 m/s. Calculated: "
            + soundSpeed);

    logger.debug("Calculated speed of sound for CO2 at " + temperature + " K and " + pressure
        + " bar: " + soundSpeed + " m/s");
  }

  @Test
  @DisplayName("Check speed of sound of Methane with EOS-CG")
  public void testMethaneSoundSpeed() {
    double temperature = 298.15; // K
    double pressure = 1.0; // bar

    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent("methane", 1.0);
    // system.setMixingRule("classic"); // Not strictly needed for pure component in EOS-CG usually

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);
    system.initProperties();

    double soundSpeed = system.getPhase(0).getSoundSpeed();

    // Expected value approx 446 m/s for Methane at 25C 1 atm.
    assertTrue(soundSpeed > 440.0 && soundSpeed < 460.0,
        "Speed of sound for Methane at 298.15 K and 1 bar should be around 446 m/s. Calculated: "
            + soundSpeed);

    logger.debug("Calculated speed of sound for Methane at " + temperature + " K and " + pressure
        + " bar: " + soundSpeed + " m/s");
  }

  @Test
  @DisplayName("Check speed of sound of CO with EOS-CG")
  public void testCOSoundSpeed() {
    double temperature = 298.15; // K
    double pressure = 1.0; // bar

    SystemInterface system = new SystemEOSCGEos(temperature, pressure);
    system.addComponent("CO", 1.0);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(3);
    system.initProperties();

    double soundSpeed = system.getPhase(0).getSoundSpeed();

    // Expected value approx 352 m/s for CO at 25C 1 atm (Ideal gas approx:
    // sqrt(1.4*8.314*298.15/0.02801) ~= 352 m/s)
    assertTrue(soundSpeed > 345.0 && soundSpeed < 360.0,
        "Speed of sound for CO at 298.15 K and 1 bar should be around 352 m/s. Calculated: "
            + soundSpeed);

    logger.debug("Calculated speed of sound for CO at " + temperature + " K and " + pressure
        + " bar: " + soundSpeed + " m/s");
  }
}
