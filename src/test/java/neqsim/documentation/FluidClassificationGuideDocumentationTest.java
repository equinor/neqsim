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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;

/** Compiles and executes the reservoir-fluid classification guide. */
public class FluidClassificationGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/thermo/fluid_classification.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern.compile(
      "(?ms)^## Executable classification example.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA =
      Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir Path temporaryDirectory;

  @Test
  void guideStatesRoutesUnitsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();

    assertTrue(guide.contains("C7+-screening route"));
    assertTrue(guide.contains("finite, non-negative GOR"));
    assertTrue(guide.contains("temperature in K"));
    assertTrue(guide.contains("pressure in bara"));
    assertTrue(guide.contains("C7+ in mol%"));
    assertTrue(guide.contains("GOR in scf/STB"));
    assertTrue(guide.contains("PVT samples and laboratory CCE, CVD, DLE"));
    assertTrue(guide.contains("not a substitute for measured stock-tank density"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable classification example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("double temperatureK = 373.15"));
    assertTrue(source.contains("double pressureBara = 100.0"));
    assertTrue(source.contains("FluidClassifier.calculateC7PlusContent(fluid)"));
    assertTrue(source.contains("FluidClassifier.classify(fluid)"));
    assertTrue(source.contains("FluidClassifier.classifyByGOR(producingGorScfStb)"));
    assertTrue(source.contains("Math.abs(c7PlusMolPercent - 10.0) < 1.0e-8"));
    assertTrue(source.contains("compositionScreen == ReservoirFluidType.GAS_CONDENSATE"));
    assertTrue(source.contains("gorScreen == ReservoirFluidType.GAS_CONDENSATE"));
    assertTrue(source.contains("report.contains(\"C7+ Content: 10.00 mol%\")"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fence.find(), "Executable section must contain one Java program");
  }

  private String readGuide() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    return new String(
        Files.readAllBytes(repositoryRoot.resolve(GUIDE)), StandardCharsets.UTF_8);
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
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options =
        Arrays.asList(
            "-source",
            "8",
            "-target",
            "8",
            "-classpath",
            classPath,
            "-d",
            outputDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  options,
                  null,
                  manager.getJavaFileObjects(javaSource.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful), diagnostics.getDiagnostics().toString());
    }

    try (URLClassLoader loader =
        new URLClassLoader(
            new URL[] {outputDirectory.toUri().toURL()}, getClass().getClassLoader())) {
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
