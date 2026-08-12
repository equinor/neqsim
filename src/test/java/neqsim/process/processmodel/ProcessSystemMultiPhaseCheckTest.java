package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that {@link ProcessSystem#setMultiPhaseCheck(boolean)} and {@link ProcessModel#setMultiPhaseCheck(boolean)}
 * switch the three-phase flash on and off per process area.
 */
public class ProcessSystemMultiPhaseCheckTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("water", 0.02);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(1000.0, "kg/hr");
    return fluid;
  }

  private static ProcessSystem buildArea(String name) {
    Stream feed = new Stream(name + " feed", makeGas());
    Heater cooler = new Heater(name + " cooler", feed);
    cooler.setOutTemperature(280.0);
    Separator sep = new Separator(name + " sep", cooler.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.setName(name);
    process.add(feed);
    process.add(cooler);
    process.add(sep);
    return process;
  }

  @Test
  public void settingIsUnsetByDefault() {
    ProcessSystem process = buildArea("area");
    assertNull(process.getMultiPhaseCheck());
  }

  @Test
  public void turnsMultiPhaseCheckOffAndOnForAProcessSystem() {
    ProcessSystem process = buildArea("area");

    int updatedOn = process.setMultiPhaseCheck(true);
    assertTrue(updatedOn > 0);
    assertEquals(Boolean.TRUE, process.getMultiPhaseCheck());
    process.run();
    assertTrue(process.getUnit("area feed").getFluid().doMultiPhaseCheck());
    assertTrue(process.getUnit("area sep").getFluid().doMultiPhaseCheck());

    int updatedOff = process.setMultiPhaseCheck(false);
    assertTrue(updatedOff > 0);
    assertEquals(Boolean.FALSE, process.getMultiPhaseCheck());
    process.run();
    assertFalse(process.getUnit("area feed").getFluid().doMultiPhaseCheck());
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());
  }

  @Test
  public void modelCanConfigureAreasIndividually() {
    ProcessSystem separation = buildArea("separation");
    ProcessSystem compression = buildArea("compression");

    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("compression", compression);

    model.setMultiPhaseCheck(true);
    assertEquals(Boolean.TRUE, separation.getMultiPhaseCheck());
    assertEquals(Boolean.TRUE, compression.getMultiPhaseCheck());

    // Turn the three-phase flash off only in the compression area.
    assertTrue(model.setMultiPhaseCheck("compression", false) > 0);
    model.run();

    assertTrue(separation.getUnit("separation sep").getFluid().doMultiPhaseCheck());
    assertFalse(compression.getUnit("compression sep").getFluid().doMultiPhaseCheck());
  }

  @Test
  public void unknownAreaNameReturnsMinusOne() {
    ProcessModel model = new ProcessModel();
    model.add("separation", buildArea("separation"));
    assertEquals(-1, model.setMultiPhaseCheck("does not exist", false));
  }

  @Test
  public void settingIsReAppliedWhenEquipmentTurnsTheCheckBackOn() {
    ProcessSystem process = buildArea("area");
    assertTrue(process.setMultiPhaseCheck(false) > 0);
    process.run();
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());

    // Simulate a unit that turns the multiphase check back on mid-solve (ThreePhaseSeparator does
    // exactly this). The next run must restore the flowsheet-wide setting instead of leaking it.
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.getFluid() != null) {
        unit.getFluid().setMultiPhaseCheck(true);
      }
    }
    process.run();
    assertFalse(process.getUnit("area feed").getFluid().doMultiPhaseCheck());
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());
  }

  @Test
  public void settingIsReAppliedByEveryRunEntryPoint() throws InterruptedException {
    ProcessSystem process = buildArea("area");
    assertTrue(process.setMultiPhaseCheck(false) > 0);

    process.getUnit("area sep").getFluid().setMultiPhaseCheck(true);
    process.runSequential(java.util.UUID.randomUUID());
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());

    process.getUnit("area sep").getFluid().setMultiPhaseCheck(true);
    process.runParallel(java.util.UUID.randomUUID());
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());

    process.getUnit("area sep").getFluid().setMultiPhaseCheck(true);
    process.run_step(java.util.UUID.randomUUID());
    assertFalse(process.getUnit("area sep").getFluid().doMultiPhaseCheck());
  }

  @Test
  public void propertyInitLevelIsAppliedPerAreaAndSurvivesRun() {
    ProcessSystem separation = buildArea("separation");
    ProcessSystem compression = buildArea("compression");

    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("compression", compression);

    assertNull(separation.getPropertyInitLevel());
    assertTrue(model.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY) > 0);
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, separation.getPropertyInitLevel());
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY, compression.getPropertyInitLevel());

    // Restore full properties in the separation area only - same per-area granularity as
    // setMultiPhaseCheck(String, boolean).
    assertTrue(model.setPropertyInitLevel("separation", Stream.PropertyInitLevel.FULL) > 0);
    assertEquals(-1, model.setPropertyInitLevel("does not exist", Stream.PropertyInitLevel.FULL));

    model.run();

    assertEquals(Stream.PropertyInitLevel.FULL,
        ((Stream) separation.getUnit("separation feed")).getPropertyInitLevel());
    assertEquals(Stream.PropertyInitLevel.DENSITY_ONLY,
        ((Stream) compression.getUnit("compression feed")).getPropertyInitLevel());
    assertTrue(separation.getUnit("separation feed").getFluid().getViscosity("kg/msec") > 0.0);
  }
}
