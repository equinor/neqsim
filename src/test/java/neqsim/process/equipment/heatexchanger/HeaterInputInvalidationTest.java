package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Accepted-input regressions for steady-state heaters and coolers.
 *
 * @author NeqSim
 * @version 1.0
 */
class HeaterInputInvalidationTest {
  /**
   * Supplies both unit types, all changes and all public execution paths.
   *
   * @return independent fixture arguments
   */
  static List<Arguments> changes() {
    List<Arguments> cases = new ArrayList<Arguments>();
    for (boolean cooler : new boolean[] {false, true}) {
      for (String change : new String[] {"deltaT", "temperatureUnit", "pressureUnit", "externalDuty", "mode",
          "outStream", "energyBus"}) {
        for (String path : new String[] {"direct", "optimized", "model"}) {
          cases.add(Arguments.of(cooler, change, path));
        }
      }
    }
    return cases;
  }

  /**
   * Compares a changed accepted fixture with an independent unconditional calculation.
   *
   * @param cooler whether to instantiate Cooler
   * @param change input transition
   * @param path execution entry point
   */
  @ParameterizedTest(name = "cooler={0}, change={1}, path={2}")
  @MethodSource("changes")
  void changedInputsReachOutputs(boolean cooler, String change, String path) {
    Fixture fixture = new Fixture(cooler);
    fixture.configure(change, false);
    fixture.run(path);
    fixture.configure(change, true);
    assertTrue(fixture.unit.needRecalculation(), change);
    fixture.run(path);

    Fixture reference = new Fixture(cooler);
    reference.configure(change, true);
    reference.run("direct");
    assertEquals(reference.unit.getOutletStream().getTemperature(), fixture.unit.getOutletStream().getTemperature(),
        1.0e-6);
    assertEquals(reference.unit.getOutletStream().getPressure(), fixture.unit.getOutletStream().getPressure(), 1.0e-8);
    assertEquals(reference.unit.getDuty(), fixture.unit.getDuty(), 0.1);
    assertEquals(reference.unit.getOutletStream().getFluid().getNumberOfPhases(),
        fixture.unit.getOutletStream().getFluid().getNumberOfPhases());
    assertEquals(1000.0, fixture.unit.getOutletStream().getFlowRate("kg/hr"), 1.0e-6);
    for (int i = 0; i < 2; i++) {
      assertEquals(fixture.feed.getFluid().getComponent(i).getNumberOfmoles(),
          fixture.unit.getOutletStream().getFluid().getComponent(i).getNumberOfmoles(), 1.0e-8);
    }
    fixture.feed.getFluid().init(2);
    fixture.unit.getOutletStream().getFluid().init(2);
    assertEquals(fixture.unit.getOutletStream().getFluid().getEnthalpy() - fixture.feed.getFluid().getEnthalpy(),
        fixture.unit.getDuty(), 0.1);
    if ("deltaT".equals(change)) {
      assertEquals(310.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
    } else if ("temperatureUnit".equals(change)) {
      assertEquals(573.15, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
    } else if ("pressureUnit".equals(change)) {
      assertEquals(41.01325, fixture.unit.getOutletStream().getPressure(), 1.0e-8);
    } else if ("externalDuty".equals(change) || "energyBus".equals(change)) {
      assertEquals(2000.0, fixture.unit.getDuty(), 0.1);
    }
    if ("model".equals(path)) {
      assertTrue(fixture.model.isModelConverged());
    }
  }

  /** Verifies that unchanged local specifications retain clean accepted state. */
  @Test
  void unchangedLocalInputsRemainClean() {
    for (boolean cooler : new boolean[] {false, true}) {
      Fixture fixture = new Fixture(cooler);
      fixture.unit.setdT(10.0);
      fixture.run("optimized");
      assertFalse(fixture.unit.needRecalculation());
      fixture.unit.setdT(10.0);
      Object acceptedFluid = fixture.unit.getOutletStream().getFluid();
      fixture.run("optimized");
      assertFalse(fixture.unit.needRecalculation());
      assertSame(acceptedFluid, fixture.unit.getOutletStream().getFluid());
      assertEquals(fixture.area.getCalculationIdentifier(), fixture.unit.getCalculationIdentifier());
      fixture.unit.setOutletTemperature(310.0, "K");
      assertTrue(fixture.unit.needRecalculation());
      fixture.run("optimized");
      fixture.unit.setOutletTemperature(310.0, "K");
      assertFalse(fixture.unit.needRecalculation());
    }
  }

  /** Verifies a failed calculation cannot leave the preceding acceptance snapshot reusable. */
  @Test
  void failureInvalidatesPreviousAcceptance() {
    Fixture fixture = new Fixture(false);
    fixture.unit.setdT(10.0);
    fixture.run("direct");
    String specification = fixture.unit.getSpecification();
    fixture.unit.setSpecification(null);
    assertThrows(NullPointerException.class, () -> fixture.unit.run());
    fixture.unit.setSpecification(specification);
    assertTrue(fixture.unit.needRecalculation());
    fixture.run("optimized");
    assertFalse(fixture.unit.needRecalculation());
    assertEquals(310.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
  }

  /** Verifies same-valued result-to-duty mode switches are input changes, not clean results. */
  @Test
  void sameDutyWithNewSpecificationModeIsDirty() {
    for (boolean cooler : new boolean[] {false, true}) {
      Fixture fixture = new Fixture(cooler);
      fixture.unit.setdT(10.0);
      fixture.run("direct");
      double duty = fixture.unit.getDuty();
      fixture.unit.setDuty(duty);
      assertTrue(fixture.unit.needRecalculation());
      fixture.run("optimized");
      assertEquals(duty, fixture.unit.getDuty(), 0.1);
      assertFalse(fixture.unit.needRecalculation());
    }
  }

  /** Verifies external connection replacement, mode changes, and disconnection. */
  @Test
  void externalConnectionsDoNotReuseCachedDuty() {
    for (boolean cooler : new boolean[] {false, true}) {
      Fixture fixture = new Fixture(cooler);
      fixture.configure("externalDuty", false);
      fixture.run("optimized");
      EnergyStream replacement = new EnergyStream("replacement");
      replacement.setDuty(-3000.0);
      fixture.unit.setEnergyStream(replacement);
      fixture.run("optimized");
      assertEquals(3000.0, fixture.unit.getDuty(), 0.1);
      fixture.unit.disconnectEnergyStream("heatDuty");
      assertTrue(fixture.unit.needRecalculation());
      fixture.unit.setdT(0.0);
      fixture.run("optimized");
      assertEquals(300.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
      assertFalse(fixture.unit.needRecalculation());
    }
  }

  /** Verifies deserialized copies recalculate independently of the original accepted unit. */
  @Test
  void serializedCopyHasIndependentAcceptance() {
    for (boolean cooler : new boolean[] {false, true}) {
      Fixture fixture = new Fixture(cooler);
      fixture.unit.setdT(10.0);
      fixture.run("direct");
      Heater copy = (Heater) fixture.unit.copy();
      assertNotSame(fixture.unit.getInletStream(), copy.getInletStream());
      assertTrue(copy.needRecalculation());
      assertFalse(fixture.unit.needRecalculation());
      copy.run();
      assertFalse(copy.needRecalculation());
      copy.setdT(20.0);
      copy.run();
      assertEquals(320.0, copy.getOutletStream().getTemperature(), 1.0e-6);
      assertEquals(310.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
      assertFalse(fixture.unit.needRecalculation());
    }
  }

  /** Verifies input changes during low-flow bypass are honored on reactivation. */
  @Test
  void bypassThenReactivatePreservesChangedSpecification() {
    for (boolean cooler : new boolean[] {false, true}) {
      Fixture fixture = new Fixture(cooler);
      fixture.unit.setMinimumFlow(2000.0);
      fixture.unit.setdT(10.0);
      fixture.run("optimized");
      assertEquals(300.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
      fixture.unit.setdT(20.0);
      assertTrue(fixture.unit.needRecalculation());
      fixture.run("optimized");
      fixture.unit.setMinimumFlow(0.0);
      fixture.run("optimized");
      assertEquals(320.0, fixture.unit.getOutletStream().getTemperature(), 1.0e-6);
      assertFalse(fixture.unit.needRecalculation());
    }
  }

  /**
   * Independent SRK fixture with one unit and one model area.
   *
   * @author NeqSim
   * @version 1.0
   */
  private static class Fixture {
    private final Stream feed;
    private final Heater unit;
    private final EnergyStream energy = new EnergyStream("external");
    private final EnergyBus bus = new EnergyBus("heat", EnergyType.HEAT);
    private final ProcessSystem area = new ProcessSystem();
    private final ProcessModel model = new ProcessModel();

    /**
     * Creates a fresh fixture.
     *
     * @param cooler whether to use Cooler
     */
    Fixture(boolean cooler) {
      SystemSrkEos fluid = new SystemSrkEos(300.0, 40.0);
      fluid.addComponent("methane", 0.9);
      fluid.addComponent("ethane", 0.1);
      fluid.setMixingRule(2);
      feed = new Stream("feed", fluid);
      feed.setFlowRate(1000.0, "kg/hr");
      unit = cooler ? new Cooler("unit", feed) : new Heater("unit", feed);
      area.add(feed);
      area.add(unit);
      model.add("area", area);
    }

    /**
     * Applies initial or changed inputs without rewriting unrelated inputs.
     *
     * @param change input transition
     * @param changed whether to select the second state
     */
    void configure(String change, boolean changed) {
      if ("deltaT".equals(change)) {
        unit.setdT(changed ? 10.0 : 0.0);
      } else if ("temperatureUnit".equals(change)) {
        unit.setOutletTemperature(300.0, changed ? "C" : "K");
      } else if ("pressureUnit".equals(change)) {
        unit.setOutletPressure(40.0, changed ? "barg" : "bara");
      } else if ("externalDuty".equals(change)) {
        energy.setDuty(changed ? -2000.0 : -1000.0);
        if (!unit.isSetEnergyStream()) {
          unit.setEnergyStream(energy);
        }
      } else if ("energyBus".equals(change)) {
        bus.setContribution("source", changed ? 2000.0 : 1000.0);
        if (!unit.isSetEnergyStream()) {
          unit.setEnergyStream(bus);
        }
      } else if ("mode".equals(change)) {
        if (changed) {
          unit.setdT(0.0);
        } else {
          unit.setOutletTemperature(310.0, "K");
        }
      } else {
        unit.setSpecification("out stream");
        unit.getOutletStream().setTemperature(changed ? 320.0 : 300.0, "K");
      }
    }

    /**
     * Executes one entry point.
     *
     * @param path direct, optimized, or model
     */
    void run(String path) {
      if ("direct".equals(path)) {
        feed.run();
        unit.run();
      } else if ("optimized".equals(path)) {
        area.runOptimized();
      } else {
        model.run();
      }
    }
  }
}
