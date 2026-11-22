package neqsim.thermo;

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
 * Compiles and executes the exact Huron-Vidal/NRTL program published in the mixing-rule guide.
 *
 * @author ESOL
 * @version 1.0
 */
class HuronVidalMixingRulesDocumentationTest extends NeqSimTest {
  private static final Pattern HV_EXAMPLE = Pattern
      .compile("(?s)<!-- hv-nrtl-example:start -->\\s*```java\\r?\\n(.*?)" + "```\\s*<!-- hv-nrtl-example:end -->");

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies the published program compiles and executes with its assertions enabled.
   *
   * @throws Exception if reading, compiling or invoking the program fails
   */
  @Test
  void exactPublishedProgramCompilesAndRuns() throws Exception {
    Path repositoryRoot = Paths.get(System.getProperty("basedir", ".")).toAbsolutePath();
    Path guidePath = repositoryRoot.resolve("docs/thermo/mixing_rules_guide.md");
    String guide = new String(Files.readAllBytes(guidePath), StandardCharsets.UTF_8);

    Matcher example = HV_EXAMPLE.matcher(guide);
    assertTrue(example.find(), "The guide must contain the marked HV/NRTL Java program");
    String exampleSource = example.group(1);
    assertFalse(example.find(), "The guide must contain exactly one marked HV/NRTL program");
    for (String required : Arrays.asList("public final class HuronVidalNrtlExample",
        "fluid.setMixingRule(\"HV\", \"NRTL\")", "getHVDijParameter(0, 1)", "getHVDijTParameter(0, 1)",
        "double tau = dijK / temperatureK + dijT", "new ThermodynamicOperations(fluid).TPflash()",
        "assert Math.abs(recoveredMoles - feedMoles)")) {
      assertTrue(exampleSource.contains(required), required);
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Documentation examples require a JDK compiler");
    Path javaSource = temporaryDirectory.resolve("HuronVidalNrtlExample.java");
    Files.write(javaSource, exampleSource.getBytes(StandardCharsets.UTF_8));
    compile(compiler, javaSource);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {temporaryDirectory.toUri().toURL()},
        getClass().getClassLoader())) {
      loader.setDefaultAssertionStatus(true);
      Class<?> exampleClass = Class.forName("HuronVidalNrtlExample", true, loader);
      assertTrue(exampleClass.desiredAssertionStatus());
      try {
        exampleClass.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
      } catch (InvocationTargetException exception) {
        throw new AssertionError("HuronVidalNrtlExample failed", exception.getCause());
      }
    }
  }

  /**
   * Compile the extracted Markdown program with Java 8 syntax and the test classpath.
   *
   * @param compiler current JDK compiler
   * @param source extracted Java source
   * @throws Exception if source preparation or compilation fails
   */
  private void compile(JavaCompiler compiler, Path source) throws Exception {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Iterable<String> options = Arrays.asList("-source", "8", "-target", "8", "-classpath", classPath, "-d",
        temporaryDirectory.toString());
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Boolean successful = compiler
          .getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(source.toFile())).call();
      assertTrue(Boolean.TRUE.equals(successful), "docs/thermo/mixing_rules_guide.md: " + diagnostics.getDiagnostics());
    }
  }
}
