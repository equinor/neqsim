package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.component.attractiveeosterm.AttractiveTermInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression and thermodynamic-consistency tests for the opt-in CPA water caloric alpha. */
class CPAWaterCaloricTest extends neqsim.NeqSimTest {
  private SystemSrkCPAstatoil water(double temperature, double pressure, boolean caloric) {
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(temperature, pressure);
    fluid.setUseCaloricWaterAlpha(caloric);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    flash(fluid);
    return fluid;
  }

  private void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
  }

  @ParameterizedTest
  @CsvSource({"278.15,4205.037692495445,999.966633545", "283.15,4195.158885966499,999.702470187",
      "298.15,4181.314990764705,997.047636760", "313.15,4179.4147979899,992.216352873",
      "333.15,4184.953280467873,983.195824228"})
  void issue3762HeatCapacityAndDensity(double temperature, double cp, double density) {
    SystemSrkCPAstatoil fluid = water(temperature, 1.01325, true);
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(cp, fluid.getPhase(0).getCp("J/kgK"), cp * 0.02);
    assertEquals(density, fluid.getPhase(0).getDensity("kg/m3"), density * 0.005);
  }

  @ParameterizedTest
  @CsvFileSource(resources = "/thermo/water_caloric_alpha_iapws95_reference.csv", numLinesToSkip = 1)
  void referenceGridAndSteamPreservation(double temperature, double pressure, double cp, double density,
      double saturationPressure, String phase, String split, boolean checkDensity) {
    SystemSrkCPAstatoil fluid = water(temperature, pressure, true);
    assertEquals(1, fluid.getNumberOfPhases(), split);
    if ("liquid".equals(phase)) {
      assertTrue(pressure > saturationPressure);
      assertEquals(cp, fluid.getCp("J/kgK"), cp * 0.02, split);
      if (checkDensity) {
        assertEquals(density, fluid.getDensity("kg/m3"), density * 0.005, split);
      }
    } else {
      assertTrue(pressure < saturationPressure);
      SystemSrkCPAstatoil legacy = water(temperature, pressure, false);
      assertEquals(legacy.getCp(), fluid.getCp(), legacy.getCp() * 0.0001);
      assertEquals(legacy.getPhase(0).getComponent(0).getCp0(temperature),
          fluid.getPhase(0).getComponent(0).getCp0(temperature), 0.0);
    }
  }

  @ParameterizedTest
  @CsvSource({"278.15,0.008725751138417176", "298.15,0.03169929338873217", "333.15,0.19946434307815789",
      "373.15,1.014179966599509", "423.15,4.761645379697582"})
  void saturationPressureRemainsAccurate(double temperature, double saturationPressure) throws Exception {
    SystemSrkCPAstatoil fluid = water(temperature, 10.0, true);
    fluid.setPressure(saturationPressure);
    new ThermodynamicOperations(fluid).bubblePointPressureFlash(false);
    assertEquals(saturationPressure, fluid.getPressure(), saturationPressure * 0.02);
  }

  @ParameterizedTest
  @CsvSource({"278.15,1.01325", "305.15,50.0", "333.15,100.0", "423.15,10.0"})
  void caloricPropertiesAreDerivativesOfTheSamePotential(double temperature, double pressure) {
    SystemSrkCPAstatoil fluid = water(temperature, pressure, true);
    double step = 0.01;
    SystemInterface plus = water(temperature + step, pressure, true);
    SystemInterface minus = water(temperature - step, pressure, true);
    double cpFromH = (plus.getEnthalpy() - minus.getEnthalpy()) / (2.0 * step);
    double cpFromS = temperature * (plus.getEntropy() - minus.getEntropy()) / (2.0 * step);
    assertEquals(fluid.getCp(), cpFromH, 1e-5);
    assertEquals(fluid.getCp(), cpFromS, 1e-5);
    assertTrue(fluid.getCv() > 0.0);
    assertTrue(fluid.getCp() > fluid.getCv());
  }

  @Test
  void defaultAndDisablingRestoreLegacyResults() {
    SystemSrkCPAstatoil fluid = water(298.15, 1.01325, false);
    assertFalse(fluid.isUsingCaloricWaterAlpha());
    double legacyCp = fluid.getCp();
    double legacyDensity = fluid.getDensity("kg/m3");
    double idealCp = fluid.getPhase(0).getComponent(0).getCp0(298.15);
    double acentric = fluid.getPhase(0).getComponent(0).getAcentricFactor();
    assertEquals(3651.669280758189, fluid.getCp("J/kgK"), 0.01);
    fluid.setUseCaloricWaterAlpha(true);
    flash(fluid);
    assertTrue(fluid.isUsingCaloricWaterAlpha());
    assertEquals(idealCp, fluid.getPhase(0).getComponent(0).getCp0(298.15), 0.0);
    assertEquals(acentric, fluid.getPhase(0).getComponent(0).getAcentricFactor(), 1e-14);
    assertTrue(fluid.getCp() > legacyCp * 1.1);
    fluid.setUseCaloricWaterAlpha(false);
    flash(fluid);
    assertEquals(legacyCp, fluid.getCp(), 1e-8);
    assertEquals(legacyDensity, fluid.getDensity("kg/m3"), 1e-8);
  }

  @Test
  void pureTegIsUnchanged() {
    SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("TEG", 1.0);
    fluid.setMixingRule(10);
    flash(fluid);
    double cp = fluid.getCp();
    double density = fluid.getDensity("kg/m3");
    double enthalpy = fluid.getEnthalpy();
    fluid.setUseCaloricWaterAlpha(true);
    flash(fluid);
    assertEquals(cp, fluid.getCp(), 1e-9);
    assertEquals(density, fluid.getDensity("kg/m3"), 1e-8);
    assertEquals(enthalpy, fluid.getEnthalpy(), 1e-8);
  }

  @Test
  void togglingRebuildsPureComponentReferencesWithoutMutatingTheOriginal() {
    SystemSrkCPAstatoil original = water(298.15, 1.01325, false);
    assertEquals(1.0, original.getPhase(0).getActivityCoefficientSymetric(0), 1e-8);
    SystemSrkCPAstatoil copy = original.clone();
    copy.setUseCaloricWaterAlpha(true);
    flash(copy);
    assertEquals(1.0, copy.getPhase(0).getActivityCoefficientSymetric(0), 1e-8);
    assertEquals(23, copy.getPhase(0).getRefPhase(0).getComponent(0).getAttractiveTermNumber());
    assertEquals(15, original.getPhase(0).getRefPhase(0).getComponent(0).getAttractiveTermNumber());
    assertEquals(1.0, original.getPhase(0).getActivityCoefficientSymetric(0), 1e-8);
    copy.setUseCaloricWaterAlpha(false);
    flash(copy);
    assertEquals(1.0, copy.getPhase(0).getActivityCoefficientSymetric(0), 1e-8);
    assertEquals(15, copy.getPhase(0).getRefPhase(0).getComponent(0).getAttractiveTermNumber());
  }

  @ParameterizedTest
  @CsvSource({"278.15", "333.15", "423.15", "647.3"})
  void alphaDerivativesAreAnalyticAndSmooth(double temperature) {
    SystemSrkCPAstatoil fluid = water(298.15, 1.01325, true);
    AttractiveTermInterface alpha = fluid.getPhase(0).getComponent(0).getAttractiveTerm();
    double step = 0.02;
    double first = (alpha.alpha(temperature + step) - alpha.alpha(temperature - step)) / (2 * step);
    double second = (alpha.diffalphaT(temperature + step) - alpha.diffalphaT(temperature - step)) / (2 * step);
    assertEquals(alpha.diffalphaT(temperature), first, 1e-10);
    assertEquals(alpha.diffdiffalphaT(temperature), second, 1e-11);
  }

  @Test
  void phAndPsFlashesCloseWithTheNewCaloricModel() {
    SystemSrkCPAstatoil fluid = water(313.15, 10.0, true);
    double enthalpy = fluid.getEnthalpy();
    double entropy = fluid.getEntropy();
    fluid.setTemperature(298.15);
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.PHflash(enthalpy);
    fluid.init(3);
    assertEquals(313.15, fluid.getTemperature(), 1e-5);
    assertEquals(enthalpy, fluid.getEnthalpy(), 1e-4);
    fluid.setTemperature(328.15);
    operations.PSflash(entropy);
    fluid.init(3);
    assertEquals(313.15, fluid.getTemperature(), 1e-5);
    assertEquals(entropy, fluid.getEntropy(), 1e-6);
  }

  @Test
  void cloningSerializationAndAddingComponentsPreserveSelection() throws Exception {
    SystemSrkCPAstatoil original = water(298.15, 10.0, true);
    SystemSrkCPAstatoil clone = original.clone();
    clone.addComponent("water", 1.0);
    flash(clone);
    assertEquals(original.getCp("J/kgK"), clone.getCp("J/kgK"), 1e-6);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    SystemSrkCPAstatoil restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (SystemSrkCPAstatoil) input.readObject();
    }
    flash(restored);
    assertTrue(restored.isUsingCaloricWaterAlpha());
    assertEquals(original.getCp(), restored.getCp(), 1e-8);
    clone.setUseCaloricWaterAlpha(false);
    flash(clone);
    assertTrue(original.isUsingCaloricWaterAlpha());
    assertTrue(original.getCp("J/kgK") > clone.getCp("J/kgK") * 1.1);
    restored.addComponent("MEG", 0.2);
    restored.setMixingRule(10);
    flash(restored);
    assertEquals(23, restored.getPhase(0).getComponent("water").getAttractiveTermNumber());
    assertEquals(1, restored.getNumberOfPhases());
    SystemSrkCPAstatoil plus = restored.clone();
    SystemSrkCPAstatoil minus = restored.clone();
    plus.setTemperature(298.16);
    minus.setTemperature(298.14);
    flash(plus);
    flash(minus);
    assertEquals(restored.getCp(), (plus.getEnthalpy() - minus.getEnthalpy()) / 0.02, 1e-5);
  }
}
