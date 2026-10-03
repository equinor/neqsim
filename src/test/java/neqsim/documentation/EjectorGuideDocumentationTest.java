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

/** Compiles and executes the ejector equipment guide. */
public class EjectorGuideDocumentationTest extends NeqSimTest {
  private static final String GUIDE = "docs/process/equipment/ejectors.md";
  private static final Pattern EXECUTABLE_JAVA = Pattern
      .compile("(?ms)^## Executable gas-ejector example.*?^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern ALL_JAVA = Pattern.compile("(?ms)^```java\\r?\\n([\\s\\S]*?)^```[ \\t]*$");
  private static final Pattern PUBLIC_CLASS = Pattern
      .compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideStatesUnitsSemanticsAndEngineeringBoundaries() throws Exception {
    String guide = readGuide();
    String prose = guide.replaceAll("\\s+", " ");

    assertTrue(prose.contains("public `Ejector` API does not expose a CPM/CAM model selector"));
    assertTrue(guide.contains("absolute pressure in bara"));
    assertTrue(guide.contains("mass flow in `kg/sec`"));
    assertTrue(prose.contains("entrainment ratio is the identity"));
    assertTrue(prose.contains("does not solve the suction capacity available at each back pressure"));
    assertTrue(prose.contains("Failed pressure points are logged and omitted"));
    assertTrue(prose.contains("not a fabrication drawing"));
    assertTrue(prose.contains("Validate it against vendor or experimental curves"));
    assertTrue(prose.contains("Final selection requires vendor confirmation"));
    assertTrue(guide.contains("java -ea"));
    assertFalse(guide.contains("System.out"));

    Matcher fences = ALL_JAVA.matcher(guide);
    assertTrue(fences.find(), "Guide must publish one Java program");
    assertFalse(fences.find(), "Guide must not contain unverified Java fragments");
  }

  @Test
  void publishedProgramCompilesAndRunsWithAssertions() throws Exception {
    Matcher fence = EXECUTABLE_JAVA.matcher(readGuide());

    assertTrue(fence.find(), "Executable ejector example is missing");
    String source = fence.group(1);
    assertTrue(source.contains("class EjectorProcessExample"));
    assertTrue(source.contains("new SystemSrkEos(293.15, 10.0)"));
    assertTrue(source.contains("new SystemSrkEos(293.15, 1.0)"));
    assertTrue(source.contains("new Ejector(\"gas booster\", motive, suction)"));
    assertTrue(source.contains("ejector.setDischargePressure(3.0)"));
    assertTrue(source.contains("ProcessSystem process = new ProcessSystem()"));
    assertTrue(source.contains("process.run()"));
    assertTrue(source.contains("ejector.getMassBalance(\"kg/sec\")"));
    assertTrue(source.contains("assert Math.abs(entrainmentRatio - 0.30)"));
    assertTrue(source.contains("getMotiveNozzleThroatArea() > 0.0"));
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
