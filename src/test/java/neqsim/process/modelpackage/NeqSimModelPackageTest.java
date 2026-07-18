package neqsim.process.modelpackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeqSimModelPackageTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void writesReadsAndValidatesDeterministicPackage() throws Exception {
    Files.write(temporaryDirectory.resolve("engineering-model.json"),
        "{\"graph\":true}".getBytes(StandardCharsets.UTF_8));
    Files.write(temporaryDirectory.resolve("calculation.json"), "{\"value\":42}".getBytes(StandardCharsets.UTF_8));
    ModelPackageIdentity identity = new ModelPackageIdentity("ASSET-A", "FACILITY-A", "MODEL-A",
        "ENGINEERING_PROCESS_MODEL", "B", "A", "DESIGN", "PROCESS-TEAM");
    Map<String, String> software = new LinkedHashMap<String, String>();
    software.put("neqsim", "3.16.0");

    Path manifest = NeqSimModelPackage.write(temporaryDirectory, identity, "engineering-model.json", "REVIEW_REQUIRED",
        Collections.singletonList(new ModelDependency("P_AND_ID", "17", "SYNCHRONIZED_FROM", "DEXPI", true)), software);
    String first = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
    NeqSimModelPackage.write(temporaryDirectory, identity, "engineering-model.json", "REVIEW_REQUIRED",
        Collections.singletonList(new ModelDependency("P_AND_ID", "17", "SYNCHRONIZED_FROM", "DEXPI", true)), software);

    assertEquals(first, new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8));
    NeqSimModelPackage reloaded = NeqSimModelPackage.read(manifest);
    assertEquals("MODEL-A", reloaded.getIdentity().getModelId());
    assertEquals(2, reloaded.getArtifacts().size());
    assertTrue(ModelPackageValidator.validate(temporaryDirectory).isValid());
  }

  @Test
  void failsClosedWhenInventoriedContentChanges() throws Exception {
    Path graph = temporaryDirectory.resolve("engineering-model.json");
    Files.write(graph, "revision-a".getBytes(StandardCharsets.UTF_8));
    ModelPackageIdentity identity = new ModelPackageIdentity("ASSET-A", "", "MODEL-A", "PROCESS", "A", "", "DESIGN",
        "");
    NeqSimModelPackage.write(temporaryDirectory, identity, "engineering-model.json", "REVIEW_REQUIRED",
        Collections.<ModelDependency>emptyList(), Collections.<String, String>emptyMap());

    Files.write(graph, "revision-b".getBytes(StandardCharsets.UTF_8));
    ModelPackageValidator.Result result = ModelPackageValidator.validate(temporaryDirectory);

    assertFalse(result.isValid());
    assertTrue(result.getFindings().toString().contains("mismatch"));
    assertEquals(Boolean.FALSE, result.toMap().get("fitnessForConstruction"));
  }

  @Test
  void failsClosedWhenAnArtifactIsAddedAfterPackaging() throws Exception {
    Files.write(temporaryDirectory.resolve("engineering-model.json"), "revision-a".getBytes(StandardCharsets.UTF_8));
    ModelPackageIdentity identity = new ModelPackageIdentity("ASSET-A", "", "MODEL-A", "PROCESS", "A", "", "DESIGN",
        "");
    NeqSimModelPackage.write(temporaryDirectory, identity, "engineering-model.json", "REVIEW_REQUIRED",
        Collections.<ModelDependency>emptyList(), Collections.<String, String>emptyMap());

    Files.write(temporaryDirectory.resolve("untracked.json"), "{}".getBytes(StandardCharsets.UTF_8));
    ModelPackageValidator.Result result = ModelPackageValidator.validate(temporaryDirectory);

    assertFalse(result.isValid());
    assertTrue(result.getFindings().toString().contains("Uninventoried artifact untracked.json"));
  }
}
