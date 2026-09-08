package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Engineering acceptance tests for strict immutable separator evidence. */
class PlantSeparatorEvidenceTest {
  private static final UUID CALCULATION_ID = UUID.fromString("be5911fe-676e-4d44-b0e4-2c19089a3154");

  @Test
  void twoPhaseOilProfileUsesExplicitGeometryAndProducesPhysicalMargins() throws Exception {
    Separator separator = twoPhaseOilSeparator();
    PlantSeparatorEvidence evidence = evidence(separator, PlantSeparatorEvidence.Profile.TWO_PHASE_OIL,
        CALCULATION_ID.toString(), true);

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertEquals(6, evidence.getDefinitions().size());
    assertEquals(6, evidence.getSamples().size());
    assertTrue(evidence.getSamples().stream().allMatch(sample -> Double.isFinite(sample.getSampledValue())));
    assertTrue(evidence.getSamples().stream().allMatch(sample -> Double.isFinite(sample.getPhysicalMargin())));
    assertTrue(evidence.toPlantUtilizationSnapshot().isComplete());

    JsonObject json = JsonParser.parseString(evidence.toJson()).getAsJsonObject();
    assertEquals("AVAILABLE", json.get("status").getAsString());
    assertEquals("TWO_PHASE_OIL", json.get("profile").getAsString());
    assertEquals(6, json.getAsJsonArray("evidence").size());

    PlantSeparatorEvidence restored = roundTrip(evidence);
    assertNotSame(evidence, restored);
    assertEquals(evidence.toJson(), restored.toJson());
  }

  @Test
  void dryGasScrubberDoesNotInventLiquidResidenceEvidence() {
    Separator separator = dryGasScrubber();
    PlantSeparatorEvidence evidence = evidence(separator, PlantSeparatorEvidence.Profile.GAS_SCRUBBER,
        CALCULATION_ID.toString(), true);

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertEquals(3, evidence.getDefinitions().size());
    assertTrue(evidence.getDefinitions().stream()
        .noneMatch(definition -> definition.getId().contains("retention") || definition.getId().contains("settling")));
  }

  @Test
  void changedInstalledLevelLimitProducesNewEvidenceAndOldSnapshotRemainsImmutable() {
    Separator separator = twoPhaseOilSeparator();
    PlantSeparatorEvidence installed = evidence(separator, PlantSeparatorEvidence.Profile.TWO_PHASE_OIL,
        CALCULATION_ID.toString(), true);
    String installedJson = installed.toJson();

    PlantSeparatorEvidence tightened = PlantSeparatorEvidence
        .builder("qualification model", "separation", CALCULATION_ID.toString(), separator,
            PlantSeparatorEvidence.Profile.TWO_PHASE_OIL, "synthetic installed vessel data")
        .maximumLiquidLevelFraction(0.10).convergenceComplete(true).build();

    assertTrue(tightened.isComplete(), tightened.getDiagnostics().toString());
    assertFalse(tightened.isFeasible());
    assertEquals(installedJson, installed.toJson(), "later evidence construction must not mutate the old snapshot");
    assertFalse(installedJson.equals(tightened.toJson()));

    PlantSeparatorEvidence restored = evidence(separator, PlantSeparatorEvidence.Profile.TWO_PHASE_OIL,
        CALCULATION_ID.toString(), true);
    assertEquals(installedJson, restored.toJson());
  }

  @Test
  void staleUnconvergedAndFallbackGeometryPathsFailClosedWithJsonNulls() {
    Separator separator = twoPhaseOilSeparator();
    separator.getMechanicalDesign().setInletNozzleID(0.0);
    separator.getMechanicalDesign().setHLLFraction(0.0);

    PlantSeparatorEvidence evidence = evidence(separator, PlantSeparatorEvidence.Profile.TWO_PHASE_OIL,
        UUID.randomUUID().toString(), false);

    assertFalse(evidence.isComplete());
    assertFalse(evidence.isFeasible());
    assertTrue(evidence.getDiagnostics().contains("INCOMPLETE_CONVERGENCE"));
    assertTrue(evidence.getDiagnostics().contains("STALE_CALCULATION_IDENTITY"));
    assertTrue(evidence.getDiagnostics().contains("EXPLICIT_INLET_NOZZLE_MISSING"));
    assertTrue(evidence.getDiagnostics().contains("EXPLICIT_HLL_OR_GAS_LENGTH_MISSING"));
    assertTrue(evidence.getSamples().stream()
        .allMatch(sample -> sample.getStatus() == PlantConstraintSample.SampleStatus.INCOMPLETE_CONVERGENCE));
    JsonObject json = JsonParser.parseString(evidence.toJson()).getAsJsonObject();
    assertTrue(json.getAsJsonArray("evidence").get(0).getAsJsonObject().get("sampledValue").isJsonNull());
  }

  @Test
  void threePhaseProfileRequiresThreePhaseEquipmentAndInterfaceLimit() {
    Separator separator = twoPhaseOilSeparator();
    PlantSeparatorEvidence evidence = PlantSeparatorEvidence
        .builder("qualification model", "separation", CALCULATION_ID.toString(), separator,
            PlantSeparatorEvidence.Profile.THREE_PHASE, "synthetic installed vessel data")
        .maximumLiquidLevelFraction(0.8).convergenceComplete(true).build();

    assertFalse(evidence.isComplete());
    assertTrue(evidence.getDiagnostics().contains("WATER_PHASE_MISSING"));
    assertTrue(evidence.getDiagnostics().contains("THREE_PHASE_SEPARATOR_TYPE_REQUIRED"));
    assertTrue(evidence.getDiagnostics().contains("INTERFACE_SETTLING_LIMIT_MISSING"));
  }

  private static PlantSeparatorEvidence evidence(Separator separator, PlantSeparatorEvidence.Profile profile,
      String calculationId, boolean converged) {
    return PlantSeparatorEvidence
        .builder("qualification model", "separation", calculationId, separator, profile,
            "synthetic installed vessel data")
        .maximumLiquidLevelFraction(0.8).minimumInterfaceSettlingMinutes(1.0).convergenceComplete(converged).build();
  }

  private static Separator twoPhaseOilSeparator() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 5.0, 35.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.07);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.04);
    fluid.addComponent("n-hexane", 0.03);
    fluid.addComponent("n-heptane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return runSeparator(fluid, "two-phase oil separator", "horizontal");
  }

  private static Separator dryGasScrubber() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    return runSeparator(fluid, "dry gas scrubber", "vertical");
  }

  private static Separator runSeparator(SystemInterface fluid, String name, String orientation) {
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(20000.0, "kg/hr");
    Separator separator = new Separator(name, feed);
    separator.setInternalDiameter(2.4);
    separator.setSeparatorLength(6.0);
    separator.setOrientation(orientation);
    separator.setDesignGasLoadFactor(0.12);
    separator.setLiquidLevel(0.45);
    separator.getMechanicalDesign().setInletNozzleID(0.25);
    separator.getMechanicalDesign().setHLLFraction(0.70);
    separator.getMechanicalDesign().setNLLFraction(0.50);
    separator.getMechanicalDesign().setNILFraction(0.20);
    separator.getMechanicalDesign().setEffectiveLengthGas(3.8);
    separator.getMechanicalDesign().setEffectiveLengthLiquid(4.8);
    ProcessSystem process = new ProcessSystem(name + " process");
    process.add(feed);
    process.add(separator);
    process.run(CALCULATION_ID);
    assertTrue(process.solved(), process.getRunStatusJson());
    return separator;
  }

  private static PlantSeparatorEvidence roundTrip(PlantSeparatorEvidence evidence) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(evidence);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      return (PlantSeparatorEvidence) input.readObject();
    }
  }
}
