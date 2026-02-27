package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.interfaceproperties.solidadsorption.IsothermType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for AdsorptionCycleController: PSA and TSA cycle scheduling.
 *
 * @author Even Solbraa
 */
public class AdsorptionCycleControllerTest {
  private SystemInterface testGas;
  private StreamInterface feedStream;
  private AdsorptionBed bed;

  /**
   * Set up test fixtures.
   */
  @BeforeEach
  public void setUp() {
    testGas = new SystemSrkEos(298.15, 10.0);
    testGas.addComponent("methane", 0.90);
    testGas.addComponent("CO2", 0.10);
    testGas.setMixingRule("classic");
    testGas.init(0);

    feedStream = new Stream("feed", testGas);
    feedStream.setFlowRate(500.0, "kg/hr");
    feedStream.run();

    bed = new AdsorptionBed("PSA_Bed", feedStream);
    bed.setBedDiameter(0.8);
    bed.setBedLength(2.0);
    bed.setAdsorbentMaterial("AC");
    bed.setIsothermType(IsothermType.LANGMUIR);
    bed.setNumberOfCells(10);
  }

  /**
   * Test PSA cycle configuration.
   */
  @Test
  public void testPSACycleConfiguration() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(600, 60, 120, 60, 1.0);

    List<AdsorptionCycleController.PhaseStep> schedule = controller.getSchedule();
    assertEquals(4, schedule.size());
    assertEquals(AdsorptionCycleController.CyclePhase.ADSORPTION, schedule.get(0).getPhase());
    assertEquals(AdsorptionCycleController.CyclePhase.BLOWDOWN, schedule.get(1).getPhase());
    assertEquals(AdsorptionCycleController.CyclePhase.PURGE, schedule.get(2).getPhase());
    assertEquals(AdsorptionCycleController.CyclePhase.REPRESSURISATION, schedule.get(3).getPhase());

    assertEquals(600.0, schedule.get(0).getDuration(), 1e-10);
    assertEquals(1.0, schedule.get(1).getTargetPressure(), 1e-10);
  }

  /**
   * Test TSA cycle configuration.
   */
  @Test
  public void testTSACycleConfiguration() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configureTSA(3600, 1800, 1200, 473.15); // 200 degC desorption

    List<AdsorptionCycleController.PhaseStep> schedule = controller.getSchedule();
    assertEquals(3, schedule.size());
    assertEquals(AdsorptionCycleController.CyclePhase.ADSORPTION, schedule.get(0).getPhase());
    assertEquals(AdsorptionCycleController.CyclePhase.DESORPTION, schedule.get(1).getPhase());
    assertEquals(AdsorptionCycleController.CyclePhase.COOLING, schedule.get(2).getPhase());

    assertEquals(473.15, schedule.get(1).getTargetTemperature(), 1e-10);
  }

  /**
   * Test cycle phase transitions.
   */
  @Test
  public void testCyclePhaseTransitions() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(10, 5, 5, 5, 1.0); // short durations for testing
    controller.setAutoLoop(false);

    UUID id = UUID.randomUUID();

    // Initially in ADSORPTION
    assertEquals(AdsorptionCycleController.CyclePhase.ADSORPTION, controller.getCurrentPhase());

    // Advance past adsorption phase
    controller.advance(11.0, id);
    assertEquals(AdsorptionCycleController.CyclePhase.BLOWDOWN, controller.getCurrentPhase());

    // Advance past blowdown
    controller.advance(6.0, id);
    assertEquals(AdsorptionCycleController.CyclePhase.PURGE, controller.getCurrentPhase());

    // Advance past purge
    controller.advance(6.0, id);
    assertEquals(AdsorptionCycleController.CyclePhase.REPRESSURISATION,
        controller.getCurrentPhase());
  }

  /**
   * Test cycle counter.
   */
  @Test
  public void testCycleCounter() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(5, 2, 2, 1, 1.0);
    controller.setAutoLoop(true);

    UUID id = UUID.randomUUID();
    assertEquals(0, controller.getCompletedCycles());

    // Run through one complete cycle (5+2+2+1 = 10 seconds)
    for (int i = 0; i < 12; i++) {
      controller.advance(1.0, id);
    }
    assertTrue(controller.getCompletedCycles() >= 1, "Should have completed at least 1 cycle");
  }

  /**
   * Test custom step addition.
   */
  @Test
  public void testCustomStepAddition() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.addStep(new AdsorptionCycleController.PhaseStep(
        AdsorptionCycleController.CyclePhase.ADSORPTION, 300));
    controller.addStep(new AdsorptionCycleController.PhaseStep(
        AdsorptionCycleController.CyclePhase.COCURRENT_DEPRESSURISATION, 30, 5.0, -1.0));
    controller.addStep(new AdsorptionCycleController.PhaseStep(
        AdsorptionCycleController.CyclePhase.BLOWDOWN, 60, 1.0, -1.0));
    controller.addStep(
        new AdsorptionCycleController.PhaseStep(AdsorptionCycleController.CyclePhase.STANDBY, 30));

    assertEquals(4, controller.getSchedule().size());
    assertEquals(AdsorptionCycleController.CyclePhase.COCURRENT_DEPRESSURISATION,
        controller.getSchedule().get(1).getPhase());
  }

  /**
   * Test controller reset.
   */
  @Test
  public void testControllerReset() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(5, 2, 2, 1, 1.0);

    UUID id = UUID.randomUUID();
    controller.advance(6.0, id); // past adsorption
    assertFalse(controller.getCurrentPhase() == AdsorptionCycleController.CyclePhase.ADSORPTION);

    controller.reset();
    assertEquals(AdsorptionCycleController.CyclePhase.ADSORPTION, controller.getCurrentPhase());
    assertEquals(0, controller.getCompletedCycles());
    assertEquals(0.0, controller.getTimeInCurrentStep(), 1e-10);
  }

  /**
   * Test that applying conditions sets bed to desorption during blowdown.
   */
  @Test
  public void testBlowdownSetsDesorptionMode() {
    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(5, 10, 10, 5, 1.5);

    UUID id = UUID.randomUUID();

    // During adsorption phase
    controller.advance(1.0, id);
    assertFalse(bed.isDesorptionMode());

    // Transition to blowdown
    controller.advance(5.0, id);
    assertTrue(bed.isDesorptionMode(), "Bed should be in desorption mode during blowdown");
  }

  /**
   * Test integrated transient simulation with cycle controller.
   */
  @Test
  public void testIntegratedTransientWithCycle() {
    bed.setCalculateSteadyState(false);
    bed.setKLDF(0.05);
    bed.setNumberOfCells(10);

    AdsorptionCycleController controller = new AdsorptionCycleController(bed);
    controller.configurePSA(20, 5, 5, 5, 1.0);
    controller.setAutoLoop(false);

    UUID id = UUID.randomUUID();

    // Run adsorption for 15 s
    for (int i = 0; i < 15; i++) {
      controller.advance(1.0, id);
      bed.runTransient(1.0, id);
    }

    assertEquals(AdsorptionCycleController.CyclePhase.ADSORPTION, controller.getCurrentPhase());
    double loadingDuringAdsorption = bed.getAverageLoading(1);
    assertTrue(loadingDuringAdsorption > 0, "Should have positive loading during adsorption phase");
  }
}
