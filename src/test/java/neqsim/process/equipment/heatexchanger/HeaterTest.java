package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class HeaterTest {
  private static class InitTrackingSystemSrkEos extends SystemSrkEos {
    private static final long serialVersionUID = 1000L;
    private AtomicInteger levelTwoCalls = new AtomicInteger();
    private AtomicInteger levelThreeCalls = new AtomicInteger();

    InitTrackingSystemSrkEos(double temperature, double pressure) {
      super(temperature, pressure);
    }

    @Override
    public InitTrackingSystemSrkEos clone() {
      InitTrackingSystemSrkEos cloned = (InitTrackingSystemSrkEos) super.clone();
      cloned.levelTwoCalls = levelTwoCalls;
      cloned.levelThreeCalls = levelThreeCalls;
      return cloned;
    }

    @Override
    public void init(int initType) {
      super.init(initType);
      if (levelTwoCalls != null && initType == 2) {
        levelTwoCalls.incrementAndGet();
      } else if (levelThreeCalls != null && initType == 3) {
        levelThreeCalls.incrementAndGet();
      }
    }

    void resetInitCounts() {
      levelTwoCalls.set(0);
      levelThreeCalls.set(0);
    }

    int getLevelTwoCalls() {
      return levelTwoCalls.get();
    }

    int getLevelThreeCalls() {
      return levelThreeCalls.get();
    }
  }

  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    processOps = new ProcessSystem();
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Heater heater1 = new Heater("heater 1", inletStream);
    heater1.setOutTemperature(310.0);
    processOps.add(inletStream);
    processOps.add(heater1);
    processOps.run();
  }

  @Test
  void testNeedRecalculation() {
    ((Heater) processOps.getUnit("heater 1")).setOutTemperature(348.1, "K");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());

    ((Heater) processOps.getUnit("heater 1")).setOutPressure(10.0, "bara");
    assertTrue(((Heater) processOps.getUnit("heater 1")).needRecalculation());
    processOps.run();
    assertFalse(((Heater) processOps.getUnit("heater 1")).needRecalculation());
  }

  /**
   * Heater output properties only require thermodynamic initialization level 2. A level-3 pass calculates unused
   * composition derivatives and must not be reintroduced between the flash and physical-property initialization.
   */
  @Test
  void testRunUsesMinimumThermodynamicInitializationLevel() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(298.15, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("tracked inlet", fluid);
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.run(UUID.randomUUID());
    fluid.resetInitCounts();

    Heater heater = new Heater("tracked heater", inlet);
    heater.setOutTemperature(320.0, "K");
    heater.setOutPressure(75.0, "bara");
    heater.run(UUID.randomUUID());

    assertTrue(fluid.getLevelTwoCalls() > 0, "Outlet caloric and physical properties require init(2)");
    assertEquals(0, fluid.getLevelThreeCalls(), "Heater must not calculate unused composition derivatives");
    assertEquals(320.0, heater.getOutletStream().getTemperature("K"), 1.0e-10);
    assertEquals(75.0, heater.getOutletStream().getPressure("bara"), 1.0e-10);
    assertEquals(inlet.getFlowRate("kg/hr"), heater.getOutletStream().getFlowRate("kg/hr"), 1.0e-8);
    assertEquals(heater.getOutletStream().getFluid().getEnthalpy() - inlet.getFluid().getEnthalpy(), heater.getDuty(),
        Math.max(1.0e-6, Math.abs(heater.getDuty()) * 1.0e-12));

    fluid.resetInitCounts();
    heater.setOutTemperature(325.0, "K");
    heater.run(UUID.randomUUID());
    assertEquals(0, fluid.getLevelThreeCalls(), "Nearby operating points must retain minimal initialization");
    assertEquals(325.0, heater.getOutletStream().getTemperature("K"), 1.0e-10);
  }

  /**
   * Entropy requires caloric properties but not level-3 composition derivatives. The diagnostic must preserve its value
   * and the surrounding stream state while using the minimum thermodynamic initialization level.
   */
  @Test
  void testEntropyProductionUsesMinimumThermodynamicInitializationLevel() {
    InitTrackingSystemSrkEos fluid = new InitTrackingSystemSrkEos(363.15, 40.0);
    fluid.addComponent("nitrogen", 0.02);
    fluid.addComponent("CO2", 0.03);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-heptane", 0.04);
    fluid.setMixingRule("classic");

    Stream inlet = new Stream("tracked entropy inlet", fluid);
    inlet.setTemperature(90.0, "C");
    inlet.setFlowRate(10000.0, "kg/hr");

    Heater heater = new Heater("tracked entropy heater", inlet);
    heater.setOutTemperature(130.0, "C");
    ProcessSystem process = new ProcessSystem();
    process.add(inlet);
    process.add(heater);
    process.run();

    assertMinimumEntropyInitialization(fluid, heater);

    inlet.setTemperature(95.0, "C");
    process.run();
    assertMinimumEntropyInitialization(fluid, heater);
  }

  private static void assertMinimumEntropyInitialization(InitTrackingSystemSrkEos fluid, Heater heater) {
    double inletEnthalpy = heater.getInletStream().getFluid().getEnthalpy();
    double outletEnthalpy = heater.getOutletStream().getFluid().getEnthalpy();
    double inletFlow = heater.getInletStream().getFlowRate("kg/hr");
    double outletFlow = heater.getOutletStream().getFlowRate("kg/hr");
    int inletPhases = heater.getInletStream().getFluid().getNumberOfPhases();
    int outletPhases = heater.getOutletStream().getFluid().getNumberOfPhases();

    fluid.resetInitCounts();
    double actualEntropy = heater.getEntropyProduction("J/K");
    int actualLevelTwoCalls = fluid.getLevelTwoCalls();
    int actualLevelThreeCalls = fluid.getLevelThreeCalls();
    double actualInletEnthalpy = heater.getInletStream().getFluid().getEnthalpy();
    double actualOutletEnthalpy = heater.getOutletStream().getFluid().getEnthalpy();
    double actualInletFlow = heater.getInletStream().getFlowRate("kg/hr");
    double actualOutletFlow = heater.getOutletStream().getFlowRate("kg/hr");
    int actualInletPhases = heater.getInletStream().getFluid().getNumberOfPhases();
    int actualOutletPhases = heater.getOutletStream().getFluid().getNumberOfPhases();

    double expectedEntropy = referenceEntropyProduction(heater, "J/K");

    assertEquals(expectedEntropy, actualEntropy, Math.max(1.0e-10, Math.abs(expectedEntropy) * 1.0e-12));
    assertTrue(actualLevelTwoCalls >= 2, "Both inlet and outlet still require caloric initialization");
    assertEquals(0, actualLevelThreeCalls, "Entropy diagnostics must not calculate composition derivatives");
    assertEquals(inletEnthalpy, actualInletEnthalpy, Math.max(1.0e-6, Math.abs(inletEnthalpy) * 1.0e-12));
    assertEquals(outletEnthalpy, actualOutletEnthalpy, Math.max(1.0e-6, Math.abs(outletEnthalpy) * 1.0e-12));
    assertEquals(inletFlow, actualInletFlow, 1.0e-8);
    assertEquals(outletFlow, actualOutletFlow, 1.0e-8);
    assertEquals(actualInletFlow, actualOutletFlow, 1.0e-8);
    assertEquals(inletPhases, actualInletPhases);
    assertEquals(outletPhases, actualOutletPhases);
  }

  private static double referenceEntropyProduction(Heater heater, String unit) {
    UUID id = UUID.randomUUID();
    heater.getInletStream().run(id);
    heater.getInletStream().getFluid().init(3);
    heater.getOutletStream().run(id);
    heater.getOutletStream().getFluid().init(3);
    return heater.getOutletStream().getThermoSystem().getEntropy(unit)
        - heater.getInletStream().getThermoSystem().getEntropy(unit);
  }
}
