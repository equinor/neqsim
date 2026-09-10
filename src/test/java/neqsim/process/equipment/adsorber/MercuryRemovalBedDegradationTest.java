package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Inventory and transient integration regressions for contaminant-blocked mercury beds. */
class MercuryRemovalBedDegradationTest extends neqsim.NeqSimTest {
  private MercuryRemovalBed bed;
  private Stream feed;

  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkCPAstatoil(303.15, 60.0);
    gas.addComponent("methane", 0.998);
    gas.addComponent("methanol", 0.002);
    gas.addComponent("mercury", 1.0e-9);
    gas.setMixingRule(10);
    gas.setMultiPhaseCheck(true);
    feed = new Stream("feed", gas);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();
    bed = new MercuryRemovalBed("blocked bed", feed);
    bed.setBedDiameter(2.0);
    bed.setBedLength(4.0);
    bed.setNumberOfCells(3);
    bed.setCalculatePressureDrop(false);
    bed.run();
    bed.setCalculateSteadyState(false);
  }

  @Test
  void fullyBlockedFreshBedKeepsFiniteProfilesAndPassesMercuryAfterFlushing() {
    blockMesopores();
    bed.runTransient(50.0 * gasResidenceTime(), UUID.randomUUID());
    assertEquals(0.0, bed.getAverageLoading(), 0.0);
    assertFiniteProfiles();
    assertEquals(mercuryMoles(feed), mercuryMoles(bed.getOutletStream()), mercuryMoles(feed) * 1.0e-8);
    assertTrue(bed.isBreakthroughOccurred());
  }

  @Test
  void fullBlockagePreservesCapturedMercury() {
    bed.preloadBed(0.5);
    double[] retained = bed.getLoadingProfile();
    blockMesopores();
    bed.runTransient(0.1, UUID.randomUUID());
    assertArrayEquals(retained, bed.getLoadingProfile(), 0.0);
    assertFiniteProfiles();
  }

  @Test
  void partialBlockageBelowExistingLoadingPreservesCapturedMercury() {
    bed.preloadBed(0.5);
    double[] retained = bed.getLoadingProfile();
    bed.setSorbentPoreRadius(1.0);
    bed.applyContaminantDegradation("methanol", methanolMoleFraction() / 0.5);
    assertTrue(bed.getDegradationFactor() > 0.0 && bed.getDegradationFactor() < 0.5);
    bed.runTransient(0.1, UUID.randomUUID());
    assertArrayEquals(retained, bed.getLoadingProfile(), 0.0);
    assertFiniteProfiles();
  }

  @Test
  void fullyBlockedSteadyStateBedPassesMercury() {
    blockMesopores();
    bed.run();
    assertEquals(mercuryMoles(feed), mercuryMoles(bed.getOutletStream()), mercuryMoles(feed) * 1.0e-10);
  }

  @ParameterizedTest
  @ValueSource(doubles = { 1.0e-10, 100000.0 })
  void adsorptionConservesMercuryAtCapacityAndGasInventoryLimits(double capacity) {
    bed.setMaxMercuryCapacity(capacity);
    bed.setReactionRateConstant(1.0e8);
    // One substep from an empty gas grid: no mercury has reached the downstream cell face.
    double dt = 0.25 * gasResidenceTime() / bed.getNumberOfCells();
    double inletMercuryMg = mercuryMoles(feed) * 200.59 * 1.0e3 * dt;
    bed.runTransient(dt, UUID.randomUUID());
    double cellVoidVolume = bed.getBedVolume() * bed.getVoidFraction() / bed.getNumberOfCells();
    double cellSorbentMass = bed.getSorbentMass() / bed.getNumberOfCells();
    double retainedMercuryMg = 0.0;
    double[] loading = bed.getLoadingProfile();
    double[] concentration = bed.getConcentrationProfile();
    for (int cell = 0; cell < bed.getNumberOfCells(); cell++) {
      retainedMercuryMg += loading[cell] * cellSorbentMass + concentration[cell] * cellVoidVolume * 1.0e-3;
      assertTrue(loading[cell] <= capacity * (1.0 + 1.0e-12));
    }
    assertFiniteProfiles();
    assertTrue(bed.getAverageLoading() > 0.0);
    assertEquals(inletMercuryMg, retainedMercuryMg, inletMercuryMg * 1.0e-10,
        "Adsorbed mercury plus gas inventory must equal the first-substep inlet inventory");
  }

  private void blockMesopores() {
    bed.setSorbentPoreRadius(6.0);
    // A controlled saturation reference exercises the fully filled Kelvin branch.
    MercuryRemovalBed.ContaminantAssessment assessment = bed.applyContaminantDegradation("methanol",
        methanolMoleFraction() / 0.99);
    assertEquals(1.0, assessment.poreFillingFraction, 0.0);
    assertEquals(0.0, bed.getDegradationFactor(), 0.0);
  }

  private double methanolMoleFraction() {
    return bed.getOutletStream().getThermoSystem().getPhase(0).getComponent("methanol").getx();
  }

  private double gasResidenceTime() {
    SystemInterface gas = feed.getThermoSystem();
    double volumeFlow = gas.getTotalNumberOfMoles() / gas.getPhase(0).getDensity("mol/m3");
    return bed.getBedVolume() * bed.getVoidFraction() / volumeFlow;
  }

  private double mercuryMoles(neqsim.process.equipment.stream.StreamInterface stream) {
    return stream.getThermoSystem().getPhase(0).getComponent("mercury").getNumberOfmoles();
  }

  private void assertFiniteProfiles() {
    for (double loading : bed.getLoadingProfile()) {
      assertTrue(Double.isFinite(loading) && loading >= 0.0);
    }
    for (double concentration : bed.getConcentrationProfile()) {
      assertTrue(Double.isFinite(concentration) && concentration >= 0.0);
    }
  }
}
