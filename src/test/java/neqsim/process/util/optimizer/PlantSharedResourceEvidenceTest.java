package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression and engineering acceptance tests for participant-complete shared-power evidence. */
class PlantSharedResourceEvidenceTest {

  @Test
  void explicitConversionsProducePhysicalMarginAndSnapshotEvidence() {
    PlantConstraintDefinition definition = powerDefinition("total-power", "MW", "compressor shaft power",
        participant("train-a", "kW", "compressor shaft power", 0.001),
        participant("train-b", "kW", "compressor shaft power", 0.001));
    PlantSharedResourceEvidence evidence = PlantSharedResourceEvidence.builder(definition, "calc-1", 2.0)
        .participant(sample("train-b", "calc-1", 800.0, "kW", "compressor shaft power"))
        .participant(sample("train-a", "calc-1", 900.0, "kW", "compressor shaft power"))
        .sourceTotal(1.7, "MW", "compressor shaft power", "solved process total").convergenceComplete(true).build();

    assertTrue(evidence.isComplete());
    assertTrue(evidence.isFeasible());
    assertEquals(1.7, evidence.getAggregateValue(), 1.0e-12);
    assertEquals(0.85, evidence.getNormalizedUtilization(), 1.0e-12);
    assertEquals(0.3, evidence.getPhysicalMargin(), 1.0e-12);
    assertEquals(0.0, evidence.getRequiredRelief(), 0.0);
    assertEquals(1.7, evidence.getSourceTotal(), 0.0);
    assertEquals("solved process total", evidence.getSourceTotalProvenance());
    assertEquals("train-a", evidence.getParticipants().get(0).getSourceId());

    PlantConstraintRegistry registry = new PlantConstraintRegistry().register(definition);
    PlantUtilizationSnapshot snapshot = PlantUtilizationSnapshot.builder(registry, "calc-1")
        .sample(evidence.toPlantConstraintSample()).convergenceComplete(true).build();
    assertTrue(snapshot.isFeasible());
    assertEquals(0.85, snapshot.getBottleneck().getNormalizedUtilization(), 1.0e-12);
  }

  @Test
  void missingStaleNonFiniteAndUnexpectedParticipantsFailClosedWithoutZeroes() {
    PlantConstraintDefinition definition = powerDefinition("total-power", "kW", "electrical demand",
        directParticipant("load-a", "kW", "electrical demand"), directParticipant("load-b", "kW", "electrical demand"));

    PlantSharedResourceEvidence missing = PlantSharedResourceEvidence.builder(definition, "calc-2", 1000.0)
        .participant(sample("load-a", "calc-2", 400.0, "kW", "electrical demand"))
        .sourceTotal(400.0, "kW", "electrical demand", "meter").convergenceComplete(true).build();
    assertFalse(missing.isComplete());
    assertTrue(Double.isNaN(missing.getAggregateValue()));
    assertTrue(missing.getDiagnostics().contains("load-b=MISSING:Expected participant observation is missing"));
    JsonObject missingJson = JsonParser.parseString(missing.toJson()).getAsJsonObject();
    assertTrue(missingJson.get("aggregateValue").isJsonNull());
    assertTrue(missingJson.getAsJsonArray("participants").get(1).getAsJsonObject().get("value").isJsonNull());

    PlantSharedResourceParticipantSample stale = PlantSharedResourceParticipantSample.builder("load-a", "calc-2")
        .status(PlantSharedResourceParticipantSample.Status.STALE).unit("kW").basis("electrical demand")
        .provenance("old meter sample").build();
    PlantSharedResourceParticipantSample nonFinite = PlantSharedResourceParticipantSample.builder("load-b", "calc-2")
        .value(Double.POSITIVE_INFINITY).unit("kW").basis("electrical demand").provenance("meter").build();
    PlantSharedResourceEvidence invalid = PlantSharedResourceEvidence.builder(definition, "calc-2", 1000.0)
        .participant(stale).participant(nonFinite)
        .participant(sample("load-c", "calc-2", 1.0, "kW", "electrical demand"))
        .sourceTotal(401.0, "kW", "electrical demand", "meter").convergenceComplete(true).build();
    assertFalse(invalid.isComplete());
    assertEquals(PlantSharedResourceParticipantSample.Status.NON_FINITE_VALUE,
        invalid.getParticipants().get(1).getStatus());
    assertEquals(PlantConstraintSample.SampleStatus.NON_FINITE_VALUE, invalid.toPlantConstraintSample().getStatus());
    assertTrue(invalid.getDiagnostics().contains("load-c=UNEXPECTED_PARTICIPANT"));
  }

  @Test
  void processModelShaftPowerUsesEveryAreaAndDoesNotInferElectricalLoad() {
    ProcessModel model = new ProcessModel();
    model.add("Compression A", compressorArea("A", 5000.0, 90.0));
    model.add("Compression B", compressorArea("B", 3500.0, 105.0));
    assertTrue(model.runUntilConverged(50, 5.0e-3));

    PlantConstraintDefinition definition = powerDefinition("shaft-power", "kW", "compressor and pump shaft power",
        directParticipant("Compression A", "kW", "compressor and pump shaft power"),
        directParticipant("Compression B", "kW", "compressor and pump shaft power"));
    double total = model.getPower("kW");
    PlantSharedResourceEvidence evidence = PlantSharedResourceEvidence.fromProcessModelShaftPower(definition,
        "model-calc", total * 1.1, model, true, "completed isolated ProcessModel");

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertTrue(evidence.isFeasible());
    assertEquals(total, evidence.getAggregateValue(), Math.max(1.0, total) * 1.0e-10);
    assertEquals(2, evidence.getParticipants().size());
    assertEquals("compressor and pump shaft power", evidence.getDefinition().getBasis());
    assertFalse(evidence.toJson().contains("electrical"));

    PlantConstraintDefinition incompleteDefinition = powerDefinition("shaft-power", "kW",
        "compressor and pump shaft power", directParticipant("Compression A", "kW", "compressor and pump shaft power"));
    PlantSharedResourceEvidence incomplete = PlantSharedResourceEvidence.fromProcessModelShaftPower(
        incompleteDefinition, "model-calc", total * 1.1, model, true, "completed isolated ProcessModel");
    assertFalse(incomplete.isComplete());
    assertTrue(incomplete.getDiagnostics().contains("Compression B=UNEXPECTED_PARTICIPANT"));
  }

  @Test
  void solvedElectricalBusUsesRequestedDemandAndRejectsMissingParticipantsAndStaleState() {
    EnergyBus bus = new EnergyBus("main electrical bus", EnergyType.ELECTRICAL);
    EnergyPort generator = port("generator", EnergyPortDirection.OUTPUT, EnergyPortMode.CALCULATED, bus);
    generator.setDuty(1.2, "MW");
    EnergyPort essential = port("essential", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    essential.setRequestedPower(1.0, "MW");
    EnergyPort flexible = port("flexible", EnergyPortDirection.INPUT, EnergyPortMode.SPECIFICATION, bus);
    flexible.setRequestedPower(0.5, "MW");
    assertEquals(0.3e6, bus.solveBalance().getUnmetDemand(), 1.0e-6);

    PlantConstraintDefinition definition = powerDefinition("electrical-demand", "MW", "requested electrical load",
        participant(essential.getParticipantId(), "W", "requested electrical load", 1.0e-6),
        participant(flexible.getParticipantId(), "W", "requested electrical load", 1.0e-6));
    PlantSharedResourceEvidence evidence = PlantSharedResourceEvidence.fromSolvedEnergyBusRequestedDemand(definition,
        "bus-calc", 1.4, bus, "solved EnergyBus report");

    assertTrue(evidence.isComplete(), evidence.getDiagnostics().toString());
    assertFalse(evidence.isFeasible());
    assertEquals(1.5, evidence.getAggregateValue(), 1.0e-12);
    assertEquals(0.1, evidence.getRequiredRelief(), 1.0e-12);

    PlantConstraintDefinition missingParticipant = powerDefinition("electrical-demand", "MW",
        "requested electrical load",
        participant(essential.getParticipantId(), "W", "requested electrical load", 1.0e-6));
    PlantSharedResourceEvidence incomplete = PlantSharedResourceEvidence
        .fromSolvedEnergyBusRequestedDemand(missingParticipant, "bus-calc", 1.4, bus, "solved EnergyBus report");
    assertFalse(incomplete.isComplete());
    assertTrue(incomplete.getDiagnostics().contains(flexible.getParticipantId() + "=UNEXPECTED_PARTICIPANT"));

    bus.setDuty(-0.1, "MW");
    PlantSharedResourceEvidence stale = PlantSharedResourceEvidence.fromSolvedEnergyBusRequestedDemand(definition,
        "bus-stale", 1.4, bus, "stale EnergyBus report");
    assertFalse(stale.isComplete());
    assertTrue(stale.getDiagnostics().contains("ENERGY_BUS_SOLUTION_STALE_OR_MISSING"));
  }

  @Test
  void changedLimitAndExplicitAvailabilityRemainImmutableAndRestorable() throws Exception {
    PlantConstraintDefinition definition = powerDefinition("total-power", "kW", "shaft power",
        directParticipant("train-a", "kW", "shaft power"), directParticipant("train-b", "kW", "shaft power"));
    PlantSharedResourceEvidence baseline = evidence(definition, "baseline", 200.0,
        sample("train-a", "baseline", 90.0, "kW", "shaft power"),
        sample("train-b", "baseline", 80.0, "kW", "shaft power"));
    PlantSharedResourceParticipantSample unavailable = PlantSharedResourceParticipantSample
        .builder("train-b", "candidate").status(PlantSharedResourceParticipantSample.Status.OUT_OF_SERVICE).value(0.0)
        .unit("kW").basis("shaft power").provenance("approved line-up").build();
    PlantSharedResourceEvidence candidate = PlantSharedResourceEvidence.builder(definition, "candidate", 85.0)
        .participant(sample("train-a", "candidate", 90.0, "kW", "shaft power")).participant(unavailable)
        .sourceTotal(90.0, "kW", "shaft power", "candidate replay").convergenceComplete(true).build();

    assertTrue(baseline.isFeasible());
    assertFalse(candidate.isFeasible());
    assertEquals(170.0, baseline.getAggregateValue(), 0.0);
    assertEquals(90.0, candidate.getAggregateValue(), 0.0);
    assertEquals(5.0, candidate.getRequiredRelief(), 0.0);
    PlantSharedResourceEvidence restored = roundTrip(baseline);
    assertEquals(baseline.toJson(), restored.toJson());
    assertEquals(PlantSharedResourceParticipantSample.Status.AVAILABLE, restored.getParticipants().get(1).getStatus());
    assertThrows(UnsupportedOperationException.class, () -> restored.getParticipants().clear());
    assertThrows(UnsupportedOperationException.class, () -> restored.getDiagnostics().clear());

    PlantSharedResourceParticipantSample invalidUnavailable = PlantSharedResourceParticipantSample
        .builder("train-b", "candidate").status(PlantSharedResourceParticipantSample.Status.OUT_OF_SERVICE).value(1.0)
        .unit("kW").basis("shaft power").provenance("line-up").build();
    assertEquals(PlantSharedResourceParticipantSample.Status.METADATA_MISMATCH, invalidUnavailable.getStatus());
  }

  @Test
  void sixAreaParticipantLedgerIsDeterministicAndBounded() throws Exception {
    Runtime runtime = Runtime.getRuntime();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
    long started = System.nanoTime();
    List<PlantConstraintParticipant> definitions = new ArrayList<PlantConstraintParticipant>();
    for (int area = 1; area <= 6; area++) {
      for (int load = 1; load <= 30; load++) {
        definitions.add(directParticipant(String.format("area-%d/load-%03d", area, load), "kW", "electrical load"));
      }
    }
    PlantConstraintDefinition definition = powerDefinition("large-ledger", "kW", "electrical load",
        definitions.toArray(new PlantConstraintParticipant[definitions.size()]));
    PlantSharedResourceEvidence.Builder builder = PlantSharedResourceEvidence.builder(definition, "large-calc", 200.0)
        .sourceTotal(180.0, "kW", "electrical load", "synthetic acceptance ledger").convergenceComplete(true);
    for (int index = definitions.size() - 1; index >= 0; index--) {
      builder.participant(sample(definitions.get(index).getSourceId(), "large-calc", 1.0, "kW", "electrical load"));
    }
    PlantSharedResourceEvidence evidence = builder.build();
    byte[] serialized = serialize(evidence);
    byte[] json = evidence.toJson().getBytes("UTF-8");
    long elapsedMillis = (System.nanoTime() - started) / 1000000L;
    long memoryDelta = Math.max(0L, runtime.totalMemory() - runtime.freeMemory() - memoryBefore);

    assertTrue(evidence.isComplete());
    assertEquals(180, evidence.getParticipants().size());
    assertEquals("area-1/load-001", evidence.getParticipants().get(0).getSourceId());
    assertEquals("area-6/load-030", evidence.getParticipants().get(179).getSourceId());
    assertTrue(serialized.length < 500000, "shared-resource evidence must stay below 500 kB");
    assertTrue(json.length < 1000000, "shared-resource JSON must stay below 1 MB");
    assertTrue(elapsedMillis < 2000L, "180-participant capture must complete within two seconds");
    assertTrue(memoryDelta < 64L * 1024L * 1024L, "180-participant capture must not grow used heap by 64 MB");
    assertEquals(evidence.toJson(), roundTrip(evidence).toJson());
  }

  private static PlantSharedResourceEvidence evidence(PlantConstraintDefinition definition, String calculationId,
      double limit, PlantSharedResourceParticipantSample... samples) {
    double total = 0.0;
    PlantSharedResourceEvidence.Builder builder = PlantSharedResourceEvidence.builder(definition, calculationId, limit)
        .convergenceComplete(true);
    for (PlantSharedResourceParticipantSample sample : samples) {
      builder.participant(sample);
      total += sample.getValue();
    }
    return builder.sourceTotal(total, definition.getUnit(), definition.getBasis(), "independent total").build();
  }

  private static PlantConstraintDefinition powerDefinition(String id, String unit, String basis,
      PlantConstraintParticipant... participants) {
    PlantConstraintDefinition.Builder builder = PlantConstraintDefinition
        .builder(id, PlantConstraintScope.sharedResource("Plant", id))
        .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
        .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
        .category(PlantConstraintDefinition.Category.DESIGN).severity(ConstraintSeverity.HARD).unit(unit).basis(basis)
        .provenance("synthetic engineering power budget").owner("electrical and rotating equipment");
    for (PlantConstraintParticipant participant : participants) {
      builder.participant(participant);
    }
    return builder.build();
  }

  private static PlantConstraintParticipant directParticipant(String id, String unit, String basis) {
    return PlantConstraintParticipant.direct(id, unit, basis);
  }

  private static PlantConstraintParticipant participant(String id, String unit, String basis, double factor) {
    return PlantConstraintParticipant.converted(id, unit, basis, factor, 0.0);
  }

  private static PlantSharedResourceParticipantSample sample(String id, String calculationId, double value, String unit,
      String basis) {
    return PlantSharedResourceParticipantSample.builder(id, calculationId).value(value).unit(unit).basis(basis)
        .provenance("completed synthetic calculation").build();
  }

  private static ProcessSystem compressorArea(String suffix, double flowKgPerHr, double outletPressureBara) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed " + suffix, fluid);
    feed.setFlowRate(flowKgPerHr, "kg/hr");
    Compressor compressor = new Compressor("compressor " + suffix, feed);
    compressor.setOutletPressure(outletPressureBara, "bara");
    compressor.setIsentropicEfficiency(0.78);
    ProcessSystem process = new ProcessSystem("area " + suffix);
    process.add(feed);
    process.add(compressor);
    return process;
  }

  private static EnergyPort port(String owner, EnergyPortDirection direction, EnergyPortMode mode, EnergyBus bus) {
    EnergyPort port = new EnergyPort("power", EnergyType.ELECTRICAL, direction, mode);
    port.setOwnerName(owner);
    port.connect(bus);
    return port;
  }

  private static byte[] serialize(PlantSharedResourceEvidence evidence) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(evidence);
    output.close();
    return bytes.toByteArray();
  }

  private static PlantSharedResourceEvidence roundTrip(PlantSharedResourceEvidence evidence) throws Exception {
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialize(evidence)));
    PlantSharedResourceEvidence restored = (PlantSharedResourceEvidence) input.readObject();
    input.close();
    assertNotNull(restored);
    return restored;
  }
}
