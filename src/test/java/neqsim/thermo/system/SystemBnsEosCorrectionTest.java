package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for the BNS corrections and the helium extension.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemBnsEosCorrectionTest {
  /** Checks pure hydrocarbon attraction derivatives and the caloric temperature derivative. */
  @Test
  void pureHydrocarbonUsesItsOwnAttractionDerivative() {
    SystemBnsEos fluid = new SystemBnsEos(288.7055556, 137.8951459, 0.9, 0, 0, 0, 0, false);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC_T2);
    PhaseEos phase = (PhaseEos) calculate(fluid);
    ComponentEosInterface hydrocarbon = (ComponentEosInterface) phase.getComponent("HC");
    assertEquals(4, hydrocarbon.getComponentNumber());
    assertEquals(hydrocarbon.getaDiffT(), phase.getAT(), 1e-8);
    assertEquals(85.69353676, phase.getCp("J/molK"), 0.01);

    double cp = phase.getCp("J/molK");
    double temperature = fluid.getTemperature();
    double step = 0.01;
    fluid.setTemperature(temperature + step);
    double upperEnthalpy = calculate(fluid).getEnthalpy("J/mol");
    fluid.setTemperature(temperature - step);
    double lowerEnthalpy = calculate(fluid).getEnthalpy("J/mol");
    assertEquals(cp, (upperEnthalpy - lowerEnthalpy) / (2 * step), 1e-5);
  }

  /** Checks translation against the existing density correction and a numerical pressure derivative. */
  @Test
  void volumeCorrectionShiftsEnthalpyAndJouleThomsonConsistently() {
    SystemBnsEos fluid = new SystemBnsEos(322.03889, 137.8948965, 0.8, 0.2, 0.1, 0.02, 0.1, false);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC_T2);
    fluid.useVolumeCorrection(false);
    PhaseInterface phase = calculate(fluid);
    double cp = phase.getCp("J/molK");
    double cv = phase.getCv("J/molK");
    double entropy = phase.getEntropy();
    double internalEnergy = phase.getInternalEnergy();
    double enthalpy = phase.getEnthalpy("J/mol");
    double jt = phase.getJouleThomsonCoefficient();
    double z = phase.getZ();

    fluid.useVolumeCorrection(true);
    phase = calculate(fluid);
    double pressure = phase.getPressure();
    double shift = (z - phase.getZvolcorr()) * ThermodynamicConstantsInterface.R * phase.getTemperature() / pressure;
    assertEquals(shift / cp, phase.getJouleThomsonCoefficient() - jt, 1e-10);
    assertEquals(-pressure * shift, phase.getEnthalpy("J/mol") - enthalpy, 1e-8);
    assertEquals(z, phase.getZ(), 1e-12);
    assertEquals(cp, phase.getCp("J/molK"), 1e-10);
    assertEquals(cv, phase.getCv("J/molK"), 1e-10);
    assertEquals(entropy, phase.getEntropy(), 1e-10);
    assertEquals(internalEnergy, phase.getInternalEnergy(), 1e-10);

    double translatedJt = phase.getJouleThomsonCoefficient();
    double step = 0.01;
    fluid.setPressure(pressure + step);
    double upperEnthalpy = calculate(fluid).getEnthalpy("J/mol");
    fluid.setPressure(pressure - step);
    double lowerEnthalpy = calculate(fluid).getEnthalpy("J/mol");
    assertEquals(translatedJt, -(upperEnthalpy - lowerEnthalpy) / (2 * step * cp), 1e-7);
  }

  /** Checks that the hydrocarbon molar-mass floor feeds density and caloric properties. */
  @Test
  void hydrocarbonMassIsFlooredAtMethane() {
    SystemBnsEos fluid = new SystemBnsEos(288.7055556, 137.8951459, 0.4, 0, 0, 0.05, 0, false);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC_T2);
    PhaseInterface phase = calculate(fluid);
    assertEquals(0.0160425, phase.getComponent("HC").getMolarMass(), 1e-12);
    assertEquals(119.24243807, phase.getDensity("kg/m3"), 0.001);
    assertEquals(52.55522747, phase.getCp("J/molK"), 0.01);
  }

  /** Checks helium added to an initialized fluid, retained on a clone, and cleared through the old overload. */
  @Test
  void heliumCanBeAddedAfterInitialization() {
    SystemBnsEos fluid = new SystemBnsEos(338.7055556, 344.7378647, 0.7, 0.05, 0.025, 0.1, 0.25, true);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC_T2);
    double originalZ = calculate(fluid).getZvolcorr();
    SystemBnsEos copy = fluid.clone();

    copy.setComposition(0.7, 0.05, 0.025, 0.1, 0.25, 0.2, true);
    PhaseInterface mixed = calculate(copy);
    assertEquals(0.2, mixed.getComponent("helium").getz(), 1e-12);
    assertEquals(1.085562507, mixed.getZvolcorr(), 2e-6);
    assertEquals(0.023444063, mixed.getViscosity("cP"), 1e-7);
    assertEquals(originalZ, calculate(fluid).getZvolcorr(), 1e-12);

    copy.setComposition(0.7, 0.05, 0.025, 0.1, 0.25, true);
    assertEquals(originalZ, calculate(copy).getZvolcorr(), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> copy.setComposition(0.7, 0, 0, 0, 0, -0.1, true));
    assertThrows(IllegalArgumentException.class, () -> copy.setComposition(0.7, 0, 0, 0, 0.8, 0.3, true));
  }

  /** Checks the 2026 pure-helium parameter set, including its effective critical temperature. */
  @Test
  void pureHeliumMatchesTheReference() {
    SystemBnsEos fluid = new SystemBnsEos(288.7055556, 689.4757293);
    fluid.setComposition(0.7, 0, 0, 0, 0, 1.0, false);
    PhaseInterface phase = calculate(fluid);
    assertEquals(6.35 * 5.0 / 9.0, phase.getComponent("helium").getTC(), 1e-12);
    assertEquals(1.310539586, phase.getZvolcorr(), 2e-6);
    assertEquals(87.733428843, phase.getDensity("kg/m3"), 0.001);
    assertEquals(20.877642407, phase.getCp("J/molK"), 0.005);
    assertEquals(0.021407961, phase.getViscosity("cP"), 1e-7);
  }

  /** Checks the dense-fluid BNS viscosity of a hydrogen-rich gas near the upper pressure limit. */
  @Test
  void hydrogenRichViscosityMatchesTheReference() {
    SystemBnsEos fluid = new SystemBnsEos(422.0388889, 1034.21359398, 0.3, 0.025, 0, 0.025, 0.8, false);
    fluid.setMixingRule(EosMixingRuleType.CLASSIC_T2);
    assertEquals(0.020148886, calculate(fluid).getViscosity("cP"), 1e-7);
  }

  /**
   * Recalculates EOS derivatives and physical properties through the normal flash path.
   *
   * @param fluid BNS fluid at the desired state
   * @return initialized phase
   */
  private PhaseInterface calculate(SystemBnsEos fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    fluid.init(3);
    return fluid.getPhase(0);
  }
}
