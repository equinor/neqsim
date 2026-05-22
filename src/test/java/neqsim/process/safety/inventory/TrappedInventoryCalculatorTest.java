package neqsim.process.safety.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.barrier.DocumentEvidence;
import neqsim.process.safety.depressurization.DepressurizationSimulator;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link TrappedInventoryCalculator}.
 *
 * @author ESOL
 * @version 1.0
 */
class TrappedInventoryCalculatorTest {

  /**
   * Verifies pipe and equipment volume inventory calculations for a gas case.
   */
  @Test
  void calculatesPipeAndEquipmentGasInventory() {
    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(10.0, "bara", 27.0, "C")
        .addPipeSegment("P-001", 0.10, 10.0, 0.0, createEvidence())
        .addEquipmentVolume("V-001", 1.0, 0.0, createEvidence());

    InventoryResult result = calculator.calculate();

    double expectedPipeVolume = Math.PI * 0.10 * 0.10 * 10.0 / 4.0;
    assertEquals(1.0 + expectedPipeVolume, result.getTotalVolumeM3(), 1.0e-10);
    assertEquals(result.getTotalVolumeM3(), result.getTotalGasVolumeM3(), 1.0e-10);
    assertTrue(result.getTotalGasMassKg() > 0.0);
    assertEquals(2, result.getSegmentResults().size());
    assertTrue(result.toJson().contains("P-001"));
  }

  /**
   * Verifies unit conversion for pipe and volume inputs.
   */
  @Test
  void convertsEngineeringUnits() {
    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(145.0377, "psia", 80.6, "F")
        .addPipeSegment("P-UNIT", 4.0, "in", 32.80839895, "ft", 0.0, createEvidence())
        .addVolumeSegment("V-UNIT", 1000.0, "L", 0.0, createEvidence());

    InventoryResult result = calculator.calculate();

    double expectedPipeVolume = Math.PI * 0.1016 * 0.1016 * 10.0 / 4.0;
    assertEquals(1.0 + expectedPipeVolume, result.getTotalVolumeM3(), 1.0e-6);
    assertEquals(10.0, result.getPressureBara(), 1.0e-3);
    assertEquals(300.15, result.getTemperatureK(), 1.0e-10);
  }

  /**
   * Verifies fallback liquid inventory handling.
   */
  @Test
  void calculatesLiquidHoldupWithFallbackDensity() {
    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(10.0, 300.15).setFallbackLiquidDensity(900.0)
        .addEquipmentVolume("KO-001", 2.0, 0.25, createEvidence());

    InventoryResult result = calculator.calculate();

    assertEquals(0.5, result.getTotalLiquidVolumeM3(), 1.0e-12);
    assertEquals(450.0, result.getTotalLiquidMassKg(), 1.0e-12);
    assertTrue(result.getTotalGasMassKg() > 0.0);
    assertEquals(1, result.getWarnings().size());
  }

  /**
   * Verifies invalid dimensions and fill fractions are rejected.
   */
  @Test
  void rejectsInvalidInputs() {
    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(10.0, 300.15);

    assertThrows(IllegalArgumentException.class,
        () -> calculator.addPipeSegment("bad", -0.1, 10.0, 0.0, null));
    assertThrows(IllegalArgumentException.class,
        () -> calculator.addEquipmentVolume("bad-fill", 1.0, 1.2, null));
  }

  /**
   * Verifies evidence is retained in JSON output.
   */
  @Test
  void preservesEvidenceInJson() {
    InventoryResult result = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(10.0, 300.15)
        .addEquipmentVolume("V-TRACE", 1.0, 0.0, createEvidence()).calculate();

    assertTrue(result.toJson().contains("S1-AA-PBP-2340"));
    assertTrue(result.toJson().contains("traceable"));
  }

  /**
   * Verifies missing document evidence is visible as a warning.
   */
  @Test
  void warnsWhenEvidenceIsMissing() {
    InventoryResult result = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(10.0, 300.15).addEquipmentVolume("V-NO-EVIDENCE", 1.0, 0.0,
            null)
        .calculate();

    assertEquals(1, result.getWarnings().size());
    assertTrue(result.getWarnings().get(0).contains("V-NO-EVIDENCE"));
  }

  /**
   * Verifies the calculated gas inventory can initialize a depressurization simulation.
   */
  @Test
  void createsFluidForDepressurizationSimulator() {
    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(createGas())
        .setOperatingConditions(20.0, 313.15).addEquipmentVolume("V-BD", 1.0, 0.0,
            createEvidence());

    SystemInterface lumpedFluid = calculator.createDepressurizationFluid();
    DepressurizationSimulator simulator = new DepressurizationSimulator(lumpedFluid, 1.0, 0.015,
        0.72, 1.5e5).setMaxTime(30.0).setTimeStep(1.0);
    DepressurizationResult result = simulator.run();

    assertNotNull(result);
    assertTrue(result.time.size() > 1);
    assertTrue(result.massKg.get(0) > result.massKg.get(result.massKg.size() - 1));
  }

  /**
   * Verifies the documented workflow for inventory and depressurization handoff.
   */
  @Test
  void documentedWorkflowCalculatesInventoryAndCreatesBlowdownFluid() {
    SystemInterface gas = new SystemSrkEos(300.15, 20.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");

    DocumentEvidence evidence = new DocumentEvidence("E-INV-001", "P-ID-001",
        "Gas recompression P&ID", "A", "isolation boundary", 1,
        "references/P-ID-001.pdf",
        "Pipe P-100 and compressor casing inside the isolated boundary.", 0.90);

    TrappedInventoryCalculator calculator = new TrappedInventoryCalculator().setFluid(gas)
        .setOperatingConditions(20.0, "bara", 27.0, "C")
        .addPipeSegment("P-100", 0.1524, 18.0, 0.0, evidence)
        .addEquipmentVolume("K-100 casing", 0.75, 0.0, evidence);

    InventoryResult inventory = calculator.calculate();
    double gasMassKg = inventory.getTotalGasMassKg();
    double isolatedVolumeM3 = inventory.getTotalVolumeM3();
    String inventoryJson = calculator.toJson();

    SystemInterface blowdownFluid = calculator.createDepressurizationFluid();
    DepressurizationSimulator simulator = new DepressurizationSimulator(blowdownFluid,
        inventory.getTotalGasVolumeM3(), 0.010, 0.72, 1.5e5);
    simulator.setMaxTime(300.0);
    simulator.setTimeStep(1.0);
    DepressurizationResult blowdown = simulator.run();

    assertTrue(gasMassKg > 0.0);
    assertTrue(isolatedVolumeM3 > 0.75);
    assertTrue(inventoryJson.contains("P-100"));
    assertTrue(inventory.getWarnings().isEmpty());
    assertNotNull(blowdown);
    assertTrue(blowdown.time.size() > 1);
  }

  /**
   * Creates a methane-rich SRK gas fluid for tests.
   *
   * @return configured test gas
   */
  private SystemInterface createGas() {
    SystemInterface gas = new SystemSrkEos(300.15, 10.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.07);
    gas.addComponent("propane", 0.03);
    gas.setMixingRule("classic");
    return gas;
  }

  /**
   * Creates reusable document evidence for tests.
   *
   * @return traceable document evidence
   */
  private DocumentEvidence createEvidence() {
    return new DocumentEvidence("E-INV-001", "S1-AA-PBP-2340",
        "P&ID gas recompression systems recompressor", "M5", "zone C4", 1,
        "references/S1-AA-PBP-2340.pdf", "23A-KA01 isolated volume boundary.", 0.90);
  }
}
