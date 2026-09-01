package neqsim.process.processmodel.lifecycle.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.processmodel.lifecycle.event.ModelChangePublishResult.Status;

class ModelChangeEventTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void convertsGraphDiffAndVerifiesDeterministicRoundTrip() {
    EngineeringGraph older = graph("A", 50.0, false);
    EngineeringGraph newer = graph("B", 55.0, true);
    EngineeringGraphDiff diff = older.compareTo(newer);

    ModelChangeEvent event = ModelChangeEvent.fromEngineeringGraphDiff(diff, "EVENT-001", "MODEL-A:A:B",
        Instant.parse("2026-07-18T08:00:00Z"), "NEQSIM", "PROCESS-TEAM", "ASSET-A", "MODEL-A",
        "Updated design pressure basis");
    ModelChangeEvent reloaded = ModelChangeEvent.fromJson(event.toJson());

    assertEquals("A", reloaded.getBaseRevision());
    assertEquals("B", reloaded.getTargetRevision());
    assertEquals(event.getPayloadFingerprint(), reloaded.getPayloadFingerprint());
    assertTrue(hasSubject(reloaded, "equipment:20-vg-001"));
    assertTrue(hasSubject(reloaded, "calculation:20-vg-001-design-pressure"));
    assertTrue(reloaded.getImpactHintNodeIds().contains("equipment:20-vg-001"));
    assertEquals(event.toJson(), reloaded.toJson());
  }

  @Test
  void publishesIdempotentlyAndReplaysInOrder() {
    ModelChangeEvent event = event("EVENT-001", "KEY-001", "B");
    InMemoryModelChangeEventBus bus = new InMemoryModelChangeEventBus();
    final List<String> received = new ArrayList<String>();
    bus.subscribe(value -> received.add(value.getEventId()));

    ModelChangePublishResult first = bus.publish(event);
    ModelChangePublishResult duplicate = bus.publish(event);
    List<String> replayed = new ArrayList<String>();
    int replayCount = bus.replayAfter("", value -> replayed.add(value.getEventId()));

    assertEquals(Status.PUBLISHED, first.getStatus());
    assertEquals(Status.DUPLICATE, duplicate.getStatus());
    assertEquals(1, received.size());
    assertEquals(1, replayCount);
    assertEquals(received, replayed);
    assertThrows(IllegalArgumentException.class, () -> bus.publish(event("EVENT-002", "KEY-001", "C")));
  }

  @Test
  void journalsEventsDurablyAndRejectsFingerprintTampering() throws Exception {
    Path journalFile = temporaryDirectory.resolve("events/model-change-events.jsonl");
    ModelChangeEvent event = event("EVENT-001", "KEY-001", "B");
    ModelChangeEventJournal journal = new ModelChangeEventJournal(journalFile);

    assertEquals(Status.PUBLISHED, journal.publish(event).getStatus());
    assertEquals(Status.DUPLICATE, journal.publish(event).getStatus());
    ModelChangeEventJournal reloaded = new ModelChangeEventJournal(journalFile);

    assertEquals(1, reloaded.getHistory().size());
    assertEquals(event.getPayloadFingerprint(), reloaded.getHistory().get(0).getPayloadFingerprint());
    String tampered = event.toJson().replace("Updated model", "Unreviewed change");
    assertFalse(tampered.equals(event.toJson()));
    assertThrows(IllegalArgumentException.class, () -> ModelChangeEvent.fromJson(tampered));
  }

  private static EngineeringGraph graph(String revision, double pressure, boolean addCalculation) {
    EngineeringGraph graph = new EngineeringGraph("PROJECT-A", revision);
    String projectId = EngineeringIds.nodeId(EngineeringNode.Kind.PROJECT, "PROJECT-A");
    String equipmentId = EngineeringIds.nodeId(EngineeringNode.Kind.EQUIPMENT, "20-VG-001");
    graph.addNode(new EngineeringNode(projectId, EngineeringNode.Kind.PROJECT, "PROJECT-A", "Project A"));
    graph.addNode(new EngineeringNode(equipmentId, EngineeringNode.Kind.EQUIPMENT, "20-VG-001", "Separator")
        .putProperty("designPressureBara", Double.valueOf(pressure)));
    if (addCalculation) {
      String calculationId = EngineeringIds.nodeId(EngineeringNode.Kind.CALCULATION, "20-VG-001-DESIGN-PRESSURE");
      graph.addNode(new EngineeringNode(calculationId, EngineeringNode.Kind.CALCULATION, "20-VG-001-DESIGN-PRESSURE",
          "Design pressure"));
      graph.addEdge(new EngineeringEdge(
          EngineeringIds.edgeId(EngineeringEdge.Kind.DEPENDS_ON, calculationId, equipmentId, "input"), calculationId,
          equipmentId, EngineeringEdge.Kind.DEPENDS_ON, "input"));
    }
    return graph;
  }

  private static ModelChangeEvent event(String eventId, String idempotencyKey, String targetRevision) {
    EngineeringGraph older = graph("A", 50.0, false);
    EngineeringGraph newer = graph(targetRevision, 55.0, true);
    return ModelChangeEvent.fromEngineeringGraphDiff(older.compareTo(newer), eventId, idempotencyKey,
        Instant.parse("2026-07-18T08:00:00Z"), "NEQSIM", "PROCESS-TEAM", "ASSET-A", "MODEL-A", "Updated model");
  }

  private static boolean hasSubject(ModelChangeEvent event, String subjectId) {
    for (ModelChangeSubject subject : event.getSubjects()) {
      if (subjectId.equals(subject.getSubjectId())) {
        return true;
      }
    }
    return false;
  }
}
