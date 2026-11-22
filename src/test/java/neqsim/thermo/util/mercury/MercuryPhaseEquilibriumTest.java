package neqsim.thermo.util.mercury;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies mercury partitioning and the separate mercury phase with UMR-PRU-MC against the mercury calculator.
 *
 * <p>
 * Expected phase fractions and mercury mole fractions were calculated with libhg 2.0.3 for synthetic fluids. The
 * mercury and water group-interaction parameters of UMR-PRU-MC come from that library. The pure-component parameters
 * and the remaining group parameters do not, so agreement is within about 1 %, not exact.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MercuryPhaseEquilibriumTest {
  /** Relative tolerance on mercury mole fractions against libhg. */
  private static final double RELATIVE_TOLERANCE = 0.02;

  /** Absolute tolerance on phase mole fractions against libhg. */
  private static final double FRACTION_TOLERANCE = 3.0e-3;

  /** Component names of the synthetic condensate, without water. */
  private static final String[] CONDENSATE_NAMES = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane",
      "n-butane", "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "n-nonane", "nC10", "nC13", "nC17",
      "nC20"};

  /** Moles of the synthetic condensate components. */
  private static final double[] CONDENSATE_MOLES = {0.005, 0.02, 0.55, 0.07, 0.04, 0.01, 0.02, 0.01, 0.01, 0.015, 0.02,
      0.02, 0.01, 0.015, 0.01, 0.008, 0.007};

  /** Component names of the synthetic gas. */
  private static final String[] GAS_NAMES = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "n-hexane", "benzene", "c-hexane", "toluene", "n-heptane"};

  /** Moles of the synthetic gas components. */
  private static final double[] GAS_MOLES = {0.01, 0.02, 0.85, 0.07, 0.03, 0.005, 0.008, 0.002, 0.002, 0.001, 0.0005,
      0.0005, 0.0005, 0.0005};

  /**
   * Create a UMR-PRU-MC fluid with a given mercury mole fraction.
   *
   * @param temperatureC temperature in Celsius
   * @param pressure pressure in bara
   * @param names component names
   * @param moles moles of each component
   * @param extraNames further component names, may be empty
   * @param extraMoles moles of the further components
   * @param mercuryFraction mole fraction of mercury in the total fluid
   * @return fluid, not flashed
   */
  private static SystemInterface createFluid(double temperatureC, double pressure, String[] names, double[] moles,
      String[] extraNames, double[] extraMoles, double mercuryFraction) {
    SystemInterface fluid = new SystemUMRPRUMCEos(273.15 + temperatureC, pressure);
    double total = 0.0;
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], moles[i]);
      total += moles[i];
    }
    for (int i = 0; i < extraNames.length; i++) {
      fluid.addComponent(extraNames[i], extraMoles[i]);
      total += extraMoles[i];
    }
    fluid.addComponent("mercury", total * mercuryFraction / (1.0 - mercuryFraction));
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    return fluid;
  }

  /**
   * Create a methane and propane gas at 20 C and 50 bara.
   *
   * @param mercuryMoles moles of mercury per mole of gas
   * @return fluid, not flashed
   */
  private static SystemInterface createLightGas(double mercuryMoles) {
    SystemInterface fluid = new SystemUMRPRUMCEos(293.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("mercury", mercuryMoles);
    fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
    return fluid;
  }

  /**
   * Run the mercury calculation.
   *
   * @param fluid fluid with mercury
   * @return calculation after run
   */
  private static MercuryPhaseEquilibrium run(SystemInterface fluid) {
    MercuryPhaseEquilibrium result = new MercuryPhaseEquilibrium(fluid);
    result.run();
    return result;
  }

  /**
   * Compare one fluid phase with the libhg result.
   *
   * @param result calculation after run
   * @param type phase type
   * @param fraction expected mole fraction of the feed in the phase
   * @param mercury expected mercury mole fraction in the phase
   */
  private static void assertPhase(MercuryPhaseEquilibrium result, PhaseType type, double fraction, double mercury) {
    assertTrue(result.getFluid().hasPhaseType(type), "Expected a " + type + " phase");
    int phase = result.getFluid().getPhaseNumberOfPhase(type);
    assertEquals(fraction, result.getPhaseFraction(phase), FRACTION_TOLERANCE, type + " phase fraction");
    assertEquals(mercury, result.getMercuryMoleFraction(phase), RELATIVE_TOLERANCE * mercury,
        type + " mercury mole fraction");
  }

  /** Gas and condensate split, which exercises the mercury parameters against the hydrocarbon groups. */
  @Test
  void dryCondensateMatchesLibhg() {
    MercuryPhaseEquilibrium result = run(
        createFluid(-30.0, 21.0, CONDENSATE_NAMES, CONDENSATE_MOLES, new String[0], new double[0], 2.0e-9));
    assertFalse(result.hasFreeMercury());
    assertEquals(2, result.getFluid().getNumberOfPhases());
    assertPhase(result, PhaseType.GAS, 0.7057778865, 1.5698211920083495e-10);
    assertPhase(result, PhaseType.OIL, 0.2942221135, 6.421018017383822e-09);
  }

  /** Gas, condensate and water split, which needs the water parameters to form the aqueous phase at all. */
  @Test
  void wetCondensateMatchesLibhg() {
    MercuryPhaseEquilibrium result = run(createFluid(5.0, 70.0, CONDENSATE_NAMES, CONDENSATE_MOLES,
        new String[] {"water"}, new double[] {0.15}, 2.0e-9));
    assertFalse(result.hasFreeMercury());
    assertEquals(3, result.getFluid().getNumberOfPhases());
    assertPhase(result, PhaseType.GAS, 0.5411834129, 2.2388115655528125e-10);
    assertPhase(result, PhaseType.OIL, 0.3076102913, 6.081667090264288e-09);
    assertPhase(result, PhaseType.AQUEOUS, 0.1512062958, 5.3277188828782404e-11);
  }

  /** Aromatics, cyclic alkanes, hydrogen sulfide and water. */
  @Test
  void sourWetGasMatchesLibhg() {
    MercuryPhaseEquilibrium result = run(createFluid(30.0, 60.0, GAS_NAMES, GAS_MOLES, new String[] {"H2S", "water"},
        new double[] {0.01, 0.02}, 5.0e-9));
    assertFalse(result.hasFreeMercury());
    assertPhase(result, PhaseType.GAS, 0.9815140974, 5.086835978626755e-09);
    assertPhase(result, PhaseType.AQUEOUS, 0.0184859026, 3.894197738420032e-10);
  }

  /** Gas, condensate, water and liquid mercury: the four phases the mercury calculator reports. */
  @Test
  void liquidMercuryWithGasOilAndWaterMatchesLibhg() {
    SystemInterface fluid = createFluid(20.0, 21.0, CONDENSATE_NAMES, CONDENSATE_MOLES, new String[] {"water"},
        new double[] {0.15}, 0.01);
    double mercuryMoles = fluid.getPhase(0).getComponent("mercury").getNumberOfmoles();
    MercuryPhaseEquilibrium result = run(fluid);

    assertEquals("liquid", result.getFreeMercuryState());
    assertEquals(0.009999716378, result.getFreeMercuryFraction(), 1.0e-7);
    assertEquals(3, result.getFluid().getNumberOfPhases());
    assertPhase(result, PhaseType.GAS, 0.6608186694, 1.0454487731367843e-07);
    assertPhase(result, PhaseType.OIL, 0.1805184475, 1.184664845977331e-06);
    assertPhase(result, PhaseType.AQUEOUS, 0.1486631667, 4.592345167722857e-09);
    assertEquals(mercuryMoles, result.getFreeMercuryMoles() + result.getDissolvedMercuryMoles(), 1.0e-15);
    assertEquals(mercuryMoles, fluid.getPhase(0).getComponent("mercury").getNumberOfmoles(), 0.0,
        "The input fluid is not changed");
    // libhg reports 886.9 ug/Sm3 in the gas.
    int gas = result.getFluid().getPhaseNumberOfPhase(PhaseType.GAS);
    assertEquals(886.9, result.getMercuryConcentration(gas, "ug/Sm3"), RELATIVE_TOLERANCE * 886.9);
    assertEquals(result.getMercuryMoleFraction(gas) * 1.0e9, result.getMercuryConcentration(gas, "ppb"), 1.0e-9);
  }

  /** Liquid mercury drops out of a cold dense gas with a small condensate phase. */
  @Test
  void liquidMercuryFromGasAndCondensateMatchesLibhg() {
    MercuryPhaseEquilibrium result = run(
        createFluid(-20.0, 70.0, GAS_NAMES, GAS_MOLES, new String[0], new double[0], 1.0e-7));
    assertTrue(result.hasFreeMercury());
    assertTrue(result.getSaturationRatio() > 1.0);
    assertEquals(9.804455516392827e-08, result.getFreeMercuryFraction(), 1.0e-3 * 9.8e-08);
    assertPhase(result, PhaseType.GAS, 0.9619353208, 1.2913413529457329e-09);
    assertPhase(result, PhaseType.OIL, 0.0380645812, 1.8738101272637295e-08);
  }

  /** Below saturation the result is the ordinary flash and no mercury phase is reported. */
  @Test
  void undersaturatedFluidEqualsOrdinaryFlash() {
    SystemInterface fluid = createFluid(30.0, 60.0, GAS_NAMES, GAS_MOLES, new String[] {"water"}, new double[] {0.02},
        5.0e-9);
    MercuryPhaseEquilibrium result = run(fluid);
    SystemInterface flashed = fluid.clone();
    flashed.setMultiPhaseCheck(true);
    new ThermodynamicOperations(flashed).TPflash();

    assertEquals("none", result.getFreeMercuryState());
    assertEquals(0.0, result.getFreeMercuryMoles(), 0.0);
    assertTrue(result.getSaturationRatio() > 0.0 && result.getSaturationRatio() < 1.0);
    assertEquals(flashed.getNumberOfPhases(), result.getFluid().getNumberOfPhases());
    for (int phase = 0; phase < flashed.getNumberOfPhases(); phase++) {
      double expected = flashed.getPhase(phase).getComponent("mercury").getx();
      assertEquals(flashed.getBeta(phase), result.getPhaseFraction(phase), 1.0e-9);
      assertEquals(expected, result.getMercuryMoleFraction(phase), 1.0e-6 * expected);
    }
  }

  /** A feed just below saturation keeps all its mercury dissolved and one just above drops out the excess. */
  @Test
  void feedCloseToSaturationIsClassifiedConsistently() {
    MercuryPhaseEquilibrium saturated = run(createLightGas(1.0e-6));
    double capacity = saturated.getDissolvedMercuryMoles();
    assertTrue(saturated.hasFreeMercury());

    MercuryPhaseEquilibrium below = run(createLightGas(0.999 * capacity));
    assertFalse(below.hasFreeMercury());
    assertEquals(0.999, below.getSaturationRatio(), 1.0e-5);

    MercuryPhaseEquilibrium above = run(createLightGas(1.001 * capacity));
    assertTrue(above.hasFreeMercury());
    assertEquals(capacity, above.getDissolvedMercuryMoles(), 1.0e-6 * capacity);
    assertEquals(0.001 * capacity, above.getFreeMercuryMoles(), 1.0e-5 * capacity);
  }

  /**
   * Below the melting point the mercury phase is solid. libhg gives a gas mole fraction of 3.694e-11 here, but it
   * divides the volume term of solid mercury by a gas constant ten times too small for its units; with consistent units
   * the value is 9.3 % lower.
   */
  @Test
  void solidMercuryBelowMeltingPoint() {
    SystemInterface fluid = createFluid(-50.0, 30.0,
        new String[] {"nitrogen", "CO2", "methane", "ethane", "propane", "n-butane"},
        new double[] {0.01, 0.02, 0.87, 0.07, 0.02, 0.01}, new String[0], new double[0], 1.0e-7);
    MercuryPhaseEquilibrium result = run(fluid);
    assertEquals("solid", result.getFreeMercuryState());
    int gas = result.getFluid().getPhaseNumberOfPhase(PhaseType.GAS);
    assertEquals(3.694351462121628e-11 * 0.907, result.getMercuryMoleFraction(gas), RELATIVE_TOLERANCE * 3.35e-11);
  }

  /** Solid and liquid mercury have the same fugacity at the melting point, so the solubility is continuous there. */
  @Test
  void solubilityIsContinuousAcrossMeltingPoint() {
    double[] temperatureC = {MercuryPhaseEquilibrium.MELTING_TEMPERATURE - 273.15 - 0.01,
        MercuryPhaseEquilibrium.MELTING_TEMPERATURE - 273.15 + 0.01};
    double[] moleFraction = new double[2];
    String[] state = new String[2];
    for (int i = 0; i < 2; i++) {
      MercuryPhaseEquilibrium result = run(createFluid(temperatureC[i], 1.01325, new String[] {"methane"},
          new double[] {1.0}, new String[0], new double[0], 1.0e-6));
      moleFraction[i] = result.getMercuryMoleFraction(0);
      state[i] = result.getFreeMercuryState();
    }
    assertEquals("solid", state[0]);
    assertEquals("liquid", state[1]);
    assertEquals(moleFraction[1], moleFraction[0], 0.01 * moleFraction[1]);
    assertTrue(moleFraction[0] < moleFraction[1]);
  }

  /** The pure mercury reference uses the equation of state of the fluid, so other models are accepted. */
  @Test
  void worksWithAnotherEquationOfState() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("mercury", 1.0e-6);
    fluid.setMixingRule("classic");
    MercuryPhaseEquilibrium result = run(fluid);
    assertTrue(result.hasFreeMercury());
    int gas = result.getFluid().getPhaseNumberOfPhase(PhaseType.GAS);
    double fugacity = result.getMercuryMoleFraction(gas)
        * result.getFluid().getPhase(gas).getComponent("mercury").getFugacityCoefficient() * 50.0;
    assertEquals(result.getPureMercuryFugacity(), fugacity, 1.0e-8 * fugacity);
  }

  /** Invalid input and reading results before the calculation are reported. */
  @Test
  void rejectsFluidWithoutMercuryAndResultsBeforeRun() {
    SystemInterface fluid = new SystemSrkEos(293.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    assertThrows(IllegalArgumentException.class, () -> new MercuryPhaseEquilibrium(fluid));

    MercuryPhaseEquilibrium result = new MercuryPhaseEquilibrium(createLightGas(1.0e-9));
    assertThrows(IllegalStateException.class, () -> result.getFreeMercuryMoles());
    result.run();
    assertThrows(IllegalArgumentException.class, () -> result.getMercuryConcentration(0, "mg/l"));
  }
}
