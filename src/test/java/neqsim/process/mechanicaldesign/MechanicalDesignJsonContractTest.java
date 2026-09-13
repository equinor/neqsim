package neqsim.process.mechanicaldesign;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesignResponse;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesignResponse;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerType;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/** Regression tests for issue #3678 and downstream design consumers. */
class MechanicalDesignJsonContractTest {
  private Stream feed() {
    SystemSrkEos fluid = new SystemSrkEos(313.15, 20.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-decane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.run();
    return feed;
  }

  @Test
  void compressorExportsLiveOperatingConditions() throws Exception {
    Separator separator = new Separator("separator", feed());
    separator.run();
    Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setUsePolytropicCalc(true);
    compressor.setPolytropicEfficiency(0.78);
    compressor.setSpeed(6000.0);
    compressor.run();
    compressor.getMechanicalDesign().calcDesign();
    JsonObject json = JsonParser.parseString(compressor.getMechanicalDesign().toJson()).getAsJsonObject();
    assertEquals(20.0, json.get("inletPressure").getAsDouble(), 1e-9);
    JsonObject data = JsonParser.parseString(compressor.getMechanicalDesign().toDesignDataJson()).getAsJsonObject();
    assertEquals(2e6, value(data, "operatingConditions", "inletPressure"), 1e-6);
    assertEquals(8e6, value(data, "operatingConditions", "outletPressure"), 1e-6);
    assertEquals(compressor.getMechanicalDesign().getImpellerDiameter() / 1000.0,
        value(data, "geometry", "impellerDiameter"), 1e-12);
    assertEquals(compressor.getMechanicalDesign().getCasingDesignCalculator().getSelectedWallThicknessMm() / 1000.0,
        value(data, "geometry", "pressureCasingWallThickness"), 1e-12);
    assertEquals(JsonParser.parseString(compressor.getMechanicalDesign().toJson()),
        JsonParser.parseString(compressor.getMechanicalDesign().toCompactJson()));
    Files.createDirectories(Paths.get("target/design-json"));
    Files.write(Paths.get("target/design-json/compressor.json"),
        compressor.getMechanicalDesign().toDesignDataJson().getBytes(StandardCharsets.UTF_8));
    assertEquals(80.0, json.get("outletPressure").getAsDouble(), 1e-9);
    assertEquals(4.0, json.get("pressureRatio").getAsDouble(), 1e-9);
    assertEquals(0.78, json.get("polytropicEfficiency").getAsDouble(), 1e-9);
    assertEquals(compressor.getMechanicalDesign().getDesignPressure(), json.get("maxDesignPressure").getAsDouble(),
        1e-9);
  }

  @Test
  void separatorExportsGeometryWithCorrectScale() throws Exception {
    Separator separator = new Separator("separator", feed());
    separator.setOrientation("horizontal");
    separator.run();
    MechanicalDesign design = separator.getMechanicalDesign();
    design.calcDesign();
    JsonObject json = JsonParser.parseString(design.toJson()).getAsJsonObject();
    assertEquals("horizontal", json.get("orientation").getAsString());
    assertEquals("m", json.get("wallThicknessUnit").getAsString());
    JsonObject data = JsonParser.parseString(design.toDesignDataJson()).getAsJsonObject();
    assertEquals(data, json.getAsJsonObject("designData"));
    assertEquals("consistent", data.get("geometryConsistency").getAsString());
    assertEquals(design.getWallThickness(), value(data, "geometry", "wallThickness"), 1e-12);
    Files.createDirectories(Paths.get("target/design-json"));
    Files.write(Paths.get("target/design-json/separator.json"),
        design.toDesignDataJson().getBytes(StandardCharsets.UTF_8));
    assertEquals(design.getWallThickness() * 1000.0, json.get("shellThickness").getAsDouble(), 1e-9);
    assertTrue(json.get("headType").isJsonNull(), "No head specification exists; do not invent one");
    assertTrue(json.get("headThickness").isJsonNull(), "Uncalculated thickness must be unavailable");
  }

  @Test
  void unconnectedSeparatorKeepsDesignResponseAvailable() {
    MechanicalDesign design = new MechanicalDesign(new Separator("unit test separator"));
    assertDoesNotThrow(() -> new MechanicalDesignResponse(design));
    JsonObject data = JsonParser.parseString(design.toDesignDataJson()).getAsJsonObject();
    assertTrue(
        data.getAsJsonObject("operatingConditions").getAsJsonObject("gasOutletPressure").get("value").isJsonNull());
  }

  @Test
  void heaterDesignSerializesSelectedSizing() {
    Heater heater = new Heater("heater", feed());
    heater.setOutTemperature(353.15);
    heater.run();
    heater.getMechanicalDesign().calcDesign();
    JsonObject json = JsonParser.parseString(heater.getMechanicalDesign().toJson()).getAsJsonObject();
    assertTrue(json.get("requiredArea").getAsDouble() > 0.0);
    assertFalse(json.get("heatExchangerType").isJsonNull());
  }

  private double value(JsonObject data, String section, String field) {
    return data.getAsJsonObject(section).getAsJsonObject(field).get("value").getAsDouble();
  }

  @Test
  void unpopulatedResponsesUseNullAndStrictJson() {
    JsonObject empty = JsonParser.parseString(new CompressorMechanicalDesignResponse().toJson()).getAsJsonObject();
    assertTrue(empty.get("inletPressure").isJsonNull());
    assertTrue(empty.get("polytropicEfficiency").isJsonNull());
    MechanicalDesignResponse response = new MechanicalDesignResponse();
    response.setWallThickness(Double.POSITIVE_INFINITY);
    response.addSpecificParameter("nonfinite", Double.NaN);
    assertEquals(JsonParser.parseString(response.toJson()), JsonParser.parseString(response.toCompactJson()));
    assertFalse(response.toJson().contains("NaN"));
    assertFalse(response.toJson().contains("Infinity"));
    assertTrue(JsonParser.parseString(response.toJson()).getAsJsonObject().get("wallThickness").isJsonNull());
    assertDoesNotThrow(() -> new HeatExchangerMechanicalDesignResponse().toJson());
    MechanicalDesign unknown = new MechanicalDesign(null);
    unknown.setWallThickness(8.0);
    JsonObject data = JsonParser.parseString(unknown.toDesignDataJson()).getAsJsonObject();
    assertTrue(data.getAsJsonObject("geometry").getAsJsonObject("wallThickness").get("value").isJsonNull());
    assertEquals("unavailable",
        data.getAsJsonObject("geometry").getAsJsonObject("wallThickness").get("status").getAsString());
  }

  @Test
  void repeatedExportTracksCurrentCompressorWithoutOverwritingDesignEnvelope() {
    Compressor compressor = new Compressor("compressor", feed());
    compressor.setOutletPressure(60.0, "bara");
    compressor.run();
    compressor.getMechanicalDesign().setMaxOperationPressure(125.0);
    compressor.getMechanicalDesign().calcDesign();
    JsonObject first = JsonParser.parseString(compressor.getMechanicalDesign().toJson()).getAsJsonObject();
    compressor.setOutletPressure(80.0, "bara");
    compressor.run();
    compressor.getMechanicalDesign().calcDesign();
    JsonObject second = JsonParser.parseString(compressor.getMechanicalDesign().toJson()).getAsJsonObject();
    assertEquals(60.0, first.get("outletPressure").getAsDouble(), 1e-9);
    assertEquals(80.0, second.get("outletPressure").getAsDouble(), 1e-9);
    assertEquals(125.0, compressor.getMechanicalDesign().getMaxOperationPressure(), 1e-9);
    assertEquals("configured_or_default", second.getAsJsonObject("designData").getAsJsonObject("designBasis")
        .getAsJsonObject("maximumOperatingPressure").get("status").getAsString());
  }

  @Test
  void pumpConvertsMillimetresAndUsesCalculatedPressure() {
    SystemSrkEos water = new SystemSrkEos(298.15, 2.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");
    Stream inlet = new Stream("water", water);
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();
    Pump pump = new Pump("pump", inlet);
    pump.setOutletPressure(10.0, "bara");
    pump.run();
    pump.getMechanicalDesign().calcDesign();
    JsonObject legacy = JsonParser.parseString(pump.getMechanicalDesign().toJson()).getAsJsonObject();
    JsonObject data = legacy.getAsJsonObject("designData");
    assertEquals("mm", legacy.get("wallThicknessUnit").getAsString());
    assertEquals(pump.getMechanicalDesign().getCasingWallThickness() / 1000.0, value(data, "geometry", "wallThickness"),
        1e-12);
    assertEquals("consistent", data.get("geometryConsistency").getAsString());
    assertEquals(2.0, legacy.get("suctionPressure").getAsDouble(), 1e-9);
    assertEquals(pump.getMechanicalDesign().getDesignPressure(), legacy.get("maxDesignPressure").getAsDouble(), 1e-9);
  }

  @Test
  void valveUsesMetresForShellAndMillimetresForFaceToFace() {
    ThrottlingValve valve = new ThrottlingValve("valve", feed());
    valve.setOutletPressure(10.0, "bara");
    valve.run();
    valve.getMechanicalDesign().calcDesign();
    JsonObject legacy = JsonParser.parseString(valve.getMechanicalDesign().toJson()).getAsJsonObject();
    JsonObject data = legacy.getAsJsonObject("designData");
    assertEquals("m", legacy.get("wallThicknessUnit").getAsString());
    assertEquals(valve.getMechanicalDesign().getFaceToFace() / 1000.0, value(data, "geometry", "faceToFace"), 1e-12);
    assertEquals(2e6, value(data, "operatingConditions", "inletPressure"), 1e-6);
    assertEquals(valve.getMechanicalDesign().getDesignPressure(), legacy.get("maxDesignPressure").getAsDouble(), 1e-9);
  }

  @Test
  void pipelineSeparatesSpecifiedGeometryFromSizingThickness() {
    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", feed());
    pipe.setLength(100.0);
    pipe.setDiameter(0.3);
    pipe.setWallThickness(0.012);
    pipe.run();
    pipe.initMechanicalDesign();
    pipe.getMechanicalDesign().calcDesign();
    JsonObject data = JsonParser.parseString(pipe.getMechanicalDesign().toDesignDataJson()).getAsJsonObject();
    assertEquals(0.3, value(data, "geometry", "innerDiameter"), 1e-12);
    assertEquals(0.324, value(data, "geometry", "outerDiameter"), 1e-12);
    assertEquals(0.012, value(data, "geometry", "wallThickness"), 1e-12);
    assertEquals("consistent", data.get("geometryConsistency").getAsString());
    assertEquals(pipe.getMechanicalDesign().getWallThickness() / 1000.0,
        value(data, "designBasis", "designWallThickness"), 1e-12);
  }

  @Test
  void heatExchangerExportsSelectedTypeWithoutInventingShellForPlate() {
    Stream hot = feed();
    Stream cold = feed();
    cold.setTemperature(293.15);
    cold.run();
    HeatExchanger exchanger = new HeatExchanger("exchanger", hot, cold);
    exchanger.setUAvalue(1000.0);
    exchanger.run();
    JsonObject data = JsonParser.parseString(exchanger.getMechanicalDesign().toDesignDataJson()).getAsJsonObject();
    assertEquals(hot.getPressure("Pa"), value(data, "operatingConditions", "inlet0Pressure"), 1e-6);
    assertEquals(cold.getPressure("Pa"), value(data, "operatingConditions", "inlet1Pressure"), 1e-6);
    assertEquals(exchanger.getDuty(), value(data, "operatingConditions", "duty"), 1e-9);
    exchanger.getMechanicalDesign().setManualSelection(HeatExchangerType.PLATE_AND_FRAME);
    exchanger.getMechanicalDesign().calcDesign();
    JsonObject plate = JsonParser.parseString(exchanger.getMechanicalDesign().toJson()).getAsJsonObject();
    assertEquals("PLATE_AND_FRAME", plate.get("heatExchangerType").getAsString());
    assertTrue(plate.get("shellInnerDiameter").isJsonNull());
    assertEquals(exchanger.getMechanicalDesign().getSelectedSizingResult().getRequiredArea(),
        plate.get("requiredArea").getAsDouble(), 1e-12);
    exchanger.getMechanicalDesign().setManualSelection(HeatExchangerType.SHELL_AND_TUBE);
    exchanger.getMechanicalDesign().calcDesign();
    JsonObject shell = JsonParser.parseString(exchanger.getMechanicalDesign().toJson()).getAsJsonObject();
    assertEquals(exchanger.getMechanicalDesign().getInnerDiameter() * 1000.0,
        shell.get("shellInnerDiameter").getAsDouble(), 1e-9);
    assertEquals(exchanger.getMechanicalDesign().getWallThickness() * 1000.0,
        shell.get("shellWallThickness").getAsDouble(), 1e-9);
  }

  @Test
  void inconsistentGeometryIsReported() {
    Separator separator = new Separator("separator", feed());
    MechanicalDesign design = separator.getMechanicalDesign();
    design.setInnerDiameter(1.0);
    design.setOuterDiameter(0.5);
    design.setWallThickness(0.02);
    JsonObject data = JsonParser.parseString(design.toDesignDataJson()).getAsJsonObject();
    assertEquals("inconsistent", data.get("geometryConsistency").getAsString());
  }
}
