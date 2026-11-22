package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Executes the literal Java program published by the wax-characterization guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class WaxCharacterizationDocumentationTest {
  private static final Path GUIDE = Paths.get("docs", "thermo", "characterization", "wax_characterization.md");
  private static final String MARKER = "<!-- wax-characterization-example: complete-screen -->";
  private static final String NAMED_PARAFFIN_MARKER = "<!-- wax-characterization-example: named-paraffin -->";
  private static final Pattern JAVA_FENCE = Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /** Verifies the guide contract, compiles the literal Java 8 source, and executes it. */
  @Test
  void completeWaxScreenCompilesAndRuns() throws Exception {
    String source = exampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("printStackTrace"));
    assertFalse(source.contains("catch ("));
    assertTrue(source.contains("LogManager.getLogger(WaxCharacterizationScreeningExample.class)"));
    assertTrue(source.contains("fluid.enableWaxModel(\"Pedersen\")"));
    assertTrue(source.contains("calculator.setEnforceMonotonicity(false)"));
    assertTrue(source.contains("calculator.calculateWAT()"));
    assertTrue(source.contains("calculator.getRawWaxFractions()"));
    assertTrue(source.contains("calculator.getFailureMessages()"));
    assertTrue(source.contains("calculator.getFailCount() != 0"));
    assertTrue(source.contains("calculator.calculateAtMultiplePressures("));
    assertTrue(source.contains("Double.isFinite(waxMassFraction)"));

    try (URLClassLoader loader = compile("WaxCharacterizationScreeningExample", source)) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = loader.loadClass("WaxCharacterizationScreeningExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Compiles and runs the literal named n-paraffin snippet of the guide (#5210) and checks the wax mass fraction it
   * reports.
   *
   * @throws Exception if the guide cannot be read or the snippet cannot be compiled or run
   */
  @Test
  void namedParaffinSnippetCompilesAndRuns() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerIndex = markdown.indexOf(NAMED_PARAFFIN_MARKER);
    assertTrue(markerIndex >= 0, "Named n-paraffin example marker is missing");
    assertEquals(markerIndex, markdown.lastIndexOf(NAMED_PARAFFIN_MARKER), "Named n-paraffin marker must be unique");
    Matcher fenceMatcher = JAVA_FENCE.matcher(markdown.substring(markerIndex + NAMED_PARAFFIN_MARKER.length()));
    assertTrue(fenceMatcher.find(), "Named n-paraffin Java fence is missing");
    String snippet = fenceMatcher.group(1).trim();
    assertTrue(snippet.contains("fluid.getWaxModel().addTBPWax();"));
    assertTrue(snippet.contains("double[] waxMassFraction = wax.getWaxFraction();"));

    String source = "import neqsim.pvtsimulation.simulation.WaxFractionSim;\n"
        + "import neqsim.thermo.system.SystemInterface;\n" + "import neqsim.thermo.system.SystemSrkEos;\n"
        + "public final class NamedParaffinWaxExample {\n" + "  public static double[] run() {\n" + snippet + "\n"
        + "    return waxMassFraction;\n" + "  }\n" + "}\n";
    try (URLClassLoader loader = compile("NamedParaffinWaxExample", source)) {
      double[] waxMassFraction = (double[]) loader.loadClass("NamedParaffinWaxExample").getMethod("run").invoke(null);
      assertEquals(3, waxMassFraction.length);
      for (double fraction : waxMassFraction) {
        assertEquals(0.976, fraction, 5e-4);
      }
    }
  }

  /**
   * Extracts the unique marked Java program and verifies its engineering boundary.
   *
   * @return exact Java source published by the guide
   * @throws IOException if the guide cannot be read
   */
  private String exampleSource() throws IOException {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerIndex = markdown.indexOf(MARKER);
    assertTrue(markerIndex >= 0, "Wax-screening example marker is missing");
    assertEquals(markerIndex, markdown.lastIndexOf(MARKER), "Wax-screening example marker must be unique");
    assertFalse(markdown.contains("System.out.println"));
    assertFalse(markdown.contains("String risk"));

    String prose = markdown.replaceAll("\\s+", " ");
    assertTrue(prose.contains("they do not by themselves assign a deposition or plugging risk class"));
    assertTrue(
        prose.contains("Equilibrium wax amount does not establish a deposition rate, location, or plugging time"));
    assertTrue(prose.contains("laboratory WAT and wax-fraction data"));
    assertTrue(prose.contains("treat any `NaN` as a failed point, not zero wax"));

    String markedTail = markdown.substring(markerIndex + MARKER.length());
    Matcher fenceMatcher = JAVA_FENCE.matcher(markedTail);
    assertTrue(fenceMatcher.find(), "Marked wax-characterization Java fence is missing");
    String source = fenceMatcher.group(1).trim();
    assertTrue(source.contains("public final class WaxCharacterizationScreeningExample"));
    return source;
  }

  /**
   * Compiles one Java 8 documentation source and returns its class loader.
   *
   * @param className public class name
   * @param source exact Java source
   * @return class loader for the compiled source
   * @throws Exception if the source or class output cannot be created
   */
  private URLClassLoader compile(String className, String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(className));
    Path javaFile = classes.resolve(className + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(diagnostics, manager, null, Arrays.asList("-source", "8", "-target", "8", "-proc:none", "-classpath",
              classpath, "-d", classes.toString()), null, manager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
