package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.costestimation.CostEstimationCalculator;
import neqsim.process.costestimation.separator.SeparatorCostEstimate;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Integration tests for a tagged separator and outlet valve datasheet package.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class EquipmentDatasheetGeneratorTest {
  /**
   * Verifies the physical asset count, material connections and partial weight roll-up.
   */
  @Test
  void reportsTheWholeFlowsheetWithoutSubstitutingUnknownWeights() {
    SystemInterface fluid = new SystemSrkCPAstatoil(324.15, 25.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("nC10", 0.10);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("S01", fluid);
    feed.setFlowRate(62000, "kg/hr");
    ThreePhaseSeparator separator = new ThreePhaseSeparator("V-101", feed);
    separator.setOrientation("horizontal");
    ThrottlingValve valve = new ThrottlingValve("PCV-101", separator.getGasOutStream());
    valve.setOutletPressure(22.0);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(valve);
    process.run();

    assertTrue(separator.getThermoSystem().hasPhaseType("gas"));
    assertTrue(separator.getThermoSystem().hasPhaseType("oil"));
    assertTrue(separator.getThermoSystem().hasPhaseType("aqueous"));
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.setMaxOperationPressure(55.0);
    separator.autoSize(1.2);
    assertTrue(design.getWallThickness() > 0.0);
    assertTrue(design.getWeightTotal() > design.getWeigthVesselShell());

    JsonObject pack = JsonParser.parseString(new EquipmentDatasheetGenerator(process).generateAllDatasheets())
        .getAsJsonObject();
    assertEquals(2, pack.get("equipmentCount").getAsInt());
    assertEquals("S01", pack.getAsJsonArray("excludedSimulationObjects").get(0).getAsString());
    JsonObject sheet = pack.getAsJsonArray("datasheets").get(0).getAsJsonObject();
    assertEquals("V-101", sheet.get("equipmentTag").getAsString());
    assertEquals("PRELIMINARY", sheet.get("issueStatus").getAsString());
    assertEquals(3, sheet.getAsJsonArray("outletStreams").size());
    JsonObject weights = sheet.getAsJsonObject("weights");
    double dryWeight = design.getWeightTotal();
    assertEquals(dryWeight, weights.get("dryWeight_kg").getAsDouble(), 1e-8);
    double length = design.getTantanLength();
    double volume = Math.PI * Math.pow(design.getInnerDiameter() / 2.0, 2) * length;
    double liquidVolume = separator.liquidArea(design.getNLL()) * length;
    double waterVolume = separator.liquidArea(design.getNIL()) * length;
    SystemInterface contents = separator.getThermoSystem();
    double expected = dryWeight + (volume - liquidVolume) * contents.getPhase("gas").getDensity("kg/m3")
        + (liquidVolume - waterVolume) * contents.getPhase("oil").getDensity("kg/m3")
        + waterVolume * contents.getPhase("aqueous").getDensity("kg/m3");
    assertEquals(expected, weights.get("operatingWeight_kg").getAsDouble(), 1e-8);
    assertTrue(weights.get("hydrotestWeight_kg").isJsonNull());
    JsonObject operatingSummary = pack.getAsJsonObject("weightSummary").getAsJsonObject("operating");
    assertEquals(expected, operatingSummary.get("knownSubtotal_kg").getAsDouble(), 1e-8);
    assertEquals(1, operatingSummary.get("missingCount").getAsInt());
    assertTrue(operatingSummary.get("total_kg").isJsonNull());
  }

  /**
   * Ensures the costing method uses the matching vessel orientation for identical geometry.
   */
  @Test
  void selectsTheCostCorrelationByVesselOrientation() {
    ThreePhaseSeparator separator = new ThreePhaseSeparator("V-101");
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    design.setFromExistingDesign(2.0, 6.0, 0.035);
    double volume = 6.0 * Math.PI;
    CostEstimationCalculator calculator = new CostEstimationCalculator();
    separator.setOrientation("horizontal");
    SeparatorCostEstimate horizontal = new SeparatorCostEstimate(design);
    horizontal.calculateCostEstimate();
    assertEquals(calculator.calcHorizontalVesselCostByVolume(volume), horizontal.getPurchasedEquipmentCost(), 1e-7);
    separator.setOrientation("vertical");
    SeparatorCostEstimate vertical = new SeparatorCostEstimate(design);
    vertical.calculateCostEstimate();
    assertEquals(calculator.calcVerticalVesselCostByVolume(volume), vertical.getPurchasedEquipmentCost(), 1e-7);
  }

  /**
   * Provides a reviewable sheet even when the case has never run.
   */
  @Test
  void retainsUnsolvedEquipmentWithHolds() {
    ProcessSystem process = new ProcessSystem();
    process.add(new ThreePhaseSeparator("V-101"));
    JsonObject pack = JsonParser.parseString(new EquipmentDatasheetGenerator(process).generateAllDatasheets())
        .getAsJsonObject();
    JsonObject sheet = pack.getAsJsonArray("datasheets").get(0).getAsJsonObject();
    assertEquals("unsolved", pack.get("operatingCaseStatus").getAsString());
    assertEquals(0, sheet.getAsJsonObject("operatingConditions").size());
    assertTrue(sheet.getAsJsonArray("holds").size() > 0);
  }
}
