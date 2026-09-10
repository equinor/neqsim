package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
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
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.NeqSimTest;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.processmodel.ProcessSystem;

/** Compiles and executes the exact Java blocks published in the practical optimization guide. */
class PracticalOptimizationDocumentationTest extends NeqSimTest {
  @TempDir
  Path output;

  private static void assertBhpRow(String deck, int thpIndex, double[] expected) {
    Matcher row = Pattern.compile("^\\s*" + thpIndex + "\\s+1\\s+1\\s+1\\s+([^/]+?)/\\s*$", Pattern.MULTILINE)
        .matcher(deck);
    assertTrue(row.find(), "Missing indexed BHP row " + thpIndex);
    String[] values = row.group(1).trim().split("\\s+");
    assertEquals(expected.length, values.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], Double.parseDouble(values[i]), 1e-10);
    }
  }

  @Test
  void everyJavaBlockCompilesAndRunsAndBranchedProcessConservesMass() throws Exception {
    String document = new String(Files.readAllBytes(Paths.get("docs/process/optimization/PRACTICAL_EXAMPLES.md")),
        StandardCharsets.UTF_8);
    Matcher blocks = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL).matcher(document);
    List<File> files = new ArrayList<File>();
    List<String> names = new ArrayList<String>();
    while (blocks.find()) {
      String source = blocks.group(1);
      Matcher className = Pattern.compile("public class (\\w+)").matcher(source);
      assertTrue(className.find(), "Each Java example must have an executable class");
      String name = className.group(1);
      Path file = output.resolve(name + ".java");
      assertFalse(source.contains("System.out"), "Use Log4j in Java documentation");
      Files.write(file, source.getBytes(StandardCharsets.UTF_8));
      files.add(file.toFile());
      names.add(name);
    }
    assertEquals(5, files.size(), "Track every Java block on the page");
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "Run documentation tests on a full JDK");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<String>(Arrays.asList("-classpath", System.getProperty("java.class.path"),
          "-d", output.toString(), "-encoding", "UTF-8", "-proc:none"));
      if (System.getProperty("java.specification.version").equals("1.8")) {
        options.addAll(Arrays.asList("-source", "8", "-target", "8"));
      } else {
        options.addAll(Arrays.asList("--release", "8"));
      }
      assertTrue(compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files))
          .call(), diagnostics.getDiagnostics().toString());
    }
    try (
        URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, getClass().getClassLoader())) {
      for (String name : names) {
        String[] args = name.equals("VFPTableGeneration") ? new String[] { output.resolve("table.inc").toString() }
            : new String[0];
        loader.loadClass(name).getMethod("main", String[].class).invoke(null, (Object) args);
      }
      String vfp = new String(Files.readAllBytes(output.resolve("table.inc")), StandardCharsets.UTF_8);
      assertTrue(vfp.contains("VFPPROD"));
      assertBhpRow(vfp, 1, new double[] { 30.0, 36.0, 48.0 });
      assertBhpRow(vfp, 3, new double[] { 74.0, 80.0, 92.0 });
      assertFalse(vfp.contains("No BHP data"));

      ProcessSystem process = (ProcessSystem) loader.loadClass("MultiEquipmentOptimization").getMethod("createProcess")
          .invoke(null);
      ThreePhaseSeparator hp = (ThreePhaseSeparator) process.getUnit("HP Separator");
      Separator lp = (Separator) process.getUnit("LP Separator");
      Pump pump = (Pump) process.getUnit("Export Pump");
      double outlets = hp.getWaterOutStream().getFlowRate("kg/hr") + lp.getGasOutStream().getFlowRate("kg/hr")
          + pump.getOutletStream().getFlowRate("kg/hr") + process.getUnit("Gas Export").getFluid().getFlowRate("kg/hr");
      for (String name : Arrays.asList("Inlet Scrubber", "Interstage Scrubber", "Export Scrubber")) {
        outlets += ((Separator) process.getUnit(name)).getLiquidOutStream().getFlowRate("kg/hr");
      }
      assertEquals(100000.0, outlets, 1.0e-4);
      assertEquals(6.0, lp.getLiquidOutStream().getPressure("bara"), 1.0e-8);
      assertEquals(20.0, pump.getOutletStream().getPressure("bara"), 1.0e-8);
      assertEquals(180.0, process.getUnit("Gas Export").getFluid().getPressure("bara"), 1.0e-8);
      assertTrue(pump.getPower() > 0.0, "The export pump must increase pressure and consume power");
    }
  }
}
