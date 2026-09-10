package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import neqsim.process.mechanicaldesign.DesignLimitData;
import neqsim.process.mechanicaldesign.data.CsvMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.StandardBasedCsvDataSource;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.torg.CsvTorgDataSource;
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;

/** Compiles and executes the Java and CSV examples in the four mechanical-design workflow guides. */
class MechanicalDesignGuideDocumentationTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void designDatabaseFormatsAndExampleUseDeclaredUnits() throws Exception {
    String document = readDocument("mechanical_design_database.md");
    List<String> csvBlocks = codeBlocks(document, "csv");
    assertEquals(2, csvBlocks.size());
    Path companyFile = writeFixture("company_limits.csv", csvBlocks.get(0));
    Path standardFile = writeFixture("standard_limits.csv", csvBlocks.get(1));
    DesignLimitData companyLimits = new CsvMechanicalDesignDataSource(companyFile)
        .getDesignLimits("Separator", "ExampleCo").get();
    DesignLimitData standardLimits = new StandardBasedCsvDataSource(standardFile)
        .getDesignLimitsByStandard("DOCS-DEMO", "1", "Separator").get();
    assertEquals(companyLimits, standardLimits);
    assertEquals(150.0, companyLimits.getMaxPressure(), 1.0e-12);
    assertEquals(1.01325, companyLimits.getMinPressure(), 1.0e-12);
    assertEquals(423.15, companyLimits.getMaxTemperature(), 1.0e-12);
    assertEquals(233.15, companyLimits.getMinTemperature(), 1.0e-12);
    assertEquals(3.0, companyLimits.getCorrosionAllowance(), 1.0e-12);
    assertEquals(0.85, companyLimits.getJointEfficiency(), 1.0e-12);
    compileAndRun(document, companyFile.toString(), standardFile.toString());
  }

  @Test
  void torgExampleLoadsAppliesAndCorrectsTemperatureUnits() throws Exception {
    String document = readDocument("torg_integration.md");
    List<String> csvBlocks = codeBlocks(document, "csv");
    assertEquals(1, csvBlocks.size());
    Path torgFile = writeFixture("project_torg.csv", csvBlocks.get(0));
    CsvTorgDataSource source = new CsvTorgDataSource(torgFile);
    TechnicalRequirementsDocument torg = source.loadByProjectId("DOCS-001").get();
    assertEquals("Demonstration separator", torg.getProjectName());
    assertEquals("ExampleCo", torg.getCompanyIdentifier());
    assertEquals("1", torg.getRevision());
    assertEquals("2026-09-10", torg.getIssueDate());
    assertTrue(torg.getAllApplicableStandards("Separator").contains(StandardType.ASME_VIII_DIV1));
    assertTrue(torg.getAllApplicableStandards("Separator").contains(StandardType.API_12J));
    compileAndRun(document, torgFile.toString());
  }

  @Test
  void fieldExampleExecutesDistinctThroughputsAndChecksPowerTrend() throws Exception {
    compileAndRun(readDocument("field_development_orchestration.md"));
  }

  @Test
  void processExampleChecksBalancePressureAndCompleteMechanicalResults() throws Exception {
    compileAndRun(readDocument("process_design_guide.md"));
  }

  /**
   * Reads a guide from the project root used by Maven.
   *
   * @param fileName documentation filename
   * @return UTF-8 Markdown source
   * @throws Exception if the document cannot be read
   */
  private String readDocument(String fileName) throws Exception {
    return new String(Files.readAllBytes(Paths.get("docs", "process", fileName)), StandardCharsets.UTF_8);
  }

  /**
   * Extracts fenced code without copying the tested program into the test.
   *
   * @param document Markdown source
   * @param language fence language
   * @return fenced block contents in document order
   */
  private List<String> codeBlocks(String document, String language) {
    Pattern pattern = Pattern.compile("^```" + language + "[ \\t]*\\r?\\n(.*?)^```[ \\t]*$",
        Pattern.MULTILINE | Pattern.DOTALL);
    Matcher matcher = pattern.matcher(document);
    List<String> blocks = new ArrayList<>();
    while (matcher.find()) {
      blocks.add(matcher.group(1));
    }
    return blocks;
  }

  /**
   * Writes a CSV example to an isolated test directory.
   *
   * @param fileName temporary filename
   * @param contents exact code-fence contents
   * @return path to the written file
   * @throws Exception if writing fails
   */
  private Path writeFixture(String fileName, String contents) throws Exception {
    return Files.write(temporaryDirectory.resolve(fileName), contents.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Compiles a complete documented Java class and invokes its main method, including its input and result checks.
   *
   * @param document Markdown source containing one complete Java example
   * @param arguments command-line arguments documented by the example
   * @throws Exception if compilation, execution, or a documented result check fails
   */
  private void compileAndRun(String document, String... arguments) throws Exception {
    List<String> javaBlocks = codeBlocks(document, "java");
    assertEquals(1, javaBlocks.size(), "Each guide must contain one complete executable Java example");
    String source = javaBlocks.get(0);
    Matcher classMatcher = Pattern.compile("public\\s+class\\s+(\\w+)").matcher(source);
    assertTrue(classMatcher.find(), "The documented example must declare a public class");
    String className = classMatcher.group(1);
    Path classesDirectory = Files.createTempDirectory(temporaryDirectory, "example-");
    Path javaFile = Files.write(classesDirectory.resolve(className + ".java"), source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate the documented Java examples");
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d",
          classesDirectory.toString(), "-source", "8", "-target", "8");
      Boolean compiled = compiler
          .getTask(diagnostics, fileManager, null, options, null, fileManager.getJavaFileObjects(javaFile.toFile()))
          .call();
      assertTrue(Boolean.TRUE.equals(compiled), className + " failed to compile: " + diagnostics);
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] { classesDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      Class<?> example = Class.forName(className, true, loader);
      example.getMethod("main", String[].class).invoke(null, (Object) arguments);
    }
  }
}
