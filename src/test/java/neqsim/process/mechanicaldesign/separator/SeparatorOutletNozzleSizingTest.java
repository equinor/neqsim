package neqsim.process.mechanicaldesign.separator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for actual-volume outlet nozzle sizing (issue #3675). */
class SeparatorOutletNozzleSizingTest extends neqsim.NeqSimTest {
  @Test
  void gasAndOilNozzlesRespectVelocityLimitsAtHighFlow() {
    Separator separator = createSeparator(false, true, true, false);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.calcDesign();
    assertNozzle(separator.getThermoSystem().getPhase("gas").getFlowRate("m3/sec"), design.calcGasOutletNozzleID(),
        20.0, 0.05);
    assertEquals(0.30, design.getGasOutletNozzleID(), 1e-12);
    assertNozzle(separator.getThermoSystem().getPhase("oil").getFlowRate("m3/sec"), design.calcOilOutletNozzleID(), 1.5,
        0.025);
    assertJsonAgreement(design);
  }

  @Test
  void twoPhaseSeparatorSizesCombinedOilAndWaterOutlet() {
    Separator separator = createSeparator(false, true, true, true);
    SystemInterface fluid = separator.getThermoSystem();
    assertEquals(3, fluid.getNumberOfPhases());
    double liquidFlow = fluid.getPhase("oil").getFlowRate("m3/sec") + fluid.getPhase("aqueous").getFlowRate("m3/sec");
    assertNozzle(liquidFlow, separator.getMechanicalDesign().calcOilOutletNozzleID(), 1.5, 0.025);
  }

  @Test
  void threePhaseSeparatorSizesOilIndependentlyOfWater() {
    Separator separator = createSeparator(true, true, true, true);
    SystemInterface fluid = separator.getThermoSystem();
    assertEquals(3, fluid.getNumberOfPhases());
    assertNozzle(fluid.getPhase("oil").getFlowRate("m3/sec"), separator.getMechanicalDesign().calcOilOutletNozzleID(),
        1.5, 0.025);
    assertNozzle(fluid.getPhase("gas").getFlowRate("m3/sec"), separator.getMechanicalDesign().calcGasOutletNozzleID(),
        20.0, 0.05);
    assertJsonAgreement(separator.getMechanicalDesign());
  }

  @Test
  void waterOnlyOutletIsLiquidAndNotGas() {
    Separator separator = createSeparator(false, false, false, true);
    SystemInterface fluid = separator.getThermoSystem();
    assertFalse(fluid.hasPhaseType("gas"));
    assertEquals(1, fluid.getNumberOfPhases());
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    assertEquals(0.0, design.calcGasOutletNozzleID(), 0.0);
    assertNozzle(fluid.getPhase("aqueous").getFlowRate("m3/sec"), design.calcOilOutletNozzleID(), 1.5, 0.025);
    assertJsonAgreement(design);
  }

  @Test
  void zeroFlowResetsPreviouslyCalculatedNozzlesAndJson() {
    Separator separator = createSeparator(false, true, true, false);
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    assertTrue(design.calcGasOutletNozzleID() > 0.05);
    assertTrue(design.calcOilOutletNozzleID() > 0.05);
    separator.getThermoSystem().setEmptyFluid();
    assertEquals(0.0, design.calcGasOutletNozzleID(), 0.0);
    assertEquals(0.05, design.calcOilOutletNozzleID(), 0.0);
    assertJsonAgreement(design);
  }

  private Separator createSeparator(boolean threePhase, boolean gas, boolean oil, boolean water) {
    SystemInterface fluid = new SystemSrkEos(313.15, 20.0);
    if (gas) {
      fluid.addComponent("methane", 0.80);
      fluid.addComponent("ethane", 0.05);
      fluid.addComponent("propane", 0.05);
    }
    if (oil) {
      fluid.addComponent("n-decane", 0.10);
    }
    if (water) {
      fluid.addComponent("water", 2.0);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();
    Separator separator = threePhase ? new ThreePhaseSeparator("separator", feed) : new Separator("separator", feed);
    separator.setOrientation("horizontal");
    separator.run();
    return separator;
  }

  private void assertNozzle(double flow, double diameter, double velocityLimit, double increment) {
    assertTrue(flow > 0.0);
    double minimumDiameter = Math.sqrt(4.0 * flow / (Math.PI * velocityLimit));
    assertTrue(diameter >= minimumDiameter - 1e-12);
    assertTrue(diameter < minimumDiameter + increment + 1e-12);
    assertEquals(Math.rint(diameter / increment), diameter / increment, 1e-12);
    assertTrue(4.0 * flow / (Math.PI * diameter * diameter) <= velocityLimit + 1e-12);
  }

  private void assertJsonAgreement(SeparatorMechanicalDesign design) {
    JsonObject json = JsonParser.parseString(design.toJson()).getAsJsonObject();
    assertEquals(design.getGasOutletNozzleID() * 1000.0, json.get("gasOutletNozzleDiameter").getAsDouble(), 1e-9);
    assertEquals(design.getOilOutletNozzleID() * 1000.0, json.get("liquidOutletNozzleDiameter").getAsDouble(), 1e-9);
  }
}
