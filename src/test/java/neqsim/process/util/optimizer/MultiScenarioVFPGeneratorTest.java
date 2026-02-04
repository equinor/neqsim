package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for MultiScenarioVFPGenerator class.
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiScenarioVFPGeneratorTest {

  private FluidMagicInput fluidInput;
  private RecombinationFlashGenerator flashGenerator;
  private Supplier<ProcessSystem> processFactory;

  @TempDir
  Path tempDir;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  void setUp() {
    // Create a typical oil/gas fluid
    SystemInterface referenceFluid = new SystemSrkEos(288.15, 1.01325);
    referenceFluid.addComponent("nitrogen", 0.005);
    referenceFluid.addComponent("CO2", 0.01);
    referenceFluid.addComponent("methane", 0.70);
    referenceFluid.addComponent("ethane", 0.08);
    referenceFluid.addComponent("propane", 0.06);
    referenceFluid.addComponent("n-butane", 0.04);
    referenceFluid.addComponent("n-pentane", 0.03);
    referenceFluid.addComponent("n-hexane", 0.025);
    referenceFluid.addComponent("n-heptane", 0.025);
    referenceFluid.addComponent("n-octane", 0.025);
    referenceFluid.setMixingRule("classic");

    fluidInput = FluidMagicInput.fromFluid(referenceFluid);
    fluidInput.setGORRange(200, 5000);
    fluidInput.setWaterCutRange(0.0, 0.60);
    fluidInput.separateToStandardConditions();

    flashGenerator = new RecombinationFlashGenerator(fluidInput);

    // Create process factory for pipeline simulation
    processFactory = this::createSimplePipeline;
  }

  /**
   * Create a simple pipeline process for testing.
   *
   * @return ProcessSystem with pipeline
   */
  private ProcessSystem createSimplePipeline() {
    // Create feed fluid
    SystemInterface feed = new SystemSrkEos(353.15, 100.0);
    feed.addComponent("nitrogen", 0.005);
    feed.addComponent("CO2", 0.01);
    feed.addComponent("methane", 0.70);
    feed.addComponent("ethane", 0.08);
    feed.addComponent("propane", 0.06);
    feed.addComponent("n-butane", 0.04);
    feed.addComponent("n-pentane", 0.03);
    feed.addComponent("n-hexane", 0.025);
    feed.addComponent("n-heptane", 0.025);
    feed.addComponent("n-octane", 0.025);
    feed.setMixingRule("classic");
    feed.setMultiPhaseCheck(true);

    ProcessSystem process = new ProcessSystem();

    // Inlet stream
    Stream inlet = new Stream("Inlet", feed);
    inlet.setFlowRate(10000.0, "Sm3/day");
    inlet.setTemperature(80.0, "C");
    inlet.setPressure(100.0, "bara");
    process.add(inlet);

    // Simple pipeline
    AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", inlet);
    pipe.setLength(10000.0); // 10 km
    pipe.setDiameter(0.2); // 8 inch
    process.add(pipe);

    return process;
  }

  /**
   * Test VFP generator construction.
   */
  @Test
  void testConstruction() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    assertNotNull(generator);
  }

  /**
   * Test configuration.
   */
  @Test
  void testConfiguration() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");
    generator.setFlashGenerator(flashGenerator);

    generator.setFlowRates(new double[] {5000, 10000, 20000});
    generator.setOutletPressures(new double[] {50, 70});
    generator.setWaterCuts(new double[] {0.0, 0.20});
    generator.setGORs(new double[] {500, 1000});

    assertNotNull(generator.getFlowRates());
    assertEquals(3, generator.getFlowRates().length);
    assertEquals(2, generator.getOutletPressures().length);
    assertEquals(2, generator.getWaterCuts().length);
    assertEquals(2, generator.getGORs().length);
  }

  /**
   * Test validation of configuration.
   */
  @Test
  void testValidationMissingFlashGenerator() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setFlowRates(new double[] {5000, 10000});
    generator.setOutletPressures(new double[] {50, 70});
    generator.setWaterCuts(new double[] {0.0, 0.20});
    generator.setGORs(new double[] {500, 1000});

    // Should fail without flash generator
    assertThrows(IllegalStateException.class, () -> generator.generateVFPTable());
  }

  /**
   * Test validation of missing parameters.
   */
  @Test
  void testValidationMissingParameters() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");
    generator.setFlashGenerator(flashGenerator);

    // Missing flow rates
    generator.setOutletPressures(new double[] {50});
    generator.setWaterCuts(new double[] {0.0});
    generator.setGORs(new double[] {500});

    assertThrows(IllegalStateException.class, () -> generator.generateVFPTable());
  }

  /**
   * Test VFPTable methods.
   */
  @Test
  void testVFPTableMethods() {
    double[] rates = {5000, 10000};
    double[] thps = {50, 60};
    double[] wcs = {0.0, 0.30};
    double[] gors = {500, 1000};

    MultiScenarioVFPGenerator.VFPTable table =
        new MultiScenarioVFPGenerator.VFPTable(rates, thps, wcs, gors);

    assertEquals(16, table.getTotalPoints()); // 2*2*2*2
    assertEquals(0, table.getFeasibleCount()); // All NaN initially

    assertEquals(2, table.getFlowRates().length);
    assertEquals(2, table.getOutletPressures().length);
    assertEquals(2, table.getWaterCuts().length);
    assertEquals(2, table.getGORs().length);
  }

  /**
   * Test parallel execution setting.
   */
  @Test
  void testParallelExecutionSetting() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setEnableParallel(true);
    generator.setNumberOfWorkers(4);

    // Just verify setters work without exception
    assertDoesNotThrow(() -> generator.setEnableParallel(false));
    assertDoesNotThrow(() -> generator.setNumberOfWorkers(2));
  }

  /**
   * Test flow rate unit setting.
   */
  @Test
  void testFlowRateUnit() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setFlowRateUnit("kg/hr");
    assertEquals("kg/hr", generator.getFlowRateUnit());

    generator.setFlowRateUnit("Sm3/day");
    assertEquals("Sm3/day", generator.getFlowRateUnit());
  }

  /**
   * Test pressure settings.
   */
  @Test
  void testPressureSettings() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setMinInletPressure(20.0);
    generator.setMaxInletPressure(250.0);
    generator.setPressureTolerance(0.1);

    // Just verify setters work
    assertDoesNotThrow(() -> generator.setMinInletPressure(30.0));
    assertDoesNotThrow(() -> generator.setMaxInletPressure(200.0));
    assertDoesNotThrow(() -> generator.setPressureTolerance(0.5));
  }

  /**
   * Test inlet temperature setting.
   */
  @Test
  void testInletTemperatureSetting() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setInletTemperature(373.15); // 100°C
    assertEquals(373.15, generator.getInletTemperature(), 0.01);
  }

  /**
   * Test VFPTable print slice method.
   */
  @Test
  void testVFPTablePrintSlice() {
    double[] rates = {5000, 10000};
    double[] thps = {50, 60};
    double[] wcs = {0.0, 0.30};
    double[] gors = {500, 1000};

    MultiScenarioVFPGenerator.VFPTable table =
        new MultiScenarioVFPGenerator.VFPTable(rates, thps, wcs, gors);

    // Should not throw
    assertDoesNotThrow(() -> table.printSlice(0, 0));
  }

  /**
   * Test getting flash generator.
   */
  @Test
  void testGetFlashGenerator() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    generator.setFlashGenerator(flashGenerator);

    assertNotNull(generator.getFlashGenerator());
    assertEquals(flashGenerator, generator.getFlashGenerator());
  }

  /**
   * Test export without generating table first.
   */
  @Test
  void testExportWithoutGeneration() {
    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(processFactory, "Inlet", "Pipeline");

    assertThrows(IllegalStateException.class, () -> generator.toVFPEXPString(1));
  }

  /**
   * Test VFPTable BHP access methods.
   */
  @Test
  void testVFPTableBHPAccess() {
    double[] rates = {5000, 10000};
    double[] thps = {50, 60};
    double[] wcs = {0.0, 0.30};
    double[] gors = {500, 1000};

    MultiScenarioVFPGenerator.VFPTable table =
        new MultiScenarioVFPGenerator.VFPTable(rates, thps, wcs, gors);

    // Initially all NaN
    assertTrue(Double.isNaN(table.getBHP(0, 0, 0, 0)));

    // Initially not feasible
    assertFalse(table.isFeasible(0, 0, 0, 0));

    // Get the 4D array
    double[][][][] bhpArray = table.getBHPTable();
    assertNotNull(bhpArray);
    assertEquals(2, bhpArray.length);
    assertEquals(2, bhpArray[0].length);
    assertEquals(2, bhpArray[0][0].length);
    assertEquals(2, bhpArray[0][0][0].length);
  }

  /**
   * Test VFPTable flow rate unit.
   */
  @Test
  void testVFPTableFlowRateUnit() {
    double[] rates = {5000};
    double[] thps = {50};
    double[] wcs = {0.0};
    double[] gors = {500};

    MultiScenarioVFPGenerator.VFPTable table =
        new MultiScenarioVFPGenerator.VFPTable(rates, thps, wcs, gors);

    table.setFlowRateUnit("kg/hr");
    assertEquals("kg/hr", table.getFlowRateUnit());
  }

  /**
   * Test with ProcessSystem copy constructor.
   */
  @Test
  void testWithProcessSystemCopy() {
    ProcessSystem process = createSimplePipeline();

    MultiScenarioVFPGenerator generator =
        new MultiScenarioVFPGenerator(process, "Inlet", "Pipeline");

    assertNotNull(generator);
    // Just verify construction works - don't run full generation
  }
}
