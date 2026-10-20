package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executes the supported-model guide's actual Java example.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProducedWaterDegassingDocumentationTest {
  /** Temporary compilation output, isolated from the source tree. */
  @TempDir
  Path temporaryDirectory;

  /**
   * Compiles and runs the guide's code fence rather than a separately maintained copy.
   *
   * @throws Exception if reading, compilation, loading, or execution fails
   */
  @Test
  void supportedGuideExampleCompilesAndRuns() throws Exception {
    Path guide = Paths.get("docs/process/equipment/util/produced_water_degassing.md");
    String markdown = new String(Files.readAllBytes(guide), StandardCharsets.UTF_8);
    int start = markdown.indexOf("```java\n") + "```java\n".length();
    assertTrue(start >= "```java\n".length(), "Guide must contain the executable example");
    int end = markdown.indexOf("```", start);
    assertTrue(end > start);
    assertEquals(-1, markdown.indexOf("```java\n", end), "Every Java fence must be executed");
    String source = markdown.substring(start, end);
    Path javaFile = temporaryDirectory.resolve("ProducedWaterDegassingExample.java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation validation requires a JDK");
    assertEquals(0, compiler.run(null, null, null, "-source", "8", "-target", "8", "-classpath",
        System.getProperty("java.class.path"), "-d", temporaryDirectory.toString(), javaFile.toString()));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      Class<?> example = Class.forName("ProducedWaterDegassingExample", true, loader);
      ProducedWaterDegassingSystem model = (ProducedWaterDegassingSystem) example.getMethod("runExample").invoke(null);
      assertTrue(Double.isFinite(model.getTotalCO2EmissionRate("kg/hr")));
      assertTrue(model.getTotalCO2EmissionRate("kg/hr") >= 0.0);
      assertTrue(Double.isFinite(model.getTotalMethaneEmissionRate("kg/hr")));
      assertNotNull(model.getValidationResults().get("GWR"));
      @SuppressWarnings("unchecked")
      Map<String, Object> conditions = (Map<String, Object>) model.toMap().get("processConditions");
      assertEquals(Boolean.FALSE, conditions.get("useTunedKijParameters"));
      assertFalse(model.getMethodComparisonReport().contains("±3.6%"));
      assertTrue(model.getMethodComparisonReport().contains("case-specific"));
      double co2 = model.getTotalCO2EmissionRate("kg/hr");
      // Rejection after execution preserves both calculation state and prior results.
      org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
          () -> model.setTunedInteractionParameters(true));
      assertEquals(co2, model.getTotalCO2EmissionRate("kg/hr"), 0.0);
      // A historical true flag must not relabel already cached default-model results as tuned.
      Field legacyFlag = ProducedWaterDegassingSystem.class.getDeclaredField("useTunedKijParameters");
      legacyFlag.setAccessible(true);
      legacyFlag.setBoolean(model, true);
      org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, model::getEmissionsReport);
      org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, model::toMap);
    }
  }
}
