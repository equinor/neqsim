package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * VU recovery cases captured from the synthetic three-phase separator replay in equinor/neqsim#5036.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class OptimizedVUflashThreePhaseRecoveryTest {
  private static final int MAXIMUM_RECOVERY_FLASH_COUNT = 1 + 10 * (4 + 8);
  private static final String[] COMPONENTS = {"methane", "ethane", "propane", "n-pentane", "n-heptane", "nC10",
      "water"};
  // The standard path and the recovery path may converge to the same state on different JVMs.
  private static final double RELATIVE_TOLERANCE = 1.0e-6;
  private static final double STATE_TOLERANCE = 5.0e-5;

  /** A small inventory update near a valid three-phase state previously failed volume closure by 1.83%. */
  @Test
  void closesBothSpecificationsAfterThreePhaseInventoryUpdate() {
    double[] moles = {37705.789308905780, 5446.1472769755470, 5294.7532396646775, 15522.445191714109,
        16212.248545495933, 11678.615342819125, 75766.394643813920};
    assertRecovered(moles, 167626.39354938918, 56.640602478850540, 315.95412020422873, 21.991148575128552,
        -4821082094.4702580, 56.64217742, 315.99809000);
  }

  /** A second three-phase condition from the perturbed choke case requires a different pressure correction. */
  @Test
  void retainsGasOilAndWaterWhenThePerturbedVesselStateIsRecovered() {
    double[] moles = {66177.389371868900, 9683.2918067908120, 9475.6690000829170, 27922.230755537880,
        29191.816697363340, 21032.989274650463, 135085.54892445830};
    assertRecovered(moles, 298568.93583075160, 56.778313535479460, 318.94441706793160, 39.269908169872416,
        -8510817029.3287950, 56.77548215, 318.97997578);
  }

  /** Recovered active phases keep a distinct slot available for later phase appearance. */
  @Test
  void recoveryRetainsAnIndependentInactivePhaseSlot() {
    SystemInterface seed = new SystemSrkEos(298.15, 70.0);
    seed.addComponent("methane", 0.7);
    seed.addComponent("n-heptane", 0.2);
    seed.addComponent("water", 0.1);
    seed.setMixingRule("classic");
    seed.setMultiPhaseCheck(true);
    new ThermodynamicOperations(seed).TPflash();
    assertEquals(3, seed.getNumberOfPhases());

    SystemInterface candidate = seed.clone();
    candidate.setNumberOfPhases(2);
    candidate.setBeta(0, 0.4);
    candidate.setBeta(1, 0.6);
    SystemInterface destination = seed.clone();
    destination.removePhaseKeepTotalComposition(1);
    OptimizedVUflash.installEquilibrium(destination, candidate);

    assertEquals(2, destination.getNumberOfPhases());
    assertNotSame(candidate.getPhase(1), destination.getPhase(1));
    destination.addPhase();
    assertEquals(3, destination.getNumberOfPhases());
    assertNotSame(destination.getPhase(1), destination.getPhase(2));
  }

  /**
   * Verify three-phase recovery against independent equilibrium pressure and temperature and the two specifications.
   *
   * @param moles conserved component inventory in mol
   * @param totalMoles vessel total after its last inventory update in mol
   * @param initialPressure incoming pressure in bara
   * @param initialTemperature incoming temperature in K
   * @param targetVolume vessel volume in m3
   * @param targetEnergy vessel internal energy in J
   * @param referencePressure pressure from a detached reference solve in bara
   * @param referenceTemperature temperature from a detached reference solve in K
   */
  private static void assertRecovered(double[] moles, double totalMoles, double initialPressure,
      double initialTemperature, double targetVolume, double targetEnergy, double referencePressure,
      double referenceTemperature) {
    SystemInterface directFluid = createFluid(moles, totalMoles, initialPressure, initialTemperature);
    OptimizedVUflash directRecovery = new OptimizedVUflash(directFluid, targetVolume, targetEnergy);
    boolean directWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    assertTrue(directRecovery.recoverFromInitialEquilibrium(initialPressure, initialTemperature),
        "The detached recovery algorithm must close the captured state directly");
    assertEquals(directWarmStart, ThermodynamicModelSettings.isUseWarmStartKValues());
    assertTrue(directRecovery.wasEquilibriumRecoveryUsed());
    assertTrue(directRecovery.getEquilibriumRecoveryFlashCount() > 0);
    assertTrue(directRecovery.getEquilibriumRecoveryFlashCount() <= MAXIMUM_RECOVERY_FLASH_COUNT);
    assertRecoveredState(directFluid, moles, targetVolume, targetEnergy, referencePressure, referenceTemperature);

    SystemInterface fluid = createFluid(moles, totalMoles, initialPressure, initialTemperature);
    boolean originalWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.VUflash(targetVolume, targetEnergy, "m3", "J");
    OptimizedVUflash flash = (OptimizedVUflash) operations.getOperation();
    assertTrue(flash.isLastRunConverged(), "The physical three-phase VU state must be recovered");
    assertEquals(originalWarmStart, ThermodynamicModelSettings.isUseWarmStartKValues());
    if (flash.wasEquilibriumRecoveryUsed()) {
      assertTrue(flash.getEquilibriumRecoveryFlashCount() > 0);
      assertTrue(flash.getEquilibriumRecoveryFlashCount() <= MAXIMUM_RECOVERY_FLASH_COUNT);
    } else {
      assertEquals(0, flash.getEquilibriumRecoveryFlashCount());
    }
    assertRecoveredState(fluid, moles, targetVolume, targetEnergy, referencePressure, referenceTemperature);
  }

  /**
   * Build the captured three-phase vessel state before its VU solve.
   *
   * @param moles conserved component inventory in mol
   * @param totalMoles vessel total after its last inventory update in mol
   * @param initialPressure incoming pressure in bara
   * @param initialTemperature incoming temperature in K
   * @return initialized vessel fluid
   */
  private static SystemInterface createFluid(double[] moles, double totalMoles, double initialPressure,
      double initialTemperature) {
    SystemInterface fluid = new SystemSrkEos(initialTemperature, initialPressure);
    for (int component = 0; component < COMPONENTS.length; component++) {
      fluid.addComponent(COMPONENTS[component], moles[component]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    assertEquals(3, fluid.getNumberOfPhases());
    // Reproduce a vessel entering the VU step with a prior equilibrium and a refreshed mole inventory.
    fluid.setTotalNumberOfMoles(totalMoles);
    return fluid;
  }

  /**
   * Verify the accepted state, phase set and conserved inventory.
   *
   * @param fluid recovered vessel fluid
   * @param moles conserved component inventory in mol
   * @param targetVolume vessel volume in m3
   * @param targetEnergy vessel internal energy in J
   * @param referencePressure pressure from a detached reference solve in bara
   * @param referenceTemperature temperature from a detached reference solve in K
   */
  private static void assertRecoveredState(SystemInterface fluid, double[] moles, double targetVolume,
      double targetEnergy, double referencePressure, double referenceTemperature) {
    assertEquals(targetVolume, fluid.getVolume("m3"), RELATIVE_TOLERANCE * targetVolume);
    assertEquals(targetEnergy, fluid.getInternalEnergy(), RELATIVE_TOLERANCE * Math.abs(targetEnergy));
    assertEquals(referencePressure, fluid.getPressure("bara"), STATE_TOLERANCE);
    assertEquals(referenceTemperature, fluid.getTemperature("K"), STATE_TOLERANCE);
    assertTrue(fluid.hasPhaseType("gas") && fluid.hasPhaseType("oil") && fluid.hasPhaseType("aqueous"));
    for (int component = 0; component < COMPONENTS.length; component++) {
      assertEquals(moles[component], fluid.getComponent(COMPONENTS[component]).getNumberOfmoles(),
          1.0e-12 * moles[component], COMPONENTS[component]);
    }
  }
}
