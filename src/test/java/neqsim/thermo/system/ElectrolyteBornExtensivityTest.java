package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentModifiedFurstElectrolyteEos;
import neqsim.thermo.component.ComponentModifiedFurstElectrolyteEosMod2004;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.phase.PhaseElectrolyteCPAMM;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEosMod2004;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.util.constants.FurstElectrolyteConstants;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression for issue #3862: chemical potentials cannot depend on the amount of a phase. */
class ElectrolyteBornExtensivityTest extends neqsim.NeqSimTest {
  private final double[] originalFurstParams = FurstElectrolyteConstants.furstParams;

  @AfterEach
  void restoreLegacyParameterSelection() {
    FurstElectrolyteConstants.furstParams = originalFurstParams;
  }

  private SystemInterface system(String model) {
    switch (model) {
    case "Furst":
      return new SystemFurstElectrolyteEos(298.15, 10.01325);
    case "2004":
      return new SystemFurstElectrolyteEosMod2004(298.15, 10.01325);
    case "CPA":
      return new SystemElectrolyteCPA(298.15, 10.01325);
    case "Statoil":
      return new SystemElectrolyteCPAstatoil(298.15, 10.01325);
    case "Advanced":
      return new SystemElectrolyteCPAAdvanced(298.15, 10.01325);
    default:
      SystemElectrolyteCPAMM mm = new SystemElectrolyteCPAMM(298.15, 10.01325);
      mm.setDielectricMixingRule(PhaseElectrolyteCPAMM.DielectricMixingRule.valueOf(model));
      return mm;
    }
  }

  private SystemInterface liquid(String model, double scale, boolean mixedSolvent) {
    SystemInterface fluid = system(model);
    fluid.addComponent("water", scale);
    if (mixedSolvent) {
      fluid.addComponent("methanol", 0.2 * scale);
    }
    fluid.addComponent("Na+", 0.01 * scale);
    fluid.addComponent("Cl-", 0.01 * scale);
    fluid.setMixingRule("Furst".equals(model) || "2004".equals(model) ? 4 : 10);
    fluid.setForceSinglePhase(PhaseType.LIQUID);
    fluid.setBeta(1.0);
    fluid.init(3);
    return fluid;
  }

  private double bornEnergy(PhaseInterface phase) {
    if (phase instanceof PhaseModifiedFurstElectrolyteEosMod2004) {
      return ((PhaseModifiedFurstElectrolyteEosMod2004) phase).FBorn();
    }
    if (phase instanceof PhaseElectrolyteCPAMM) {
      return ((PhaseElectrolyteCPAMM) phase).FBorn();
    }
    return ((PhaseModifiedFurstElectrolyteEos) phase).FBorn();
  }

  private double bornDerivative(PhaseInterface phase, int i) {
    ComponentInterface component = phase.getComponent(i);
    int count = phase.getNumberOfComponents();
    double temperature = phase.getTemperature();
    double pressure = phase.getPressure();
    if (component instanceof ComponentModifiedFurstElectrolyteEosMod2004) {
      return ((ComponentModifiedFurstElectrolyteEosMod2004) component).dFBorndN(phase, count, temperature, pressure);
    }
    if (component instanceof ComponentSrkCPAMM) {
      return ((ComponentSrkCPAMM) component).dFBorndN(phase, count, temperature, pressure);
    }
    return ((ComponentModifiedFurstElectrolyteEos) component).dFBorndN(phase, count, temperature, pressure);
  }

  private void assertClose(double expected, double actual, double relative, String label) {
    assertTrue(Double.isFinite(expected) && Double.isFinite(actual), label + " must be finite");
    assertEquals(expected, actual, relative * Math.max(1.0, Math.abs(expected)), label);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Furst", "2004", "CPA", "Statoil", "Advanced", "MOLAR_AVERAGE", "VOLUME_AVERAGE", "LOOYENGA",
      "LICHTENECKER"})
  void bornEnergyIsExtensiveAndChemicalPotentialsAreIntensive(String model) {
    PhaseInterface reference = liquid(model, 1.0, true).getPhase(0);
    for (double scale : new double[] {1e-6, 1e-3, 2.0, 1e3, 1e6}) {
      PhaseInterface scaled = liquid(model, scale, true).getPhase(0);
      assertClose(reference.getMolarVolume(), scaled.getMolarVolume(), 1e-8, "molar volume");
      assertClose(bornEnergy(reference), bornEnergy(scaled) / scale, 1e-10, "Born energy / scale");
      double eulerSum = 0.0;
      for (int i = 0; i < reference.getNumberOfComponents(); i++) {
        String label = model + ": " + reference.getComponent(i).getComponentName() + ", scale=" + scale;
        assertClose(bornDerivative(reference, i), bornDerivative(scaled, i), 1e-10, label + " Born dF/dn");
        assertClose(reference.getComponent(i).getLogFugacityCoefficient(),
            scaled.getComponent(i).getLogFugacityCoefficient(), 1e-8, label + " ln(phi)");
        eulerSum += scaled.getComponent(i).getNumberOfMolesInPhase() * bornDerivative(scaled, i);
      }
      // These Born implementations use solvent permittivity with no volume dependence.
      assertClose(bornEnergy(scaled), eulerSum, 1e-10, model + " Born Euler identity");
    }
  }

  @Test
  void mod2004SingleSolventBornDerivativeMatchesEnergyDifference() {
    SystemInterface fluid = liquid("2004", 1.0, false);
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      double step = fluid.getPhase(0).getComponent(i).getNumberOfMolesInPhase() * 1e-4;
      SystemInterface plus = fluid.clone();
      SystemInterface minus = fluid.clone();
      plus.addComponent(i, step);
      minus.addComponent(i, -step);
      plus.init_x_y();
      minus.init_x_y();
      plus.init(3);
      minus.init(3);
      // Born energy has no volume dependence; one neutral solvent also fixes epsilon at this T.
      double numerical = (bornEnergy(plus.getPhase(0)) - bornEnergy(minus.getPhase(0))) / (2.0 * step);
      assertClose(numerical, bornDerivative(fluid.getPhase(0), i), 1e-8, "Born finite difference");
    }
  }

  private SystemInterface flashedMod2004(double scale) {
    SystemInterface fluid = system("2004");
    fluid.addComponent("methane", 0.1 * scale);
    fluid.addComponent("water", scale);
    fluid.addComponent("Na+", 0.001 * scale);
    fluid.addComponent("Cl-", 0.001 * scale);
    fluid.setMixingRule(4);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    return fluid;
  }

  @Test
  void mod2004FlashIsIndependentOfFeedAmount() {
    SystemInterface reference = flashedMod2004(1.0);
    assertEquals(2, reference.getNumberOfPhases());
    for (double scale : new double[] {1e-3, 2.0, 1e3}) {
      SystemInterface scaled = flashedMod2004(scale);
      assertEquals(reference.getNumberOfPhases(), scaled.getNumberOfPhases());
      for (int p = 0; p < reference.getNumberOfPhases(); p++) {
        PhaseInterface expected = reference.getPhase(p);
        PhaseInterface actual = scaled.getPhase(p);
        assertEquals(expected.getType(), actual.getType());
        assertClose(expected.getBeta(), actual.getBeta(), 1e-8, "phase fraction");
        for (int i = 0; i < expected.getNumberOfComponents(); i++) {
          assertClose(expected.getComponent(i).getx(), actual.getComponent(i).getx(), 1e-8, "phase composition");
          assertClose(expected.getComponent(i).getLogFugacityCoefficient(),
              actual.getComponent(i).getLogFugacityCoefficient(), 1e-8, "flashed ln(phi)");
        }
      }
    }
  }
}
