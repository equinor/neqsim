package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compile and execute the actual guide snippets, including their expected engineering values.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class UnitConversionGuideTest {
  /** Temporary compiler output, removed by JUnit after the test. */
  @TempDir
  Path output;

  /**
   * Verify every Java block in the unit guide without maintaining a duplicate example implementation.
   *
   * @throws Exception if compilation, loading or execution fails
   */
  @Test
  void guideExamplesCompileAndProduceDocumentedValues() throws Exception {
    String guide = new String(Files.readAllBytes(Paths.get("docs/util/unit_conversion.md")), StandardCharsets.UTF_8);
    Matcher blocks = Pattern.compile("```java\\s*\\n(.*?)```", Pattern.DOTALL).matcher(guide);
    String[] checks = {
        "check(101325.0, pressurePa); check(273.15, temperatureK); check(1.0, dropBar);"
            + "check(10.0, riseK); check(-18.0, coolingF);",
        "check(1.0, distanceM); check(1000.0, distanceMm); check(3600000.0, energyJ);"
            + "check(0.745699872, powerKw); check(7200.0, durationS);",
        "check(10.0, molPerSecond); check(0.2, kgPerSecond); check(0.9, cubicMetresPerHour);"
            + "check(5.0, heavierFluidMolPerSecond);",
        "check(25.0, temperatureC); check(50.0, pressureBar); check(1.0, massRate);",
        "if (Units.activeUnits != previous) { throw new AssertionError(\"Display map was not restored\"); }"};
    int index = 0;
    while (blocks.find()) {
      String snippet = blocks.group(1);
      if (index == 4) {
        snippet = snippet.replace("} finally {",
            "if (!\"Pa\".equals(pressureSymbol) || !\"K\".equals(temperatureSymbol))"
                + " { throw new AssertionError(\"Wrong SI display units\"); } } finally {");
      }
      assertEquals(true, index < checks.length, "A new guide example needs independent expected values");
      compileAndRun(snippet, checks[index], index);
      index++;
    }
    assertEquals(checks.length, index, "Every documented Java example must be executed");
  }

  /**
   * Compile one documentation block and run its numerical checks.
   *
   * @param snippet original Java block from the guide
   * @param checks assertions of independent expected values
   * @param index unique example number
   * @throws Exception if source creation, compilation or invocation fails
   */
  private void compileAndRun(String snippet, String checks, int index) throws Exception {
    String className = "UnitGuideExample" + index;
    Matcher imports = Pattern.compile("(?m)^import [^;]+;\\s*").matcher(snippet);
    StringBuilder source = new StringBuilder();
    while (imports.find()) {
      source.append(imports.group());
    }
    source.append("public class ").append(className).append(" { public static void run() {\n")
        .append(imports.replaceAll("")).append(checks).append("\n}")
        .append("private static void check(double expected, double actual) {")
        .append("if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9)")
        .append(" { throw new AssertionError(expected + \" != \" + actual); } } }");
    Path file = output.resolve(className + ".java");
    Files.write(file, source.toString().getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "The documentation examples require a JDK, as does the Maven build");
    StringWriter diagnostics = new StringWriter();
    List<String> options = new ArrayList<>(Arrays.asList("-source", "8", "-target", "8", "-classpath",
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), "-d",
        output.toString()));
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean success = compiler
          .getTask(diagnostics, manager, null, options, null, manager.getJavaFileObjects(file.toFile())).call();
      assertEquals(Boolean.TRUE, success, diagnostics.toString());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] {output.toUri().toURL()}, getClass().getClassLoader())) {
      loader.loadClass(className).getMethod("run").invoke(null);
    }
  }
}
