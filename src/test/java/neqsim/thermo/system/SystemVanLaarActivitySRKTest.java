package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEVanLaarAcid;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * SystemVanLaarActivitySRKTest class.
 * </p>
 *
 * <p>
 * Demonstrates that the gamma-phi system {@link SystemVanLaarActivitySRK}
 * reproduces the equilibrium
 * identity {@code fugacity_i = gamma_i * x_i * P0_i} for the
 * water-nitric-acid-sulfuric-acid mixture,
 * matching the partial pressures of the standalone Van Laar model
 * {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure} (Taleb,
 * Ponche and Mirabel,
 * 1996).
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class SystemVanLaarActivitySRKTest extends neqsim.NeqSimTest {
  /** Conversion factor from pascal (Pa) to bar. */
  private static final double PA_TO_BAR = 1.0e5;

  /**
   * Build a configured ternary water-nitric-acid-sulfuric-acid system from mass
   * fractions.
   *
   * @param wH2O        mass fraction (or weight percent) of water
   * @param wHNO3       mass fraction (or weight percent) of nitric acid
   * @param wH2SO4      mass fraction (or weight percent) of sulfuric acid
   * @param temperature temperature in kelvin
   * @param pressure    pressure in bara
   * @return a configured {@link SystemVanLaarActivitySRK}
   */
  private SystemVanLaarActivitySRK buildSystem(double wH2O, double wHNO3, double wH2SO4,
      double temperature, double pressure) {
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
   * Locate the Van Laar excess-Gibbs-energy liquid phase in a (possibly
   * multiphase) system.
   *
   * @param system the system to search
   * @return the {@link PhaseGEVanLaarAcid} liquid phase, or {@code null} if not
   *         present
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
   * Renormalised acid-basis mole fractions {x_H2O, x_HNO3, x_H2SO4} read from a
   * liquid phase,
   * excluding any carrier gas. This is the composition basis on which the Van
   * Laar model is
   * evaluated, so the standalone correlations called with these fractions
   * reproduce the phase's
   * activity coefficients exactly.
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
   * Verifies that HNO3 SRK vapour tuning is embedded in the system model and
   * applied through the
   * regular add-component workflow.
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
    assertEquals(0.849356, system.getPhase(0).getComponent("nitric acid").getAcentricFactor(),
        1.0e-12);
  }

  /**
   * Verifies that the model refreshes temperature-dependent acid kij values
   * during initialization.
   */
  @Test
  public void testTemperatureDependentAcidKijAppliedByDefault() {
    double temperatureC = 40.0;
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

    assertEquals(SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(temperatureC),
        mixingRule.getBinaryInteractionParameter(co2Index, hno3Index), 1.0e-12);
    assertEquals(SystemVanLaarActivitySRK.carbonDioxideSulfuricAcidKij(temperatureC),
        mixingRule.getBinaryInteractionParameter(co2Index, h2so4Index), 1.0e-12);

    double newTemperatureC = 53.0;
    system.setTemperature(newTemperatureC + 273.15);
    system.init(0);
    assertEquals(SystemVanLaarActivitySRK.carbonDioxideNitricAcidKij(newTemperatureC),
        mixingRule.getBinaryInteractionParameter(co2Index, hno3Index), 1.0e-12);
  }

  /**
   * Verifies the model-level acid solubility helper against the high-priority
   * HNO3 and H2SO4 CO2
   * data points.
   */
  @Test
  public void testAcidSolubilityHelperMatchesHighPriorityCo2Data() {
    assertEquals(1828.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            0.0, 100.0),
        60.0);
    assertEquals(2150.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            24.0, 98.6),
        80.0);
    assertEquals(2443.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            40.0, 100.0),
        120.0);
    assertEquals(1250.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            48.0, 119.0),
        150.0);
    assertEquals(830.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            53.0, 98.6),
        150.0);
    assertEquals(520.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            53.0, 101.3),
        250.0);
    assertEquals(2.26,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("sulfuric acid", 98.0, 2.0,
            25.0, 94.6),
        0.01);
  }

  /**
   * Verifies the lower-priority uncertain low-temperature HNO3 measurements
   * constrain the model
   * without displacing the high-pressure points.
   */
  @Test
  public void testAcidSolubilityHelperTracksUncertainLowPressureHno3Data() {
    assertEquals(723.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            -21.0, 20.0),
        250.0);
    assertEquals(313.0,
        SystemVanLaarActivitySRK.acidSolubilityInCarbonDioxidePpm("nitric acid", 65.0, 35.0,
            -29.0, 20.0),
        150.0);
  }

  /**
   * Verifies that the model-level component solubility helper can also report
   * water in CO2.
   */
  @Test
  public void testComponentSolubilityHelperReportsWaterInCo2() {
    double waterInNitricSource = SystemVanLaarActivitySRK.componentSolubilityInCarbonDioxidePpm(
        "water", "nitric acid", 65.0, 35.0, 40.0, 100.0);
    double waterInSulfuricSource = SystemVanLaarActivitySRK.componentSolubilityInCarbonDioxidePpm(
        "water", "sulfuric acid", 98.0, 2.0, 40.0, 100.0);

    assertTrue(waterInNitricSource > 0.0 && Double.isFinite(waterInNitricSource));
    assertTrue(waterInSulfuricSource > 0.0 && Double.isFinite(waterInSulfuricSource));
    assertTrue(waterInNitricSource > waterInSulfuricSource,
        "65 wt% HNO3 source should carry more water to CO2 than 98 wt% H2SO4 source");
  }

  /**
   * Verifies that a normal TPflash accepts the raw Van Laar acid split for a
   * sulfuric-acid CO2
   * feed without applying a post-flash solubility filter.
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
  assertTrue(liquid.getComponent("water").getNumberOfMolesInPhase() > 30.0,
    "The raw split should pull water from the CO2-rich phase");

    double liquidAcidMass = liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase()
        * liquid.getComponent("sulfuric acid").getMolarMass();
    double liquidWaterMass = liquid.getComponent("water").getNumberOfMolesInPhase()
        * liquid.getComponent("water").getMolarMass();
  assertEquals(30.6, 100.0 * liquidAcidMass / (liquidAcidMass + liquidWaterMass), 1.0,
    "The accepted raw Van Laar liquid should preserve the converged TPflash composition");

    PhaseInterface gas = system.getPhase(0);
    double gasSulfuricPpm = gas.getComponent("sulfuric acid").getx() * 1.0e6;
    assertTrue(gasSulfuricPpm < 3.0,
        "CO2-rich phase H2SO4 should be reduced below the total feed ppm");

    String[] names = { "CO2", "water", "nitric acid", "sulfuric acid" };
    for (int i = 0; i < names.length; i++) {
      double phaseMoles = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        phaseMoles += system.getPhase(phaseIndex).getComponent(names[i]).getNumberOfMolesInPhase();
      }
      assertEquals(system.getPhase(0).getComponent(names[i]).getNumberOfmoles(), phaseMoles,
          Math.max(1.0e-10, Math.abs(phaseMoles) * 1.0e-12),
          "phase split must conserve " + names[i]);
    }
  }

  /**
   * Verifies that an undersaturated HNO3 inventory does not mask supersaturated
   * H2SO4 dropout in a
   * mixed-acid CO2 feed.
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
    assertNotNull(liquid,
        "Supersaturated H2SO4 should form a Van Laar liquid even when HNO3 is present");
    assertTrue(liquid.getComponent("sulfuric acid").getNumberOfMolesInPhase() > 0.1,
        "H2SO4 should be allocated to the acid-rich liquid phase");

    PhaseInterface gas = system.getPhase(0);
    double gasSulfuricPpm = gas.getComponent("sulfuric acid").getx() * 1.0e6;
    assertTrue(gasSulfuricPpm < 1.0,
        "CO2-rich phase H2SO4 should be reduced below the total mixed-acid feed ppm");
  }

  /**
     * Verifies that HNO3 TPflash keeps the raw Van Laar aqueous split instead of
     * applying the older
     * coupled water/acid solubility post-filter.
   */
  @Test
    public void testTPflashAcceptsRawHno3VanLaarSplit() {
    SystemVanLaarActivitySRK lowerInventory = hno3CarbonDioxideSystem(2300.0);
    new ThermodynamicOperations(lowerInventory).TPflash();
    lowerInventory.init(1);

    PhaseInterface lowerInventoryLiquid = findVanLaarLiquid(lowerInventory);
    assertNotNull(lowerInventoryLiquid,
      "The raw Van Laar TPflash split should retain the lower-HNO3 aqueous phase");
    assertTrue(lowerInventory.getBeta(1) > 1.0e-4,
      "The lower-HNO3 Van Laar liquid should be material");

    SystemVanLaarActivitySRK higherInventory = hno3CarbonDioxideSystem(2500.0);
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
    assertEquals(15.0, acidWeightPercent, 1.0,
      "The accepted raw Van Laar liquid should preserve the converged TPflash composition");
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
   * Core test: for a ternary mixture, the activity coefficient read from the
   * liquid phase equals the
   * Van Laar model value, and the component fugacity {@code x * phi * P} equals
   * {@code gamma * x *
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
    double[] gammaRef = {
        NitricSulfuricAcidVaporPressure.activityCoefficientWater(x[0], x[1], x[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(x[0], x[1], x[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(x[0], x[1], x[2], t) };
    double[] p0RefBar = {
        NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PA_TO_BAR };
    double[] partialRefBar = {
        NitricSulfuricAcidVaporPressure.partialPressureWater(x[0], x[1], x[2], t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureNitricAcid(x[0], x[1], x[2], t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(x[0], x[1], x[2], t)
            / PA_TO_BAR };

    for (int i = 0; i < names.length; i++) {
      double gammaSystem = liquid.getActivityCoefficient(i);
      assertEquals(gammaRef[i], gammaSystem, 1.0e-9,
          "activity coefficient mismatch for " + names[i]);

      double xi = liquid.getComponent(i).getx();
      double phi = liquid.getComponent(i).getFugacityCoefficient();
      double fugacity = xi * phi * liquid.getPressure();

      // Identity 1: fugacity = gamma * x * P0
      assertEquals(gammaRef[i] * xi * p0RefBar[i], fugacity, 1.0e-12,
          "gamma*x*P0 != fugacity for " + names[i]);

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
   * Binary water-sulfuric-acid test (50 wt% each) verifying the water fugacity
   * matches the reference
   * paper partial pressure.
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
    double refWater = NitricSulfuricAcidVaporPressure.partialPressureWater(x[0], x[1], x[2], t) / PA_TO_BAR;
    assertEquals(refWater, fugWater, Math.abs(refWater) * 1.0e-9 + 1.0e-15);

    double xSulf = liquid.getComponent(1).getx();
    double phiSulf = liquid.getComponent(1).getFugacityCoefficient();
    double fugSulf = xSulf * phiSulf * liquid.getPressure();
    double refSulf = NitricSulfuricAcidVaporPressure.partialPressureSulfuricAcid(x[0], x[1], x[2], t) / PA_TO_BAR;
    assertEquals(refSulf, fugSulf, Math.abs(refSulf) * 1.0e-9 + 1.0e-15);
  }

  /**
   * Demonstrates the user requested API flow: build the system, add components
   * and run a TPflash,
   * then read the liquid-phase fugacities and confirm the gamma-phi identity
   * holds on the flashed
   * result. A carrier gas (CO2) is added so the flash produces a genuine
   * vapour-liquid equilibrium
   * and instantiates the Van Laar liquid phase; without a vapour phase the
   * all-liquid acid mixture
   * collapses onto the EOS phase object and the activity-model phase is never
   * exposed.
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
    double[] gammaRef = {
        NitricSulfuricAcidVaporPressure.activityCoefficientWater(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(xa[0], xa[1], xa[2], t) };
    double[] p0RefBar = {
        NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PA_TO_BAR };

    String[] names = { "water", "nitric acid", "sulfuric acid" };
    for (int i = 0; i < 3; i++) {
      double xi = liquid.getComponent(i).getx();
      double gammaSystem = liquid.getActivityCoefficient(i);
      double phi = liquid.getComponent(i).getFugacityCoefficient();
      double fugacity = xi * phi * liquid.getPressure();

      assertTrue(phi > 0.0 && Double.isFinite(phi),
          "fugacity coefficient must be positive finite for " + names[i]);
      // System activity coefficient equals the Van Laar model on the liquid's acid
      // basis.
      assertEquals(gammaRef[i], gammaSystem, Math.abs(gammaRef[i]) * 1.0e-9 + 1.0e-15,
          "activity coefficient mismatch for " + names[i]);
      // gamma-phi identity on the flashed liquid: fugacity = gamma * x * P0.
      assertEquals(gammaRef[i] * xi * p0RefBar[i], fugacity,
          Math.abs(gammaRef[i] * xi * p0RefBar[i]) * 1.0e-9 + 1.0e-18,
          "gamma*x*P0 != fugacity for " + names[i]);
      // Recovered pure-component vapour pressure P0 = phi * P / gamma.
      double p0Recovered = phi * liquid.getPressure() / gammaSystem;
      assertEquals(p0RefBar[i], p0Recovered, Math.abs(p0RefBar[i]) * 1.0e-9 + 1.0e-15,
          "recovered P0 mismatch for " + names[i]);
    }
  }

  /**
   * Demonstrates the user's carrier-gas idea. Adding CO2 (which stays
   * predominantly in the vapour
   * phase) lets the flash establish a real vapour-liquid equilibrium. The Van
   * Laar activity model
   * and the gamma-phi identity {@code fugacity_i = gamma_i * x_i * P0_i} are then
   * reproduced exactly
   * by the flashed liquid. The carrier gas does shift the equilibrium liquid
   * composition (water
   * partitions into the vapour), so the absolute acid partial pressures move;
   * what is invariant is
   * the activity model itself: at the resulting composition the identity and the
   * recovered pure
   * vapour pressures are exact.
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
    double[] gammaRef = {
        NitricSulfuricAcidVaporPressure.activityCoefficientWater(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientNitricAcid(xa[0], xa[1], xa[2], t),
        NitricSulfuricAcidVaporPressure.activityCoefficientSulfuricAcid(xa[0], xa[1], xa[2], t) };
    double[] p0RefBar = {
        NitricSulfuricAcidVaporPressure.pureVaporPressureWater(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureNitricAcid(t) / PA_TO_BAR,
        NitricSulfuricAcidVaporPressure.pureVaporPressureSulfuricAcid(t) / PA_TO_BAR };

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
          Math.abs(gammaRef[i] * xi * p0RefBar[i]) * 1.0e-9 + 1.0e-18,
          "gamma*x*P0 != fugacity for component " + i);
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
