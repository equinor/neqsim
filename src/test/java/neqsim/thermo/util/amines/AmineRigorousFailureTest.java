package neqsim.thermo.util.amines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * The experimental wrapper must distinguish an unavailable CO2 result from zero pressure.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AmineRigorousFailureTest {
  /**
   * A successful bubble flash does not make a missing CO2 property available.
   *
   * @throws Exception if the isolated wrapper fixture cannot be configured
   */
  @Test
  void missingCo2ReadDoesNotBecomeZeroPressure() throws Exception {
    SystemInterface fluid = new SystemSrkEos(150.0, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);

    double pressure = wrap(fluid).getCO2PartialPressureRigorous();

    assertTrue(fluid.getPressure() > 1.0, "The bubble calculation must finish before the component read fails");
    assertTrue(Double.isNaN(pressure), "Unavailable CO2 data must not report absence of CO2");
  }

  /**
   * Flash failure keeps the same unavailable-result convention as property-read failure.
   *
   * @throws Exception if the isolated wrapper fixture cannot be configured
   */
  @Test
  void supercriticalBubbleFailureIsUnavailable() throws Exception {
    SystemInterface fluid = new SystemSrkEos(300.0, 1.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);

    assertTrue(Double.isNaN(wrap(fluid).getCO2PartialPressureRigorous()));
  }

  /**
   * A successful calculation must still publish the vapor-phase CO2 partial pressure.
   *
   * @throws Exception if the isolated wrapper fixture cannot be configured
   */
  @Test
  void successfulReadRetainsTheComputedPartialPressure() throws Exception {
    SystemInterface fluid = new SystemSrkEos(250.0, 5.0);
    fluid.addComponent("CO2", 0.25);
    fluid.addComponent("ethane", 0.75);
    fluid.setMixingRule(2);

    double pressure = wrap(fluid).getCO2PartialPressureRigorous();

    assertTrue(Double.isFinite(pressure) && pressure > 0.0 && pressure < fluid.getPressure());
    assertEquals(fluid.getPhase(0).getComponent("CO2").getx() * fluid.getPressure(), pressure, 1.0e-12);
  }

  /**
   * Supply a real cubic fixture to test the wrapper contract independently of uncalibrated amine equilibrium.
   *
   * @param fluid initialized component system used by the wrapper
   * @return wrapper containing the supplied thermodynamic system
   * @throws Exception if the private fixture state cannot be assigned
   */
  private AmineSystem wrap(SystemInterface fluid) throws Exception {
    AmineSystem wrapper = new AmineSystem(AmineSystem.AmineType.MDEA);
    Field systemField = AmineSystem.class.getDeclaredField("system");
    systemField.setAccessible(true);
    systemField.set(wrapper, fluid);
    Field initializedField = AmineSystem.class.getDeclaredField("initialized");
    initializedField.setAccessible(true);
    initializedField.setBoolean(wrapper, true);
    return wrapper;
  }
}
