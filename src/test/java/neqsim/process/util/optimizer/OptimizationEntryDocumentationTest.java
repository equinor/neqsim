package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

/** Compiles and executes the exact Java entry-guide examples using their stated process fixture. */
class OptimizationEntryDocumentationTest extends NeqSimTest {
  @TempDir
  Path output;

  private static final String IMPORTS = "import java.util.*; import java.util.function.*; "
      + "import java.nio.file.*; import java.nio.charset.*; "
      + "import org.apache.logging.log4j.*; import neqsim.thermo.system.*; "
      + "import neqsim.process.processmodel.*; import neqsim.process.equipment.*; "
      + "import neqsim.process.equipment.stream.*; import neqsim.process.equipment.compressor.*; "
      + "import neqsim.process.equipment.compressor.driver.*; import neqsim.process.equipment.separator.*; "
      + "import neqsim.process.equipment.heatexchanger.*; import neqsim.process.equipment.capacity.*; "
      + "import neqsim.process.util.optimizer.*; "
      + "import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig; "
      + "import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective; "
      + "import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode; "
      + "import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType; "
      + "import neqsim.process.util.optimizer.ProcessOptimizationEngine.OptimizationResult;\n";

  @Test
  void entryAndArchitectureExamplesCompileAndExecute() throws Exception {
    List<File> files = new ArrayList<File>();
    List<String> names = new ArrayList<String>();
    String fixture = IMPORTS + "public class EntryDocumentationFixture {\n"
        + "protected static final Logger logger = LogManager.getLogger(EntryDocumentationFixture.class);\n"
        + "protected static ProcessSystem process; protected static Stream feed; "
        + "protected static Compressor compressor; protected static ProcessOptimizationEngine engine; "
        + "protected static ProcessConstraintEvaluator evaluator; protected static OptimizationResult result; "
        + "protected static EclipseVFPExporter exporter; protected static OptimizationConfig config;\n"
        + "protected static void reset() { process = OptimizationGuideSetup.createProcess(); "
        + "feed = (Stream) process.getUnit(\"feed\"); compressor = (Compressor) process.getUnit(\"comp\"); "
        + "engine = new ProcessOptimizationEngine(process); engine.setFeedStreamName(\"feed\"); "
        + "engine.setOutletStreamName(\"outlet\"); engine.setTolerance(1.0); "
        + "engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH); "
        + "result = OptimizationGuideSetup.optimize(process); feed.setFlowRate(50000.0,\"kg/hr\"); process.run(); "
        + "evaluator = new ProcessConstraintEvaluator(process); exporter = new EclipseVFPExporter(1); "
        + "exporter.setFlowRates(new double[]{10000,20000,40000}); exporter.setTHPs(new double[]{20,40}); "
        + "exporter.setWaterCuts(new double[]{0}); exporter.setGORs(new double[]{0}); "
        + "exporter.setALQs(new double[]{0}); double[][][][][] bhp = new double[2][1][1][1][3]; "
        + "bhp[0][0][0][0]=new double[]{30,36,48}; bhp[1][0][0][0]=new double[]{50,56,68}; "
        + "exporter.setBHPTable(bhp); config = new OptimizationConfig(10000,200000); }\n"
        + "protected static void verify() { if (!Double.isFinite(compressor.getPower(\"kW\"))) "
        + "throw new AssertionError(\"Non-finite compressor power\"); } }\n";
    write(files, "EntryDocumentationFixture", fixture);
    int snippetCount = 0;
    for (String document : Arrays.asList("getting-started.md", "OPTIMIZER_PLUGIN_ARCHITECTURE.md",
        "OPTIMIZATION_OVERVIEW.md")) {
      String markdown = new String(Files.readAllBytes(Paths.get("docs/process/optimization", document)),
          StandardCharsets.UTF_8);
      Matcher blocks = Pattern.compile("```java\\n(.*?)```", Pattern.DOTALL).matcher(markdown);
      int index = 0;
      while (blocks.find()) {
        if (document.equals("OPTIMIZATION_OVERVIEW.md") && !Arrays.asList(0, 1, 4, 9, 10, 11, 12, 13).contains(index)) {
          index++;
          continue;
        }
        String source = blocks.group(1);
        Matcher type = Pattern.compile("public class (\\w+)").matcher(source);
        String name;
        if (type.find()) {
          name = type.group(1);
        } else {
          name = "EntryExample" + snippetCount;
          Matcher imports = Pattern.compile("(?m)^import .*?;\\s*").matcher(source);
          StringBuilder addedImports = new StringBuilder();
          while (imports.find()) {
            addedImports.append(imports.group()).append('\n');
          }
          source = IMPORTS + addedImports + "public class " + name + " extends EntryDocumentationFixture {\n"
              + "public static void main(String[] args) throws Exception { reset();\n" + imports.replaceAll("")
              + "\nverify(); }}\n";
        }
        assertTrue(!source.contains("System.out"), document + " block " + index + " must use Log4j");
        write(files, name, source);
        names.add(name);
        snippetCount++;
        index++;
      }
    }
    assertEquals(54, snippetCount, "Cover both entry guides and all eight standalone overview examples");
    String overview = new String(Files.readAllBytes(Paths.get("docs/process/optimization/OPTIMIZATION_OVERVIEW.md")),
        StandardCharsets.UTF_8);
    Matcher yaml = Pattern.compile("```yaml\\n(.*?)```", Pattern.DOTALL).matcher(overview);
    assertTrue(yaml.find(), "The scenario example requires the published YAML specification");
    Files.write(output.resolve("optimization.yaml"), yaml.group(1).getBytes(StandardCharsets.UTF_8));
    String runner = "public class EntryDocumentationRunner { public static void main(String[] args) throws Exception {";
    for (String name : names) {
      runner += name + ".main(new String[0]);\n";
    }
    runner += "}}";
    write(files, "EntryDocumentationRunner", runner);
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A full JDK is required to execute documentation examples");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    String classpath = absoluteClasspath(System.getProperty("java.class.path"));
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<String>(
          Arrays.asList("-classpath", classpath, "-d", output.toString(), "-encoding", "UTF-8", "-proc:none"));
      options.addAll(
          System.getProperty("java.specification.version").equals("1.8") ? Arrays.asList("-source", "8", "-target", "8")
              : Arrays.asList("--release", "8"));
      assertTrue(compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjectsFromFiles(files))
          .call(), diagnostics.getDiagnostics().toString());
    }
    Path log = output.resolve("execution.log");
    Process child = new ProcessBuilder(Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx512m",
        "-classpath", output + File.pathSeparator + classpath, "EntryDocumentationRunner").directory(output.toFile())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    boolean finished = child.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      child.destroyForcibly();
    }
    assertTrue(finished, "Documentation examples exceeded two minutes");
    assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
    assertTrue(Files.size(output.resolve("VFPPROD_WELL1.INC")) > 100);
  }

  private void write(List<File> files, String name, String source) throws Exception {
    Path target = output.resolve(name + ".java");
    Files.write(target, source.getBytes(StandardCharsets.UTF_8));
    files.add(target.toFile());
  }

  private static String absoluteClasspath(String classpath) {
    List<String> entries = new ArrayList<String>();
    for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
      entries.add(Paths.get(entry).toAbsolutePath().toString());
    }
    return String.join(File.pathSeparator, entries);
  }
}
