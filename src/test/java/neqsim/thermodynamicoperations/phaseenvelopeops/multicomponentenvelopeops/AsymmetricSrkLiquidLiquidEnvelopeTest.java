package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Qualifies the liquid-liquid continuation of an asymmetric SRK hydrocarbon envelope against independently solved cubic
 * pressure and fugacity equations. The reference equations and their 60/80-digit reproduction are stored in
 * {@code asymmetric-srk-liquid-liquid} test resources.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class AsymmetricSrkLiquidLiquidEnvelopeTest {
  private static final String[] COMPONENTS = {"methane", "n-hexane", "nC10"};
  private static final double[] FEED = {0.8, 0.1, 0.1};
  private static final double TEMPERATURE = 162.03989088929555;
  private static final double PRESSURE = 152.94443048981309;
  private static final double[] INCIPIENT = {0.94675571763856884, 0.03644939098018227, 0.01679489138124889};

  /** Both distinct liquid roots close the EOS and share the independent reference chemical potentials. */
  @Test
  void independentLiquidLiquidOnsetHasDistinctPressureValidRoots() {
    SystemInterface feedLiquid = createLiquid(FEED);
    SystemInterface incipientLiquid = createLiquid(INCIPIENT);
    assertEquals(0.7644688872477662, feedLiquid.getPhase(0).getZ(), 1.0e-12);
    assertEquals(0.5551429401471584, incipientLiquid.getPhase(0).getZ(), 1.0e-12);
    assertEquals(PRESSURE, ((PhaseEos) feedLiquid.getPhase(0)).calcPressure(), 1.0e-8);
    assertEquals(PRESSURE, ((PhaseEos) incipientLiquid.getPhase(0)).calcPressure(), 1.0e-8);
    for (int component = 0; component < COMPONENTS.length; component++) {
      double feedChemicalPotential = Math.log(FEED[component])
          + feedLiquid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      double incipientChemicalPotential = Math.log(INCIPIENT[component])
          + incipientLiquid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      assertEquals(feedChemicalPotential, incipientChemicalPotential, 1.0e-10);
    }
  }

  /** A multiphase flash crosses the independently located liquid-liquid onset rather than inventing a vapor. */
  @Test
  void nearbyPressuresStraddleLiquidLiquidOnsetWithMaterialAndFugacityClosure() {
    SystemInterface split = createAndFlash(PRESSURE * 0.999);
    assertEquals(2, split.getNumberOfPhases());
    assertTrue(!split.hasPhaseType(PhaseType.GAS));
    for (int phase = 0; phase < split.getNumberOfPhases(); phase++) {
      assertEquals(PhaseType.OIL, split.getPhase(phase).getType());
      assertTrue(split.getBeta(phase) > 0.0 && split.getBeta(phase) < 1.0);
      assertEquals(split.getPressure(), ((PhaseEos) split.getPhase(phase)).calcPressure(), 1.0e-8);
    }
    assertEquals(1.0, split.getBeta(0) + split.getBeta(1), 1.0e-12);
    for (int component = 0; component < COMPONENTS.length; component++) {
      double recoveredFeed = 0.0;
      double[] chemicalPotential = new double[2];
      for (int phase = 0; phase < 2; phase++) {
        double moleFraction = split.getPhase(phase).getComponent(component).getx();
        recoveredFeed += split.getBeta(phase) * moleFraction;
        chemicalPotential[phase] = Math.log(moleFraction)
            + split.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
      }
      assertEquals(FEED[component], recoveredFeed, 1.0e-10);
      assertEquals(chemicalPotential[0], chemicalPotential[1], 1.0e-8);
    }
    double firstGibbsEnergy = split.getGibbsEnergy();
    new ThermodynamicOperations(split).TPflash();
    split.init(3);
    assertEquals(2, split.getNumberOfPhases());
    assertEquals(firstGibbsEnergy, split.getGibbsEnergy(), 1.0e-8);

    SystemInterface homogeneous = createAndFlash(PRESSURE * 1.001);
    assertEquals(1, homogeneous.getNumberOfPhases());
    assertEquals(PhaseType.OIL, homogeneous.getPhase(0).getType());
    assertEquals(homogeneous.getPressure(), ((PhaseEos) homogeneous.getPhase(0)).calcPressure(), 1.0e-8);
  }

  /**
   * Initialize the specified composition on a forced liquid root without performing an equilibrium flash.
   *
   * @param composition mole fractions
   * @return initialized homogeneous liquid
   */
  private SystemInterface createLiquid(double[] composition) {
    SystemInterface system = createSystem(PRESSURE, composition);
    system.init(0);
    system.setNumberOfPhases(1);
    system.setPhaseType(0, PhaseType.LIQUID);
    system.init(3);
    return system;
  }

  /**
   * Flash the reference feed with independent multiphase stability enabled.
   *
   * @param pressure absolute pressure in bara
   * @return initialized equilibrated system
   */
  private SystemInterface createAndFlash(double pressure) {
    SystemInterface system = createSystem(pressure, FEED);
    system.setMultiPhaseCheck(true);
    new ThermodynamicOperations(system).TPflash();
    system.init(3);
    return system;
  }

  /**
   * Configure the reference SRK model with classic mixing.
   *
   * @param pressure absolute pressure in bara
   * @param composition mole fractions
   * @return configured thermodynamic system
   */
  private SystemInterface createSystem(double pressure, double[] composition) {
    SystemInterface system = new SystemSrkEos(TEMPERATURE, pressure);
    for (int component = 0; component < COMPONENTS.length; component++) {
      system.addComponent(COMPONENTS[component], composition[component]);
    }
    system.setMixingRule("classic");
    return system;
  }
}
