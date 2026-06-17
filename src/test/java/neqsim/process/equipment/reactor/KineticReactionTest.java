package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for KineticReaction class.
 */
public class KineticReactionTest extends neqsim.NeqSimTest {

  @Test
  public void testArrheniusRateConstant() {
    KineticReaction rxn = new KineticReaction("test");
    rxn.setPreExponentialFactor(1.0e10);
    rxn.setActivationEnergy(100000.0); // 100 kJ/mol
    rxn.setTemperatureExponent(0.0);

    // k = A * exp(-Ea/(R*T))
    // At T=500K: k = 1e10 * exp(-100000/(8.31446*500))
    double k500 = rxn.calculateRateConstant(500.0);
    double expectedK500 = 1.0e10 * Math.exp(-100000.0 / (8.31446 * 500.0));
    assertEquals(expectedK500, k500, expectedK500 * 1e-6);

    // At T=600K: should be larger
    double k600 = rxn.calculateRateConstant(600.0);
    assertTrue(k600 > k500, "Rate constant should increase with temperature");
  }

  @Test
  public void testModifiedArrheniusWithTempExponent() {
    KineticReaction rxn = new KineticReaction("modified");
    rxn.setPreExponentialFactor(5.0e8);
    rxn.setActivationEnergy(80000.0);
    rxn.setTemperatureExponent(1.5);

    double T = 550.0;
    double k = rxn.calculateRateConstant(T);
    double expected = 5.0e8 * Math.pow(T, 1.5) * Math.exp(-80000.0 / (8.31446 * T));
    assertEquals(expected, k, expected * 1e-6);
  }

  @Test
  public void testEquilibriumConstantCorrelation() {
    KineticReaction rxn = new KineticReaction("equilibrium");
    // ln(Keq) = a + b/T + c*ln(T) + d*T
    rxn.setEquilibriumConstantCorrelation(10.0, -5000.0, 0.0, 0.0);

    double T = 500.0;
    double Keq = rxn.calculateEquilibriumConstant(T);
    double expectedLnKeq = 10.0 + (-5000.0) / T;
    double expected = Math.exp(expectedLnKeq);
    assertEquals(expected, Keq, expected * 1e-6);
  }

  @Test
  public void testPowerLawRate() {
    // A simple first-order reaction: A -> B
    // r = k * [A]^1
    KineticReaction rxn = new KineticReaction("A to B");
    rxn.addReactant("methane", 1.0, 1.0); // stoich = -1, order = 1
    rxn.addProduct("ethane", 0.5); // stoich = +0.5
    rxn.setPreExponentialFactor(1.0e6);
    rxn.setActivationEnergy(50000.0);
    rxn.setRateType(KineticReaction.RateType.POWER_LAW);

    // Create a fluid system to test
    SystemInterface fluid = new SystemSrkEos(273.15 + 200.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    try {
      ops.TPflash();
    } catch (Exception e) {
      // ignore
    }
    fluid.initProperties();

    double rate = rxn.calculateRate(fluid, 0);
    // Rate should be positive (forward direction consuming A)
    assertTrue(rate > 0, "Rate should be positive for power-law reaction");
  }

  @Test
  public void testStoichiometry() {
    KineticReaction rxn = new KineticReaction("synthesis");
    rxn.addReactant("nitrogen", 1.0, 1.0);
    rxn.addReactant("hydrogen", 3.0, 1.5);
    rxn.addProduct("ammonia", 2.0);

    // Stoichiometric coefficients: reactants negative, products positive
    assertEquals(-1.0, rxn.getStoichiometricCoefficient("nitrogen"), 1e-10);
    assertEquals(-3.0, rxn.getStoichiometricCoefficient("hydrogen"), 1e-10);
    assertEquals(2.0, rxn.getStoichiometricCoefficient("ammonia"), 1e-10);
    assertEquals(0.0, rxn.getStoichiometricCoefficient("nonexistent"), 1e-10);
  }

  @Test
  public void testHeatOfReaction() {
    KineticReaction rxn = new KineticReaction("exothermic");
    rxn.setHeatOfReaction(-92000.0); // exothermic, J/mol
    assertEquals(-92000.0, rxn.getHeatOfReaction(), 1e-6);
  }

  @Test
  public void testReversibleReaction() {
    KineticReaction rxn = new KineticReaction("reversible");
    rxn.setReversible(true);
    assertTrue(rxn.isReversible());
    rxn.setEquilibriumConstantCorrelation(5.0, -2000.0, 0.0, 0.0);

    double T = 400.0;
    double Keq = rxn.calculateEquilibriumConstant(T);
    assertTrue(Keq > 0, "Equilibrium constant should be positive");
  }

  @Test
  public void testRateTypeAndBasis() {
    KineticReaction rxn = new KineticReaction("lhhw test");
    rxn.setRateType(KineticReaction.RateType.LHHW);
    assertEquals(KineticReaction.RateType.LHHW, rxn.getRateType());

    rxn.setRateBasis(KineticReaction.RateBasis.CATALYST_MASS);
    assertEquals(KineticReaction.RateBasis.CATALYST_MASS, rxn.getRateBasis());
  }

  @Test
  public void testAdsorptionTermSetup() {
    KineticReaction rxn = new KineticReaction("lhhw");
    rxn.setRateType(KineticReaction.RateType.LHHW);
    rxn.addReactant("methane", 1.0, 1.0);
    rxn.addProduct("hydrogen", 2.0);
    rxn.setPreExponentialFactor(1.0e8);
    rxn.setActivationEnergy(80000.0);
    rxn.addAdsorptionTerm("methane", 5.0, 1.0);
    rxn.setAdsorptionExponent(2);

    // Just verify it doesn't throw exceptions
    assertEquals(KineticReaction.RateType.LHHW, rxn.getRateType());
  }
}
