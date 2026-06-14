package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the universal "utilization via mechanical design" capability added to
 * {@link MechanicalDesign} and {@link neqsim.process.equipment.ProcessEquipmentBaseClass}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MechanicalDesignUtilizationTest {

  /**
   * Builds a simple run process with a heater that has a 10 bara pressure drop.
   *
   * @return a run heater with inlet pressure 60 bara and outlet pressure 50 bara
   */
  private Heater buildRunHeater() {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(60.0, "bara");
    feed.setTemperature(25.0, "C");

    Heater heater = new Heater("heater", feed);
    heater.setOutletPressure(50.0);
    heater.setOutTemperature(30.0 + 273.15);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();
    return heater;
  }

  /**
   * The pressure-drop design limit yields a utilization equal to operating dP divided by the limit.
   */
  @Test
  void designUtilizationFromPressureDrop() {
    Heater heater = buildRunHeater();
    MechanicalDesign design = heater.getMechanicalDesign();
    design.setMaxDesignPressureDrop(20.0);

    Map<String, Double> utilization = design.getDesignUtilization();
    assertTrue(utilization.containsKey("design pressure drop"),
        "pressure drop utilization should be present");
    // dP = 60 - 50 = 10 bara; limit 20 bara -> utilization 0.5
    assertEquals(0.5, utilization.get("design pressure drop"), 1.0e-6);
    assertEquals(0.5, design.getMaxDesignUtilization(), 1.0e-6);
  }

  /**
   * The volumetric-flow design limit yields a utilization between 0 and 1 when the limit exceeds
   * the actual inlet flow.
   */
  @Test
  void designUtilizationFromVolumeFlow() {
    Heater heater = buildRunHeater();
    double inletFlow = heater.getInletStreams().get(0).getFlowRate("m3/hr");
    assertTrue(inletFlow > 0.0, "inlet volumetric flow should be positive");

    MechanicalDesign design = heater.getMechanicalDesign();
    design.setMaxDesignVolumeFlow(inletFlow * 2.0);

    Map<String, Double> utilization = design.getDesignUtilization();
    assertTrue(utilization.containsKey("design volume flow"),
        "volume flow utilization should be present");
    assertEquals(0.5, utilization.get("design volume flow"), 1.0e-3);
  }

  /**
   * With no design limits set, no utilization is reported and behavior is unchanged.
   */
  @Test
  void noLimitsYieldNoUtilization() {
    Heater heater = buildRunHeater();
    MechanicalDesign design = heater.getMechanicalDesign();

    assertTrue(design.getDesignUtilization().isEmpty(),
        "no constraints expected when no design limit is set");
    assertEquals(0.0, design.getMaxDesignUtilization(), 0.0);

    int added = heater.applyMechanicalDesignCapacityConstraints();
    assertEquals(0, added, "no mechanical-design constraints should be registered");
    assertEquals(0.0, heater.getMaxUtilization(), 0.0);
  }

  /**
   * The opt-in bridge registers a derived constraint that surfaces in the equipment-level
   * utilization, and is idempotent on repeated calls.
   */
  @Test
  void bridgeRegistersConstraintsIdempotently() {
    Heater heater = buildRunHeater();
    heater.getMechanicalDesign().setMaxDesignPressureDrop(20.0);

    int added = heater.applyMechanicalDesignCapacityConstraints();
    assertEquals(1, added, "one pressure-drop constraint should be registered");
    assertTrue(heater.getCapacityConstraints().containsKey("design pressure drop"),
        "derived constraint should be present in the equipment constraint map");
    assertEquals(0.5, heater.getMaxUtilization(), 1.0e-6);

    // Idempotent: re-applying must not duplicate the constraint.
    int addedAgain = heater.applyMechanicalDesignCapacityConstraints();
    assertEquals(1, addedAgain, "re-applying should register the same single constraint");
    assertEquals(1L,
        heater.getCapacityConstraints().values().stream()
            .filter(c -> "mechanicalDesign".equals(c.getDataSource())).count(),
        "exactly one mechanical-design constraint should remain");
    assertFalse(heater.getCapacityConstraints().isEmpty());
  }
}
