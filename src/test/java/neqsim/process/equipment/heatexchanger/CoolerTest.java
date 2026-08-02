package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class CoolerTest {

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
      if (cloned == null) {
        throw new IllegalStateException("Failed to clone initialization-tracking fluid");
      }
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

  neqsim.thermo.system.SystemInterface testSystem;
  Stream inletStream;
  ProcessSystem process;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(298.0, 50.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.setMixingRule("classic");

    inletStream = new Stream("inlet", testSystem);
    inletStream.setPressure(50.0, "bara");
    inletStream.setTemperature(80.0, "C");
    inletStream.setFlowRate(5.0, "MSm3/day");

    process = new ProcessSystem();
    process.add(inletStream);
  }

  @Test
  void testCoolerReducesTemperature() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double outletTempC = cooler.getOutletStream().getTemperature("C");
    assertEquals(30.0, outletTempC, 0.5);
  }

  @Test
  void testCoolerDutyIsNegative() {
    // Cooling should remove heat, resulting in a negative duty for the cooler
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    // getDuty() returns heat added. For a cooler, this should be negative
    // (heat is removed from the stream)
    double duty = cooler.getDuty();
    assertTrue(duty < 0, "Cooler duty should be negative (heat removed), got " + duty);
  }

  @Test
  void testCoolerPreservesPressure() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double inletP = inletStream.getPressure("bara");
    double outletP = cooler.getOutletStream().getPressure("bara");
    assertEquals(inletP, outletP, 0.01);
  }

  @Test
  void testCoolerWithOutletPressure() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    cooler.setOutPressure(45.0, "bara");
    process.add(cooler);
    process.run();

    double outletP = cooler.getOutletStream().getPressure("bara");
    assertEquals(45.0, outletP, 0.1);

    double outletTempC = cooler.getOutletStream().getTemperature("C");
    assertEquals(30.0, outletTempC, 0.5);
  }

  @Test
  void testCoolerMassBalance() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    double inletFlow = inletStream.getThermoSystem().getFlowRate("kg/hr");
    double outletFlow = cooler.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletFlow, outletFlow, inletFlow * 1e-6);
  }

  @Test
  void testCoolerToJson() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    String json = cooler.toJson();
    assertNotNull(json);
    assertFalse(json.isEmpty());
  }

  @Test
  void testCoolerNeedRecalculation() {
    Cooler cooler = new Cooler("cooler", inletStream);
    cooler.setOutTemperature(273.15 + 30.0);
    process.add(cooler);
    process.run();

    assertFalse(cooler.needRecalculation());

    cooler.setOutTemperature(273.15 + 20.0);
    assertTrue(cooler.needRecalculation());
  }

  /**
   * Entropy requires caloric properties but not level-3 composition derivatives. This is also a deterministic
   * performance gate: the diagnostic must preserve its value without derivative initialization.
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

    Stream inlet = new Stream("tracked inlet", fluid);
    inlet.setTemperature(90.0, "C");
    inlet.setFlowRate(10000.0, "kg/hr");

    Cooler cooler = new Cooler("tracked cooler", inlet);
    cooler.setOutTemperature(30.0, "C");
    ProcessSystem trackedProcess = new ProcessSystem();
    trackedProcess.add(inlet);
    trackedProcess.add(cooler);
    trackedProcess.run();

    assertMinimumEntropyInitialization(fluid, cooler);

    inlet.setTemperature(95.0, "C");
    trackedProcess.run();
    assertMinimumEntropyInitialization(fluid, cooler);
  }

  private static void assertMinimumEntropyInitialization(InitTrackingSystemSrkEos fluid, Cooler cooler) {
    double inletEnthalpy = cooler.getInletStream().getFluid().getEnthalpy();
    double outletEnthalpy = cooler.getOutletStream().getFluid().getEnthalpy();
    double inletFlow = cooler.getInletStream().getFlowRate("kg/hr");
    double outletFlow = cooler.getOutletStream().getFlowRate("kg/hr");
    int inletPhases = cooler.getInletStream().getFluid().getNumberOfPhases();
    int outletPhases = cooler.getOutletStream().getFluid().getNumberOfPhases();

    fluid.resetInitCounts();
    double actualEntropy = cooler.getEntropyProduction("J/K");
    int actualLevelTwoCalls = fluid.getLevelTwoCalls();
    int actualLevelThreeCalls = fluid.getLevelThreeCalls();
    double actualInletEnthalpy = cooler.getInletStream().getFluid().getEnthalpy();
    double actualOutletEnthalpy = cooler.getOutletStream().getFluid().getEnthalpy();
    double actualInletFlow = cooler.getInletStream().getFlowRate("kg/hr");
    double actualOutletFlow = cooler.getOutletStream().getFlowRate("kg/hr");
    int actualInletPhases = cooler.getInletStream().getFluid().getNumberOfPhases();
    int actualOutletPhases = cooler.getOutletStream().getFluid().getNumberOfPhases();

    double expectedEntropy = referenceEntropyProduction(cooler, "J/K");

    assertEquals(expectedEntropy, actualEntropy, Math.max(1.0e-10, Math.abs(expectedEntropy) * 1.0e-12));
    assertTrue(actualLevelTwoCalls >= 2, "Both inlet and outlet still require caloric initialization");
    assertEquals(0, actualLevelThreeCalls, "Entropy diagnostics must not calculate composition derivatives");
    assertEquals(inletEnthalpy, actualInletEnthalpy, Math.max(1.0e-6, Math.abs(inletEnthalpy) * 1.0e-12));
    assertEquals(outletEnthalpy, actualOutletEnthalpy, Math.max(1.0e-6, Math.abs(outletEnthalpy) * 1.0e-12));
    assertEquals(actualOutletEnthalpy - actualInletEnthalpy, cooler.getDuty(),
        Math.max(1.0e-6, Math.abs(cooler.getDuty()) * 1.0e-8));
    assertEquals(inletFlow, actualInletFlow, 1.0e-8);
    assertEquals(outletFlow, actualOutletFlow, 1.0e-8);
    assertEquals(actualInletFlow, actualOutletFlow, 1.0e-8);
    assertEquals(inletPhases, actualInletPhases);
    assertEquals(outletPhases, actualOutletPhases);
  }

  private static double referenceEntropyProduction(Cooler cooler, String unit) {
    UUID id = UUID.randomUUID();
    cooler.getInletStream().run(id);
    cooler.getInletStream().getFluid().init(3);
    cooler.getOutletStream().run(id);
    cooler.getOutletStream().getFluid().init(3);
    return cooler.getOutletStream().getThermoSystem().getEntropy(unit)
        - cooler.getInletStream().getThermoSystem().getEntropy(unit);
  }

}
