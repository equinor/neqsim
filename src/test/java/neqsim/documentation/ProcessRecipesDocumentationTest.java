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
import neqsim.NeqSimTest;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;

/** Compiles and executes the maintained process-cookbook reference example. */
public class ProcessRecipesDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/cookbook/process-recipes.md";
  private static final Pattern JAVA_FENCE = Pattern.compile("(?m)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern CODE_FENCE = Pattern.compile("(?m)^```[^\\r\\n]*\\r?\\n[\\s\\S]*?^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");
  private static final Pattern DUPLICATE_H1 = Pattern.compile("(?m)^# ");
  private static final Pattern UNITLESS_PRESSURE = Pattern.compile("setOutletPressure\\(\\s*80\\.0\\s*\\)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesCurrentApisAndLearningBoundaries() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(GUIDE));
    String body = guide.split("---", 3)[2];
    String bodyWithoutFences = CODE_FENCE.matcher(body).replaceAll("");
    String normalizedGuide = guide.replaceAll("\\s+", " ");

    assertTrue(guide.startsWith("---\ntitle:"));
    assertFalse(DUPLICATE_H1.matcher(bodyWithoutFences).find());
    assertTrue(normalizedGuide.contains("Python sections are ordered session fragments"));
    assertTrue(normalizedGuide.contains("They are not independent programs"));
    assertTrue(normalizedGuide.contains("not vendor selection, mechanical design"));
    assertTrue(normalizedGuide.contains(
        "validate the thermodynamic model, binary interaction parameters, compressor chart"));
    assertFalse(guide.contains("setOutTemperature("));
    assertFalse(UNITLESS_PRESSURE.matcher(guide).find());

    assertNotNull(Compressor.class.getMethod("setOutletPressure", double.class, String.class));
    assertNotNull(Compressor.class.getMethod("setIsentropicEfficiency", double.class));
    assertNotNull(Cooler.class.getMethod("setOutletTemperature", double.class, String.class));
    assertEquals(StreamInterface.class, Separator.class.getMethod("getGasOutStream").getReturnType());
    assertEquals(StreamInterface.class, Separator.class.getMethod("getLiquidOutStream").getReturnType());

    String cookbookIndex = read(repositoryRoot.resolve("docs/cookbook/index.md"));
    assertTrue(cookbookIndex.contains("process-recipes.md#executable-reference-process"));
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve(GUIDE));
    Matcher fences = JAVA_FENCE.matcher(guide);

    assertTrue(fences.find(), "Guide must contain one complete Java program");
    String source = fences.group(1);
    assertTrue(source.contains("LogManager.getLogger"));
    assertTrue(source.contains("assert massBalanceError < 1.0e-6"));
    assertTrue(source.contains("assert Double.isFinite(powerKW) && powerKW > 0.0"));
    assertTrue(source.contains("assert Math.abs(dischargePressure - 80.0) < 1.0e-6"));
    assertTrue(source.contains("assert Math.abs(cooledTemperature - 40.0) < 1.0e-6"));
    assertFalse(source.contains("System.out"));
    compileAndRun(source);
    assertFalse(fences.find(), "Guide must contain exactly one Java program");
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
