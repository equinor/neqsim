package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorDriver;
import neqsim.process.equipment.compressor.DriverType;
import neqsim.process.equipment.energy.Gearbox;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.MechanicalShaft;

/** Engineering acceptance tests for immutable common-shaft evidence. */
class PlantCommonShaftEvidenceTest {
  private static final String CALCULATION_ID = "8b8533a7-65a8-4ca1-a62d-5f2d4c6b8e10";

  @Test
  void completeTrainExposesSpeedPowerTorqueAndCasingMapEvidence() throws Exception {
    TrainFixture fixture = trainFixture(800.0, 900.0);
    PlantCommonShaftEvidence evidence = fixture.evidence(400.0, 350.0, 10000.0, 0.12, 0.18, 0.08, 0.21, true);

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertTrue(evidence.isFeasible());
    assertEquals(2, evidence.getCasings().size());
    assertEquals(750.0, evidence.getTotalCasingPowerKw(), 1.0e-12);
    assertEquals(760.0, evidence.getTotalShaftLoadKw(), 1.0e-12);
    assertEquals(760.0 / 0.98, evidence.getGearboxInputPowerKw(), 1.0e-12);
    assertEquals(10000.0, evidence.getDriverSpeedRpm(), 0.0);
    assertEquals(760000.0 / (10000.0 * 2.0 * Math.PI / 60.0), evidence.getShaftTorqueNm(), 1.0e-10);
    assertEquals(9, evidence.getDefinitions().size());
    assertEquals(9, evidence.getSamples().size());
    assertTrue(evidence.toPlantUtilizationSnapshot().isComplete());

    JsonObject json = JsonParser.parseString(evidence.toJson()).getAsJsonObject();
    assertEquals("1.0", json.get("schemaVersion").getAsString());
    assertEquals("AVAILABLE", json.get("status").getAsString());
    assertEquals(2, json.getAsJsonArray("casings").size());
    assertTrue(json.getAsJsonObject("utilizationSnapshot").get("complete").getAsBoolean());

    PlantCommonShaftEvidence restored = roundTrip(evidence);
    assertNotSame(evidence, restored);
    assertEquals(evidence.toJson(), restored.toJson());
  }

  @Test
  void physicalViolationsRemainCompleteAndIdentifyConstraintBottleneck() {
    TrainFixture fixture = trainFixture(800.0, 700.0);
    PlantCommonShaftEvidence evidence = fixture.evidence(400.0, 350.0, 10006.0, 0.12, 0.18, -0.03, 0.21, true);

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertFalse(evidence.isFeasible());
    assertTrue(evidence.getSamples().stream().anyMatch(sample -> sample.getNormalizedResidual() > 0.0));
    assertTrue(
        evidence.getCasings().stream().anyMatch(casing -> !casing.isWithinChart() && casing.getMapMargin() < 0.0));
  }

  @Test
  void shaftFrictionIsIncludedInPowerBalanceAndCanBecomeBinding() {
    TrainFixture fixture = trainFixture(755.0, 900.0);
    PlantCommonShaftEvidence evidence = fixture.evidence(400.0, 350.0, 10000.0, 0.12, 0.18, 0.08, 0.21, true);

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertFalse(evidence.isFeasible());
    assertEquals(5.0, evidence.getUnmetPowerKw(), 1.0e-12);
    assertEquals(760.0, evidence.getTotalShaftLoadKw(), 1.0e-12);
  }

  @Test
  void explicitlyUnavailableCasingRequiresAndPreservesVerifiedZeroLoad() {
    TrainFixture fixture = trainFixture(500.0, 900.0);
    fixture.casingBPort.setRequestedPower(0.0, "kW");
    fixture.shaft.solveBalance();
    StubCompressor casingA = new StubCompressor("casing A", 10000.0, 400.0, 0.12, 0.18, true,
        UUID.fromString(CALCULATION_ID));
    StubCompressor casingB = new StubCompressor("casing B", 0.0, 0.0, 0.0, 0.0, false, UUID.fromString(CALCULATION_ID));

    PlantCommonShaftEvidence evidence = PlantCommonShaftEvidence
        .builder("medium production model", "compression", "export train", CALCULATION_ID, fixture.shaft,
            "completed isolated candidate")
        .casing(fixture.casingAPort.getParticipantId(), casingA)
        .casing(fixture.casingBPort.getParticipantId(), casingB, true)
        .driver(fixture.driverPort.getParticipantId(), fixture.driver).gearbox("gearbox-1", fixture.gearbox)
        .speedToleranceRpm(1.0).powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(800.0).convergenceComplete(true)
        .build();

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertTrue(evidence.isFeasible());
    assertTrue(evidence.getCasings().stream()
        .anyMatch(casing -> casing.getStatus() == PlantCommonShaftEvidence.CasingStatus.OUT_OF_SERVICE));
    assertTrue(evidence.toPlantUtilizationSnapshot().getEvidence().stream()
        .anyMatch(row -> row.getOperatingStatus() == PlantConstraintEvidence.OperatingStatus.DISABLED));
    assertEquals(400.0, evidence.getTotalCasingPowerKw(), 1.0e-12);
  }

  @Test
  void inconsistentChartFlagAndSignedMarginsFailClosed() {
    TrainFixture fixture = trainFixture(800.0, 900.0);
    StubCompressor casingA = new StubCompressor("casing A", 10000.0, 400.0, 0.12, 0.18, true,
        UUID.fromString(CALCULATION_ID));
    StubCompressor casingB = new StubCompressor("casing B", 10000.0, 350.0, -0.01, 0.21, true,
        UUID.fromString(CALCULATION_ID));
    casingB.operatingPoint.put("withinChart", true);

    PlantCommonShaftEvidence evidence = PlantCommonShaftEvidence
        .builder("medium production model", "compression", "export train", CALCULATION_ID, fixture.shaft,
            "completed isolated candidate")
        .casing(fixture.casingAPort.getParticipantId(), casingA).casing(fixture.casingBPort.getParticipantId(), casingB)
        .driver(fixture.driverPort.getParticipantId(), fixture.driver).gearbox("gearbox-1", fixture.gearbox)
        .speedToleranceRpm(1.0).powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(800.0).convergenceComplete(true)
        .build();

    assertFalse(evidence.isComplete());
    assertTrue(evidence.getDiagnostics().stream().anyMatch(value -> value.contains("METADATA_MISMATCH")));
  }

  @Test
  void staleChartlessUnexpectedAndUnconvergedPathsFailClosedWithJsonNulls() {
    TrainFixture fixture = trainFixture(800.0, 900.0);
    EnergyPort unexpected = port("unexpected", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, fixture.shaft);
    unexpected.setRequestedPower(1.0, "kW");
    fixture.shaft.solveBalance();

    PlantCommonShaftEvidence evidence = fixture.evidenceWithCalculationIds(400.0, 350.0, 10000.0, UUID.randomUUID(),
        UUID.fromString(CALCULATION_ID), false, false);

    assertFalse(evidence.isComplete());
    assertFalse(evidence.isFeasible());
    assertTrue(evidence.getDiagnostics().stream().anyMatch(value -> value.contains("UNEXPECTED_SHAFT_PARTICIPANT")));
    assertTrue(evidence.getDiagnostics().stream().anyMatch(value -> value.contains("STALE")));
    assertTrue(evidence.getDiagnostics().stream().anyMatch(value -> value.contains("NO_CHART")));
    assertTrue(evidence.getDiagnostics().contains("INCOMPLETE_CONVERGENCE"));
    JsonObject json = JsonParser.parseString(evidence.toJson()).getAsJsonObject();
    assertTrue(json.get("totalCasingPowerKw").isJsonNull());
    assertTrue(json.getAsJsonArray("casings").get(0).getAsJsonObject().get("shaftPowerKw").isJsonNull());
    assertFalse(json.getAsJsonObject("utilizationSnapshot").get("complete").getAsBoolean());
  }

  @Test
  void changedShaftStateInvalidatesEvidenceUntilBalanceIsSolvedAgain() {
    TrainFixture fixture = trainFixture(800.0, 900.0);
    PlantCommonShaftEvidence cold = fixture.evidence(400.0, 350.0, 10000.0, 0.12, 0.18, 0.08, 0.21, true);
    assertTrue(cold.isComplete());

    fixture.driverPort.setDuty(810.0, "kW");
    PlantCommonShaftEvidence stale = fixture.evidence(400.0, 350.0, 10000.0, 0.12, 0.18, 0.08, 0.21, true);
    assertFalse(stale.isComplete());
    assertTrue(stale.getDiagnostics().contains("SHAFT_SOLUTION_STALE_OR_MISSING"));

    fixture.driverPort.setDuty(800.0, "kW");
    fixture.shaft.solveBalance();
    PlantCommonShaftEvidence restored = fixture.evidence(400.0, 350.0, 10000.0, 0.12, 0.18, 0.08, 0.21, true);
    assertTrue(restored.isComplete(), restored.getDiagnostics().toString());
    assertEquals(cold.toJson(), restored.toJson());
  }

  private static PlantCommonShaftEvidence roundTrip(PlantCommonShaftEvidence evidence) throws Exception {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
      output.writeObject(evidence);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      return (PlantCommonShaftEvidence) input.readObject();
    }
  }

  private static TrainFixture trainFixture(double driverSupplyKw, double gearboxLimitKw) {
    MechanicalShaft shaft = new MechanicalShaft("export compression shaft");
    shaft.setSpeed(10000.0);
    shaft.setMaximumSpeed(12000.0);
    shaft.setFrictionLoss(10.0e3);
    EnergyPort driver = port("driver", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, shaft);
    driver.setDuty(driverSupplyKw, "kW");
    EnergyPort casingA = port("casing-a", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, shaft);
    casingA.setRequestedPower(400.0, "kW");
    EnergyPort casingB = port("casing-b", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, shaft);
    casingB.setRequestedPower(350.0, "kW");
    shaft.solveBalance();

    CompressorDriver driverModel = new CompressorDriver(DriverType.ELECTRIC_MOTOR, 900.0);
    driverModel.setMinSpeed(8000.0);
    driverModel.setMaxSpeed(12000.0);
    Gearbox gearbox = new Gearbox("train gearbox");
    gearbox.setEfficiency(0.98);
    gearbox.setSpeedRatio(1.0);
    gearbox.setMaximumInputPower(gearboxLimitKw * 1000.0);
    return new TrainFixture(shaft, driver, casingA, casingB, driverModel, gearbox);
  }

  private static EnergyPort port(String name, EnergyPortDirection direction, EnergyPortMode mode,
      MechanicalShaft shaft) {
    EnergyPort port = new EnergyPort(name, EnergyType.SHAFT_WORK, direction, mode);
    port.connect(shaft);
    return port;
  }

  private static final class TrainFixture {
    private final MechanicalShaft shaft;
    private final EnergyPort driverPort;
    private final EnergyPort casingAPort;
    private final EnergyPort casingBPort;
    private final CompressorDriver driver;
    private final Gearbox gearbox;

    private TrainFixture(MechanicalShaft shaft, EnergyPort driverPort, EnergyPort casingAPort, EnergyPort casingBPort,
        CompressorDriver driver, Gearbox gearbox) {
      this.shaft = shaft;
      this.driverPort = driverPort;
      this.casingAPort = casingAPort;
      this.casingBPort = casingBPort;
      this.driver = driver;
      this.gearbox = gearbox;
    }

    private PlantCommonShaftEvidence evidence(double casingAKw, double casingBKw, double casingBSpeedRpm,
        double casingASurge, double casingAStonewall, double casingBSurge, double casingBStonewall,
        boolean convergenceComplete) {
      return evidenceWithCalculationIds(casingAKw, casingBKw, casingBSpeedRpm, UUID.fromString(CALCULATION_ID),
          UUID.fromString(CALCULATION_ID), true, convergenceComplete, casingASurge, casingAStonewall, casingBSurge,
          casingBStonewall);
    }

    private PlantCommonShaftEvidence evidenceWithCalculationIds(double casingAKw, double casingBKw,
        double casingBSpeedRpm, UUID casingACalculationId, UUID casingBCalculationId, boolean casingBChartActive,
        boolean convergenceComplete) {
      return evidenceWithCalculationIds(casingAKw, casingBKw, casingBSpeedRpm, casingACalculationId,
          casingBCalculationId, casingBChartActive, convergenceComplete, 0.12, 0.18, 0.08, 0.21);
    }

    private PlantCommonShaftEvidence evidenceWithCalculationIds(double casingAKw, double casingBKw,
        double casingBSpeedRpm, UUID casingACalculationId, UUID casingBCalculationId, boolean casingBChartActive,
        boolean convergenceComplete, double casingASurge, double casingAStonewall, double casingBSurge,
        double casingBStonewall) {
      StubCompressor casingA = new StubCompressor("casing A", 10000.0, casingAKw, casingASurge, casingAStonewall, true,
          casingACalculationId);
      StubCompressor casingB = new StubCompressor("casing B", casingBSpeedRpm, casingBKw, casingBSurge,
          casingBStonewall, casingBChartActive, casingBCalculationId);
      return PlantCommonShaftEvidence
          .builder("medium production model", "compression", "export train", CALCULATION_ID, shaft,
              "completed isolated candidate")
          .casing(casingAPort.getParticipantId(), casingA).casing(casingBPort.getParticipantId(), casingB)
          .driver(driverPort.getParticipantId(), driver).gearbox("gearbox-1", gearbox).speedToleranceRpm(1.0)
          .powerBalanceToleranceKw(1.0e-6).maximumTorqueNm(800.0).convergenceComplete(convergenceComplete).build();
    }
  }

  private static final class StubCompressor extends Compressor {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> operatingPoint;

    private StubCompressor(String name, double speedRpm, double powerKw, double distanceToSurge,
        double distanceToStoneWall, boolean chartActive, UUID calculationId) {
      super(name);
      operatingPoint = new LinkedHashMap<String, Object>();
      operatingPoint.put("speed_rpm", speedRpm);
      operatingPoint.put("power_kW", powerKw);
      operatingPoint.put("chartActive", chartActive);
      operatingPoint.put("withinChart", distanceToSurge >= 0.0 && distanceToStoneWall >= 0.0);
      operatingPoint.put("distanceToSurge", distanceToSurge);
      operatingPoint.put("distanceToStoneWall", distanceToStoneWall);
      operatingPoint.put("limitingConstraint", "compressor map");
      setCalculationIdentifier(calculationId);
    }

    @Override
    public boolean solved() {
      return true;
    }

    @Override
    public Map<String, Object> getOperatingPoint() {
      return new LinkedHashMap<String, Object>(operatingPoint);
    }
  }
}
