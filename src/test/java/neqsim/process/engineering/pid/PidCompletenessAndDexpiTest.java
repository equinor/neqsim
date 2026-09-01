package neqsim.process.engineering.pid;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/** Verifies completeness gates and DEXPI proposal materialization. */
public class PidCompletenessAndDexpiTest extends NeqSimTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void completeProposalHasReviewGatesAndMaterializesInDexpi() throws Exception {
    PidDesignModel model = new PidDesignModel("PID-PACKAGE-TEST", "NORSOK-COMPLETE");
    add(model, "PT-101", PidElementType.MEASUREMENT);
    add(model, "PIC-101", PidElementType.CONTROLLER);
    add(model, "PCV-101", PidElementType.CONTROL_VALVE);
    add(model, "PAHH-101", PidElementType.TRIP);
    add(model, "ESDV-101", PidElementType.SHUTDOWN_VALVE);

    PidCompletenessReport report = PidCompletenessValidator.validate(model);
    assertTrue(report.isStructurallyComplete());
    assertFalse(report.isReadyForApproval());
    assertTrue(report.toJson().contains("PID-HAZOP-LOPA-REQUIRED"));

    Path dexpi = temporaryDirectory.resolve("plant-pydexpi.xml");
    Files.write(dexpi, "<?xml version=\"1.0\"?><PlantModel></PlantModel>".getBytes(StandardCharsets.UTF_8));
    PidDexpiMaterializer.materialize(model, dexpi);
    String xml = new String(Files.readAllBytes(dexpi), StandardCharsets.UTF_8);
    assertTrue(xml.contains("NeqSimPidDesignModel"));
    assertTrue(xml.contains("PT-101"));
    assertTrue(xml.contains("pid-completeness-report.json"));
  }

  private static void add(PidDesignModel model, String tag, PidElementType type) {
    model.add(new PidElement("PID-" + tag, tag, type).equipment("20-VG-001").provenance("TEST-RULE",
        "Completeness test proposal"));
  }
}
