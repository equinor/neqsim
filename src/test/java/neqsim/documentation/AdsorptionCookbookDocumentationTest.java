package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

/** Executable contracts for the adsorption cookbook programs. */
class AdsorptionCookbookDocumentationTest {
  private static final String GUIDE = "docs/cookbook/adsorption-recipes.md";

  private String guide() throws Exception {
    return new String(Files.readAllBytes(Paths.get(GUIDE)), StandardCharsets.UTF_8);
  }

  private String markedSource(String guide, String marker) {
    assertEquals(guide.indexOf(marker), guide.lastIndexOf(marker), "Marker must be unique: " + marker);
    assertTrue(guide.indexOf(marker) >= 0, "Missing marker: " + marker);
    Matcher fence = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL)
        .matcher(guide.substring(guide.indexOf(marker) + marker.length()));
    assertTrue(fence.find(), "Missing Java fence after " + marker);
    return fence.group(1);
  }

  private void compileAndRun(String marker, String className) throws Exception {
    String source = markedSource(guide(), marker);
    java.nio.file.Path directory = Files.createTempDirectory("adsorption-cookbook-");
    java.nio.file.Path file = directory.resolve(className + ".java");
    Files.write(file, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "A JDK is required for the literal documentation examples");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      assertTrue(
          compiler
              .getTask(
                  diagnostics,
                  manager,
                  null,
                  Arrays.asList(
                      "-source",
                      "8",
                      "-target",
                      "8",
                      "-classpath",
                      System.getProperty("java.class.path"),
                      "-d",
                      directory.toString()),
                  null,
                  manager.getJavaFileObjects(file.toFile()))
              .call(),
          diagnostics.toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      loader
          .loadClass(className)
          .getMethod("main", String[].class)
          .invoke(null, (Object) new String[0]);
    }
  }

  @Test
  void guideOwnsThreeExecutablePrograms() throws Exception {
    String guide = guide();
    assertFalse(Pattern.compile("(?m)^#\\s+").matcher(guide).find());
    assertEquals(3, guide.split("```java", -1).length - 1);
    assertTrue(
        guide.contains(
            "[`AdsorptionCookbookDocumentationTest`](../../src/test/java/neqsim/documentation/AdsorptionCookbookDocumentationTest.java)"));
    assertTrue(guide.contains("./mvnw -Dtest=AdsorptionCookbookDocumentationTest test"));
  }

  @Test
  void competitiveEquilibriumProgramCompilesAndRuns() throws Exception {
    compileAndRun(
        "<!-- adsorption-example: competitive-equilibrium -->", "CompetitiveAdsorptionScreen");
  }

  @Test
  void bedScreenProgramCompilesAndRuns() throws Exception {
    compileAndRun("<!-- adsorption-example: bed-screen -->", "AdsorptionBedScreen");
  }

  @Test
  void cycleScheduleProgramCompilesAndRuns() throws Exception {
    compileAndRun("<!-- adsorption-example: cycle-schedule -->", "AdsorptionCycleSchedule");
  }
}
