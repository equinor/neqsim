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

/** Compiles and executes every Java program in the Java getting-started guide. */
public class JavaGettingStartedGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final Pattern JAVA_FENCE =
      Pattern.compile("(?m)^\x60\x60\x60java\\r?\\n([\\s\\S]*?)^\x60\x60\x60[ \\t]*$");
  private static final Pattern PUBLIC_CLASS =
      Pattern.compile("public\\s+(?:final\\s+)?class\\s+([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void guideMatchesReleaseAndCurrentApis() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/java-getting-started.md"));
    String pom = read(repositoryRoot.resolve("pom.xml"));
    String heaterSource =
        read(
            repositoryRoot.resolve(
                "src/main/java/neqsim/process/equipment/heatexchanger/Heater.java"));
    String compressorSource =
        read(
            repositoryRoot.resolve(
                "src/main/java/neqsim/process/equipment/compressor/Compressor.java"));

    assertTrue(pom.contains("<revision>3.20.0</revision>"));
    assertTrue(guide.contains("com.equinor.neqsim:neqsim:3.20.0"));
    assertTrue(guide.contains("neqsim-3.20.0-Java8.jar"));
    assertTrue(guide.contains("Java 17 or newer"));
    assertTrue(guide.contains("Source must remain Java 8 compatible"));
    assertTrue(
        heaterSource.contains("public void setOutletTemperature(double temperature, String unit)"));
    assertTrue(
        compressorSource.contains("public void setOutletPressure(double pressure, String unit)"));

    for (String rejected :
        Arrays.asList(
            "3.6.1",
            "System.out",
            "setOutTemperature(",
            "11+ recommended",
            "Highest accuracy for gas properties",
            "Better liquid density than SRK",
            "ALWAYS set a mixing rule")) {
      assertFalse(guide.contains(rejected), "Rejected getting-started guidance: " + rejected);
    }
  }

  @Test
  void everyJavaProgramCompilesAndRunsWithAssertions() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    String guide = read(repositoryRoot.resolve("docs/java-getting-started.md"));

    Matcher fences = JAVA_FENCE.matcher(guide);
    int programCount = 0;
    while (fences.find()) {
      String source = fences.group(1);
      assertTrue(source.contains("LogManager.getLogger"));
      assertTrue(source.contains("assert "));
      assertFalse(source.contains("System.out"));
      compileAndRun(source);
      programCount++;
    }
    assertEquals(2, programCount, "The guide must expose two complete maintained programs");
  }

  private String read(Path path) throws Exception {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private void compileAndRun(String source) throws Exception {
    Matcher className = PUBLIC_CLASS.matcher(source);
    assertTrue(className.find(), "Each Java fence must contain a complete public class");
    String name = className.group(1);
    assertFalse(className.find(), "Each Java fence must contain one public class");

    Path outputDirectory = temporaryDirectory.resolve(name);
    Files.createDirectories(outputDirectory);
    Path javaSource = outputDirectory.resolve(name + ".java");
    Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    compile(compiler, javaSource, outputDirectory);

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

  /**
   * Compile one extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @param outputDirectory class-output directory
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source, Path outputDirectory) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty(
            "surefire.test.class.path", System.getProperty("java.class.path"));
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
                  manager.getJavaFileObjects(source.toFile()))
              .call();
      assertTrue(
          Boolean.TRUE.equals(successful),
          "docs/java-getting-started.md: " + diagnostics.getDiagnostics());
    }
  }
}
