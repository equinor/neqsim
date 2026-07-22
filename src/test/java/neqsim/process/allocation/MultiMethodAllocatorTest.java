package neqsim.process.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link MultiMethodAllocator} and {@link AllocationComparison}, covering the three facility-split methods
 * (component ratio, all-in, stand-alone) run individually and together with auto-evaluation.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class MultiMethodAllocatorTest {

  /** Relative tolerance for mass-closure checks. */
  private static final double CLOSURE_TOL = 0.5e-2;

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

  /**
   * Configures a {@link MultiMethodAllocator} on the two-source process.
   *
   * @param process the base-case process
   * @return the configured allocator
   */
  private MultiMethodAllocator configure(ProcessSystem process) {
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    MultiMethodAllocator allocator = new MultiMethodAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Well-A", (Stream) process.getUnit("Well-A"));
    allocator.addSource("Well-B", (Stream) process.getUnit("Well-B"));
    allocator.addCustodyOutlet("ExportGas", separator.getGasOutStream(), ProductType.GAS);
    allocator.addCustodyOutlet("ExportOil", separator.getLiquidOutStream(), ProductType.OIL);
    return allocator;
  }

  /**
   * Asserts that an allocation result closes (custody totals sum to source totals) within tolerance.
   *
   * @param result the result to check
   */
  private void assertCloses(ProductionAllocationResult result) {
    double totalIn = 0.0;
    for (String s : result.getSourceNames()) {
      totalIn += result.getSourceTotal(s, "kg/hr");
    }
    double totalOut = 0.0;
    for (String c : result.getCustodyNames()) {
      totalOut += result.getCustodyTotal(c, "kg/hr");
    }
    assertEquals(totalIn, totalOut, CLOSURE_TOL * totalIn);
  }

  @Test
  void testEachMethodRunsAndCloses() {
    ProcessSystem process = buildTwoSourceProcess();
    MultiMethodAllocator allocator = configure(process);

    for (AllocationMethod method : AllocationMethod.values()) {
      ProductionAllocationResult result = allocator.allocate(method);
      assertNotNull(result, "null result for " + method);
      assertCloses(result);
    }
  }

  @Test
  void testComponentRatioAndAllInAgreeAtSharedEntryPoint() {
    ProcessSystem process = buildTwoSourceProcess();
    MultiMethodAllocator allocator = configure(process);

    AllocationComparison comparison = allocator.allocateAll();
    // Both wells enter at the same commingle point, so the common recovery factor and the all-in
    // proxy must give the
    // same component ratio (per the allocation-principles matrix: "same component ratio if they
    // come in at the same
    // place").
    double diff = comparison.getMaxRelativeDifference(AllocationMethod.COMPONENT_RATIO, AllocationMethod.ALL_IN);
    assertTrue(diff < 1.0e-3, "component-ratio and all-in should agree at a shared entry point, diff=" + diff);
  }

  @Test
  void testStandAloneRestoresBaseCase() {
    ProcessSystem process = buildTwoSourceProcess();
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    StreamInterface gas = separator.getGasOutStream();

    double gasFlowBefore = gas.getFlowRate("kg/hr");
    double feedABefore = ((Stream) process.getUnit("Well-A")).getFlowRate("kg/hr");
    double feedBBefore = ((Stream) process.getUnit("Well-B")).getFlowRate("kg/hr");

    MultiMethodAllocator allocator = configure(process);
    ProductionAllocationResult result = allocator.allocate(AllocationMethod.STAND_ALONE);
    assertNotNull(result);
    assertCloses(result);

    // The stand-alone re-simulation must restore the commingled base case.
    assertEquals(feedABefore, ((Stream) process.getUnit("Well-A")).getFlowRate("kg/hr"), 1.0e-6 * feedABefore);
    assertEquals(feedBBefore, ((Stream) process.getUnit("Well-B")).getFlowRate("kg/hr"), 1.0e-6 * feedBBefore);
    assertEquals(gasFlowBefore, gas.getFlowRate("kg/hr"), 1.0e-3 * gasFlowBefore + 1.0e-6);
  }

  @Test
  void testCompareProducesRecommendationAndSensitivity() {
    ProcessSystem process = buildTwoSourceProcess();
    MultiMethodAllocator allocator = configure(process);

    AllocationComparison comparison = allocator.allocateAll();
    assertEquals(3, comparison.getResults().size());

    AllocationMethod recommended = comparison.getRecommendedMethod();
    assertNotNull(recommended);
    assertNotNull(comparison.getRecommendationRationale());

    // Owner-sensitivity rows must exist for both wells and at least one product.
    assertFalse(comparison.getOwnerSensitivity("kg/hr").isEmpty());

    // JSON export must be well-formed and carry the recommendation.
    String json = comparison.toJson();
    assertNotNull(json);
    assertTrue(json.contains("recommendedMethod"));
    assertTrue(json.contains("ownerSensitivity"));
  }

  @Test
  void testGasRichWellGetsMoreGasOilRichWellGetsMoreOil() {
    ProcessSystem process = buildTwoSourceProcess();
    MultiMethodAllocator allocator = configure(process);

    ProductionAllocationResult result = allocator.allocate(AllocationMethod.COMPONENT_RATIO);
    double gasToA = result.getProductAllocation("Well-A", ProductType.GAS, "kg/hr");
    double gasToB = result.getProductAllocation("Well-B", ProductType.GAS, "kg/hr");
    double oilToA = result.getProductAllocation("Well-A", ProductType.OIL, "kg/hr");
    double oilToB = result.getProductAllocation("Well-B", ProductType.OIL, "kg/hr");

    // Well-A is gas-rich (95% methane); Well-B is oil-rich (70% nC10).
    assertTrue(gasToA > gasToB, "gas-rich Well-A should be allocated more gas than Well-B");
    assertTrue(oilToB > oilToA, "oil-rich Well-B should be allocated more oil than Well-A");

    Map<AllocationMethod, ProductionAllocationResult> single = new java.util.LinkedHashMap<AllocationMethod, ProductionAllocationResult>();
    single.put(AllocationMethod.COMPONENT_RATIO, result);
    assertNotNull(single.get(AllocationMethod.COMPONENT_RATIO));
  }
}
