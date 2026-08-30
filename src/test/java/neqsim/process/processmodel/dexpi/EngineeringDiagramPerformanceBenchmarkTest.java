package neqsim.process.processmodel.dexpi;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet;
import neqsim.process.engineering.model.EngineeringDiagramDocumentSet.ContentProfile;
import neqsim.process.processmodel.ProcessConnection;
import neqsim.process.processmodel.diagram.EngineeringDiagramReferenceFixtures;
import neqsim.process.processmodel.diagram.NativeEngineeringDiagramRenderer;
import neqsim.process.processmodel.diagram.ProcessDiagramDocumentSetAdapter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Profiles the representative multi-area engineering-diagram delivery path.
 *
 * <p>
 * This assertion-free benchmark writes {@code target/engineering-diagram-performance.json}. The separate validator
 * applies repository-controlled budgets so timing policy remains reviewable and this test remains a measurement
 * surface. Run explicitly with:
 * </p>
 *
 * <pre>
 * ./mvnw -B -ntp -Dtest=EngineeringDiagramPerformanceBenchmarkTest \
 *   -Dgroups=benchmark -DexcludedTestGroups= -Djacoco.skip=true test
 * </pre>
 */
@Tag("benchmark")
public class EngineeringDiagramPerformanceBenchmarkTest {
  private static final String SCHEMA_VERSION = "neqsim_engineering_diagram_performance.v1";
  private static final String REFERENCE_CASE = "DEXPI-REF-MULTI-AREA";
  private static final int WARMUP_RUNS = 1;
  private static final int SAMPLE_RUNS = 3;

  @TempDir
  Path temporaryDirectory;

  /**
   * Measures export, intake, controlled rendering and revision-impact projections.
   *
   * @throws Exception if the reference workflow or report write fails
   */
  @Test
  public void profileRepresentativeDeliveryPath() throws Exception {
    final EngineeringDiagramReferenceFixtures.ModelCase fixture = referenceFixture();
    final File baselinePackage = temporaryDirectory.resolve("baseline.zip").toFile();
    final File revisedPackage = temporaryDirectory.resolve("revised.zip").toFile();
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), baselinePackage, REFERENCE_CASE, "A");
    Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), revisedPackage, REFERENCE_CASE, "B");
    final Dexpi20ProcessModelPackageReader.Snapshot baseline = Dexpi20ProcessModelPackageReader.read(baselinePackage);
    final Dexpi20ProcessModelPackageReader.Snapshot revised = Dexpi20ProcessModelPackageReader.read(revisedPackage);
    final EngineeringDiagramDocumentSet baselineDocument = document(fixture, "A");
    final EngineeringDiagramDocumentSet revisedDocument = document(fixture, "B");
    final Dexpi20ProcessModelPackageRevisionImpact revisionImpact = Dexpi20ProcessModelPackageRevisionImpact
        .compare(baseline, revised);

    List<Map<String, Object>> operations = new ArrayList<Map<String, Object>>();
    operations.add(measure("dexpi-package-export", new MeasuredOperation() {
      private int sequence;

      @Override
      public String run() throws Exception {
        File output = temporaryDirectory.resolve("export-" + sequence++ + ".zip").toFile();
        return Dexpi20ProcessModelPackageWriter.writeAndAssess(fixture.getProcessModel(), output, REFERENCE_CASE, "A")
            .getPackageFileSha256();
      }
    }));
    operations.add(measure("dexpi-package-intake", new MeasuredOperation() {
      @Override
      public String run() throws Exception {
        return Dexpi20ProcessModelPackageReader.read(baselinePackage).getPackageFileSha256();
      }
    }));
    operations.add(measure("native-svg-pdf-render", new MeasuredOperation() {
      @Override
      public String run() {
        NativeEngineeringDiagramRenderer.Result result = new NativeEngineeringDiagramRenderer(baselineDocument)
            .render();
        return result.getVisualFingerprintsBySheetId().toString() + ":pdf-bytes=" + result.getPdf().length;
      }
    }));
    operations.add(measure("package-revision-impact", new MeasuredOperation() {
      @Override
      public String run() {
        return Dexpi20ProcessModelPackageRevisionImpact.compare(baseline, revised).toJson();
      }
    }));
    operations.add(measure("package-document-impact", new MeasuredOperation() {
      @Override
      public String run() {
        return Dexpi20ProcessModelPackageDocumentImpact.project(revisionImpact, baselineDocument, revisedDocument)
            .toJson();
      }
    }));

    Map<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("schemaVersion", SCHEMA_VERSION);
    report.put("referenceCase", REFERENCE_CASE);
    report.put("warmupRuns", Integer.valueOf(WARMUP_RUNS));
    report.put("sampleRuns", Integer.valueOf(SAMPLE_RUNS));
    report.put("measurement", "wall-clock milliseconds; medians are compared with conservative CI budgets");
    report.put("operations", operations);
    report.put("engineeringStatus", "PERFORMANCE_REGRESSION_EVIDENCE_ONLY");
    report.put("approvalStatus", "REVIEW_REQUIRED");
    report.put("fitnessForConstruction", Boolean.FALSE);

    Path output = Paths.get("target", "engineering-diagram-performance.json");
    Files.createDirectories(output.getParent());
    Files.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, Object> measure(String name, MeasuredOperation operation) throws Exception {
    for (int warmup = 0; warmup < WARMUP_RUNS; warmup++) {
      operation.run();
    }
    List<Double> samples = new ArrayList<Double>();
    String expectedFingerprint = null;
    boolean deterministic = true;
    for (int sample = 0; sample < SAMPLE_RUNS; sample++) {
      long start = System.nanoTime();
      String output = operation.run();
      samples.add(Double.valueOf((System.nanoTime() - start) / 1.0e6));
      String fingerprint = sha256(output);
      if (expectedFingerprint == null) {
        expectedFingerprint = fingerprint;
      } else if (!expectedFingerprint.equals(fingerprint)) {
        deterministic = false;
      }
    }
    List<Double> ordered = new ArrayList<Double>(samples);
    Collections.sort(ordered);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("name", name);
    result.put("unit", "milliseconds");
    result.put("samples", samples);
    result.put("median", ordered.get(ordered.size() / 2));
    result.put("maximum", ordered.get(ordered.size() - 1));
    result.put("deterministic", Boolean.valueOf(deterministic));
    result.put("outputFingerprint", expectedFingerprint);
    return result;
  }

  private static EngineeringDiagramReferenceFixtures.ModelCase referenceFixture() {
    EngineeringDiagramReferenceFixtures.ModelCase fixture = EngineeringDiagramReferenceFixtures.multiAreaFacility();
    fixture.getProcessModel().get("Inlet").connect("30-XV-001", "energyOut", "30-VA-001", "energyIn",
        ProcessConnection.ConnectionType.ENERGY);
    fixture.getProcessModel().get("Inlet").connect("30-VA-001", "signalOut", "30-SP-001", "signalIn",
        ProcessConnection.ConnectionType.SIGNAL);
    return fixture;
  }

  private static EngineeringDiagramDocumentSet document(EngineeringDiagramReferenceFixtures.ModelCase fixture,
      String revision) {
    return ProcessDiagramDocumentSetAdapter.fromProcessModel(fixture.getProcessModel(), REFERENCE_CASE, revision,
        "PFD-PERF-001", "Engineering diagram performance reference", ContentProfile.PID);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) {
        result.append(String.format("%02x", item & 0xff));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private interface MeasuredOperation {
    String run() throws Exception;
  }
}
