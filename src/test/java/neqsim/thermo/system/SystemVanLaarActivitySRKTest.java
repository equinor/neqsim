package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEVanLaarAcid;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SystemVanLaarActivitySRKTest class.
 * </p>
 *
 * <p>
 * Demonstrates that the gamma-phi system {@link SystemVanLaarActivitySRK} reproduces the equilibrium identity
 * {@code fugacity_i = gamma_i * x_i * P0_i} for the water-nitric-acid-sulfuric-acid mixture, matching the partial
 * pressures of the standalone Van Laar model {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure} (Taleb,
 * Ponche and Mirabel, 1996).
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class SystemVanLaarActivitySRKTest extends neqsim.NeqSimTest {
  /** Number of pascals per bar. */
  private static final double PASCALS_PER_BAR = 1.0e5;

  /**
   * Build a configured ternary water-nitric-acid-sulfuric-acid system from mass fractions.
   *
   * @param wH2O mass fraction (or weight percent) of water
   * @param wHNO3 mass fraction (or weight percent) of nitric acid
   * @param wH2SO4 mass fraction (or weight percent) of sulfuric acid
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @return a configured {@link SystemVanLaarActivitySRK}
   */
  private SystemVanLaarActivitySRK buildSystem(double wH2O, double wHNO3, double wH2SO4, double temperature,
      double pressure) {
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(wH2O, wHNO3, wH2SO4);
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(temperature, pressure);
    if (x[0] > 0.0) {
      system.addComponent("water", x[0]);
    }
    if (x[1] > 0.0) {
      system.addComponent("nitric acid", x[1]);
    }
    if (x[2] > 0.0) {
      system.addComponent("sulfuric acid", x[2]);
    }
    system.createDatabase(true);
    system.setMixingRule(2);
    return system;
  }

  /**
   * Locate the Van Laar excess-Gibbs-energy liquid phase in a (possibly multiphase) system.
   *
   * @param system the system to search
   * @return the {@link PhaseGEVanLaarAcid} liquid phase, or {@code null} if not present
   */
  private PhaseInterface findVanLaarLiquid(SystemInterface system) {
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i) instanceof PhaseGEVanLaarAcid) {
        return system.getPhase(i);
      }
    }
    return null;
  }

  /**
   * Locate the CO2-rich phase in a flashed system.
   *
   * @param system the system to search
   * @return the phase with the highest CO2 mole fraction, or {@code null} if CO2 is absent
   */
  private PhaseInterface findCarbonDioxideRichPhase(SystemInterface system) {
    PhaseInterface bestPhase = null;
    double bestCarbonDioxideFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      if (phase.hasComponent("CO2")) {
        double carbonDioxideFraction = phase.getComponent("CO2").getx();
        if (carbonDioxideFraction > bestCarbonDioxideFraction) {
          bestCarbonDioxideFraction = carbonDioxideFraction;
          bestPhase = phase;
        }
      }
    }
    return bestPhase;
  }

  /** Component names reported by the acid-solubility notebook example. */
  private static final String[] ACID_REPORT_COMPONENTS = { "CO2", "water", "nitric acid", "sulfuric acid" };

  /** Acid component names included in the reported total acid concentration. */
  private static final String[] ACID_REPORT_ACIDS = { "nitric acid", "sulfuric acid" };

  /**
   * Runs a CO2-rich Van Laar acid flash and returns the values printed by the notebook example.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @param waterMoles water feed in moles
   * @param nitricAcidMoles nitric acid feed in moles
   * @param sulfuricAcidMoles sulfuric acid feed in moles
   * @return report values in the order acid wt%, acid-phase mol%, acid-phase wt%, CO2-phase acid ppm, CO2-phase water
   * ppm, and acid beta
   */
  private double[] flashedAcidReportValues(double temperatureC, double pressureBar, double waterMoles,
      double nitricAcidMoles, double sulfuricAcidMoles) {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(temperatureC + 273.15, pressureBar);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", waterMoles);
    system.addComponent("nitric acid", nitricAcidMoles);
    system.addComponent("sulfuric acid", sulfuricAcidMoles);
    system.createDatabase(true);
    system.setMixingRule("classic");

    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    int carbonDioxideRichIndex = findCarbonDioxideRichPhaseIndex(system);
    int acidIndex = findAcidReportPhaseIndex(system, carbonDioxideRichIndex);
    assertTrue(carbonDioxideRichIndex >= 0, "A CO2-rich phase should be present");
    assertTrue(acidIndex >= 0, "A Van Laar acid-rich phase should be present");

    PhaseInterface carbonDioxideRichPhase = system.getPhase(carbonDioxideRichIndex);
    PhaseInterface acidPhase = system.getPhase(acidIndex);

    double[] values = new double[12];
    values[0] = weightPercent(acidPhase, ACID_REPORT_ACIDS);
    for (int componentIndex = 0; componentIndex < ACID_REPORT_COMPONENTS.length; componentIndex++) {
      String componentName = ACID_REPORT_COMPONENTS[componentIndex];
      values[1 + componentIndex] = molePercent(acidPhase, componentName);
      values[5 + componentIndex] = weightPercent(acidPhase, new String[] { componentName });
    }
    values[9] = (componentMoleFraction(carbonDioxideRichPhase, "nitric acid")
        + componentMoleFraction(carbonDioxideRichPhase, "sulfuric acid")) * 1.0e6;
    values[10] = componentMoleFraction(carbonDioxideRichPhase, "water") * 1.0e6;
    values[11] = system.getBeta(acidIndex);
    return values;
  }

  /**
   * Locates the phase containing the largest amount of CO2.
   *
   * @param system the flashed system to search
   * @return phase index, or {@code -1} if CO2 is absent
   */
  private int findCarbonDioxideRichPhaseIndex(SystemInterface system) {
    int bestPhaseIndex = -1;
    double bestCarbonDioxideMoles = -1.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      if (phase.hasComponent("CO2")) {
        double carbonDioxideMoles = phase.getComponent("CO2").getNumberOfMolesInPhase();
        if (carbonDioxideMoles > bestCarbonDioxideMoles) {
          bestCarbonDioxideMoles = carbonDioxideMoles;
          bestPhaseIndex = phaseIndex;
        }
      }
    }
    return bestPhaseIndex;
  }

  /**
   * Locates the non-CO2 phase used for the notebook acid report.
   *
   * @param system the flashed system to search
   * @param carbonDioxideRichIndex phase index of the CO2-rich phase
   * @return phase index, or {@code -1} if no material acid phase exists
   */
  private int findAcidReportPhaseIndex(SystemInterface system, int carbonDioxideRichIndex) {
    int bestPhaseIndex = -1;
    double bestAcidBasisFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex != carbonDioxideRichIndex && system.getBeta(phaseIndex) > 1.0e-9) {
        PhaseInterface phase = system.getPhase(phaseIndex);
        double acidBasisFraction = componentMoleFraction(phase, "water") + componentMoleFraction(phase, "nitric acid")
            + componentMoleFraction(phase, "sulfuric acid");
        if (acidBasisFraction > bestAcidBasisFraction) {
          bestAcidBasisFraction = acidBasisFraction;
          bestPhaseIndex = phaseIndex;
        }
      }
    }
    return bestPhaseIndex;
  }

  /**
   * Reads a component mole fraction from a phase.
   *
   * @param phase the phase to read
   * @param componentName component name
   * @return mole fraction, or zero when the component is absent
   */
  private double componentMoleFraction(PhaseInterface phase, String componentName) {
    if (!phase.hasComponent(componentName)) {
      return 0.0;
    }
    return phase.getComponent(componentName).getx();
  }

  /**
   * Calculates component mole percent in a phase.
   *
   * @param phase the phase to read
   * @param componentName component name
   * @return component mole percent
   */
  private double molePercent(PhaseInterface phase, String componentName) {
    return 100.0 * componentMoleFraction(phase, componentName);
  }

  /**
   * Calculates mass percent for selected components on the report component basis.
   *
   * @param phase the phase to read
   * @param selectedNames component names included in the numerator
   * @return selected component mass percent
   */
  private double weightPercent(PhaseInterface phase, String[] selectedNames) {
    double selectedMass = 0.0;
    for (int i = 0; i < selectedNames.length; i++) {
      selectedMass += componentMassBasis(phase, selectedNames[i]);
    }
    double totalMass = 0.0;
    for (int i = 0; i < ACID_REPORT_COMPONENTS.length; i++) {
      totalMass += componentMassBasis(phase, ACID_REPORT_COMPONENTS[i]);
    }
    return 100.0 * selectedMass / totalMass;
  }

  /**
   * Calculates a mole-fraction-weighted component mass basis for a phase.
   *
   * @param phase the phase to read
   * @param componentName component name
   * @return mole fraction multiplied by molar mass
   */
  private double componentMassBasis(PhaseInterface phase, String componentName) {
    if (!phase.hasComponent(componentName)) {
      return 0.0;
    }
    return phase.getComponent(componentName).getx() * phase.getComponent(componentName).getMolarMass();
  }

  /**
   * Renormalised acid-basis mole fractions {x_H2O, x_HNO3, x_H2SO4} read from a liquid phase, excluding any carrier
   * gas. This is the composition basis on which the Van Laar model is evaluated, so the standalone correlations called
   * with these fractions reproduce the phase's activity coefficients exactly.
   *
   * @param liquid the liquid phase to read mole fractions from
   * @return a three-element array {x1, x2, x3} that sums to one
   */
  private double[] acidFractions(PhaseInterface liquid) {
    double x1 = 0.0;
    double x2 = 0.0;
    double x3 = 0.0;
    for (int i = 0; i < liquid.getNumberOfComponents(); i++) {
      String name = liquid.getComponent(i).getName().toLowerCase();
      double xi = liquid.getComponent(i).getx();
      if (name.equals("water")) {
        x1 += xi;
      } else if (name.equals("nitric acid")) {
        x2 += xi;
      } else if (name.equals("sulfuric acid")) {
        x3 += xi;
      }
    }
    double sum = x1 + x2 + x3;
    return new double[] { x1 / sum, x2 / sum, x3 / sum };
  }

  /**
   * Verifies that HNO3 SRK vapour tuning is embedded in the system model and applied through the regular add-component
   * workflow.
   */
  @Test
  public void testNitricAcidTunedSrkPropertiesAppliedByDefault() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(313.15, 100.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("nitric acid", 1.0e-6);
    system.addComponent("water", 1.0e-8);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);

    assertEquals(578.433819, system.getPhase(0).getComponent("nitric acid").getTC(), 1.0e-9);
    assertEquals(107.435001, system.getPhase(0).getComponent("nitric acid").getPC(), 1.0e-9);
    assertEquals(0.849356, system.getPhase(0).getComponent("nitric acid").getAcentricFactor(), 1.0e-12);
  }

  /**
   * Verifies that the model refreshes state-dependent acid kij values during initialization.
   */
  @Test
  public void testStateDependentAcidKijAppliedByDefault() {
    double temperatureC = 40.0;
    double pressureBar = 100.0;
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(temperatureC + 273.15, 100.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("nitric acid", 1.0e-6);
    system.addComponent("sulfuric acid", 1.0e-12);
    system.addComponent("water", 1.0e-8);
    system.createDatabase(true);
    system.setMixingRule(2);
    system.init(0);

    PhaseEos vapour = (PhaseEos) system.getPhase(0);
    EosMixingRulesInterface mixingRule = vapour.getEosMixingRule();
    int co2Index = vapour.getComponent("CO2").getComponentNumber();
    int hno3Index = vapour.getComponent("nitric acid").getComponentNumber();
    int h2so4Index = vapour.getComponent("sulfuric acid").getComponentNumber();

    assertEquals(SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(temperatureC, pressureBar),
        mixingRule.getBinaryInteractionParameter(co2Index, hno3Index), 1.0e-12);
    assertEquals(SystemVanLaarActivitySRK.carbonDioxideSulfuricAcidKij(temperatureC),
        mixingRule.getBinaryInteractionParameter(co2Index, h2so4Index), 1.0e-12);

    double newTemperatureC = 53.0;
    system.setTemperature(newTemperatureC + 273.15);
    system.init(0);
    assertEquals(SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(newTemperatureC, pressureBar),
        mixingRule.getBinaryInteractionParameter(co2Index, hno3Index), 1.0e-12);
  }

  /** Verifies that fitted CO2-HNO3 kij changes with CO2 density through pressure at fixed temperature. */
  @Test
  public void testCarbonDioxideNitricAcidKijDependsOnCo2Density() {
    double lowPressureKij = SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(48.0, 99.3);
    double highPressureKij = SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(48.0, 169.1);

    assertNotEquals(lowPressureKij, highPressureKij);
    assertTrue(Double.isFinite(lowPressureKij));
    assertTrue(Double.isFinite(highPressureKij));
  }

  /**
   * Verifies the model-level acid solubility helper against the high-priority HNO3 and H2SO4 CO2 data points.
   */
  @Test
  public void testAcidSolubilityHelperMatchesHighPriorityCo2Data() {
    assertEquals(1381.5603693790067,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 0.0, 100.0), 60.0);
    assertEquals(2248.399491759289,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 24.0, 98.6), 80.0);
    assertEquals(1374.0996756747031,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 40.0, 100.0), 120.0);
    assertEquals(1591.4458533558266,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 48.0, 119.0), 150.0);
    assertEquals(613.0776621130957,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 53.0, 98.6), 150.0);
    assertEquals(676.8091875877002,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, 53.0, 101.3), 250.0);
    assertEquals(2.26,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("sulfuric acid", 98.0, 2.0, 25.0, 94.6), 0.01);
  }

  /**
   * Verifies the lower-priority uncertain low-temperature HNO3 measurements constrain the model without displacing the
   * high-pressure points.
   */
  @Test
  public void testAcidSolubilityHelperTracksUncertainLowPressureHno3Data() {
    assertEquals(723.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, -21.0, 20.0), 250.0);
    assertEquals(313.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0, -29.0, 20.0), 150.0);
  }

  /**
   * Verifies that the model-level component solubility helper can also report water in CO2.
   */
  @Test
  public void testComponentSolubilityHelperReportsWaterInCo2() {
    double waterInNitricSource = SystemVanLaarActivitySRK.componentSolubilityInCarbonDioxidePpm("water", "nitric acid",
        65.0, 35.0, 40.0, 100.0);
    double waterInSulfuricSource = SystemVanLaarActivitySRK.componentSolubilityInCarbonDioxidePpm("water",
        "sulfuric acid", 98.0, 2.0, 40.0, 100.0);

    assertTrue(waterInNitricSource > 0.0 && Double.isFinite(waterInNitricSource));
    assertTrue(waterInSulfuricSource > 0.0 && Double.isFinite(waterInSulfuricSource));
    assertTrue(waterInNitricSource > waterInSulfuricSource,
        "65 wt% HNO3 source should carry more water to CO2 than 98 wt% H2SO4 source");
  }

  /**
   * Verifies that a CO2-rich direct gamma-phi flash uses one internally consistent tuned carrier reference for every
   * Van Laar activity component.
   */
  @Test
  public void testCo2RichGammaPhiUsesCarrierReferenceForAllActivityComponents() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(313.15, 100.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 1000.0);
    system.addComponent("nitric acid", 3.0);
    system.addComponent("sulfuric acid", 1.0);
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);

    PhaseInterface vapour = system.getPhase(0);
    assertTrue(SystemVanLaarActivitySRK.isPredominantlyCarbonDioxidePhase(vapour));
    for (String componentName : new String[] { "water", "nitric acid", "sulfuric acid" }) {
      assertEquals(system.carbonDioxideCarrierFugacityCoefficient(componentName),
          system.getGammaPhiVapourFugacityCoefficient(vapour.getComponent(componentName), vapour), 0.0,
          componentName + " should use the tuned trace-CO2 carrier reference");
    }
  }

  /** Verifies that logarithmic K updates are more strongly damped near a phase-fraction boundary. */
  @Test
  public void testGammaPhiRelaxationTightensNearPhaseBoundary() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(293.15, 20.0);
    system.setBeta(0.5);
    double interiorUpdate = system.relaxGammaPhiKValue(1.0, 1.0e-4);

    system.setBeta(1.0 - 1.0e-6);
    double boundaryUpdate = system.relaxGammaPhiKValue(1.0, 1.0e-4);

    assertTrue(boundaryUpdate > interiorUpdate && boundaryUpdate < 1.0,
        "Near-boundary damping must take a smaller positive logarithmic K step");
  }

  /**
   * Verifies that direct gamma-phi flashing restores the EOS/GE phase roles after active-phase reordering.
   */
  @Test
  public void testDirectGammaPhiRestoresCreationOrderAfterPhaseReordering() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(313.15, 100.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 1000.0);
    system.addComponent("nitric acid", 3.0);
    system.addComponent("sulfuric acid", 1.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    system.setPhaseIndex(0, 1);
    system.setPhaseIndex(1, 0);
    new ThermodynamicOperations(system).TPflash();

    assertEquals(0, system.getPhaseIndex(0));
    assertEquals(1, system.getPhaseIndex(1));
    assertTrue(system.getPhase(0) instanceof PhaseEos);
    assertTrue(system.getPhase(1) instanceof PhaseGEVanLaarAcid);
  }

  /** Verifies that a liquid-only collapse keeps the reusable EOS/GE creation-order topology intact. */
  @Test
  public void testLiquidCollapsePreservesCreationOrderPhaseObjects() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(293.15, 20.0);
    system.addComponent("CO2", 1.0);
    system.addComponent("water", 10.0);
    system.addComponent("nitric acid", 1.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    system.collapseToSinglePhase(1);

    assertEquals(1, system.getNumberOfPhases());
    assertTrue(system.getPhase(0) instanceof PhaseGEVanLaarAcid);
    assertTrue(system.getEquationOfStatePhase() instanceof PhaseEos,
        "Selecting the GE liquid must not overwrite the creation-order EOS phase");

    system.prepareGammaPhiFlash();
    assertTrue(system.getPhase(0) instanceof PhaseEos);
    assertTrue(system.getPhase(1) instanceof PhaseGEVanLaarAcid);
  }

  /**
   * Verifies that a normal TPflash accepts the raw Van Laar acid split for a sulfuric-acid CO2 feed without applying a
   * post-flash solubility filter.
   */
  @Test
  public void testTPflashAcceptsRawSulfuricAcidSplit() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(313.15, 100.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 1000.0);
    system.addComponent("nitric acid", 1.0e-30);
    system.addComponent("sulfuric acid", 3.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "The raw Van Laar TPflash split should retain an aqueous phase");
    assertTrue(system.getBeta(1) > 1.0e-5, "The Van Laar liquid should be material");
    assertTrue(liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase() > 2.9,
        "The raw split should allocate essentially all H2SO4 to the Van Laar liquid");
    assertTrue(liquid.getComponent("water").getNumberOfMolesInPhase() > 0.0,
        "The raw split should contain water from the CO2-rich feed");

    double liquidAcidMass = liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase()
        * liquid.getComponent("sulfuric acid").getMolarMass();
    double liquidWaterMass = liquid.getComponent("water").getNumberOfMolesInPhase()
        * liquid.getComponent("water").getMolarMass();
    double liquidAcidWeightPercent = 100.0 * liquidAcidMass / (liquidAcidMass + liquidWaterMass);
    assertTrue(liquidAcidWeightPercent > 0.0 && liquidAcidWeightPercent < 100.0,
        "The accepted raw Van Laar liquid should have a finite acid-water composition");

    PhaseInterface gas = system.getPhase(0);
    double gasSulfuricPpm = gas.getComponent("sulfuric acid").getx() * 1.0e6;
    assertTrue(gasSulfuricPpm < 3.0, "CO2-rich phase H2SO4 should be reduced below the total feed ppm");

    String[] names = { "CO2", "water", "nitric acid", "sulfuric acid" };
    for (int i = 0; i < names.length; i++) {
      double phaseMoles = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        phaseMoles += system.getPhase(phaseIndex).getComponent(names[i]).getNumberOfMolesInPhase();
      }
      assertEquals(system.getPhase(0).getComponent(names[i]).getNumberOfmoles(), phaseMoles,
          Math.max(1.0e-10, Math.abs(phaseMoles) * 1.0e-12), "phase split must conserve " + names[i]);
    }
  }

  /**
   * Verifies that an undersaturated HNO3 inventory does not mask supersaturated H2SO4 dropout in a mixed-acid CO2 feed.
   */
  @Test
  public void testMixedAcidFlashDoesNotLetNitricAcidMaskSulfuricAcidDropout() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15 + 53.0, 100.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 30.0);
    system.addComponent("nitric acid", 3.0);
    system.addComponent("sulfuric acid", 1.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "Supersaturated H2SO4 should form a Van Laar liquid even when HNO3 is present");
    assertTrue(liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase() > 0.1,
        "H2SO4 should be allocated to the acid-rich liquid phase");

    PhaseInterface gas = system.getPhase(0);
    double gasSulfuricPpm = gas.getComponent("sulfuric acid").getx() * 1.0e6;
    assertTrue(gasSulfuricPpm < 1.0, "CO2-rich phase H2SO4 should be reduced below the total mixed-acid feed ppm");
  }

  /**
   * Verifies that a low-ppm H2SO4-only inventory can keep a material acid-water phase without failing on the Van Laar
   * phase-role fugacity guard.
   */
  @Test
  public void testLowPpmSulfuricAcidFlashKeepsAcidWaterPhase() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15 + 20.0, 20.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 50.0);
    system.addComponent("sulfuric acid", 1.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    assertEquals(2, system.getNumberOfPhases(), "Low-ppm H2SO4 with water should keep the acid-water phase");
    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "A Van Laar acid-water phase should be present");
    assertTrue(liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase() > 0.9,
        "H2SO4 should be retained in the acid-water phase");
    assertTrue(system.getPhase(0).getComponent("sulfuric acid").getx() * 1.0e6 < 1.0e-3,
        "CO2-rich phase should contain only trace H2SO4");
  }

  /**
   * Verifies that low-ppm mixed acid inventories collapse numerical trace Van Laar phases instead of failing the flash
   * on nonmaterial fugacity mismatch.
   */
  @Test
  public void testLowPpmMixedAcidFlashCollapsesTracePhase() {
    assertLowPpmMixedAcidFlashCollapsesTracePhase(1.0e-6);
  }

  /**
   * Verifies that ppm-level H2SO4 inventories above the material cutoff keep the acid-water phase.
   */
  @Test
  public void testLowPpmMixedAcidFlashKeepsPpmLevelSulfuricAcidPhase() {
    assertLowPpmMixedAcidFlashKeepsMaterialSulfuricAcidPhase(0.01);
    assertLowPpmMixedAcidFlashKeepsMaterialSulfuricAcidPhase(0.1);
  }

  /**
   * Verifies that material H2SO4 still forms an acid-water phase when HNO3 is present.
   */
  @Test
  public void testLowPpmMixedAcidFlashKeepsMaterialSulfuricAcidPhase() {
    assertLowPpmMixedAcidFlashKeepsMaterialSulfuricAcidPhase(0.1);
  }

  /**
   * Verifies one mixed-acid material sulfuric-acid flash case.
   *
   * @param sulfuricAcidMoles sulfuric acid feed in moles on a 1e6 mol CO2 basis
   */
  private void assertLowPpmMixedAcidFlashKeepsMaterialSulfuricAcidPhase(double sulfuricAcidMoles) {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15 + 20.0, 20.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 50.0);
    system.addComponent("nitric acid", 50.0);
    system.addComponent("sulfuric acid", sulfuricAcidMoles);
    system.createDatabase(true);
    system.setMixingRule("classic");

    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    assertEquals(2, system.getNumberOfPhases(), "Mixed acid case should keep the material H2SO4 acid-water phase");
    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "A Van Laar acid-water phase should be present");
    assertTrue(liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase() > 0.9 * sulfuricAcidMoles,
        "H2SO4 should remain in the acid-water phase");
  }

  /**
   * Verifies one low-ppm mixed acid flash case.
   *
   * @param sulfuricAcidMoles sulfuric acid feed in moles on a 1e6 mol CO2 basis
   */
  private void assertLowPpmMixedAcidFlashCollapsesTracePhase(double sulfuricAcidMoles) {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15 + 20.0, 20.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 50.0);
    system.addComponent("nitric acid", 50.0);
    system.addComponent("sulfuric acid", sulfuricAcidMoles);
    system.createDatabase(true);
    system.setMixingRule("classic");

    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    assertEquals(1, system.getNumberOfPhases(), "Low-ppm acid inventory should remain a single CO2-rich phase");
    PhaseInterface carrier = system.getPhase(0);
    assertTrue(carrier.getComponent("CO2").getx() > 0.999, "Collapsed phase should remain CO2 rich");
    assertEquals(50.0, carrier.getComponent("water").getx() * 1.0e6, 0.1,
        "Water ppm should reflect the material feed inventory");
    assertEquals(50.0, carrier.getComponent("nitric acid").getx() * 1.0e6, 0.1,
        "HNO3 ppm should reflect the material feed inventory");
    assertEquals(sulfuricAcidMoles, carrier.getComponent("sulfuric acid").getx() * 1.0e6,
        Math.max(1.0e-4, sulfuricAcidMoles * 1.0e-3), "H2SO4 ppm should reflect the material feed inventory");
  }

  /**
   * Verifies that HNO3 TPflash keeps the raw Van Laar aqueous split instead of applying the older coupled water/acid
   * solubility post-filter.
   */
  @Test
  public void testTPflashAcceptsRawHno3VanLaarSplit() {
    SystemVanLaarActivitySRK lowerInventory = hno3CarbonDioxideSystem(10000.0);
    new ThermodynamicOperations(lowerInventory).TPflash();
    lowerInventory.init(1);

    PhaseInterface lowerInventoryLiquid = findVanLaarLiquid(lowerInventory);
    assertNotNull(lowerInventoryLiquid, "The raw Van Laar TPflash split should retain the lower-HNO3 aqueous phase");
    assertTrue(lowerInventory.getBeta(1) > 1.0e-4, "The lower-HNO3 Van Laar liquid should be material");

    SystemVanLaarActivitySRK higherInventory = hno3CarbonDioxideSystem(20000.0);
    new ThermodynamicOperations(higherInventory).TPflash();
    higherInventory.init(1);

    PhaseInterface liquid = findVanLaarLiquid(higherInventory);
    assertNotNull(liquid, "The raw Van Laar TPflash split should retain the higher-HNO3 phase");
    assertTrue(higherInventory.getBeta(1) > lowerInventory.getBeta(1),
        "The higher-HNO3 inventory should produce a larger retained Van Laar phase");

    double acidMass = liquid.getComponent("nitric acid").getNumberOfMolesInPhase()
        * liquid.getComponent("nitric acid").getMolarMass();
    double waterMass = liquid.getComponent("water").getNumberOfMolesInPhase()
        * liquid.getComponent("water").getMolarMass();
    double acidWeightPercent = 100.0 * acidMass / (acidMass + waterMass);
    assertTrue(acidWeightPercent > 20.0 && acidWeightPercent < 100.0,
        "The Van Laar liquid should remain an acid-water activity-model phase");
  }

  /**
   * Verifies that a dry CO2/HNO3 feed keeps separate SRK carrier and Van Laar acid phases.
   */
  @Test
  public void testDryHno3CarbonDioxideTpflashKeepsAcidActivityPhase() {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(273.15 - 20.0, 20.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 0.0);
    system.addComponent("nitric acid", 1.0e6);
    system.addComponent("sulfuric acid", 0.0);
    system.createDatabase(true);
    system.setMixingRule("classic");

    new ThermodynamicOperations(system).TPflash();
    system.initProperties();

    assertEquals(PhaseType.GAS, system.getEquationOfStatePhase().getType(),
        "Direct gamma-phi iteration must retain the EOS vapour root");
    PhaseInterface carbonDioxideRichPhase = findCarbonDioxideRichPhase(system);
    PhaseInterface acidPhase = findVanLaarLiquid(system);
    assertNotNull(carbonDioxideRichPhase, "A CO2-rich SRK carrier phase should be present");
    assertNotNull(acidPhase, "A Van Laar acid phase should be present");
    assertTrue(carbonDioxideRichPhase.getComponent("CO2").getx() > 0.95, "The SRK carrier phase should stay CO2-rich");
    assertTrue(acidPhase.getComponent("nitric acid").getx() > 0.95, "The activity phase should stay acid-rich");
    assertTrue(acidPhase.getBeta() > 1.0e-3, "The acid phase should be material");
  }

  /**
   * Verifies notebook-visible TPflash report values for material HNO3, H2SO4, and mixed-acid CO2 feeds.
   */
  @Test
  public void testMaterialAcidSolubilityNotebookReferenceCases() {
    assertAcidReportValues(48.0, 169.0, 100000.0, 54000.0, 0.0,
        new double[] { 64.5861407589, 4.61321199649e-11, 65.7289201930, 34.2710798070, 0.00000000000, 6.07207705205e-11,
            35.4138592411, 64.5861407589, 0.00000000000, 2653.04705015, 1535.99871337, 0.129803580865 });
    assertAcidReportValues(0.0, 100.0, 100000.0, 54500.0, 0.0,
        new double[] { 65.1413190444, 3.19845990494e-11, 65.1780977127, 34.8219022872, 0.00000000000, 4.17895102490e-11,
            34.8586809556, 65.1413190444, 0.00000000000, 1408.71182512, 630.172783960, 0.132054525639 });
    assertAcidReportValues(-28.0, 20.0, 100000.0, 53500.0, 0.0,
        new double[] { 65.0877627397, 8.19980668535e-11, 65.2315894654, 34.7684105345, 0.00000000000, 1.07211268705e-10,
            34.9122372602, 65.0877627397, 0.00000000000, 425.310614046, 422.963641934, 0.132337239329 });
    assertAcidReportValues(50.0, 125.0, 10.0, 0.0, 90.0, new double[] { 97.9375, 5.66359e-11, 10.2859, 0.0, 89.7141,
        2.77433e-11, 2.06249, 0.0, 97.9375, 3.39581, 0.0706188, 9.65239e-05 });
    assertMixedAcidReportInvariants(50.0, 125.0, 10.0, 30.0, 90.0);
  }

  /**
   * Verifies the notebook report invariants for a mixed-acid feed without selecting one of several valid liquid roots.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @param waterMoles water feed in moles
   * @param nitricAcidMoles nitric acid feed in moles
   * @param sulfuricAcidMoles sulfuric acid feed in moles
   */
  private void assertMixedAcidReportInvariants(double temperatureC, double pressureBar, double waterMoles,
      double nitricAcidMoles, double sulfuricAcidMoles) {
    double[] actual = flashedAcidReportValues(temperatureC, pressureBar, waterMoles, nitricAcidMoles,
        sulfuricAcidMoles);
    for (double value : actual) {
      assertTrue(Double.isFinite(value) && value >= 0.0,
          "Every notebook report value should be finite and non-negative");
    }

    assertEquals(100.0, actual[1] + actual[2] + actual[3] + actual[4], 1.0e-8,
        "Acid-phase mole percentages should be normalized");
    assertEquals(100.0, actual[5] + actual[6] + actual[7] + actual[8], 1.0e-8,
        "Acid-phase weight percentages should be normalized");
    assertEquals(actual[0], actual[7] + actual[8], 1.0e-8,
        "Total acid weight percent should equal the HNO3 and H2SO4 contributions");
    assertTrue(actual[3] > 0.0 && actual[4] > 0.0, "The mixed liquid should contain both acid components");
    assertTrue(actual[9] > 0.0 && actual[10] > 0.0, "The CO2-rich phase should report acid and water vapour");
    assertTrue(actual[11] > 0.0 && actual[11] < 1.0, "The acid phase fraction should be material and bounded");
  }

  /**
   * Runs and verifies one notebook-visible acid report case.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @param waterMoles water feed in moles
   * @param nitricAcidMoles nitric acid feed in moles
   * @param sulfuricAcidMoles sulfuric acid feed in moles
   * @param expected expected report values
   */
  private void assertAcidReportValues(double temperatureC, double pressureBar, double waterMoles,
      double nitricAcidMoles, double sulfuricAcidMoles, double[] expected) {
    double[] actual = flashedAcidReportValues(temperatureC, pressureBar, waterMoles, nitricAcidMoles,
        sulfuricAcidMoles);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], actual[i], acidReportTolerance(expected[i]),
          "Mismatch in report value " + i + " for T=" + temperatureC + " C and P=" + pressureBar + " bara");
    }
  }

  /**
   * Calculates assertion tolerance for rounded notebook report values.
   *
   * <p>
   * Material values retain a two-percent relative tolerance. Sub-nanopercent carrier traces use an absolute
   * {@code 1.0e-9} percentage-point floor because their last digits depend on convergence path without affecting any
   * material phase quantity.
   * </p>
   *
   * @param expected expected report value
   * @return absolute tolerance
   */
  private double acidReportTolerance(double expected) {
    return Math.max(1.0e-9, Math.abs(expected) * 2.0e-2);
  }

  /**
   * Build the CO2/H2O/HNO3 test system used for acid dropout scans.
   *
   * @param nitricAcidMoles HNO3 moles on a 1e6 mol CO2 and 1000 mol water basis
   * @return configured Van Laar activity SRK system
   */
  private SystemVanLaarActivitySRK hno3CarbonDioxideSystem(double nitricAcidMoles) {
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(313.15, 100.0);
    system.addComponent("CO2", 1.0e6);
    system.addComponent("water", 1000.0);
    system.addComponent("nitric acid", nitricAcidMoles);
    system.addComponent("sulfuric acid", 1.0e-30);
    system.createDatabase(true);
    system.setMixingRule("classic");
    return system;
  }

  /**
   * Core test: for a ternary mixture, the activity coefficient read from the liquid phase equals the Van Laar model
   * value, and the component fugacity {@code x * phi * P} equals {@code gamma * x *
   * P0} and the reference paper partial pressure.
   */
  @Test
  public void testGammaPhiIdentityTernary() {
    double t = 273.15;
    double p = 1.0;
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(60.0, 20.0, 20.0);
    SystemVanLaarActivitySRK system = buildSystem(60.0, 20.0, 20.0, t, p);
    system.init(0);
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "Van Laar liquid phase must exist");

    String[] names = { "water", "nitric acid", "sulfuric acid" };
    double[] gammaRef = { NitricSulfuricAcidVaporPressure.activityCoefficientWater(x[0], x[1], x[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(x[0], x[1], x[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(x[0], x[1], x[2], t) };
    double[] p0RefBar = { NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PASCALS_PER_BAR };
    double[] partialRefBar = {
        NitricSulfuricAcidVaporPressure.partialPressureWater(x[0], x[1], x[2], t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureNitricAcid(x[0], x[1], x[2], t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(x[0], x[1], x[2], t) / PASCALS_PER_BAR };

    for (int i = 0; i < names.length; i++) {
      double gammaSystem = liquid.getActivityCoefficient(i);
      assertEquals(gammaRef[i], gammaSystem, 1.0e-9, "activity coefficient mismatch for " + names[i]);

      double xi = liquid.getComponent(i).getx();
      double phi = liquid.getComponent(i).getFugacityCoefficient();
      double fugacity = xi * phi * liquid.getPressure();

      // Identity 1: fugacity = gamma * x * P0
      assertEquals(gammaRef[i] * xi * p0RefBar[i], fugacity, 1.0e-12, "gamma*x*P0 != fugacity for " + names[i]);

      // Identity 2: fugacity equals the reference paper partial pressure (in bar)
      assertEquals(partialRefBar[i], fugacity, Math.abs(partialRefBar[i]) * 1.0e-9 + 1.0e-15,
          "fugacity != reference partial pressure for " + names[i]);

      // Recovered pure-component vapour pressure P0 = phi * P / gamma
      double p0Recovered = phi * liquid.getPressure() / gammaSystem;
      assertEquals(p0RefBar[i], p0Recovered, Math.abs(p0RefBar[i]) * 1.0e-9 + 1.0e-15,
          "recovered P0 mismatch for " + names[i]);
    }
  }

  /**
   * Binary water-sulfuric-acid test (50 wt% each) verifying the water fugacity matches the reference paper partial
   * pressure.
   */
  @Test
  public void testGammaPhiIdentityBinaryWaterSulfuric() {
    double t = 273.15;
    double p = 1.0;
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(50.0, 0.0, 50.0);
    SystemVanLaarActivitySRK system = buildSystem(50.0, 0.0, 50.0, t, p);
    system.init(0);
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid);

    // component 0 = water, component 1 = sulfuric acid (nitric acid absent)
    double xWater = liquid.getComponent(0).getx();
    double phiWater = liquid.getComponent(0).getFugacityCoefficient();
    double fugWater = xWater * phiWater * liquid.getPressure();
    double refWater = NitricSulfuricAcidVaporPressure.partialPressureWater(x[0], x[1], x[2], t) / PASCALS_PER_BAR;
    assertEquals(refWater, fugWater, Math.abs(refWater) * 1.0e-9 + 1.0e-15);

    double xSulf = liquid.getComponent(1).getx();
    double phiSulf = liquid.getComponent(1).getFugacityCoefficient();
    double fugSulf = xSulf * phiSulf * liquid.getPressure();
    double refSulf = NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(x[0], x[1], x[2], t) / PASCALS_PER_BAR;
    assertEquals(refSulf, fugSulf, Math.abs(refSulf) * 1.0e-9 + 1.0e-15);
  }

  /**
   * Demonstrates the user requested API flow: build the system, add components and run a TPflash, then read the
   * liquid-phase fugacities and confirm the gamma-phi identity holds on the flashed result. A carrier gas (CO2) is
   * added so the flash produces a genuine vapour-liquid equilibrium and instantiates the Van Laar liquid phase; without
   * a vapour phase the all-liquid acid mixture collapses onto the EOS phase object and the activity-model phase is
   * never exposed.
   */
  @Test
  public void testTPflashGammaPhiIdentity() {
    double t = 273.15;
    double p = 1.0;
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(70.0, 15.0, 15.0);
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(t, p);
    system.addComponent("water", x[0]);
    system.addComponent("nitric acid", x[1]);
    system.addComponent("sulfuric acid", x[2]);
    system.addComponent("CO2", 10.0);
    system.createDatabase(true);
    system.setMixingRule(2);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "TPflash with a carrier gas must instantiate the Van Laar liquid phase");

    // The Van Laar model is evaluated on the liquid's renormalised acid
    // composition.
    double[] xa = acidFractions(liquid);
    double[] gammaRef = { NitricSulfuricAcidVaporPressure.activityCoefficientWater(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(xa[0], xa[1], xa[2], t) };
    double[] p0RefBar = { NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PASCALS_PER_BAR };

    String[] names = { "water", "nitric acid", "sulfuric acid" };
    for (int i = 0; i < 3; i++) {
      double xi = liquid.getComponent(i).getx();
      double gammaSystem = liquid.getActivityCoefficient(i);
      double phi = liquid.getComponent(i).getFugacityCoefficient();
      double fugacity = xi * phi * liquid.getPressure();

      assertTrue(phi > 0.0 && Double.isFinite(phi), "fugacity coefficient must be positive finite for " + names[i]);
      // System activity coefficient equals the Van Laar model on the liquid's acid
      // basis.
      assertEquals(gammaRef[i], gammaSystem, Math.abs(gammaRef[i]) * 1.0e-9 + 1.0e-15,
          "activity coefficient mismatch for " + names[i]);
      // gamma-phi identity on the flashed liquid: fugacity = gamma * x * P0.
      assertEquals(gammaRef[i] * xi * p0RefBar[i], fugacity,
          Math.abs(gammaRef[i] * xi * p0RefBar[i]) * 1.0e-9 + 1.0e-18, "gamma*x*P0 != fugacity for " + names[i]);
      // Recovered pure-component vapour pressure P0 = phi * P / gamma.
      double p0Recovered = phi * liquid.getPressure() / gammaSystem;
      assertEquals(p0RefBar[i], p0Recovered, Math.abs(p0RefBar[i]) * 1.0e-9 + 1.0e-15,
          "recovered P0 mismatch for " + names[i]);
    }
  }

  /**
   * Demonstrates the user's carrier-gas idea. Adding CO2 (which stays predominantly in the vapour phase) lets the flash
   * establish a real vapour-liquid equilibrium. The Van Laar activity model and the gamma-phi identity
   * {@code fugacity_i = gamma_i * x_i * P0_i} are then reproduced exactly by the flashed liquid. The carrier gas does
   * shift the equilibrium liquid composition (water partitions into the vapour), so the absolute acid partial pressures
   * move; what is invariant is the activity model itself: at the resulting composition the identity and the recovered
   * pure vapour pressures are exact.
   */
  @Test
  public void testCarrierGasReproducesActivityModelUnderFlash() {
    double t = 273.15;
    double p = 1.0;
    double[] x = NitricSulfuricAcidVaporPressure.moleFractionsFromMassFractions(60.0, 20.0, 20.0);
    SystemVanLaarActivitySRK system = new SystemVanLaarActivitySRK(t, p);
    system.addComponent("water", x[0]);
    system.addComponent("nitric acid", x[1]);
    system.addComponent("sulfuric acid", x[2]);
    // Large excess of CO2 carrier gas to establish a genuine VLE.
    system.addComponent("CO2", 10.0);
    system.createDatabase(true);
    system.setMixingRule(2);

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.init(1);

    PhaseInterface liquid = findVanLaarLiquid(system);
    assertNotNull(liquid, "A Van Laar liquid phase must form in the carrier-gas VLE");

    double[] xa = acidFractions(liquid);
    double[] gammaRef = { NitricSulfuricAcidVaporPressure.activityCoefficientWater(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(xa[0], xa[1], xa[2], t) };
    double[] p0RefBar = { NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PASCALS_PER_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PASCALS_PER_BAR };

    for (int i = 0; i < 3; i++) {
      double xi = liquid.getComponent(i).getx();
      double gammaSystem = liquid.getActivityCoefficient(i);
      double phi = liquid.getComponent(i).getFugacityCoefficient();
      double fugacity = xi * phi * liquid.getPressure();
      // The activity coefficient still matches the Van Laar model on the liquid's
      // acid basis.
      assertEquals(gammaRef[i], gammaSystem, Math.abs(gammaRef[i]) * 1.0e-9 + 1.0e-15,
          "carrier gas changed the Van Laar activity coefficient for component " + i);
      // The gamma-phi identity is exact on the flashed liquid.
      assertEquals(gammaRef[i] * xi * p0RefBar[i], fugacity,
          Math.abs(gammaRef[i] * xi * p0RefBar[i]) * 1.0e-9 + 1.0e-18, "gamma*x*P0 != fugacity for component " + i);
      // The recovered pure-component vapour pressure equals the reference value.
      double p0Recovered = phi * liquid.getPressure() / gammaSystem;
      assertEquals(p0RefBar[i], p0Recovered, Math.abs(p0RefBar[i]) * 1.0e-9 + 1.0e-15,
          "recovered P0 must equal the reference pure vapour pressure for component " + i);
    }

    // CO2 is outside the Van Laar acid model and is strongly rejected from the
    // liquid phase.
    double xCO2 = 0.0;
    double gammaCO2 = 0.0;
    for (int i = 0; i < liquid.getNumberOfComponents(); i++) {
      if (liquid.getComponent(i).getName().equalsIgnoreCase("CO2")) {
        xCO2 = liquid.getComponent(i).getx();
        gammaCO2 = liquid.getActivityCoefficient(i);
      }
    }
    assertTrue(xCO2 < 1.0e-8, "CO2 should be essentially excluded from the Van Laar liquid");
    assertTrue(gammaCO2 > 1.0e10, "CO2 should have a high activity in the Van Laar liquid");
  }
}
