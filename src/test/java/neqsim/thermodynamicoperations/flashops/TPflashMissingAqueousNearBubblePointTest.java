package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.process.equipment.pump.Pump;
import neqsim.thermo.Fluid;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression for the public API oil/water pump inlet in issue #3955.
 *
 * <p>
 * Every recovered equilibrium must satisfy the #2937 normalization, material-balance, and fugacity-residual gates.
 * </p>
 */
class TPflashMissingAqueousNearBubblePointTest extends neqsim.NeqSimTest {
  private static final Logger logger = LogManager.getLogger(TPflashMissingAqueousNearBubblePointTest.class);
  private static final double NORMALIZATION_TOLERANCE = 1.0e-12;
  private static final double MATERIAL_BALANCE_TOLERANCE = 1.0e-10;
  private static final double FUGACITY_TOLERANCE = 1.0e-8;

  private SystemInterface inlet() {
    Fluid creator = new Fluid();
    creator.setAutoSelectModel(false);
    SystemInterface fluid = creator.create2(
        new String[] {"water", "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "iC5", "nC5"},
        new double[] {0.034266, 0.005269, 0.039189, 0.700553, 0.091154, 0.050908, 0.007751, 0.014665, 0.004249,
            0.004878},
        "mol/sec");
    fluid.addOilFractions(
        new String[] {"C6", "C7", "C8", "C9", "C10_C12", "C13_C14", "C15_C16", "C17_C19", "C20_C22", "C23_C25",
            "C26_C30", "C31_C38", "C39_C80"},
        new double[] {0.004541, 0.007189, 0.006904, 0.004355, 0.007658, 0.003861, 0.003301, 0.002624, 0.001857,
            0.001320, 0.001426, 0.001164, 0.000916},
        new double[] {0.08618, 0.09096, 0.10343, 0.11719, 0.14581, 0.18133, 0.21228, 0.24814, 0.28922, 0.33034, 0.38470,
            0.47116, 0.66246},
        new double[] {0.66266, 0.74084, 0.76922, 0.78921, 0.80411, 0.82491, 0.83780, 0.84946, 0.86331, 0.87527, 0.88783,
            0.90479, 0.92660},
        false, true, 12);
    fluid.setMixingRule("classic");
    fluid.setMolarComposition(new double[] {0.05684802165363617, 5.515874787449834e-07, 0.0010278596324360058,
        0.001723849100030006, 0.012980798821537624, 0.08992573613980387, 0.054774575901924305, 0.16785159721279627,
        0.10204135194676313, 0.13436281683359771, 0.1214022423241734, 0.14890621140698698, 0.07663824786913744,
        0.022557139369736048, 0.008246766157693826, 0.0006031573677095158, 9.714932818066216e-05,
        1.1051586519425497e-05, 8.13048005190817e-07, 5.941478805650325e-08, 3.2688782803627164e-09,
        2.8181467781810254e-11, 5.696372070102718e-15});
    assertTrue(fluid.doMultiPhaseCheck());
    fluid.setTemperature(298.15);
    fluid.setPressure(2.67);
    return fluid;
  }

  @Test
  void aqueousPhaseFoundNextToBubblePoint() {
    SystemInterface fluid = inlet();
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    assertAqueousEquilibrium(fluid);
    assertEquals(0.05575417, fluid.getBeta(fluid.getPhaseNumberOfPhase("aqueous")), 1.0e-7);
  }

  private static Stream<Arguments> bubblePointGrid() {
    Stream.Builder<Arguments> grid = Stream.builder();
    for (double pressure : new double[] {1.8, 2.2, 2.5, 2.67, 2.9, 3.2, 3.6, 4.2, 5.0}) {
      for (double temperatureC : new double[] {11, 14, 17, 20, 23, 25, 27, 29, 31}) {
        grid.add(Arguments.of(temperatureC, pressure));
      }
    }
    return grid.build();
  }

  @ParameterizedTest(name = "T={0} C, P={1} bara")
  @MethodSource("bubblePointGrid")
  void aqueousPhasePersistsAcrossBubblePointGrid(double temperatureC, double pressure) {
    SystemInterface fluid = inlet();
    fluid.setTemperature(temperatureC, "C");
    fluid.setPressure(pressure);
    new ThermodynamicOperations(fluid).TPflash();
    assertAqueousEquilibrium(fluid);
    if (pressure == 1.8) {
      assertEquals(3, fluid.getNumberOfPhases(), "Preserve the vapor region");
    } else if (pressure >= 4.2) {
      assertEquals(2, fluid.getNumberOfPhases(), "Preserve the liquid-only region");
    }
  }

  @Test
  void pumpHasPositiveWorkAndSmallTemperatureRise() {
    neqsim.process.equipment.stream.Stream feed = new neqsim.process.equipment.stream.Stream("feed", inlet());
    feed.setFlowRate(3781.0149, "kg/hr");
    feed.run();
    assertAqueousEquilibrium(feed.getFluid());
    feed.getFluid().init(2);
    double inletEntropy = feed.getFluid().getEntropy();
    // Enthalpy/entropy use the EOS volume; the volume-corrected transport density is a different reference.
    double hydraulicPower = feed.getFluid().getVolume("m3") * (19.0 - 2.67) * 1.0e5 / 1000.0;
    Pump pump = new Pump("pump", feed);
    pump.setOutletPressure(19.0);
    pump.run();
    SystemInterface outlet = pump.getOutletStream().getFluid();
    double rise = outlet.getTemperature() - feed.getTemperature();
    assertTrue(rise > 0.0 && rise < 1.0, "Pump temperature rise: " + rise);
    assertTrue(pump.getPower("kW") > 0.0);
    assertEquals(hydraulicPower, pump.getPower("kW"), 0.02 * hydraulicPower);
    assertEquals(inletEntropy, outlet.getEntropy(), PSFlash.entropyTolerance(outlet, inletEntropy));
    assertAqueousEquilibrium(outlet);
    logger.info("Issue 3955 pump: power={} kW, hydraulic={} kW, outlet={} K", pump.getPower("kW"), hydraulicPower,
        outlet.getTemperature());
  }

  @Test
  void warmStartAndRepeatedFlashPreserveTheAqueousBranch() {
    SystemInterface warm = inlet();
    warm.setPressure(5.0);
    new ThermodynamicOperations(warm).TPflash();
    for (double pressure : new double[] {2.67, 2.5, 3.2, 2.67}) {
      warm.setPressure(pressure);
      new ThermodynamicOperations(warm).TPflash();
      assertAqueousEquilibrium(warm);
      SystemInterface cold = inlet();
      cold.setPressure(pressure);
      new ThermodynamicOperations(cold).TPflash();
      assertEquals(cold.getGibbsEnergy(), warm.getGibbsEnergy(), 1.0e-5);
      double gibbs = warm.getGibbsEnergy();
      new ThermodynamicOperations(warm).TPflash();
      assertAqueousEquilibrium(warm);
      assertEquals(gibbs, warm.getGibbsEnergy(), 1.0e-5);
    }
  }

  @Test
  void singleOilRecoveryPreservesInventoryWithAliasedInactivePhaseSlots() {
    SystemInterface fluid = homogeneousOil();
    double referenceGibbs = fluid.getGibbsEnergy();
    new TPmultiflash(fluid, false).rescueMetastableOilMissingAqueous();
    assertAqueousEquilibrium(fluid);
    assertTrue(fluid.getGibbsEnergy() < referenceGibbs - 1.0);
    assertTrue(fluid.getPhaseIndex(0) != fluid.getPhaseIndex(1));
  }

  @Test
  void lockedSingleOilStateIsNotRetypedByRecovery() {
    SystemInterface fluid = homogeneousOil();
    fluid.allowPhaseShift(false);
    double referenceGibbs = fluid.getGibbsEnergy();
    new TPmultiflash(fluid, false).rescueMetastableOilMissingAqueous();
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(PhaseType.OIL, fluid.getPhase(0).getType());
    assertEquals(1, fluid.getPhaseIndex(0));
    assertEquals(referenceGibbs, fluid.getGibbsEnergy(), 0.0);
  }

  /**
   * Checks that the aqueous recovery is governed by equilibrium rather than a five-percent water cutoff.
   */
  @Test
  void singleOilRecoveryRetainsDiluteWaterPhase() {
    SystemInterface fluid = homogeneousOil(diluteWaterInlet(0.007));
    double referenceGibbs = fluid.getGibbsEnergy();
    new TPmultiflash(fluid, false).rescueMetastableOilMissingAqueous();
    assertAqueousEquilibrium(fluid);
    assertTrue(fluid.getGibbsEnergy() < referenceGibbs - 1.0);
    double equilibriumGibbs = fluid.getGibbsEnergy();
    new ThermodynamicOperations(fluid).TPflash();
    assertAqueousEquilibrium(fluid);
    assertEquals(equilibriumGibbs, fluid.getGibbsEnergy(), 1.0e-5);
  }

  /** Checks dilute-water equilibrium through the public flash and pump APIs. */
  @Test
  void diluteWaterPumpHasPositiveWorkAndSmallTemperatureRise() {
    SystemInterface fluid = diluteWaterInlet(0.007);
    neqsim.process.equipment.stream.Stream feed = new neqsim.process.equipment.stream.Stream("feed", fluid);
    feed.setFlowRate(9000.0, "kg/hr");
    feed.run();
    assertAqueousEquilibrium(feed.getFluid());
    feed.getFluid().init(2);
    double inletEnthalpy = feed.getFluid().getEnthalpy();
    double inletEntropy = feed.getFluid().getEntropy();
    Pump pump = new Pump("pump", feed);
    pump.setOutletPressure(5.2);
    pump.run();
    SystemInterface outlet = pump.getOutletStream().getFluid();
    outlet.init(2);
    assertTrue(pump.getPower("W") > 0.0);
    assertTrue(outlet.getTemperature() > feed.getTemperature());
    assertTrue(outlet.getTemperature() - feed.getTemperature() < 1.0);
    assertEquals(9000.0, outlet.getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(5.2, outlet.getPressure(), 1.0e-10);
    assertEquals(pump.getPower("W"), outlet.getEnthalpy() - inletEnthalpy, 1.0e-6);
    assertEquals(inletEntropy, outlet.getEntropy(), PSFlash.entropyTolerance(outlet, inletEntropy));
    assertAqueousEquilibrium(outlet);
  }

  /** Checks that numerically unresolvable water never allocates a candidate fluid. */
  @Test
  void unresolvableWaterRecoveryDoesNotClone() {
    for (double waterFraction : new double[] {0.0, 1.0e-12,
        10.0 * neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit}) {
      SystemInterface fluid = homogeneousOil(diluteWaterInlet(waterFraction));
      double referenceGibbs = fluid.getGibbsEnergy();
      SystemInterface guarded = (SystemInterface) java.lang.reflect.Proxy.newProxyInstance(
          SystemInterface.class.getClassLoader(), new Class<?>[] {SystemInterface.class},
          (proxy, method, arguments) -> {
            if ("clone".equals(method.getName())) {
              throw new AssertionError("Unresolvable water must not trigger a nested flash");
            }
            return method.invoke(fluid, arguments);
          });
      new TPmultiflash(guarded, false).rescueMetastableOilMissingAqueous();
      assertEquals(1, fluid.getNumberOfPhases());
      assertEquals(referenceGibbs, fluid.getGibbsEnergy(), 0.0);
    }
  }

  /** Checks that extending the screen does not force an undersaturated aqueous phase. */
  @Test
  void diluteWaterRecoveryRejectsUndersaturatedAqueousSeed() {
    SystemInterface fluid = homogeneousOil(diluteWaterInlet(1.0e-8));
    double referenceGibbs = fluid.getGibbsEnergy();
    new TPmultiflash(fluid, false).rescueMetastableOilMissingAqueous();
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(PhaseType.OIL, fluid.getPhase(0).getType());
    assertEquals(referenceGibbs, fluid.getGibbsEnergy(), 1.0e-8);
  }

  /** A converged mixed aqueous stability trial must bypass the rejected full-system recovery. */
  @Test
  void undersaturatedCubicWaterRecoveryDoesNotCloneSystem() {
    for (boolean pengRobinson : new boolean[] {false, true}) {
      for (double water : new double[] {1e-8, 1e-6}) {
        SystemInterface fluid = pengRobinson ? new neqsim.thermo.system.SystemPrEos(308.2, 4.7)
            : new neqsim.thermo.system.SystemSrkEos(308.2, 4.7);
        fluid.addComponent("methane", 0.03);
        fluid.addComponent("propane", 0.2);
        fluid.addComponent("n-butane", 0.3);
        fluid.addComponent("n-heptane", 0.47 - water);
        fluid.addComponent("water", water);
        fluid.setMixingRule("classic");
        fluid.setMultiPhaseCheck(true);
        new ThermodynamicOperations(fluid).TPflash();
        assertTrue(!fluid.hasPhaseType("aqueous"));
        double gibbs = fluid.getGibbsEnergy();
        double[] composition = fluid.getMolarComposition();
        SystemInterface guarded = (SystemInterface) java.lang.reflect.Proxy.newProxyInstance(
            SystemInterface.class.getClassLoader(), new Class<?>[] {SystemInterface.class},
            (proxy, method, arguments) -> {
              if ("clone".equals(method.getName())) {
                throw new AssertionError("Stable mixed aqueous trial must avoid a candidate system clone");
              }
              return method.invoke(fluid, arguments);
            });
        new TPmultiflash(guarded, false).rescueMetastableOilMissingAqueous();
        assertEquals(gibbs, fluid.getGibbsEnergy(), 0.0);
        org.junit.jupiter.api.Assertions.assertArrayEquals(composition, fluid.getMolarComposition(), 0.0);
      }
    }
  }

  /** Checks entropy continuity after final cleanup leaves a non-equilibrium three-phase condensate. */
  @Test
  void condensateEntropyIsContinuousAcrossCleanupBoundary() {
    SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(289.17057592049406, 3.0);
    String[] components = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane", "n-octane", "water"};
    double[] composition = {0.03333751952167422, 0.054160101428384885, 0.16166835683018532, 0.2519356897139214,
        0.19282738794737694, 0.17687766832017773, 0.05152264899091814, 0.07767062724736153};
    for (int component = 0; component < components.length; component++) {
      fluid.addComponent(components[component], composition[component]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertAqueousEquilibrium(fluid);
    fluid.init(2);
    SystemInterface inletState = fluid.clone();
    fluid.setPressure(15.0);
    fluid.setTemperature(297.83);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    double lowerEntropy = fluid.getEntropy();
    fluid = inletState.clone();
    fluid.setPressure(15.0);
    fluid.setTemperature(297.84);
    new ThermodynamicOperations(fluid).TPflash();
    assertAqueousEquilibrium(fluid);
    fluid.init(2);
    assertTrue(fluid.getEntropy() > lowerEntropy);
    assertTrue(fluid.getEntropy() - lowerEntropy < 0.1, "Entropy must not jump to a metastable phase set");
  }

  /**
   * Reduces water while preserving the relative hydrocarbon composition of the public regression feed.
   *
   * @param waterFraction overall water mole fraction
   * @return the dilute-water feed before equilibrium calculation
   */
  private SystemInterface diluteWaterInlet(double waterFraction) {
    SystemInterface fluid = inlet();
    double[] composition = fluid.getMolarComposition();
    double previousWater = composition[0];
    for (int component = 1; component < composition.length; component++) {
      composition[component] *= (1.0 - waterFraction) / (1.0 - previousWater);
    }
    composition[0] = waterFraction;
    fluid.setMolarComposition(composition);
    fluid.setTemperature(308.2);
    fluid.setPressure(4.7);
    return fluid;
  }

  /**
   * Creates the oil-only regression state.
   *
   * @return a homogeneous oil with aliased inactive slots
   */
  private SystemInterface homogeneousOil() {
    return homogeneousOil(inlet());
  }

  /**
   * Initializes an oil-only endpoint after setting its composition.
   *
   * @param fluid fluid with the intended overall composition
   * @return the initialized homogeneous oil with aliased inactive slots
   */
  private SystemInterface homogeneousOil(SystemInterface fluid) {
    fluid.setNumberOfPhases(1);
    fluid.setPhaseIndex(0, 1);
    fluid.setPhaseType(0, PhaseType.OIL);
    fluid.setBeta(0, 1.0);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      fluid.getPhase(0).getComponent(component).setx(fluid.getPhase(0).getComponent(component).getz());
    }
    fluid.init(1);
    fluid.setPhaseIndex(1, 1);
    return fluid;
  }

  private void assertAqueousEquilibrium(SystemInterface fluid) {
    assertTrue(fluid.hasPhaseType("aqueous"), "Missing aqueous phase: got " + fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType("oil"));
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      assertTrue(fluid.getBeta(phase) > 0.0 && fluid.getBeta(phase) < 1.0);
      betaSum += fluid.getBeta(phase);
      double xSum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x >= 0.0 && x <= 1.0);
        xSum += x;
      }
      assertEquals(1.0, xSum, NORMALIZATION_TOLERANCE);
    }
    assertEquals(1.0, betaSum, NORMALIZATION_TOLERANCE);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      double referenceLogFugacity = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      assertTrue(Double.isFinite(referenceLogFugacity), "Finite fugacity for component " + component);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        recovered += fluid.getBeta(phase) * x;
        double logFugacity = Math.log(x) + fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient();
        assertEquals(referenceLogFugacity, logFugacity, FUGACITY_TOLERANCE, "Component " + component);
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, MATERIAL_BALANCE_TOLERANCE);
    }
  }
}
