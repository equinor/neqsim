package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Consistency checks for CO2-rich associated gas and live oil, used as the pass/fail gate for the Brazil pre-salt
 * analogue-basis skill.
 *
 * <p>
 * These are physical-consistency checks (monotonic swelling, single dense phase above the cricondenbar, pure-component
 * critical temperature), not a validation against measured pre-salt PVT data. Measured data must be added per field.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class Co2RichAssociatedGasConsistencyTest {

  private static double bubblePressure(double co2, double methane, double decane) throws Exception {
    SystemInterface fluid = new SystemSrkEos(273.15 + 100.0, 100.0);
    fluid.addComponent("CO2", co2);
    fluid.addComponent("methane", methane);
    fluid.addComponent("nC10", decane);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.bubblePointPressureFlash();
    return fluid.getPressure();
  }

  @Test
  void pureCo2CriticalTemperatureIsCorrect() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 100.0);
    fluid.addComponent("CO2", 1.0);
    fluid.setMixingRule("classic");
    assertEquals(304.1, fluid.getPhase(0).getComponent("CO2").getTC(), 0.5);
  }

  @Test
  void bubblePressureRisesWithCo2Content() throws Exception {
    double low = bubblePressure(0.05, 0.20, 0.75);
    double mid = bubblePressure(0.20, 0.20, 0.60);
    double high = bubblePressure(0.40, 0.20, 0.40);
    assertTrue(low > 1.0 && high < 2000.0, "pressures must be physical");
    assertTrue(mid > low, "bubble pressure must rise with CO2");
    assertTrue(high > mid, "bubble pressure must rise with CO2");
  }

  @Test
  void co2RichGasIsOneDensePhaseAboveTheCricondenbar() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 40.0, 250.0);
    fluid.addComponent("CO2", 0.70);
    fluid.addComponent("methane", 0.30);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(1, fluid.getNumberOfPhases());
    assertTrue(fluid.getDensity("kg/m3") > 200.0, "dense CO2-rich phase expected");
  }
}
