package neqsim.process.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the linear recovery-factor production allocation package.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SourceAllocatorTest {

  /** Absolute tolerance for molar-flow comparisons (mole/sec). */
  private static final double TOL = 1.0e-6;

  /**
   * Builds a two-source commingled process: a gas-rich source and an oil-rich source mixed together and flashed in a
   * separator that yields a gas and a liquid custody stream.
   *
   * @return the assembled and solved process system
   */
  private ProcessSystem buildTwoSourceProcess() {
    SystemSrkEos fluidA = new SystemSrkEos(298.15, 50.0);
    fluidA.addComponent("methane", 0.95);
    fluidA.addComponent("nC10", 0.05);
    fluidA.setMixingRule("classic");

    SystemSrkEos fluidB = new SystemSrkEos(298.15, 50.0);
    fluidB.addComponent("methane", 0.30);
    fluidB.addComponent("nC10", 0.70);
    fluidB.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feedA = new Stream("Well-A", fluidA);
    feedA.setFlowRate(1000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(50.0, "bara");
    process.add(feedA);

    Stream feedB = new Stream("Well-B", fluidB);
    feedB.setFlowRate(1500.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(50.0, "bara");
    process.add(feedB);

    Mixer mixer = new Mixer("Commingle");
    mixer.addStream(feedA);
    mixer.addStream(feedB);
    process.add(mixer);

    Separator separator = new Separator("Inlet Separator", mixer.getOutletStream());
    process.add(separator);

    Stream gas = new Stream("ExportGas", separator.getGasOutStream());
    process.add(gas);

    Stream oil = new Stream("ExportOil", separator.getLiquidOutStream());
    process.add(oil);

    process.run();
    return process;
  }

  @Test
  void testExplicitAllocationConservesAndMatchesAnalytic() {
    ProcessSystem process = buildTwoSourceProcess();

    Stream feedA = (Stream) process.getUnit("Well-A");
    Stream feedB = (Stream) process.getUnit("Well-B");
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    StreamInterface gas = separator.getGasOutStream();
    StreamInterface oil = separator.getLiquidOutStream();

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Well-A", feedA);
    allocator.addSource("Well-B", feedB);
    allocator.addCustodyOutlet("ExportGas", gas, ProductType.GAS);
    allocator.addCustodyOutlet("ExportOil", oil, ProductType.OIL);

    ProductionAllocationResult result = allocator.allocate();
    assertNotNull(result);

    // Base-case component flows (mole/sec) on the gas custody stream.
    double gasMethaneBase = RecoveryFactorExtractor.componentFlow(gas, "methane");
    double aMethaneIn = RecoveryFactorExtractor.componentFlow(feedA, "methane");
    double bMethaneIn = RecoveryFactorExtractor.componentFlow(feedB, "methane");
    double totalMethaneIn = aMethaneIn + bMethaneIn;

    double allocGasMethaneA = result.getAllocatedComponentFlow("Well-A", "ExportGas", "methane", "mole/sec");
    double allocGasMethaneB = result.getAllocatedComponentFlow("Well-B", "ExportGas", "methane", "mole/sec");

    // Conservation: the two sources must add up to the base-case gas methane flow.
    assertEquals(gasMethaneBase, allocGasMethaneA + allocGasMethaneB, TOL * gasMethaneBase + TOL);

    // Analytic superposition: gas methane splits in proportion to each source's methane feed.
    double expectedA = gasMethaneBase * aMethaneIn / totalMethaneIn;
    double expectedB = gasMethaneBase * bMethaneIn / totalMethaneIn;
    assertEquals(expectedA, allocGasMethaneA, 1.0e-4 * gasMethaneBase + TOL);
    assertEquals(expectedB, allocGasMethaneB, 1.0e-4 * gasMethaneBase + TOL);

    // Total mass into the system equals total mass out across custody outlets.
    double totalIn = result.getSourceTotal("Well-A", "kg/hr") + result.getSourceTotal("Well-B", "kg/hr");
    double totalOut = result.getCustodyTotal("ExportGas", "kg/hr") + result.getCustodyTotal("ExportOil", "kg/hr");
    assertEquals(totalIn, totalOut, 0.5e-2 * totalIn);
  }

  @Test
  void testAutoDetectionConserves() {
    ProcessSystem process = buildTwoSourceProcess();

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);

    ProductionAllocationResult result = allocator.allocate();
    assertNotNull(result);

    // Two external feeds and two terminal product streams must be auto-detected.
    assertEquals(2, result.getSourceNames().length);
    assertEquals(2, result.getCustodyNames().length);

    // Overall mass balance closes within 0.5%.
    double totalIn = 0.0;
    for (String s : result.getSourceNames()) {
      totalIn += result.getSourceTotal(s, "kg/hr");
    }
    double totalOut = 0.0;
    for (String c : result.getCustodyNames()) {
      totalOut += result.getCustodyTotal(c, "kg/hr");
    }
    assertEquals(totalIn, totalOut, 0.5e-2 * totalIn);

    // Solver residual must be tiny (well-posed acyclic network).
    assertTrue(result.getMaxResidual() < 1.0e-6, "residual too large: " + result.getMaxResidual());
  }

  @Test
  void testProductAggregationAndJson() {
    ProcessSystem process = buildTwoSourceProcess();
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    StreamInterface gas = separator.getGasOutStream();
    StreamInterface oil = separator.getLiquidOutStream();

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Well-A", (Stream) process.getUnit("Well-A"));
    allocator.addSource("Well-B", (Stream) process.getUnit("Well-B"));
    allocator.addCustodyOutlet("ExportGas", gas, ProductType.GAS);
    allocator.addCustodyOutlet("ExportOil", oil, ProductType.OIL);

    ProductionAllocationResult result = allocator.allocate();

    // Gas product aggregation equals the single gas custody outlet allocation.
    double gasFromA = result.getProductAllocation("Well-A", ProductType.GAS, "kg/hr");
    double gasCustodyA = result.getAllocatedFlow("Well-A", "ExportGas", "kg/hr");
    assertEquals(gasCustodyA, gasFromA, TOL + 1.0e-6 * Math.abs(gasCustodyA));

    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("schemaVersion"));
    assertTrue(json.contains("Well-A"));
    assertTrue(json.contains("ExportGas"));
  }

  @Test
  void testProductTypeInference() {
    ProcessSystem process = buildTwoSourceProcess();
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    StreamInterface gas = separator.getGasOutStream();
    StreamInterface oil = separator.getLiquidOutStream();

    assertEquals(ProductType.GAS, SourceAllocator.inferProductType(gas));
    assertEquals(ProductType.OIL, SourceAllocator.inferProductType(oil));
  }

  @Test
  void testChainTopologySingleSource() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("nC10", 0.03);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(2000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Cooler cooler = new Cooler("Gas Cooler", separator.getGasOutStream());
    cooler.setOutTemperature(20.0, "C");
    process.add(cooler);

    Stream gas = new Stream("SalesGas", cooler.getOutletStream());
    process.add(gas);
    Stream liquid = new Stream("Condensate", separator.getLiquidOutStream());
    process.add(liquid);

    process.run();

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Feed", feed);
    allocator.addCustodyOutlet("SalesGas", cooler.getOutletStream(), ProductType.GAS);
    allocator.addCustodyOutlet("Condensate", separator.getLiquidOutStream(), ProductType.OIL);

    ProductionAllocationResult result = allocator.allocate();

    // Single source: it must receive 100% of every custody outlet.
    double feedTotal = result.getSourceTotal("Feed", "kg/hr");
    double custodyTotal = result.getCustodyTotal("SalesGas", "kg/hr") + result.getCustodyTotal("Condensate", "kg/hr");
    assertEquals(custodyTotal, feedTotal, 0.5e-2 * custodyTotal);
    assertTrue(result.getMaxResidual() < 1.0e-6);
  }
}
