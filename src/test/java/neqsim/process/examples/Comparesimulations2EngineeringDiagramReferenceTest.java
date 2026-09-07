package neqsim.process.examples;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.diagram.EngineeringDiagramDualProfileDelivery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("slow")
class Comparesimulations2EngineeringDiagramReferenceTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void rendersDeterministicFullModelPfdAndPidWithoutInventingIllustratedEquipment() throws IOException {
    ProcessSystem firstProcess = Comparesimulations2EngineeringDiagramReference.createExecutedProcess();

    assertTrue(firstProcess.getUnitOperations().size() >= 39);
    assertFalse(firstProcess.getUnitOperations().stream()
        .anyMatch(unit -> "24-VB-01".equals(unit.getName())));

    EngineeringDiagramDualProfileDelivery.Report first =
        Comparesimulations2EngineeringDiagramReference.deliver(
            firstProcess, temporaryDirectory.resolve("first"));

    assertTrue(first.isComplete(), first.toJson());
    assertEquals(first.getPfd().getDocumentSet().getSourceGraphFingerprint(),
        first.getPid().getDocumentSet().getSourceGraphFingerprint());
    assertTrue(first.getPfd().getRendering().getSvgBySheetId().size() >= 3);
    assertTrue(first.getPid().getRendering().getSvgBySheetId().size() >= 3);
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pfd/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/drawing-set.pdf")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("stream-table.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("balance-table.json")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/dexpi-plant-2.0.xml")));
    assertTrue(Files.isRegularFile(first.getDirectory().resolve("pid/proteus-4.1.xml")));
    assertTrue(first.toJson().contains("\"pidApprovalStatus\": \"REVIEW_REQUIRED\""));
    assertTrue(first.toJson().contains("\"fitnessForConstruction\": false"));

    ProcessSystem secondProcess = Comparesimulations2EngineeringDiagramReference.createExecutedProcess();
    EngineeringDiagramDualProfileDelivery.Report second =
        Comparesimulations2EngineeringDiagramReference.deliver(
            secondProcess, temporaryDirectory.resolve("second"));

    assertEquals(first.getFingerprint(), second.getFingerprint());
    assertEquals(first.getPfd().getRendering().getSvgBySheetId(),
        second.getPfd().getRendering().getSvgBySheetId());
    assertEquals(first.getPid().getRendering().getSvgBySheetId(),
        second.getPid().getRendering().getSvgBySheetId());
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pfd/drawing-set.pdf")),
        Files.readAllBytes(second.getDirectory().resolve("pfd/drawing-set.pdf")));
    assertArrayEquals(Files.readAllBytes(first.getDirectory().resolve("pid/drawing-set.pdf")),
        Files.readAllBytes(second.getDirectory().resolve("pid/drawing-set.pdf")));
  }
}
