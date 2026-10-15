package neqsim.process.measurementdevice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

class pHProbeTest {
  private static final Logger logger = LogManager.getLogger(pHProbeTest.class);

  /** Logger object for class. */

  static SystemInterface testFluid;
  static StreamInterface stream_1;

  @BeforeEach
  void setUp() {
    SystemInterface testFluid = new SystemSrkCPAstatoil(318.15, 50.0);
    testFluid.addComponent("nitrogen", 1.205);
    testFluid.addComponent("CO2", 1.340);
    testFluid.addComponent("methane", 87.974);
    testFluid.addComponent("ethane", 5.258);
    testFluid.addComponent("propane", 3.283);
    testFluid.addComponent("i-butane", 0.082);
    testFluid.addComponent("n-butane", 0.487);
    testFluid.addComponent("i-pentane", 0.056);
    testFluid.addComponent("n-pentane", 1.053);
    testFluid.addComponent("nC10", 14.053);
    testFluid.addComponent("water", 141.053);
    testFluid.setMixingRule(10);
    testFluid.setMultiPhaseCheck(true);
    stream_1 = new Stream("Stream1", testFluid);
    stream_1.run();
  }

  @Test
  void testGetMeasuredValue() {
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.run();
    logger.info("pH " + phmeasurement.getMeasuredValue());
    // In unbuffered CO2-water, electroneutrality gives m_H ~= m_HCO3.
    // Use an independent Plummer-Busenberg molal K1, not the former legacy x-scale pH.
    neqsim.thermo.phase.PhaseInterface aqueous = phmeasurement.reactiveThermoSystem.getPhase("aqueous");
    double t = aqueous.getTemperature();
    double association2 = 107.8871 + 0.03252849 * t - 5151.79 / t - 38.92561 * Math.log10(t) + 563713.9 / (t * t);
    double association12 = 464.1965 + 0.09344813 * t - 26986.16 / t - 165.75951 * Math.log10(t) + 2248628.9 / (t * t);
    double waterActivity = aqueous.getComponent("water").getx()
        * aqueous.getActivityCoefficient(aqueous.getComponent("water").getComponentNumber());
    int water = aqueous.getComponent("water").getComponentNumber();
    double ionGammaRatio = aqueous.getActivityCoefficient(aqueous.getComponent("H3O+").getComponentNumber(), water)
        / aqueous.getActivityCoefficient(aqueous.getComponent("HCO3-").getComponentNumber(), water);
    double referencePH = -0.5 * (association2 - association12 + 2.0 * Math.log10(waterActivity)
        + Math.log10(molalActivity(aqueous, "CO2")) + Math.log10(ionGammaRatio));
    assertEquals(referencePH, phmeasurement.getMeasuredValue(), 0.01);
  }

  @Test
  void testGetMeasuredValueWithAlkalinity() {
    pHProbe phmeasurement = new pHProbe(stream_1);
    phmeasurement.setAlkalinity(50.0);
    phmeasurement.run();
    logger.info("pH " + phmeasurement.getMeasuredValue());
    // At finite alkalinity, use the independent USGS/PHREEQC water ionization
    // expression and the measured hydroxide inventory: a_H = K_w*a_water^2/a_OH.
    neqsim.thermo.phase.PhaseInterface aqueous = phmeasurement.reactiveThermoSystem.getPhase("aqueous");
    double t = aqueous.getTemperature();
    double logKw = 293.29227 + 0.1360833 * t - 10576.913 / t - 123.73158 * Math.log10(t) - 6.996455e-5 * t * t;
    double waterActivity = aqueous.getComponent("water").getx()
        * aqueous.getActivityCoefficient(aqueous.getComponent("water").getComponentNumber());
    double referencePH = -logKw - 2.0 * Math.log10(waterActivity) + Math.log10(molalActivity(aqueous, "OH-"));
    assertEquals(referencePH, phmeasurement.getMeasuredValue(), 0.01);
    assertEquals(1, phmeasurement.reactiveThermoSystem.getNumberOfPhases());
    assertEquals(1, phmeasurement.reactiveThermoSystem.getMaxNumberOfPhases());
    assertTrue(phmeasurement.reactiveThermoSystem.isForcePhaseTypes());
    assertTrue(phmeasurement.reactiveThermoSystem.hasPhaseType("aqueous"));
    assertTrue(Math.abs(
        phmeasurement.reactiveThermoSystem.getChemicalReactionOperations().getReactivePhaseChargeMoles()) <= 1.0e-8);
    assertTrue(phmeasurement.reactiveThermoSystem.getChemicalReactionOperations()
        .getMaximumAbsoluteReactionLogResidual() <= 2.0e-6);
    assertTrue(phmeasurement.reactiveThermoSystem.getChemicalReactionOperations()
        .getMaximumAbsoluteElementBalanceResidual() <= 1.0e-8);
  }

  /**
   * Calculates the EOS solute activity from the aqueous inventory and unsymmetric mole-fraction coefficient.
   *
   * @param phase sampled electrolyte phase
   * @param name solute name
   * @return dimensionless molal activity relative to 1 mol/kg water
   */
  private double molalActivity(neqsim.thermo.phase.PhaseInterface phase, String name) {
    int water = phase.getComponent("water").getComponentNumber();
    double waterMass = phase.getComponent(water).getNumberOfMolesInPhase() * phase.getComponent(water).getMolarMass();
    int component = phase.getComponent(name).getComponentNumber();
    return phase.getComponent(component).getNumberOfMolesInPhase() / waterMass
        * phase.getActivityCoefficient(component, water) * phase.getComponent(water).getx();
  }

}
