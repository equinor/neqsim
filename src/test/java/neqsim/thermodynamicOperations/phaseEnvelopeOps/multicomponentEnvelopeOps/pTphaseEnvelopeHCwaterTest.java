package neqsim.thermodynamicOperations.phaseEnvelopeOps.multicomponentEnvelopeOps;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * Tests for phase envelop algorithm as presented in: Lindeloff, N. and M.L. Michelsen, Phase
 * envelope calculations for hydrocarbon-water mixtures. SPE Journal, 2003. 8(03): p. 298-303.
 *
 **/
public class pTphaseEnvelopeHCwaterTest {
  static SystemInterface thermoSystem = null;

  /**
   * In the currecnt test Fluid1 from the paper is tested.
   **/
  @Test
  void testFluid1() {
    thermoSystem = new SystemSrkEos(298.0, 10.0);
    thermoSystem.addComponent("nitrogen", 0.34);
    thermoSystem.addComponent("CO2", 0.84);
    thermoSystem.addComponent("methane", 89.95);
    thermoSystem.addComponent("ethane", 5.17);
    thermoSystem.addComponent("propane", 2.04);
    thermoSystem.addComponent("i-butane", 0.36);
    thermoSystem.addComponent("n-butane", 0.55);
    thermoSystem.addComponent("i-pentane", 0.14);
    thermoSystem.addComponent("n-pentane", 0.10);
    thermoSystem.addComponent("n-hexane", 0.01);
    thermoSystem.addComponent("water", 500e-4);
    thermoSystem.setMixingRule("classic");

    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.calcPTphaseEnvelopeHCwater();

  }

  /**
   * In the currecnt test Fluid2 from the paper is tested.
   **/
  @Test
  void testFluid2() {
    thermoSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    thermoSystem.addComponent("CO2", 2.79);
    thermoSystem.addComponent("methane", 71.51);
    thermoSystem.addComponent("ethane", 5.77);
    thermoSystem.addComponent("propane", 4.10);
    thermoSystem.addComponent("i-butane", 1.32);
    thermoSystem.addComponent("n-butane", 1.60);
    thermoSystem.addComponent("i-pentane", 0.82);
    thermoSystem.addComponent("n-pentane", 0.64);
    thermoSystem.addComponent("n-hexane", 1.05);
    thermoSystem.addTBPfraction("C7", 10.40, 191.0 / 1.0e3, 0.82);

    thermoSystem.addComponent("water", 8.0);
    thermoSystem.setMixingRule(10);

    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.calcPTphaseEnvelopeHCwater();

  }
}
