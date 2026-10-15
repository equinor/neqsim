package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Independent experimental checks for the scoped CO2-potassium interaction correction.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class KClGasInteractionQualificationTest {
  private static final double WATER_MOLAR_MASS = 0.01801528;

  /** The single calibration target uses solution concentration, including physical volume correction. */
  @Test
  void ondaCalibrationUsesPhysicalSolutionVolume() {
    double[] pure = henry(0.0);
    double[] salt = henry(1.0);
    double setchenow = Math.log10(salt[0] / pure[0]) / salt[1];
    // Onda et al. (1970), Table 3: -0.2277 - 0.0362 + 0.3416 L/mol.
    assertEquals(0.0777, setchenow, 1.0e-5);
  }

  /** Unfitted He-Morse (1993) measurements retain absolute bias separately from salt suppression. */
  @Test
  void independentMolalityAndSaltFactorRemainSeparate() {
    // T/K, CO2 partial pressure/atm, KCl mol/kg water, CO2 mol/kg water, relative salting factor.
    // NIST SRD 106 original-data reproduction: sysID=62_229. None of these measurements was fitted.
    double[][] data = {{273.15, .966, .1, .0713, 1.06}, {273.15, .966, 2.015, .0575, 1.32},
        {298.15, .942, 1.0, .0291, 1.10}, {323.15, .854, 1.999, .0145, 1.15}, {348.15, .603, 3.0, .0068, 1.18},
        {363.15, .301, 3.0, .0031, 1.12}};
    for (double[] row : data) {
      double pure = co2Molality(row[0], row[1], 0.0);
      double salt = co2Molality(row[0], row[1], row[2]);
      assertTrue(pure > salt, "KCl must salt CO2 out at the matched gas partial pressure");
      assertEquals(row[4], pure / salt, 0.14 * row[4], "independent salt-factor model-error envelope");
      // This is a regression envelope, not the source's much smaller measurement uncertainty.
      assertEquals(row[3], salt, 0.35 * row[3], "retain the separately reported pure-water absolute bias");
    }
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
   * Creates a fixed one-kilogram-water feed for public flash checks.
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

  /**
   * Matches both the experimental gas partial pressure and final aqueous salt molality.
   *
   * @param temperature temperature in K
   * @param partialAtm experimental CO2 partial pressure in atm
   * @param molality experimental salt molality in mol/kg water
   * @return dissolved CO2 molality in mol/kg water
   */
  private static double co2Molality(double temperature, double partialAtm, double molality) {
    double pressure = 1.032;
    double salt = molality;
    for (int iteration = 0; iteration < 25; iteration++) {
      SystemInterface fluid = new SystemElectrolyteCPAstatoil(temperature, pressure);
      addComponents(fluid, salt);
      fluid.init(0);
      double[] inventory = FluidEquilibriumChecks.inventory(fluid);
      new TPflash(fluid).run();
      fluid.init(3);
      FluidEquilibriumChecks.validate(fluid, inventory);
      PhaseInterface aqueous = fluid.getPhase("aqueous");
      double water = aqueous.getComponent("water").getNumberOfMolesInPhase() * WATER_MOLAR_MASS;
      double actualSalt = molality == 0.0 ? 0.0 : aqueous.getComponent("K+").getNumberOfMolesInPhase() / water;
      double actualPartial = fluid.getPhase("gas").getComponent("CO2").getx() * pressure;
      if (Math.abs(actualPartial - partialAtm * 1.01325) < 1.0e-8 && Math.abs(actualSalt - molality) < 1.0e-8) {
        return aqueous.getComponent("CO2").getNumberOfMolesInPhase() / water;
      }
      pressure += partialAtm * 1.01325 - actualPartial;
      if (molality > 0.0) {
        salt *= molality / actualSalt;
      }
    }
    throw new IllegalStateException("Experimental pressure/molality state was not matched");
  }
}
