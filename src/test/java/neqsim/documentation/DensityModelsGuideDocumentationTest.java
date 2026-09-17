package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles and executes the maintained density-model workflow from the user guide. */
public class DensityModelsGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesCurrentDensityApisAndBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/physical_properties/density_models.md"));
    String systemInterface = read(repositoryRoot.resolve("src/main/java/neqsim/thermo/system/SystemInterface.java"));
    String physicalProperties = read(
        repositoryRoot.resolve("src/main/java/neqsim/physicalproperties/system/PhysicalProperties.java"));
    String costaldTest = read(repositoryRoot.resolve(
        "src/test/java/neqsim/physicalproperties/methods/liquidphysicalproperties/" + "density/CostaldTest.java"));

    assertTrue(systemInterface.contains("setLiquidDensityModel(String densityModel)"));
    assertTrue(systemInterface.contains("getDensityAtReferenceConditions(double referenceTemperature"));
    assertTrue(physicalProperties.contains("setDensityModel(String densityModel)"));
    assertTrue(costaldTest.contains("setLiquidDensityModel(\"COSTALD\")"));

    for (String required : Arrays.asList("\"Peneloux\"", "\"COSTALD\"", "\"NASTALD\"", "\"Rackett\"",
        "Do not tune `setVolumeCorrectionConst(...)` from a single density point",
        "validate against independent data")) {
      assertTrue(guide.contains(required), "Missing maintained density guidance: " + required);
    }

    for (String rejected : Arrays.asList("System.out", "### Tuning Liquid Density", "### High-Pressure Density",
        "Adjust volume correction to match experimental", "Consider using PC-SAFT or adjusting correction")) {
      assertFalse(guide.contains(rejected), "Rejected density guidance: " + rejected);
    }
  }

  @Test
  void publishedUsageProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/physical_properties/density_models.md"));
    int start = guide.indexOf("## Usage Example");
    int end = guide.indexOf("## Model Selection Guide", start);
    assertTrue(start >= 0 && end > start, "Maintained usage-example section is missing");

    Matcher fences = JAVA_FENCE.matcher(guide.substring(start, end));
    int programCount = 0;
    while (fences.find()) {
      String source = fences.group(1);
      assertTrue(source.contains("LogManager.getLogger"));
      assertTrue(source.contains("assert "));
      assertTrue(source.contains("getDensityAtReferenceConditions"));
      assertFalse(source.contains("System.out"));
      compileAndRun(source);
      programCount++;
    }
    assertEquals(1, programCount, "The usage section must expose one complete maintained program");
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
      assertTrue(Boolean.TRUE.equals(successful),
          "docs/physical_properties/density_models.md: " + diagnostics.getDiagnostics());
    }

    try (URLClassLoader loader = new URLClassLoader(new URL[] { outputDirectory.toUri().toURL() },
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
