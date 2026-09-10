package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Compiles the extension guide's source blocks and runs their documented regressions. */
class ThermodynamicExtensionGuideDocumentationTest {
  private static final Pattern JAVA_BLOCK = Pattern.compile("(?m)^```java\\r?\\n(.*?)^```", Pattern.DOTALL);
  private static final Pattern CLASS_NAME = Pattern.compile("public class ([A-Za-z][A-Za-z0-9_]*)");

  @TempDir
  Path temporaryDirectory;

  @Test
  void completeJavaExamplesCompileAndPassTheirDocumentedTests() throws Exception {
    String markdown = new String(Files.readAllBytes(Paths.get("docs/development/extending_thermodynamic_models.md")),
        StandardCharsets.UTF_8);
    List<File> sources = new ArrayList<File>();
    Set<String> seen = new LinkedHashSet<String>();
    Matcher blocks = JAVA_BLOCK.matcher(markdown);
    while (blocks.find()) {
      String source = blocks.group(1);
      Matcher className = CLASS_NAME.matcher(source);
      assertTrue(className.find(), "Each Java fence must contain a complete source class");
      String name = className.group(1);
      assertTrue(seen.add(name), "Duplicate example source " + name);
      Path file = temporaryDirectory.resolve(name + ".java");
      Files.write(file, source.getBytes(StandardCharsets.UTF_8));
      sources.add(file.toFile());
    }
    Set<String> expected = new LinkedHashSet<String>(Arrays.asList("SystemCustomEos", "PhaseCustomEos",
        "ComponentCustomEos", "SystemModifiedSrkEos", "ModifiedSrkExample", "ExtensionGuideExampleTest"));
    assertEquals(expected, seen, "Every documented class must remain covered");

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Compiling documentation examples requires a JDK");
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      boolean compiled = compiler.getTask(diagnostics, manager, null,
          Arrays.asList("-classpath", classpath, "-d", temporaryDirectory.toString(), "-source", "8", "-target", "8"),
          null, manager.getJavaFileObjectsFromFiles(sources)).call();
      assertTrue(compiled, "Extension guide failed to compile:\n" + diagnostics);
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      Class<?> testClass = loader.loadClass("example.thermo.ExtensionGuideExampleTest");
      Object instance = testClass.getDeclaredConstructor().newInstance();
      Set<String> executed = new LinkedHashSet<String>();
      for (Method method : testClass.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Test.class)) {
          invoke(method, instance);
          executed.add(method.getName());
        }
      }
      assertEquals(new LinkedHashSet<String>(Arrays.asList("inheritedModelPreservesTwoPhaseFlash",
          "temperatureDependentKijMatchesConstantKijAtEachTemperature",
          "cloneRetainsCoefficientsWhenOnlyStateChanges")), executed);
      invoke(loader.loadClass("example.thermo.ModifiedSrkExample").getMethod("main", String[].class), null,
          (Object) new String[0]);
    }
  }

  @Test
  void phaseEnvelopeApiUsedByPythonReturnsPairedPhysicalCoordinates() {
    for (SystemInterface fluid : new SystemInterface[] { new SystemSrkEos(250.0, 1.0), new SystemPrEos(250.0, 1.0) }) {
      fluid.addComponent("methane", 0.8);
      fluid.addComponent("ethane", 0.1);
      fluid.addComponent("propane", 0.1);
      fluid.setMixingRule("classic");
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.calcPTphaseEnvelope();
      for (String branch : Arrays.asList("dew", "bub")) {
        double[] temperatures = operations.get(branch + "T");
        double[] pressures = operations.get(branch + "P");
        assertNotNull(temperatures);
        assertNotNull(pressures);
        assertTrue(temperatures.length > 1, "Envelope must contain enough points to plot");
        assertEquals(temperatures.length, pressures.length);
        int physicalPoints = 0;
        for (int point = 0; point < temperatures.length; point++) {
          if (Double.isNaN(temperatures[point])) {
            assertTrue(Double.isNaN(pressures[point]), "Branch breaks must be paired NaN coordinates");
            continue;
          }
          assertTrue(Double.isFinite(temperatures[point]) && temperatures[point] > 0.0);
          assertTrue(Double.isFinite(pressures[point]) && pressures[point] > 0.0);
          physicalPoints++;
        }
        assertTrue(physicalPoints > 1, "Each envelope branch must contain physical points");
      }
    }
  }

  private static void invoke(Method method, Object receiver, Object... arguments) throws Exception {
    try {
      method.invoke(receiver, arguments);
    } catch (InvocationTargetException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof AssertionError) {
        throw (AssertionError) cause;
      }
      throw new AssertionError("Documented example failed: " + method.getName(), cause);
    }
  }
}
