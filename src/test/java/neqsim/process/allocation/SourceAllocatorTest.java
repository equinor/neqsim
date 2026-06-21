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
  void testAllocationFactorsRecoveryFactorsAndProductTotals() {
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

    // Allocation factors of a source over all custody outlets sum to one.
    double sumA = result.getAllocationFactor("Well-A", "ExportGas") + result.getAllocationFactor("Well-A", "ExportOil");
    double sumB = result.getAllocationFactor("Well-B", "ExportGas") + result.getAllocationFactor("Well-B", "ExportOil");
    assertEquals(1.0, sumA, 1.0e-6);
    assertEquals(1.0, sumB, 1.0e-6);

    // The gas-rich Well-A must send a larger fraction of itself to gas than the oil-rich Well-B.
    assertTrue(result.getAllocationFactor("Well-A", "ExportGas") > result.getAllocationFactor("Well-B", "ExportGas"),
	"gas-rich source should have a higher gas allocation factor");

    // Component recovery factors of a component over all custody outlets sum to one.
    double methaneRf = result.getComponentRecoveryFactor("ExportGas", "methane")
	+ result.getComponentRecoveryFactor("ExportOil", "methane");
    double decaneRf = result.getComponentRecoveryFactor("ExportGas", "nC10")
	+ result.getComponentRecoveryFactor("ExportOil", "nC10");
    assertEquals(1.0, methaneRf, 1.0e-6);
    assertEquals(1.0, decaneRf, 1.0e-6);

    // Methane is recovered mostly in gas; nC10 mostly in oil.
    assertTrue(result.getComponentRecoveryFactor("ExportGas", "methane") > 0.5);
    assertTrue(result.getComponentRecoveryFactor("ExportOil", "nC10") > 0.5);

    // Field product total equals the matching custody total.
    double fieldGas = result.getProductTotal(ProductType.GAS, "kg/hr");
    assertEquals(result.getCustodyTotal("ExportGas", "kg/hr"), fieldGas, 1.0e-6 + 1.0e-6 * fieldGas);
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

  @Test
  void testCharacterizedFluidWithPseudocomponents() {
    // Two wells whose heavy ends are described by TBP fractions and a lumped plus fraction, then
    // characterised into pseudocomponents. Both wells share the same characterisation scheme so the
    // generated pseudocomponent names line up on a single master slate.
    SystemSrkEos fluidA = new SystemSrkEos(298.15, 60.0);
    fluidA.addComponent("methane", 0.82);
    fluidA.addComponent("ethane", 0.06);
    fluidA.addComponent("propane", 0.03);
    fluidA.addTBPfraction("C7", 0.04, 0.10, 0.74);
    fluidA.addTBPfraction("C10", 0.03, 0.14, 0.79);
    fluidA.addPlusFraction("C12", 0.02, 0.21, 0.83);
    fluidA.getCharacterization().characterisePlusFraction();
    fluidA.setMixingRule("classic");

    SystemSrkEos fluidB = new SystemSrkEos(298.15, 60.0);
    fluidB.addComponent("methane", 0.30);
    fluidB.addComponent("ethane", 0.05);
    fluidB.addComponent("propane", 0.05);
    fluidB.addTBPfraction("C7", 0.18, 0.10, 0.74);
    fluidB.addTBPfraction("C10", 0.20, 0.14, 0.79);
    fluidB.addPlusFraction("C12", 0.22, 0.21, 0.83);
    fluidB.getCharacterization().characterisePlusFraction();
    fluidB.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feedA = new Stream("Well-A", fluidA);
    feedA.setFlowRate(1200.0, "kg/hr");
    feedA.setTemperature(45.0, "C");
    feedA.setPressure(60.0, "bara");
    process.add(feedA);

    Stream feedB = new Stream("Well-B", fluidB);
    feedB.setFlowRate(900.0, "kg/hr");
    feedB.setTemperature(45.0, "C");
    feedB.setPressure(60.0, "bara");
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

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Well-A", feedA);
    allocator.addSource("Well-B", feedB);
    allocator.addCustodyOutlet("ExportGas", separator.getGasOutStream(), ProductType.GAS);
    allocator.addCustodyOutlet("ExportOil", separator.getLiquidOutStream(), ProductType.OIL);

    ProductionAllocationResult result = allocator.allocate();
    assertNotNull(result);

    // The master slate must contain pseudocomponents (more than the four light components added).
    assertTrue(result.getComponentNames().length > 4,
	"expected pseudocomponents on the master slate, got " + result.getComponentNames().length);

    // Overall mass balance closes within 0.5% even with characterised heavy ends.
    double totalIn = result.getSourceTotal("Well-A", "kg/hr") + result.getSourceTotal("Well-B", "kg/hr");
    double totalOut = result.getCustodyTotal("ExportGas", "kg/hr") + result.getCustodyTotal("ExportOil", "kg/hr");
    assertEquals(totalIn, totalOut, 0.5e-2 * totalIn);

    // Well-level recovery factors of a component over all custody outlets sum to one.
    String heavy = null;
    double heaviestMass = -1.0;
    for (String name : result.getComponentNames()) {
      double mw = allocator.getExtractor().getMolarMass(name);
      if (mw > heaviestMass) {
	heaviestMass = mw;
	heavy = name;
      }
    }
    assertNotNull(heavy, "expected a heavy pseudocomponent on the slate");
    double rfSum = result.getComponentRecoveryFactor("ExportGas", heavy)
	+ result.getComponentRecoveryFactor("ExportOil", heavy);
    assertEquals(1.0, rfSum, 1.0e-6);

    // The heaviest pseudocomponent must be recovered predominantly in the oil custody stream.
    assertTrue(result.getComponentRecoveryFactor("ExportOil", heavy) > 0.5,
	"heaviest pseudocomponent should report mostly to oil");

    // Solver must remain well-posed on the larger pseudocomponent slate.
    assertTrue(result.getMaxResidual() < 1.0e-6, "residual too large: " + result.getMaxResidual());
  }
}
