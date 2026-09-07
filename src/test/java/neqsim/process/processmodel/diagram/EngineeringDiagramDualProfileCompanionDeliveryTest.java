package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.Direction;
import neqsim.process.engineering.model.EngineeringDiagramBalanceTable.EvidenceState;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineeringDiagramDualProfileCompanionDeliveryTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void publishesOperatingCaseCompanionsAndDistinctPidExchanges() throws IOException {
    EngineeringDiagramReferenceFixtures.SystemCase reference = completeBoundaryCase();
    ProcessSystem process = reference.getProcessSystem();
    String classicDot = process.toDOT();

    EngineeringDiagramDualProfileDelivery.Report report = EngineeringDiagramDualProfileDelivery.deliver(process,
        temporaryDirectory.resolve("complete"), request(reference));

    assertTrue(report.isComplete(), report.toJson());
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("stream-table.json")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("balance-table.json")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("pid/dexpi-plant-2.0.xml")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("pid/dexpi-plant-assessment.json")));
    assertTrue(Files.isRegularFile(report.getDirectory().resolve("pid/proteus-4.1.xml")));
    assertTrue(report.toJson().contains("\"pidNativeDexpiInformationModel\": \"DEXPI_2_0_PLANT_P_ID\""));
    assertTrue(report.toJson().contains("\"pidProteusCompatibilityProfile\": \"PROTEUS_4_1\""));
    assertTrue(report.toJson().contains("\"operatingCaseId\": \"NORMAL-01\""));
    assertTrue(report.toJson().contains("\"balanceBoundaryEvidenceState\": \"PROPOSED\""));
    assertTrue(report.toJson().contains("\"pidApprovalStatus\": \"REVIEW_REQUIRED\""));
    assertFalse(report.toJson().contains("\"fitnessForConstruction\": true"));
    assertEquals(classicDot, process.toDOT());
  }

  @Test
  void isDeterministicAcrossFreshExecutedPlants() throws IOException {
    EngineeringDiagramReferenceFixtures.SystemCase firstReference = completeBoundaryCase();
    EngineeringDiagramReferenceFixtures.SystemCase secondReference = completeBoundaryCase();

    EngineeringDiagramDualProfileDelivery.Report first = EngineeringDiagramDualProfileDelivery
        .deliver(firstReference.getProcessSystem(), temporaryDirectory.resolve("first"), request(firstReference));
    EngineeringDiagramDualProfileDelivery.Report second = EngineeringDiagramDualProfileDelivery
        .deliver(secondReference.getProcessSystem(), temporaryDirectory.resolve("second"), request(secondReference));

    assertEquals(first.toJson(), second.toJson());
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("stream-table.json")),
        Files.readAllBytes(second.getDirectory().resolve("stream-table.json")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("balance-table.json")),
        Files.readAllBytes(second.getDirectory().resolve("balance-table.json")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/dexpi-plant-2.0.xml")),
        Files.readAllBytes(second.getDirectory().resolve("pid/dexpi-plant-2.0.xml")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/proteus-4.1.xml")),
        Files.readAllBytes(second.getDirectory().resolve("pid/proteus-4.1.xml")));
  }

  @Test
  void failsClosedForMissingOperatingEvidenceOrUnknownBoundary() {
    EngineeringDiagramReferenceFixtures.SystemCase unexecuted = EngineeringDiagramReferenceFixtures.simpleTrain();
    EngineeringDiagramDualProfileDelivery.Request missingCase = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").operatingCaseId("NORMAL-01")
        .balanceBoundaries(Arrays.asList(new EngineeringDiagramDualProfileDelivery.BalanceBoundary("BAL-PLANT-10",
            unexecuted.getFeed().getName(), Direction.INLET, "project-balance-register:test", EvidenceState.PROPOSED)))
        .build();

    assertThrows(IOException.class, () -> EngineeringDiagramDualProfileDelivery.deliver(unexecuted.getProcessSystem(),
        temporaryDirectory.resolve("missing-case"), missingCase));
    assertFalse(Files.exists(temporaryDirectory.resolve("missing-case")));

    EngineeringDiagramReferenceFixtures.SystemCase executed = completeBoundaryCase();
    EngineeringDiagramDualProfileDelivery.Request unknownBoundary = EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").operatingCaseId("NORMAL-01")
        .balanceBoundaries(Arrays.asList(new EngineeringDiagramDualProfileDelivery.BalanceBoundary("BAL-PLANT-10",
            "ABSENT-STREAM", Direction.INLET, "project-balance-register:test", EvidenceState.PROPOSED)))
        .build();
    assertThrows(IOException.class, () -> EngineeringDiagramDualProfileDelivery.deliver(executed.getProcessSystem(),
        temporaryDirectory.resolve("unknown-boundary"), unknownBoundary));
    assertFalse(Files.exists(temporaryDirectory.resolve("unknown-boundary")));
  }

  @Test
  void companionRequestRequiresAnOperatingCaseAndBoundaries() {
    assertThrows(IllegalArgumentException.class,
        () -> EngineeringDiagramDualProfileDelivery.Request
            .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression")
            .balanceBoundaries(Arrays.asList(new EngineeringDiagramDualProfileDelivery.BalanceBoundary("BAL-PLANT-10",
                "10-FEED-001", Direction.INLET, "project-balance-register:test", EvidenceState.PROPOSED)))
            .build());
    assertThrows(IllegalArgumentException.class, () -> EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").operatingCaseId("NORMAL-01")
        .balanceBoundaries(new ArrayList<EngineeringDiagramDualProfileDelivery.BalanceBoundary>()).build());
  }

  private static EngineeringDiagramReferenceFixtures.SystemCase completeBoundaryCase() {
    EngineeringDiagramReferenceFixtures.SystemCase reference = EngineeringDiagramReferenceFixtures.simpleTrain();
    for (StreamInterface product : reference.getProducts()) {
      reference.getProcessSystem().add(product);
    }
    reference.getProcessSystem().run();
    return reference;
  }

  private static EngineeringDiagramDualProfileDelivery.Request request(
      EngineeringDiagramReferenceFixtures.SystemCase reference) {
    List<EngineeringDiagramDualProfileDelivery.BalanceBoundary> boundaries = new ArrayList<EngineeringDiagramDualProfileDelivery.BalanceBoundary>();
    boundaries.add(new EngineeringDiagramDualProfileDelivery.BalanceBoundary("BAL-PLANT-10",
        reference.getFeed().getName(), Direction.INLET, "project-balance-register:test", EvidenceState.PROPOSED));
    for (StreamInterface product : reference.getProducts()) {
      boundaries.add(new EngineeringDiagramDualProfileDelivery.BalanceBoundary("BAL-PLANT-10", product.getName(),
          Direction.OUTLET, "project-balance-register:test", EvidenceState.PROPOSED));
    }
    return EngineeringDiagramDualProfileDelivery.Request
        .builder("PLANT-10", "A", "PFD-10-001", "PID-10-001", "Separation and compression").operatingCaseId("NORMAL-01")
        .balanceBoundaries(boundaries).build();
  }
}
