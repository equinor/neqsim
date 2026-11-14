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
 * Executes the literal Java program published by the compressor mechanical-design guide.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class CompressorMechanicalDesignDocumentationTest {
  private static final Path GUIDE =
      Paths.get("docs", "process", "CompressorMechanicalDesign.md");
  private static final String MARKER =
      "<!-- compressor-mechanical-design-example: sour-service-barrel -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /** Verifies the page contract, compiles the literal Java 8 source, and executes it. */
  @Test
  void casingDesignExampleCompilesAndRuns() throws Exception {
    String source = exampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertFalse(source.contains("printStackTrace"));
    assertFalse(source.contains("catch ("));
    assertTrue(source.contains("LogManager.getLogger(CompressorCasingDesignExample.class)"));
    assertTrue(source.contains("calculator.setDesignPressureBara(200.0)"));
    assertTrue(source.contains("calculator.setMaterialGrade("Inconel-718")"));
    assertTrue(source.contains("CompressorMechanicalDesign.CasingType.BARREL"));
    assertTrue(source.contains("calculator.setSourService(true)"));
    assertTrue(source.contains("calculator.setH2sPartialPressureKPa(5.0)"));
    assertTrue(source.contains("calculator.calculate()"));
    assertTrue(source.contains("Double.isFinite(wallThicknessMm)"));
    assertTrue(source.contains("wallThicknessMm < 12.7"));
    assertTrue(source.contains("mawpBarg < 200.0"));
    assertTrue(source.contains("calculator.isHydroTestAcceptable()"));
    assertTrue(source.contains("calculator.isFlangeRatingAdequate()"));
    assertTrue(source.contains("calculator.getBarrelEndCoverBoltCount() < 12"));
    assertTrue(source.contains(""COMPLIANT".equals(calculator.getNaceComplianceStatus())"));

    try (URLClassLoader loader = compile("CompressorCasingDesignExample", source)) {
      Class<?> example = loader.loadClass("CompressorCasingDesignExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  /**
   * Extracts the uniquely marked Java program and verifies its applicability boundary.
   *
   * @return exact Java source published by the guide
   * @throws IOException if the guide cannot be read
   */
  private String exampleSource() throws IOException {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    assertEquals(markdown.indexOf(MARKER), markdown.lastIndexOf(MARKER),
        "Compressor casing example marker must be unique");
    assertFalse(markdown.contains("System.out.println"));
    assertTrue(markdown.contains("preliminary screening calculation"));
    assertTrue(markdown.contains("does not replace a certified pressure-vessel design"));
    assertTrue(markdown.contains("Treat the hard-coded conditions as example inputs"));

    String markedTail = markdown.substring(markdown.indexOf(MARKER) + MARKER.length());
    Matcher fenceMatcher = JAVA_FENCE.matcher(markedTail);
    assertTrue(fenceMatcher.find(), "Marked compressor casing Java fence is missing");
    String source = fenceMatcher.group(1).trim();
    assertTrue(source.contains("public final class CompressorCasingDesignExample"));
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
    String classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful =
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
                      "-proc:none",
                      "-classpath",
                      classpath,
                      "-d",
                      classes.toString()),
                  null,
                  manager.getJavaFileObjects(javaFile.toFile()))
              .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
