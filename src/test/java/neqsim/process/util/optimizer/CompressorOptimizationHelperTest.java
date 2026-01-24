package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.CompressorOptimizationHelper.CompressorBounds;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for CompressorOptimizationHelper and gradient descent search mode.
 *
 * @author NeqSim Development Team
 */
public class CompressorOptimizationHelperTest {

  private ProcessSystem process;
  private StreamInterface feedStream;
  private Compressor compressor1;
  private Compressor compressor2;

  @BeforeEach
  public void setUp() {
    // Create a simple two-train process
    SystemInterface fluid = new SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    process = new ProcessSystem();

    // Feed stream
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);
    feedStream = feed;

    // Separator
    Separator separator = new Separator("separator");
    separator.setInletStream(feed);
    process.add(separator);

    // Two parallel compressors
    compressor1 = new Compressor("compressor1", separator.getGasOutStream());
    compressor1.setOutletPressure(100.0, "bara");
    compressor1.setIsentropicEfficiency(0.75);
    process.add(compressor1);

    compressor2 = new Compressor("compressor2", separator.getGasOutStream());
    compressor2.setOutletPressure(100.0, "bara");
    compressor2.setIsentropicEfficiency(0.75);
    process.add(compressor2);

    process.run();
  }

  @Test
  public void testExtractBoundsWithoutChart() {
    // Compressor without chart should return default bounds
    CompressorBounds bounds = CompressorOptimizationHelper.extractBounds(compressor1);

    assertNotNull(bounds);
    assertTrue(bounds.getMinSpeed() > 0, "Min speed should be positive");
    assertTrue(bounds.getMaxSpeed() > bounds.getMinSpeed(),
        "Max speed should be greater than min speed");
    assertNotNull(bounds.toString());
  }

  @Test
  public void testCreateSpeedVariable() {
    ManipulatedVariable speedVar = CompressorOptimizationHelper.createSpeedVariable(compressor1, 2000.0, 5000.0);

    assertNotNull(speedVar);
    assertEquals("compressor1.speed", speedVar.getName());
    assertEquals(2000.0, speedVar.getLowerBound());
    assertEquals(5000.0, speedVar.getUpperBound());
    assertEquals("RPM", speedVar.getUnit());
  }

  @Test
  public void testCreateOutletPressureVariable() {
    ManipulatedVariable pressVar = CompressorOptimizationHelper.createOutletPressureVariable(compressor1, 80.0, 120.0);

    assertNotNull(pressVar);
    assertEquals("compressor1.outletPressure", pressVar.getName());
    assertEquals(80.0, pressVar.getLowerBound());
    assertEquals(120.0, pressVar.getUpperBound());
    assertEquals("bara", pressVar.getUnit());
  }

  @Test
  public void testCreatePowerObjective() {
    List<Compressor> compressors = Arrays.asList(compressor1, compressor2);
    OptimizationObjective objective = CompressorOptimizationHelper.createPowerObjective(compressors, 1.0);

    assertNotNull(objective);
    assertEquals("totalPower", objective.getName());

    // Evaluate the objective
    double power = objective.evaluate(process);
    assertTrue(power >= 0, "Total power should be non-negative");
  }

  @Test
  public void testCreateSurgeMarginObjective() {
    List<Compressor> compressors = Arrays.asList(compressor1, compressor2);
    OptimizationObjective objective = CompressorOptimizationHelper.createSurgeMarginObjective(compressors, 1.0);

    assertNotNull(objective);
    assertEquals("minSurgeMargin", objective.getName());
  }

  @Test
  public void testCreateEfficiencyObjective() {
    List<Compressor> compressors = Arrays.asList(compressor1, compressor2);
    OptimizationObjective objective = CompressorOptimizationHelper.createEfficiencyObjective(compressors, 1.0);

    assertNotNull(objective);
    assertEquals("avgEfficiency", objective.getName());

    double avgEff = objective.evaluate(process);
    assertTrue(avgEff >= 0 && avgEff <= 1.0,
        "Average efficiency should be between 0 and 1: " + avgEff);
  }

  @Test
  public void testCreateStandardObjectives() {
    List<Compressor> compressors = Arrays.asList(compressor1, compressor2);
    List<OptimizationObjective> objectives = CompressorOptimizationHelper.createStandardObjectives(compressors);

    assertNotNull(objectives);
    assertEquals(3, objectives.size());
  }

  @Test
  public void testCreateStandardConstraints() {
    List<Compressor> compressors = Arrays.asList(compressor1, compressor2);
    List<OptimizationConstraint> constraints = CompressorOptimizationHelper.createStandardConstraints(compressors);

    assertNotNull(constraints);
    assertEquals(2, constraints.size());
  }

  @Test
  public void testGradientDescentSearchSingleVariable() {
    // Test gradient descent with single variable optimization
    ProductionOptimizer optimizer = new ProductionOptimizer();

    OptimizationConfig config = new OptimizationConfig(50000.0, 150000.0)
        .searchMode(SearchMode.GRADIENT_DESCENT_SCORE).maxIterations(20).tolerance(100.0)
        .rateUnit("kg/hr");

    OptimizationResult result = optimizer.optimize(process, feedStream, config, null, null);

    assertNotNull(result);
    assertTrue(result.getOptimalRate() >= 50000.0 && result.getOptimalRate() <= 150000.0,
        "Optimal rate should be within bounds: " + result.getOptimalRate());
    assertTrue(result.getIterations() > 0, "Should have performed iterations");
  }

  @Test
  public void testGradientDescentSearchMultiVariable() {
    // Test gradient descent with multiple variables
    ProductionOptimizer optimizer = new ProductionOptimizer();

    // Create two variables: flow rate and compressor outlet pressure
    ManipulatedVariable flowVar = new ManipulatedVariable("flow", 50000.0, 150000.0, "kg/hr",
        (proc, val) -> feedStream.setFlowRate(val, "kg/hr"));

    ManipulatedVariable pressVar = new ManipulatedVariable("pressure", 80.0, 120.0, "bara",
        (proc, val) -> compressor1.setOutletPressure(val, "bara"));

    List<ManipulatedVariable> variables = Arrays.asList(flowVar, pressVar);

    OptimizationConfig config = new OptimizationConfig(0, 1) // Bounds ignored for multi-var
        .searchMode(SearchMode.GRADIENT_DESCENT_SCORE).maxIterations(15).tolerance(0.01);

    OptimizationResult result = optimizer.optimize(process, variables, config, null, null);

    assertNotNull(result);
    assertTrue(result.getIterations() > 0, "Should have performed iterations");
  }

  @Test
  public void testGradientDescentWithObjectives() {
    // Test gradient descent with explicit objectives
    ProductionOptimizer optimizer = new ProductionOptimizer();

    List<Compressor> compressors = Collections.singletonList(compressor1);
    List<OptimizationObjective> objectives = CompressorOptimizationHelper.createStandardObjectives(compressors);

    OptimizationConfig config = new OptimizationConfig(50000.0, 150000.0)
        .searchMode(SearchMode.GRADIENT_DESCENT_SCORE).maxIterations(15).tolerance(100.0)
        .rateUnit("kg/hr");

    OptimizationResult result = optimizer.optimize(process, feedStream, config, objectives, null);

    assertNotNull(result);
    assertNotNull(result.getObjectiveValues());
  }

  @Test
  public void testCompressorBoundsRecommendedRange() {
    CompressorBounds bounds = new CompressorBounds(2000, 5000, 100, 500, 120, 480, "Am3/hr");

    double[] recommended = bounds.getRecommendedRange(0.1);

    assertNotNull(recommended);
    assertEquals(2, recommended.length);
    assertTrue(recommended[0] > bounds.getSurgeFlow());
    assertTrue(recommended[1] < bounds.getStoneWallFlow());
  }

  @Test
  public void testSearchModeGradientDescentExists() {
    // Verify the new search mode is available
    SearchMode mode = SearchMode.GRADIENT_DESCENT_SCORE;
    assertNotNull(mode);
    assertEquals("GRADIENT_DESCENT_SCORE", mode.name());
  }
}
