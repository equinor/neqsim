package neqsim.process.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.fielddevelopment.concept.DevelopmentCaseTemplate;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.GreenfieldConceptFactory;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.Criterion;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.DevelopmentOption;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.RankingResult;
import neqsim.process.fielddevelopment.facility.ConceptToProcessLinker;
import neqsim.process.fielddevelopment.facility.ConceptToProcessLinker.FidelityLevel;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.ExportFormat;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityPlanner;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityResult;
import neqsim.process.fielddevelopment.tieback.capacity.TieInPeriodResult;
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Executable regression coverage for the field-development overview.
 *
 * @author ESOL
 * @version 1.0
 */
class FieldDevelopmentOverviewDocumentationTest extends neqsim.NeqSimTest {
  private static final Path GUIDE = Paths.get("docs", "fielddevelopment", "README.md");
  private static final String MARKER =
      "<!-- field-development-overview-example: screening-workflow -->";
  private static final Pattern JAVA_FENCE =
      Pattern.compile("```java\\r?\\n(.*?)\\r?\\n```", Pattern.DOTALL);

  @TempDir
  Path temporaryDirectory;

  /**
   * Verifies that the marked field-development screening workflow is literal executable source.
   *
   * @throws Exception if the guide source cannot be compiled or executed
   */
  @Test
  void executableScreeningWorkflowCompilesAndRunsFromGuide() throws Exception {
    String source = markedExampleSource();

    assertFalse(source.contains("System.out"));
    assertFalse(source.contains("System.err"));
    assertTrue(source.contains("public final class FieldDevelopmentOverviewExample"));
    assertTrue(source.contains("new ConceptEvaluator().evaluate(concept)"));
    assertTrue(source.contains("new TieInCapacityPlanner(host)"));
    assertTrue(source.contains("linker.generateProcessSystem(concept, FidelityLevel.CONCEPT)"));
    assertTrue(source.contains("exporter.generateVfpProd"));
    assertTrue(source.contains("cost.calculateTreeCost"));

    try (URLClassLoader loader =
        compile("FieldDevelopmentOverviewExample", source)) {
      Class<?> example = loader.loadClass("FieldDevelopmentOverviewExample");
      example.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
  }

  @Test
  void conceptEvaluationTemplatesAndNestedRankingTypesExecute() {
    FieldConcept concept = FieldConcept.gasTieback("Demo gas tieback", 30.0, 2, 0.8);
    ConceptKPIs kpis = new ConceptEvaluator().evaluate(concept);

    assertEquals("Demo gas tieback", kpis.getConceptName());
    assertTrue(kpis.getTotalCapexMUSD() > 0.0);
    assertTrue(kpis.getFieldLifeYears() > 0.0);
    assertTrue(kpis.getEstimatedRecoveryPercent() > 0.0);
    assertTrue(kpis.getCo2IntensityKgPerBoe() >= 0.0);

    DevelopmentCaseTemplate tieback = GreenfieldConceptFactory.subseaTieback("Book tieback");
    DevelopmentCaseTemplate fpso = GreenfieldConceptFactory.standaloneFpso("Book FPSO");

    assertNotNull(tieback.getFacilityConfig());
    assertNotNull(fpso.getFacilityConfig());
    assertFalse(tieback.getSummary().isEmpty());
    assertFalse(fpso.getSummary().isEmpty());

    DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
    DevelopmentOption fpsoOption = ranker.addOption("FPSO");
    fpsoOption.setScore(Criterion.NPV, 1200.0);
    fpsoOption.setScore(Criterion.CO2_INTENSITY, 12.0);
    DevelopmentOption tiebackOption = ranker.addOption("Tieback");
    tiebackOption.setScore(Criterion.NPV, 650.0);
    tiebackOption.setScore(Criterion.CO2_INTENSITY, 7.0);

    RankingResult ranking = ranker.rank();

    assertEquals(2, ranking.getRankedOptions().size());
    assertEquals("FPSO", ranking.getBestOption().getName());
  }

  @Test
  void hostCapacityQuickStartReportsTheDocumentedHoldback() {
    HostFacility host = HostFacility.builder("Host A").gasCapacity(5.0).build();
    ProductionProfileSeries base = new ProductionProfileSeries("base").addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite = new ProductionProfileSeries("satellite").addPeriod(2028, 3.0, 0.0, 0.0, 0.0);

    TieInCapacityResult capacity = new TieInCapacityPlanner(host).setHostProductionProfile(base)
        .setSatelliteProductionProfile(satellite).setAllocationPolicy(CapacityAllocationPolicy.BASE_FIRST).run();

    TieInPeriodResult period = capacity.getPeriodResults().get(0);
    assertTrue(capacity.hasHoldback());
    assertEquals(1.0, period.getAcceptedSatellite().getGasRateMSm3d(), 1.0e-9);
    assertEquals(2.0, period.getHeldBackSatellite().getGasRateMSm3d(), 1.0e-9);
    assertFalse(capacity.toMarkdownTable().isEmpty());
  }

  @Test
  void generatedScreeningProcessRunsAndExposesUtilities() {
    FieldConcept concept = FieldConcept.gasTieback("Demo gas tieback", 30.0, 2, 0.8);
    ConceptToProcessLinker linker = new ConceptToProcessLinker();
    ProcessSystem process = linker.generateProcessSystem(concept, FidelityLevel.CONCEPT);

    assertTrue(process.size() >= 5);
    process.run();

    assertTrue(linker.getTotalPowerMW(process) >= 0.0);
    assertTrue(linker.getUtilitySummary(process).contains("UTILITY SUMMARY"));
  }

  @Test
  void reservoirExportAndSurfScreeningUseCurrentSignatures() throws IOException {
    SystemInterface baseFluid = new SystemSrkEos(358.15, 250.0);
    baseFluid.addComponent("methane", 0.85);
    baseFluid.addComponent("ethane", 0.08);
    baseFluid.addComponent("propane", 0.04);
    baseFluid.addComponent("n-butane", 0.02);
    baseFluid.addComponent("CO2", 0.01);
    baseFluid.setMixingRule("classic");

    ReservoirCouplingExporter exporter = new ReservoirCouplingExporter();
    exporter.setFormat(ExportFormat.ECLIPSE_100);
    exporter.setPressureRange(40.0, 60.0, 2);
    exporter.setRateRange(500.0, 1000.0, 2);
    exporter.setWctRange(0.0, 0.5, 2);
    exporter.setGorRange(100.0, 300.0, 2);

    VfpTable vfp = exporter.generateVfpProd("PROD-A1", baseFluid, 1);
    String eclipseKeywords = exporter.getEclipseKeywords();

    assertEquals(1, vfp.getTableNumber());
    assertEquals("PROD-A1", vfp.getWellName());
    assertTrue(vfp.getBhpValues()[0][0][0][0][0] > vfp.getThpValues()[0]);
    assertTrue(eclipseKeywords.contains("VFPPROD"));

    Path output = temporaryDirectory.resolve("vfp.inc");
    exporter.exportToFile(output.toString());
    String written = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
    assertTrue(written.contains("VFPPROD"));

    SubseaCostEstimator cost = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
    cost.calculateTreeCost(10000.0, 7.0, 380.0, true, false);
    assertTrue(cost.getTotalCost() > 0.0);
    cost.calculateManifoldCost(6, 80.0, 380.0, true);
    assertTrue(cost.getTotalCost() > 0.0);
    cost.calculateUmbilicalCost(48.0, 4, 3, 2, 380.0, false);
    assertTrue(cost.getTotalCost() > 0.0);
    cost.calculateFlexiblePipeCost(1200.0, 8.0, 380.0, true, true);
    assertTrue(cost.getTotalCost() > 0.0);
  }

  /**
   * Extracts the uniquely marked Java fence.
   *
   * @return exact published Java source
   * @throws Exception if the guide cannot be read
   */
  private static String markedExampleSource() throws Exception {
    String markdown = new String(Files.readAllBytes(GUIDE), StandardCharsets.UTF_8);
    int markerPosition = markdown.indexOf(MARKER);
    assertTrue(markerPosition >= 0, "Missing marker: " + MARKER);
    assertEquals(
        markerPosition,
        markdown.lastIndexOf(MARKER),
        "Marker must be unique: " + MARKER);

    Matcher fence = JAVA_FENCE.matcher(markdown.substring(markerPosition));
    assertTrue(fence.find(), "Missing Java fence after marker: " + MARKER);
    assertTrue(
        fence.group(1).contains("public final class FieldDevelopmentOverviewExample"));
    return fence.group(1);
  }

  /**
   * Compiles one Java 8 documentation source and returns its class loader.
   *
   * @param className public class name
   * @param source exact Java source
   * @return class loader for the compiled source
   * @throws Exception if the source or class output cannot be created
   */
  private URLClassLoader compile(String className, String source) throws Exception {
    Path classes = Files.createDirectories(temporaryDirectory.resolve(className));
    Path javaFile = classes.resolve(className + ".java");
    Files.write(javaFile, source.getBytes(StandardCharsets.UTF_8));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to validate documentation examples");
    String classpath =
        System.getProperty(
            "surefire.test.class.path", System.getProperty("java.class.path"));
    StringWriter diagnostics = new StringWriter();
    try (StandardJavaFileManager manager =
        compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      Boolean successful =
          compiler
              .getTask(
                  diagnostics,
                  manager,
                  null,
                  Arrays.asList(
                      "-source",
                      "8",
                      "-target",
                      "8",
                      "-proc:none",
                      "-classpath",
                      classpath,
                      "-d",
                      classes.toString()),
                  null,
                  manager.getJavaFileObjects(javaFile.toFile()))
              .call();
      assertTrue(successful, diagnostics.toString());
    }
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, getClass().getClassLoader());
  }
}
