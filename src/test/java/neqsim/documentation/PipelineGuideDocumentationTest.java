package neqsim.documentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.fluidmechanics.flownode.twophasenode.twophasepipeflownode.StratifiedFlowNode;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.PipeFlowResult;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.PipeLineInterface;
import neqsim.process.equipment.stream.Stream;

/** Compiles and executes the actual complete examples in the pipeline and fluid-mechanics guides. */
class PipelineGuideDocumentationTest extends neqsim.NeqSimTest {
  private static final String PIPELINE_GUIDE = "docs/process/equipment/pipeline_simulation.md";
  private static final String HEAT_GUIDE = "docs/fluidmechanics/heat_transfer.md";

  @TempDir
  Path temporaryDirectory;

  @Test
  void gasExportConservesFlowAndDocumentsConstantTemperatureBehavior() throws Exception {
    AdiabaticPipe pipe = (AdiabaticPipe) runExample(PIPELINE_GUIDE, "gas-export", "pipeline");
    assertHydraulicResult(pipe);
    assertEquals(pipe.getInletStream().getTemperature("K"), pipe.getOutletTemperature("K"), 1.0e-8);
    assertTrue(pipe.getVelocity() > 0.0);
    assertTrue(pipe.getReynoldsNumber() > 4000.0);
    assertTrue(pipe.getFrictionFactor() > 0.0 && pipe.getFrictionFactor() < 0.1);
  }

  @Test
  void subseaExampleHasAlignedProfilesAndCooling() throws Exception {
    PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) runExample(PIPELINE_GUIDE, "subsea-flowline", "pipeline");
    assertHydraulicResult(pipe);
    assertProfiles(pipe);
    assertEquals(pipe.getFlowRegimeEnum().toString(), pipe.getFlowRegime());
    assertTrue(pipe.getOutletTemperature("C") < 60.0);
    assertTrue(pipe.getOutletTemperature("C") > 2.0);
    assertTrue(pipe.getInletStream().getFluid().getNumberOfPhases() > 1);
    assertTrue(pipe.getOutletStream().getFluid().getEnthalpy() < pipe.getInletStream().getFluid().getEnthalpy());
  }

  @Test
  void riserHasConsistentElevationAndPositiveHoldup() throws Exception {
    PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) runExample(PIPELINE_GUIDE, "riser", "pipeline");
    assertHydraulicResult(pipe);
    assertProfiles(pipe);
    assertEquals(500.0, pipe.getLength() * Math.sin(Math.toRadians(pipe.getAngle())), 1.0e-8);
    double[] holdup = pipe.getLiquidHoldupProfile();
    assertTrue(holdup[holdup.length - 1] > 0.0);
  }

  @Test
  void mechanicalDesignUsesMetresAndIntegerLocationClass() throws Exception {
    double thicknessM = (Double) runExample(PIPELINE_GUIDE, "mechanical-design", "minimumThicknessM");
    assertTrue(thicknessM > 0.003 && thicknessM < 0.1,
        "The calculated wall must exceed corrosion allowance and remain plausible for this pipe.");
  }

  @Test
  void lowLevelSinglePhaseExampleConservesBoundaryFlux() throws Exception {
    PipeFlowSystem flow = (PipeFlowSystem) runExample("docs/fluidmechanics/README.md", "single-phase", "flow");
    assertTrue(flow.getConvergenceReport().isConverged(), flow.getConvergenceReport().getMessage());
    assertTrue(flow.getConvergenceReport().isNonlinearMetricEquationResidual());
    for (int i = 0; i < flow.getTotalNumberOfNodes(); i++) {
      assertArrayEquals(new double[] { 0.95, 0.05 }, flow.getNode(i).getBulkSystem().getMolarComposition(), 1.0e-10);
    }
    int outlet = flow.getTotalNumberOfNodes() - 1;
    double outletPressure = flow.getNode(outlet).getBulkSystem().getPressure();
    assertTrue(outletPressure > 0.0 && outletPressure < 70.0);
    assertTrue(flow.getNode(outlet).getVelocity() > 0.0);

    // Reconstruct the conservative boundary fluxes from public node state. For solver type 1,
    // the last internal cell owns the outlet face's density and area; the boundary node supplies
    // pressure only. Use the EOS phase density used by the continuity equations, not transport density.
    double inletMassFlux = flow.getNode(1).getVelocityIn().doubleValue() * flow.getNode(0).getGeometry().getArea()
        * flow.getNode(0).getBulkSystem().getPhase(0).getDensity();
    double outletMassFlux = flow.getNode(outlet).getVelocityIn().doubleValue()
        * flow.getNode(outlet - 1).getGeometry().getArea()
        * flow.getNode(outlet - 1).getBulkSystem().getPhase(0).getDensity();
    assertTrue(Double.isFinite(inletMassFlux) && inletMassFlux > 0.0);
    assertTrue(Double.isFinite(outletMassFlux) && outletMassFlux > 0.0);
    // Each internal cell contributes a scaled continuity residual; allow their accumulated tolerance.
    double fluxRelativeTolerance = (flow.getTotalNumberOfNodes() - 2)
        * flow.getConvergenceReport().getNonlinearUpdateTolerance();
    assertEquals(inletMassFlux, outletMassFlux, inletMassFlux * fluxRelativeTolerance,
        "The steady finite-volume boundary mass fluxes must balance within the continuity-equation tolerance.");
  }

  @Test
  void twoPhaseFactoryReturnsFiniteAlignedResults() throws Exception {
    PipeFlowResult result = (PipeFlowResult) runExample("docs/fluidmechanics/README.md", "two-phase-factory", "result");
    assertTrue(Double.isFinite(result.getOutletPressure()) && result.getOutletPressure() > 0.0);
    assertTrue(Double.isFinite(result.getOutletTemperature()) && result.getOutletTemperature() > 200.0);
    assertEquals(result.getNumberOfNodes(), result.getPressureProfile().length);
    assertEquals(result.getNumberOfNodes(), result.getTemperatureProfile().length);
    for (double holdup : result.getLiquidHoldupProfile()) {
      assertTrue(Double.isFinite(holdup) && holdup >= 0.0 && holdup <= 1.0);
    }
  }

  @Test
  void wallGeometryHasFiniteInsulatedResistance() throws Exception {
    PipeData pipe = (PipeData) runExample(HEAT_GUIDE, "wall-geometry", "pipe");
    assertEquals(0.06, pipe.getTotalWallThickness(), 1.0e-10);
    double coefficient = pipe.calcOverallHeatTransferCoefficient();
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0 && coefficient < 10.0);
  }

  @Test
  void singlePhaseGasCoolsWithConservedComposition() throws Exception {
    PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) runExample(HEAT_GUIDE, "gas-cooling", "pipeline");
    assertHydraulicResult(pipe);
    assertProfiles(pipe);
    assertTrue(pipe.getOutletTemperature("C") < 100.0 && pipe.getOutletTemperature("C") > 8.0);
    assertEquals(1, pipe.getOutletStream().getFluid().getNumberOfPhases());
  }

  @Test
  void equilibriumNodeHasNoMaterialHeatDrivingForce() throws Exception {
    StratifiedFlowNode node = (StratifiedFlowNode) runExample(HEAT_GUIDE, "interphase-equilibrium", "node");
    assertEquals(2, node.getBulkSystem().getNumberOfPhases());
    for (int phase = 0; phase < 2; phase++) {
      double flux = node.getFluidBoundary().getInterphaseHeatFlux(phase);
      assertTrue(Double.isFinite(flux));
      assertEquals(0.0, flux, 1.0e-3, "Equilibrium phases should have negligible interphase heat flux.");
    }
  }

  @Test
  void coolingProducesCondensateWithConservedFlow() throws Exception {
    PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) runExample(HEAT_GUIDE, "condensation", "pipeline");
    assertHydraulicResult(pipe);
    assertProfiles(pipe);
    assertTrue(pipe.getOutletTemperature("K") < pipe.getInletStream().getTemperature("K"));
    assertTrue(pipe.getOutletStream().getFluid().getNumberOfPhases() > 1);
    double[] holdup = pipe.getLiquidHoldupProfile();
    assertTrue(holdup[holdup.length - 1] > 0.0);
  }

  @Test
  void evaporationPreparationUsesCurrentStreamPackageAndConservesFeed() throws Exception {
    Stream inlet = (Stream) runExample("docs/fluidmechanics/MassTransferAPI.md", "evaporation-preparation", "inlet");
    assertEquals(100.0, inlet.getFlowRate("kg/hr"), 1.0e-7);
    assertArrayEquals(new double[] { 0.95, 0.05 }, inlet.getFluid().getMolarComposition(), 1.0e-10);
    assertEquals(2, inlet.getFluid().getNumberOfPhases());
  }

  private static void assertHydraulicResult(PipeLineInterface pipe) {
    double inletPressure = pipe.getInletStream().getPressure("bara");
    double outletPressure = pipe.getOutletStream().getPressure("bara");
    assertTrue(Double.isFinite(outletPressure) && outletPressure > 0.0 && outletPressure < inletPressure);
    assertEquals(inletPressure - outletPressure, pipe.getPressureDrop(), 1.0e-8);
    assertEquals(pipe.getInletStream().getFlowRate("kg/sec"), pipe.getOutletStream().getFlowRate("kg/sec"), 1.0e-8);
    assertArrayEquals(pipe.getInletStream().getFluid().getMolarComposition(),
        pipe.getOutletStream().getFluid().getMolarComposition(), 1.0e-10);
  }

  private static void assertProfiles(PipeBeggsAndBrills pipe) {
    double[] pressures = pipe.getPressureProfile();
    double[] temperatures = pipe.getTemperatureProfile();
    double[] holdup = pipe.getLiquidHoldupProfile();
    assertEquals(pipe.getNumberOfIncrements() + 1, pressures.length);
    assertEquals(pressures.length, temperatures.length);
    assertEquals(pressures.length, holdup.length);
    assertEquals(pressures.length, pipe.getLengthProfile().size());
    assertEquals(pipe.getOutletTemperature("K"), temperatures[temperatures.length - 1], 1.0e-8);
    assertEquals(pipe.getOutletPressure("bara"), pressures[pressures.length - 1], 1.0e-8);
    for (int i = 0; i < pressures.length; i++) {
      assertTrue(Double.isFinite(pressures[i]) && pressures[i] > 0.0);
      assertTrue(Double.isFinite(temperatures[i]) && temperatures[i] > 200.0 && temperatures[i] < 500.0);
      assertTrue(Double.isFinite(holdup[i]) && holdup[i] >= 0.0 && holdup[i] <= 1.0);
    }
  }

  /**
   * Wraps an exact marked Markdown block in a Java method, compiles it, and returns its named result.
   *
   * @param document repository-relative Markdown path
   * @param example unique marker in the document
   * @param resultExpression local result variable to return after the documented code
   * @return the simulation object or calculated result
   * @throws Exception if extraction, compilation, or execution fails
   */
  private Object runExample(String document, String example, String resultExpression) throws Exception {
    String markdown = new String(Files.readAllBytes(Paths.get(document)), StandardCharsets.UTF_8);
    String marker = "<!-- pipeline-doc-test: " + example + " -->";
    int markerPosition = markdown.indexOf(marker);
    assertTrue(markerPosition >= 0, "Missing example marker: " + marker);
    int start = markdown.indexOf("```java\n", markerPosition);
    assertTrue(start > markerPosition, "Missing Java block for " + example);
    start += "```java\n".length();
    int end = markdown.indexOf("\n```", start);
    assertTrue(end > start, "Unclosed Java block for " + example);
    StringBuilder imports = new StringBuilder();
    StringBuilder body = new StringBuilder();
    for (String line : markdown.substring(start, end).split("\n")) {
      if (line.trim().startsWith("import ")) {
        imports.append(line).append('\n');
      } else {
        body.append(line).append('\n');
      }
    }
    String className = "PipelineDoc_" + example.replace('-', '_');
    String source = imports.toString() + "public class " + className + " {\n"
        + "public static Object run() throws Exception {\n" + body + "return " + resultExpression + ";\n}\n}\n";
    Path sourceFile = temporaryDirectory.resolve(className + ".java");
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to verify executable documentation examples.");
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> units = manager.getJavaFileObjects(sourceFile.toFile());
      boolean compiled = compiler.getTask(null, manager, diagnostics,
          Arrays.asList("-source", "8", "-target", "8", "-classpath", classpath, "-d", temporaryDirectory.toString()),
          null, units).call();
      assertTrue(compiled, document + " [" + example + "] compilation failed: " + diagnostics.getDiagnostics());
    }
    try (URLClassLoader loader = new URLClassLoader(new URL[] { temporaryDirectory.toUri().toURL() },
        getClass().getClassLoader())) {
      try {
        return loader.loadClass(className).getMethod("run").invoke(null);
      } catch (InvocationTargetException exception) {
        throw new AssertionError(document + " [" + example + "] execution failed", exception.getCause());
      }
    }
  }
}
