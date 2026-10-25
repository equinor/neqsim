package neqsim.process.equipment.reactor;

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
import neqsim.NeqSimTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles and executes the exact result-bearing examples published in the plug-flow-reactor guide.
 *
 * @author ESOL
 * @version 1.0
 */
class PlugFlowReactorDocumentationTest extends NeqSimTest {
  private static final Pattern WORKED_EXAMPLE = Pattern.compile(
      "(?s)<!-- pfr-worked-example-(\\d):start -->\\s*```java\\r?\\n(.*?)"
          + "```\\s*<!-- pfr-worked-example-\\1:end -->");

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies all three published reactor examples compile and execute with assertions enabled.
   *
   * @throws Exception if reading, compiling or invoking an example fails
   */
  @Test
  void exactWorkedExamplesCompileAndRun() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/process/equipment/plug_flow_reactor.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);

    Matcher examples = WORKED_EXAMPLE.matcher(guide);
    int exampleCount = 0;
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");

    while (examples.find()) {
      exampleCount++;
      String exampleNumber = examples.group(1);
      String exampleBody = examples.group(2);
      assertEquals(Integer.toString(exampleCount), exampleNumber,
          "Worked examples must remain ordered and contiguous");
      assertFalse(Pattern.compile("System\\.(?:out|err)").matcher(exampleBody).find(),
          "Published examples must use Log4j2 rather than console output");
      assertTrue(exampleBody.contains("Logger logger = LogManager.getLogger("));
      assertTrue(exampleBody.contains("assert Double.isFinite("));

      String className = "PlugFlowReactorWorkedExample" + exampleNumber;
      String source = wrapExample(className, exampleBody);
      Path javaSource = temporaryDirectory.resolve(className + ".java");
      Files.write(javaSource, source.getBytes(StandardCharsets.UTF_8));
      compile(compiler, javaSource);
      execute(className);
    }

    assertEquals(3, exampleCount, "The guide must contain exactly three marked worked examples");
  }

  private String wrapExample(String className, String exampleBody) {
    return "import neqsim.process.equipment.reactor.CatalystBed;\n"
        + "import neqsim.process.equipment.reactor.KineticReaction;\n"
        + "import neqsim.process.equipment.reactor.PlugFlowReactor;\n"
        + "import neqsim.process.equipment.reactor.ReactorAxialProfile;\n"
        + "import neqsim.process.equipment.stream.Stream;\n"
        + "import neqsim.thermo.system.SystemInterface;\n"
        + "import neqsim.thermo.system.SystemSrkEos;\n"
        + "import org.apache.logging.log4j.LogManager;\n"
        + "import org.apache.logging.log4j.Logger;\n\n"
        + "public final class " + className + " {\n"
        + "  private " + className + "() {}\n\n"
        + "  public static void main(String[] args) {\n"
        + exampleBody
        + "  }\n"
        + "}\n";
  }

  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics =
        new DiagnosticCollector<JavaFileObject>();
    String classPath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath",
        classPath, "-d", temporaryDirectory.toString());
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(null, manager, diagnostics, options, null,
                  manager.getJavaFileObjects(source.toFile()))
              .call();
      assertTrue(Boolean.TRUE.equals(successful),
          "docs/process/equipment/plug_flow_reactor.md: " + diagnostics.getDiagnostics());
    }
  }

  private void execute(String className) throws Exception {
    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
            getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> exampleClass = Class.forName(className, true, loader);
      assertTrue(exampleClass.desiredAssertionStatus());
      try {
        exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(className + " failed", exception.getCause());
      }
    }
  }
}
