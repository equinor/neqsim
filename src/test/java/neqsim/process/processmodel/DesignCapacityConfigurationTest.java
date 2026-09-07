package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.EquipmentDesignData;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChart;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the common capacity configuration API and its preflight mutation boundary. */
class DesignCapacityConfigurationTest {
  @Test
  void mapAndJsonBuildApplySameDesignValuesAndReports() {
    Map<String, Map<String, Object>> input = capacities();
    input.put("separator", properties("internalDiameter", 2.5, "separatorLength", 8.0));
    input.put("compressor", properties("ratedPower", 5000.0, "maxSpeed", 12000.0));
    input.put("cooler", properties("maxDesignDutyMW", 4.0));
    input.put("pump", properties("maxDesignPower", 75.0, "maxDesignVolumeFlow", 140.0));
    ProcessSystem direct = process();
    Map<String, EquipmentDesignData.ApplyResult> directResult = direct.applyDesignCapacities(input);

    JsonObject json = JsonParser.parseString(processJson()).getAsJsonObject();
    json.add("designCapacities", new Gson().toJsonTree(input));
    SimulationResult built = ProcessSystem.fromJson(json.toString());
    assertTrue(built.isSuccess(), built.toJson());
    JsonObject applied = built.getMetadata().getAsJsonObject("designDataApplied").getAsJsonObject("designCapacities");
    for (Map.Entry<String, EquipmentDesignData.ApplyResult> entry : directResult.entrySet()) {
      assertEquals(entry.getValue().toJson(), applied.getAsJsonObject(entry.getKey()));
    }
    for (ProcessSystem candidate : Arrays.asList(direct, built.getProcessSystem())) {
      assertEquals(2.5, ((Separator) candidate.getUnit("separator")).getInternalDiameter(), 1.0e-12);
      assertEquals(12000.0, ((Compressor) candidate.getUnit("compressor")).getMaximumSpeed(), 1.0e-12);
      assertEquals(5000.0, candidate.getUnit("compressor").getMechanicalDesign().maxDesignPower, 1.0e-12);
      assertEquals(4.0e6, ((Cooler) candidate.getUnit("cooler")).getMaxDesignDuty(), 1.0e-12);
      assertEquals(75000.0, candidate.getUnit("pump").getMechanicalDesign().maxDesignPower, 1.0e-12);
      assertEquals(140.0, candidate.getUnit("pump").getMechanicalDesign().getMaxDesignVolumeFlow(), 1.0e-12);
    }
    assertEquals(Arrays.asList("compressor", "cooler", "pump", "separator"),
        new ArrayList<String>(directResult.keySet()));
  }

  @Test
  void missingTargetCannotPartiallyApplyEarlierValidTarget() {
    ProcessSystem process = process();
    double previous = ((Separator) process.getUnit("separator")).getInternalDiameter();
    Map<String, Map<String, Object>> input = capacities();
    input.put("separator", properties("internalDiameter", 4.0));
    input.put("zz missing", properties("internalDiameter", 5.0));
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
    assertEquals(previous, ((Separator) process.getUnit("separator")).getInternalDiameter());
  }

  @Test
  void invalidValuesAndUnknownPropertiesRejectBeforeMutation() {
    ProcessSystem process = process();
    double previous = ((Compressor) process.getUnit("compressor")).getMaximumSpeed();
    Object[] invalid = { Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, "5000", null };
    for (Object value : invalid) {
      Map<String, Map<String, Object>> input = capacities();
      input.put("compressor", properties("maxSpeed", 13000.0));
      input.put("separator", properties("internalDiameter", value));
      assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
      assertEquals(previous, ((Compressor) process.getUnit("compressor")).getMaximumSpeed());
    }
    Map<String, Map<String, Object>> input = capacities();
    input.put("compressor", properties("ratedPowerKW", 5000.0));
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
    input.put("compressor", properties("ratedPower", 5000.0));
    input.put("cooler", properties("maxDesignDutyMW", Double.MAX_VALUE));
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
    input.put("cooler", properties("maxDesignDutyMW", 1.0, "maxDesignDutyKW", 1000.0));
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
  }

  @Test
  void invalidNamesAndUnsupportedEquipmentAreRejected() {
    ProcessSystem process = process();
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(null));
    for (String name : new String[] { null, " ", "feed" }) {
      Map<String, Map<String, Object>> input = capacities();
      input.put(name, properties("internalDiameter", 2.0));
      assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
    }
    Map<String, Map<String, Object>> input = capacities();
    input.put("separator", new LinkedHashMap<String, Object>());
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
    process.getUnit("compressor").setName("separator");
    input.put("separator", properties("internalDiameter", 2.0));
    assertThrows(IllegalArgumentException.class, () -> process.applyDesignCapacities(input));
  }

  @Test
  void repeatedUpdatesPreserveLiveMechanicalDesignAndOmittedValues() {
    ProcessSystem process = process();
    Compressor compressor = (Compressor) process.getUnit("compressor");
    if (compressor.getMechanicalDesign() == null) {
      compressor.initMechanicalDesign();
    }
    MechanicalDesign design = compressor.getMechanicalDesign();
    design.setMaxDesignVolumeFlow(1250.0);
    Map<String, Map<String, Object>> input = capacities();
    input.put("compressor", properties("ratedPower", 5000.0, "maxSpeed", 12000.0));
    process.applyDesignCapacities(input);
    input.put("compressor", properties("ratedPower", 6500.0));
    process.applyDesignCapacities(input);
    assertSame(design, compressor.getMechanicalDesign());
    assertEquals(6500.0, design.maxDesignPower);
    assertEquals(1250.0, design.getMaxDesignVolumeFlow());
    assertEquals(12000.0, compressor.getMaximumSpeed());
    assertTrue(process.applyDesignCapacities(capacities()).isEmpty());
  }

  @Test
  void warmConstraintsRefreshWithoutLosingSelectedMetadataOrCustomLimits() {
    ProcessSystem process = process();
    Pump pump = (Pump) process.getUnit("pump");
    Compressor compressor = (Compressor) process.getUnit("compressor");
    Cooler cooler = (Cooler) process.getUnit("cooler");
    Map<String, Map<String, Object>> input = capacities();
    input.put("cooler", properties("maxDesignDutyMW", 2.0));
    process.applyDesignCapacities(input);
    CapacityConstraint power = pump.getCapacityConstraints().get("power");
    CapacityConstraint flow = pump.getCapacityConstraints().get("flowRate");
    CapacityConstraint speed = compressor.getCapacityConstraints().get("speed");
    CapacityConstraint duty = cooler.getCapacityConstraints().get("duty");
    CapacityConstraint custom = new CapacityConstraint("operator pressure limit").setDesignValue(50.0)
        .setCurrentValue(35.0).setEnabled(false).setDataSource("operator");
    cooler.addCapacityConstraint(custom);
    for (CapacityConstraint constraint : Arrays.asList(power, flow, speed, duty)) {
      constraint.setEnabled(false).setWarningThreshold(0.83).setSeverity(CapacityConstraint.ConstraintSeverity.SOFT)
          .setSource(CapacityConstraint.ConstraintSource.VENDOR_DATASHEET, "datasheet-123")
          .setDataSource("installed-data");
    }
    for (double scale : new double[] { 1.0, 2.0 }) {
      input.put("pump", properties("maxDesignPower", 75.0 * scale, "maxDesignVolumeFlow", 140.0 * scale));
      input.put("compressor", properties("maxSpeed", 12000.0 * scale));
      input.put("cooler", properties("maxDesignDutyMW", 4.0 * scale));
      process.applyDesignCapacities(input);
      assertSame(power, pump.getCapacityConstraints().get("power"));
      assertSame(flow, pump.getCapacityConstraints().get("flowRate"));
      assertSame(speed, compressor.getCapacityConstraints().get("speed"));
      assertSame(duty, cooler.getCapacityConstraints().get("duty"));
      assertSame(custom, cooler.getCapacityConstraints().get("operator pressure limit"));
      assertEquals(75.0 * scale, power.getDesignValue());
      assertEquals(140.0 * scale, flow.getDesignValue());
      assertEquals(12000.0 * scale, speed.getDesignValue());
      assertEquals(12000.0 * scale, speed.getMaxValue());
      assertEquals(4.0e6 * scale, duty.getDesignValue());
      assertEquals(0.8 / scale, power.getUtilization(60.0), 1.0e-12);
      assertEquals(0.8 / scale, flow.getUtilization(112.0), 1.0e-12);
      assertEquals(0.8 / scale, speed.getUtilization(9600.0), 1.0e-12);
      assertEquals(0.8 / scale, duty.getUtilization(3.2e6), 1.0e-12);
      for (CapacityConstraint constraint : Arrays.asList(power, flow, speed, duty)) {
        assertEquals(false, constraint.isEnabled());
        assertEquals(0.83, constraint.getWarningThreshold());
        assertEquals(CapacityConstraint.ConstraintSeverity.SOFT, constraint.getSeverity());
        assertEquals("datasheet-123", constraint.getSourceReference());
        assertEquals("installed-data", constraint.getDataSource());
      }
    }
  }

  @Test
  void runningPumpReportsCapacityPowerInKilowattsAfterRepeatedConfiguration() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 2.0);
    fluid.addComponent("n-heptane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Pump pump = new Pump("pump", feed);
    pump.setOutletPressure(10.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pump);
    process.run();
    CapacityConstraint power = pump.getCapacityConstraints().get("power");
    double operatingPowerKW = pump.getPower("kW");
    assertTrue(operatingPowerKW > 0.0);
    assertEquals("kW", power.getUnit());
    assertEquals(operatingPowerKW, power.getCurrentValue(), 1.0e-12);
    Map<String, Map<String, Object>> input = capacities();
    for (double ratingKW : new double[] { 75.0, 150.0 }) {
      input.put("pump", properties("maxDesignPower", ratingKW));
      process.applyDesignCapacities(input);
      assertEquals(ratingKW * 1000.0, pump.getMechanicalDesign().maxDesignPower, 1.0e-12);
      assertEquals(ratingKW, power.getDesignValue(), 1.0e-12);
      assertEquals(operatingPowerKW, power.getCurrentValue(), 1.0e-12);
      assertEquals(operatingPowerKW / ratingKW, power.getUtilization(), 1.0e-12);
    }
  }

  @Test
  void runningCompressorReportsNormalizedAvailablePowerAsPercent() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Compressor compressor = new Compressor("compressor", feed);
    compressor.setOutletPressure(100.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();
    double operatingPowerKW = compressor.getPower("kW");
    assertTrue(operatingPowerKW > 0.0);
    Map<String, Map<String, Object>> input = capacities();
    input.put("compressor", properties("ratedPower", operatingPowerKW / 0.75));
    process.applyDesignCapacities(input);
    CapacityConstraint power = compressor.getCapacityConstraints().get("power");
    assertEquals("%", power.getUnit());
    assertEquals(100.0, power.getDesignValue());
    assertEquals(75.0, power.getCurrentValue(), 1.0e-10);
    assertEquals(0.75, power.getUtilization(), 1.0e-12);
    assertEquals(operatingPowerKW, compressor.getPower("kW"), 1.0e-12);
  }

  @Test
  void incompatibleCustomConstraintUnitsRejectAllAreaUpdatesBeforeMutation() {
    String[][] cases = { { "pump", "power", "maxDesignPower", "W" },
        { "pump", "flowRate", "maxDesignVolumeFlow", "m3/sec" }, { "cooler", "duty", "maxDesignDutyKW", "kW" },
        { "compressor", "speed", "maxSpeed", "rad/s" },
        { "separator", "gasLoadFactor", "designGasLoadFactor", "ft/s" } };
    for (String[] mismatch : cases) {
      ProcessSystem first = process();
      ProcessSystem second = process();
      CapacityConstraint custom = new CapacityConstraint(mismatch[1], mismatch[3],
          CapacityConstraint.ConstraintType.HARD).setDesignValue(123.0).setCurrentValue(50.0);
      ((CapacityConstrainedEquipment) second.getUnit(mismatch[0])).addCapacityConstraint(custom);
      ProcessModel model = new ProcessModel();
      model.add("A", first);
      model.add("B", second);
      double previousDiameter = ((Separator) first.getUnit("separator")).getInternalDiameter();
      Map<String, Map<String, Object>> input = capacities();
      input.put("A::separator", properties("internalDiameter", 5.0));
      input.put("B::" + mismatch[0], properties(mismatch[2], 150.0));
      assertThrows(IllegalArgumentException.class, () -> model.applyDesignCapacities(input));
      assertEquals(previousDiameter, ((Separator) first.getUnit("separator")).getInternalDiameter());
      assertEquals(123.0, custom.getDesignValue());
      assertEquals(mismatch[3], custom.getUnit());
      assertEquals(50.0, custom.getCurrentValue());
    }
  }

  @Test
  void advisoryJsonApplicationRetainsIncompatibleCustomConstraintsAndReportsThem() {
    ProcessSystem process = process();
    Pump pump = (Pump) process.getUnit("pump");
    Cooler cooler = (Cooler) process.getUnit("cooler");
    CapacityConstraint pumpPower = new CapacityConstraint("power", "W", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(75000.0).setCurrentValue(60000.0).setEnabled(false);
    CapacityConstraint coolerDuty = new CapacityConstraint("duty", "kW", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(4000.0).setCurrentValue(3200.0).setEnabled(false);
    pump.addCapacityConstraint(pumpPower);
    cooler.addCapacityConstraint(coolerDuty);
    JsonObject input = JsonParser
        .parseString("{\"pump\":{\"maxDesignPower\":150}," + "\"cooler\":{\"maxDesignDutyKW\":8000}}")
        .getAsJsonObject();
    Map<String, EquipmentDesignData.ApplyResult> report = process.applyDesignCapacitiesJson(input);
    assertEquals(150000.0, pump.getMechanicalDesign().maxDesignPower);
    assertEquals(8.0e6, cooler.getMaxDesignDuty());
    assertSame(pumpPower, pump.getCapacityConstraints().get("power"));
    assertSame(coolerDuty, cooler.getCapacityConstraints().get("duty"));
    assertEquals(75000.0, pumpPower.getDesignValue());
    assertEquals(4000.0, coolerDuty.getDesignValue());
    assertEquals(0.8, pumpPower.getUtilization(), 1.0e-12);
    assertEquals(0.8, coolerDuty.getUtilization(), 1.0e-12);
    for (String name : Arrays.asList("pump", "cooler")) {
      assertEquals("applied", report.get(name).status);
      assertTrue(report.get(name).toJson().get("message").getAsString().contains("Retained custom constraint"));
    }
  }

  @Test
  void updatePowerConstraintChangesRatingAndPreservesPercentEvidence() {
    ProcessSystem process = process();
    Compressor compressor = (Compressor) process.getUnit("compressor");
    compressor.setOutletPressure(100.0, "bara");
    Stream feed = (Stream) process.getUnit("feed");
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();
    compressor.run();
    double operatingPowerKW = compressor.getPower("kW");
    assertTrue(operatingPowerKW > 0.0);
    CapacityConstraint power = compressor.getCapacityConstraints().get("power");
    power.setEnabled(false).setSourceReference("retained-source");
    for (double targetUtilization : new double[] { 0.75, 0.375 }) {
      compressor.updatePowerConstraint(operatingPowerKW / targetUtilization);
      assertSame(power, compressor.getCapacityConstraints().get("power"));
      assertEquals("%", power.getUnit());
      assertEquals(100.0, power.getDesignValue());
      assertEquals(110.0, power.getMaxValue());
      assertEquals(targetUtilization * 100.0, power.getCurrentValue(), 1.0e-10);
      assertEquals(targetUtilization, power.getUtilization(), 1.0e-12);
      assertEquals(false, power.isEnabled());
      assertEquals("retained-source", power.getSourceReference());
    }
    double previousRating = compressor.getMechanicalDesign().maxDesignPower;
    assertThrows(IllegalArgumentException.class, () -> compressor.updatePowerConstraint(Double.NaN));
    assertEquals(previousRating, compressor.getMechanicalDesign().maxDesignPower);
    CompressorDriver driver = new CompressorDriver();
    driver.setMaxPower(2.0 * operatingPowerKW);
    driver.setRatedSpeed(compressor.getSpeed());
    driver.setMaxPowerCurveCoefficients(0.0, 1.0, 0.0);
    compressor.setDriver(driver);
    compressor.updatePowerConstraint(2.0 * previousRating);
    assertEquals(2.0 * previousRating, driver.getRatedPower());
    assertEquals(2.0 * previousRating, compressor.getMechanicalDesign().maxDesignPower);
    assertEquals("%", compressor.getCapacityConstraints().get("power").getUnit());
    assertEquals(50.0, compressor.getCapacityConstraints().get("power").getCurrentValue(), 1.0e-10);
    compressor.setSpeed(compressor.getSpeed() / 2.0);
    assertEquals(100.0, compressor.getCapacityConstraints().get("power").getCurrentValue(), 1.0e-10);
    assertEquals(2.0 * operatingPowerKW, driver.getMaxPower(), 1.0e-12);
    CapacityConstraint custom = new CapacityConstraint("power", "kW", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(5000.0).setCurrentValue(1000.0);
    compressor.addCapacityConstraint(custom);
    assertThrows(IllegalArgumentException.class, () -> compressor.updatePowerConstraint(6000.0));
    assertEquals(5000.0, custom.getDesignValue());
    assertEquals(2.0 * previousRating, driver.getRatedPower());
  }

  @Test
  void compressorConfigurationKeepsActiveChartSpeedLimit() {
    ProcessSystem process = process();
    Compressor compressor = (Compressor) process.getUnit("compressor");
    CompressorChart chart = new CompressorChart();
    chart.setMaxSpeedCurve(10000.0);
    chart.setUseCompressorChart(true);
    compressor.setCompressorChart(chart);
    CapacityConstraint speed = compressor.getCapacityConstraints().get("speed");
    Map<String, Map<String, Object>> input = capacities();
    input.put("compressor", properties("maxSpeed", 12000.0));
    process.applyDesignCapacities(input);
    assertEquals(10000.0, speed.getDesignValue());
    assertEquals(10000.0, speed.getMaxValue());
    input.put("compressor", properties("maxSpeed", 9000.0));
    process.applyDesignCapacities(input);
    assertEquals(9000.0, speed.getDesignValue());
    assertEquals(9000.0, speed.getMaxValue());
  }

  @Test
  void multiAreaQualifiedNamesAvoidCollisionsAndValidateAllAreasFirst() {
    ProcessModel model = new ProcessModel();
    ProcessSystem first = process();
    ProcessSystem second = process();
    model.add("A", first);
    model.add("B", second);
    double previous = ((Separator) first.getUnit("separator")).getInternalDiameter();
    Map<String, Map<String, Object>> input = capacities();
    input.put("A::separator", properties("internalDiameter", 2.0));
    input.put("B::separator", properties("internalDiameter", Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> model.applyDesignCapacities(input));
    assertEquals(previous, ((Separator) first.getUnit("separator")).getInternalDiameter());
    input.put("B::separator", properties("internalDiameter", 3.0));
    Map<String, EquipmentDesignData.ApplyResult> report = model.applyDesignCapacities(input);
    assertEquals(Arrays.asList("A::separator", "B::separator"), new ArrayList<String>(report.keySet()));
    assertEquals("B::separator", report.get("B::separator").equipmentName);
    assertEquals(2.0, ((Separator) first.getUnit("separator")).getInternalDiameter());
    assertEquals(3.0, ((Separator) second.getUnit("separator")).getInternalDiameter());
    for (String name : new String[] { "separator", "missing::separator", "A::", "A::separator::extra" }) {
      input.put(name, properties("internalDiameter", 9.0));
      assertThrows(IllegalArgumentException.class, () -> model.applyDesignCapacities(input));
      input.remove(name);
    }
  }

  @Test
  void multiAreaAliasesCannotOverwriteTheSameEquipmentTwice() {
    ProcessModel model = new ProcessModel();
    ProcessSystem process = process();
    model.add("A", process);
    model.add("B", process);
    double previous = ((Separator) process.getUnit("separator")).getInternalDiameter();
    Map<String, Map<String, Object>> input = capacities();
    input.put("A::separator", properties("internalDiameter", 2.0));
    input.put("B::separator", properties("internalDiameter", 3.0));
    assertThrows(IllegalArgumentException.class, () -> model.applyDesignCapacities(input));
    assertEquals(previous, ((Separator) process.getUnit("separator")).getInternalDiameter());
  }

  @Test
  void legacyJsonKeepsAdvisoryMissingAndUnsupportedTargetReports() {
    JsonObject json = JsonParser.parseString(processJson()).getAsJsonObject();
    JsonObject input = new JsonObject();
    input.add("missing", new JsonObject());
    input.add("feed", new JsonObject());
    input.addProperty("separator", "advisory malformed value");
    json.add("designCapacities", input);
    SimulationResult built = ProcessSystem.fromJson(json.toString());
    assertTrue(built.isSuccess(), built.toJson());
    JsonObject applied = built.getMetadata().getAsJsonObject("designDataApplied").getAsJsonObject("designCapacities");
    assertEquals("not_found", applied.getAsJsonObject("missing").get("status").getAsString());
    assertEquals("unsupported", applied.getAsJsonObject("feed").get("status").getAsString());
    assertEquals("skipped", applied.getAsJsonObject("separator").get("status").getAsString());
  }

  private static ProcessSystem process() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(new Separator("separator", feed));
    process.add(new Compressor("compressor", feed));
    process.add(new Cooler("cooler", feed));
    process.add(new Pump("pump", feed));
    return process;
  }

  private static Map<String, Map<String, Object>> capacities() {
    return new LinkedHashMap<String, Map<String, Object>>();
  }

  private static Map<String, Object> properties(Object... pairs) {
    if ((pairs.length & 1) != 0) {
      throw new IllegalArgumentException("Capacity properties require key/value pairs");
    }
    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    for (int index = 0; index < pairs.length; index += 2) {
      properties.put((String) pairs[index], pairs[index + 1]);
    }
    return properties;
  }

  private static String processJson() {
    return "{\"fluid\":{\"model\":\"SRK\",\"temperature\":298.15,\"pressure\":50.0,"
        + "\"components\":{\"methane\":0.9,\"ethane\":0.1}},\"process\":[" + "{\"type\":\"Stream\",\"name\":\"feed\"},"
        + "{\"type\":\"Separator\",\"name\":\"separator\",\"inlet\":\"feed\"},"
        + "{\"type\":\"Compressor\",\"name\":\"compressor\",\"inlet\":\"feed\"},"
        + "{\"type\":\"Cooler\",\"name\":\"cooler\",\"inlet\":\"feed\"},"
        + "{\"type\":\"Pump\",\"name\":\"pump\",\"inlet\":\"feed\"}]}";
  }
}
