package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SystemThermoSetMolarCompositionTest extends neqsim.NeqSimTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemThermoSetMolarCompositionTest.class);

  SystemInterface sys;

  @BeforeEach
  void setup() {
    sys = new SystemSrkEos(298.0, 300.0);
    sys.addComponent("nitrogen", 0.64);
    sys.addTBPfraction("C7", 1.06, 92.2 / 1000.0, 0.7324);
    sys.addPlusFraction("C20", 10.62, 381.0 / 1000.0, 0.88);
  }

  @Test
  void testSetMolarComposition() {
    sys.setMolarComposition(new double[] {1, 1.5, 1.75});
    double[] molarComposition = sys.getMolarComposition();
    Assertions.assertEquals(0.23529411764705882, molarComposition[0], 0.0000001);
    Assertions.assertEquals(0.35294117647058826, molarComposition[1], 0.0000001);
    Assertions.assertEquals(0.411764705882353, molarComposition[2], 0.0000001);

    logger.info(sys);
  }

  @Test
  void setMolarCompositionPlus() {
    sys.getCharacterization().characterisePlusFraction();
    sys.setMolarCompositionPlus(new double[] {1, 1.5, 1.75});
    double[] molarComposition = sys.getMolarComposition();

    Assertions.assertEquals(0.3999998435802131, molarComposition[0], 0.0000001);
    Assertions.assertEquals(0.17255098326920942, molarComposition[1], 0.0000001);
    Assertions.assertEquals(0.1310217963111427, molarComposition[2], 0.0000001);
    logger.info(sys);
  }

  @Test
  void testSetMolarCompositionOfPlusFluid() {
    sys.setMolarCompositionOfPlusFluid(new double[] {1, 1.5, 1.75});
    double[] molarComposition = sys.getMolarComposition();

    Assertions.assertEquals(0.39999999999999997, molarComposition[0], 0.0000001);
    Assertions.assertEquals(0.6, molarComposition[1], 0.0000001);
    Assertions.assertEquals(0.0, molarComposition[2], 0.0000001);
    logger.info(sys);
  }

  @Test
  void testNoFlow() {
    sys.reset();
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      sys.setMolarComposition(new double[] {1, 1.5, 1.75});
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: SystemSrkEos:setMolarComposition - Input totalFlow must be larger than 0 (1e-100) when setting molar composition",
        thrown.getMessage());
  }

  @Test
  void testNoFlowPlus() {
    sys.reset();
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      sys.setMolarCompositionPlus(new double[] {1, 1.5, 1.75});
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: SystemSrkEos:setMolarComposition - Input totalFlow must be larger than 0 (1e-100) when setting molar composition",
        thrown.getMessage());
  }

  @Test
  void testNoFlowPlusFluid() {
    sys.reset();
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      sys.setMolarCompositionOfPlusFluid(new double[] {1, 1.5, 1.75});
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: SystemSrkEos:setMolarComposition - Input totalFlow must be larger than 0 (1e-100) when setting molar composition",
        thrown.getMessage());
  }

  @Test
  void setFlowRateTest() {
    sys.init(0);
    double liqDensity = sys.getIdealLiquidDensity("gr/cm3");
    assertEquals(0.8762236458342041, liqDensity, 1e-3);
    liqDensity = sys.getIdealLiquidDensity("kg/m3");
    assertEquals(876.2236458342041, liqDensity, 1e-3);

    sys.setTotalFlowRate(1000.0, "idSm3/hr");

    double flowRate = sys.getFlowRate("kg/hr");

    assertEquals(876223.6458342039, flowRate, 1e-3);
  }

  @Test
  void setFlowRateMolePerHrTest() {
    SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
    fluid.addComponent("ethane", 0.5);
    fluid.addComponent("propane", 0.5);
    fluid.setMixingRule("classic");

    // Set flow rate using mole/hr
    fluid.setTotalFlowRate(3600.0, "mole/hr");
    double molarFlowMoleSec = fluid.getFlowRate("mole/sec");
    assertEquals(1.0, molarFlowMoleSec, 1e-6);
    double molarFlowMoleHr = fluid.getFlowRate("mole/hr");
    assertEquals(3600.0, molarFlowMoleHr, 1e-3);

    // Set flow rate using mol/hr (short alias)
    fluid.setTotalFlowRate(7200.0, "mol/hr");
    assertEquals(2.0, fluid.getFlowRate("mol/sec"), 1e-6);
    assertEquals(7200.0, fluid.getFlowRate("mol/hr"), 1e-3);
  }

  @Test
  void setFlowRateKmolePerHrTest() {
    SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
    fluid.addComponent("ethane", 0.5);
    fluid.addComponent("propane", 0.5);
    fluid.setMixingRule("classic");

    // Set flow rate using kmole/hr
    fluid.setTotalFlowRate(1.0, "kmole/hr");
    double molarFlowMoleSec = fluid.getFlowRate("mole/sec");
    // 1 kmole/hr = 1000 mol / 3600 sec = 0.27778 mol/sec
    assertEquals(1000.0 / 3600.0, molarFlowMoleSec, 1e-6);
    double molarFlowKmoleHr = fluid.getFlowRate("kmole/hr");
    assertEquals(1.0, molarFlowKmoleHr, 1e-6);

    // Set flow rate using kmol/hr (short alias)
    fluid.setTotalFlowRate(2.0, "kmol/hr");
    assertEquals(2.0, fluid.getFlowRate("kmol/hr"), 1e-6);
    assertEquals(2000.0 / 3600.0, fluid.getFlowRate("mol/sec"), 1e-6);
  }

  @Test
  void testWeightBasedComposition() {
    SystemInterface weightSystem = new SystemSrkEos(298.0, 50.0);
    weightSystem.addComponent("methane", 1.0);
    weightSystem.addComponent("ethane", 1.0);
    weightSystem.setMolarComposition(new double[] {0.25, 0.75});

    double[] weightComposition = weightSystem.getWeightBasedComposition();

    double mixtureMolarMass = weightSystem.getMolarMass();
    double expectedFirst =
        weightSystem.getPhase(0).getComponent(0).getMolarMass() * 0.25 / mixtureMolarMass;
    double expectedSecond =
        weightSystem.getPhase(0).getComponent(1).getMolarMass() * 0.75 / mixtureMolarMass;

    assertEquals(expectedFirst, weightComposition[0]);
    assertEquals(expectedSecond, weightComposition[1]);
    assertEquals(1.0, Arrays.stream(weightComposition).sum(), 1e-12);
  }
}
