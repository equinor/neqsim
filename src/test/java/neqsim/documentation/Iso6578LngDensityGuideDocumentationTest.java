package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import neqsim.NeqSimTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles and executes the ISO 6578 LNG-density guide. */
public class Iso6578LngDensityGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/standards/iso6578_lng_density.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Executable LNG-density screen.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideStatesInputUnitAndReportingBoundaries() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(guide.startsWith("---\ntitle: ISO 6578 LNG Density Screening\n"));
    assertTrue(guide.contains("93.15–133.15 K (-180 to -140 °C)"));
    assertTrue(guide.contains("16–30 g/mol"));
    assertTrue(guide.contains("Normalized mole fractions"));
    assertTrue(guide.contains("kg/m³, returned by `getValue(\"density\")`"));
    assertTrue(prose.contains("class reads phase 0"));
    assertTrue(prose.contains("does not reject an out-of-range temperature"));
    assertTrue(prose.contains("requested parameter and unit strings are not validated or converted"));
    assertTrue(prose.contains("`isOnSpec()` returns `true` unconditionally"));
    assertTrue(prose.contains("legacy alternative internal table"));
    assertTrue(guide.contains("java -ea"));
    assertFalse(guide.contains("System.out"));
    assertFalse(guide.contains("-195°C to -100°C"));
    assertFalse(guide.contains("Typical uncertainty: ±0.1%"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable ISO 6578 example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class Iso6578LngDensityExample"));
    assertTrue(source.contains("new Standard_ISO6578("));
    assertTrue(source.contains("lng.setMixingRule(\"classic\")"));
    assertTrue(source.contains("lng.init(0)"));
    assertTrue(source.contains("assert \"kg/m^3\".equals(standard.getUnit(\"density\"))"));
    assertTrue(source.contains("assert coldDensityKgPerM3 > referenceDensityKgPerM3"));
    assertTrue(source.contains("assert referenceDensityKgPerM3 > warmDensityKgPerM3"));
    assertTrue(source.contains("LogManager.getLogger"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
  }

  private void compileAndRun(String source) throws Exception {
    Matcher className = PUBLIC_CLASS.matcher(source);
    assertTrue(className.find(), "Java fence must contain a complete public class");
    String name = className.group(1);
    assertFalse(className.find(), "Java fence must contain one public class");

    Path outputDirectory = temporaryDirectory.resolve(name);
    Files.createDirectories(outputDirectory);
    Path javaSource = outputDirectory.resolve(name + ".java");
    Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        outputDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(javaSource.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] {outputDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> example = Class.forName(name, true, loader);
      assertTrue(example.desiredAssertionStatus());
      try {
        example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(name + " failed", exception.getCause());
      }
    }
  }
}
