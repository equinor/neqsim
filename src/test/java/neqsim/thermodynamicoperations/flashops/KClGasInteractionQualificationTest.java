package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.ElectrolyteMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;

/**
 * Source and override checks for the scoped CO2-potassium interaction correction.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class KClGasInteractionQualificationTest {
  private static final double WATER_MOLAR_MASS = 0.01801528;

  /** The calibration target uses solution concentration, including physical volume correction. */
  @Test
  void ondaCalibrationUsesPhysicalSolutionVolume() {
    double[] pure = henry(0.0);
    double[] salt = henry(1.0);
    double setchenow = Math.log10(salt[0] / pure[0]) / salt[1];
    // Onda et al. (1970), Table 3: -0.2277 - 0.0362 + 0.3416 L/mol.
    assertEquals(0.0777, setchenow, 1.0e-5);
  }

  /** A pair-specific user coefficient survives initialization, and the ScRK default is unchanged. */
  @Test
  void explicitPairAndOtherEosDefaultsRetainPriority() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    addComponents(fluid, 1.0);
    fluid.init(0);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      ElectrolyteMixingRulesInterface rule = ((PhaseModifiedFurstElectrolyteEos) fluid.getPhase(phase))
          .getElectrolyteMixingRule();
      assertEquals(-2.31476480642442e-4, rule.getWijParameter(0, 2), 0.0);
      rule.setWijParameter(0, 2, 2.0e-4);
      rule.setWijT1Parameter(0, 2, 3.0e-4);
      rule.setWijT2Parameter(0, 2, 4.0e-4);
    }
    fluid.init(0);
    ElectrolyteMixingRulesInterface overridden = ((PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0))
        .getElectrolyteMixingRule();
    assertEquals(2.0e-4, overridden.getWijParameter(0, 2), 0.0);
    assertEquals(3.0e-4, overridden.gettWijT1Parameter(0, 2), 0.0);
    assertEquals(4.0e-4, overridden.gettWijT2Parameter(0, 2), 0.0);

    SystemInterface scrk = new SystemFurstElectrolyteEos(298.15, 1.01325);
    addComponents(scrk, 1.0);
    scrk.init(0);
    assertEquals(1.27e-4,
        ((PhaseModifiedFurstElectrolyteEos) scrk.getPhase(0)).getElectrolyteMixingRule().getWijParameter(0, 2), 0.0);
  }

  /** User changes to either shared CO2 gas-ion coefficient disable the specialized default. */
  @Test
  void globalGasIonOverridesRetainPriority() {
    for (int index : new int[] {0, 1}) {
      double original = FurstElectrolyteConstants.getFurstParamGasIon(index);
      try {
        FurstElectrolyteConstants.setFurstParamGasIon(index, original + 1.0e-6);
        SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
        addComponents(fluid, 1.0);
        fluid.init(0);
        double actual = ((PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0)).getElectrolyteMixingRule()
            .getWijParameter(0, 2);
        assertEquals(FurstElectrolyteConstants.getFurstParamGasIon(0), actual, 0.0);
      } finally {
        FurstElectrolyteConstants.setFurstParamGasIon(index, original);
      }
    }
  }

  /**
   * Creates a fixed one-kilogram-water feed.
   *
   * @param fluid target fluid
   * @param salt potassium chloride amount in mol
   */
  private static void addComponents(SystemInterface fluid, double salt) {
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / WATER_MOLAR_MASS);
    if (salt > 0.0) {
      fluid.addComponent("K+", salt);
      fluid.addComponent("Cl-", salt);
    }
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
  }

  /**
   * Calculates the concentration-based Henry coefficient in the dissolved-gas dilution limit.
   *
   * @param molality salt molality in mol/kg water
   * @return Henry coefficient in bar L/mol and salt concentration in mol/L
   */
  private static double[] henry(double molality) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
    fluid.addComponent("CO2", 1.0e-8);
    fluid.addComponent("water", 1.0);
    if (molality > 0.0) {
      fluid.addComponent("K+", molality * WATER_MOLAR_MASS);
      fluid.addComponent("Cl-", molality * WATER_MOLAR_MASS);
    }
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.setForceSinglePhase(PhaseType.LIQUID);
    fluid.setBeta(1.0);
    fluid.init(3);
    fluid.initPhysicalProperties();
    PhaseInterface phase = fluid.getPhase(0);
    double litres = phase.getNumberOfMolesInPhase() * phase.getMolarMass() / phase.getPhysicalProperties().getDensity()
        * 1000.0;
    double coefficient = phase.getComponent("CO2").getFugacityCoefficient() * fluid.getPressure() * litres
        / phase.getNumberOfMolesInPhase();
    return new double[] {coefficient,
        molality > 0.0 ? phase.getComponent("K+").getNumberOfMolesInPhase() / litres : 0.0};
  }
}
