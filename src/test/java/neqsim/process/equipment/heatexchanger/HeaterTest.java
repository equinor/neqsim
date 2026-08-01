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
}
