package neqsim.thermo.phase;

import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentPRvolcor;
import neqsim.thermo.component.ComponentSrkvolcor;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrEosvolcor;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkEosvolcor;

public class PhaseEosvolcorTest {
  static PhasePrEosvolcor prPhase;

  @BeforeEach
  void setUp() {
    prPhase = new PhasePrEosvolcor();
  }

  @Test
  void testAddcomponent() {
    Assertions.assertEquals(0, prPhase.getNumberOfComponents());

    prPhase.addComponent("ethane", 0, 0, 0);
    Assertions.assertEquals(1, prPhase.getNumberOfComponents());

    prPhase.addComponent("methane", 0, 0, 1);
    Assertions.assertEquals(2, prPhase.getNumberOfComponents());
  }

  @Test
  void prMolarVolumeMatchesExplicitTranslation() {
    assertMolarVolumeMatchesTranslation("methane", 300.0, 50.0, SystemPrEos::new,
        SystemPrEosvolcor::new, component -> ((ComponentPRvolcor) component).calcc(),
        component -> ((ComponentPRvolcor) component).calccT());
    assertMolarVolumeMatchesTranslation("n-heptane", 300.0, 50.0, SystemPrEos::new,
        SystemPrEosvolcor::new, component -> ((ComponentPRvolcor) component).calcc(),
        component -> ((ComponentPRvolcor) component).calccT());
  }

  @Test
  void prLiquidPhaseDensityMatchesTranslatedVolume() {
    assertLiquidDensityMatchesTranslation("n-heptane", 300.0, 1.0, SystemPrEos::new,
        SystemPrEosvolcor::new, component -> ((ComponentPRvolcor) component).calcc(),
        component -> ((ComponentPRvolcor) component).calccT());
  }

  @Test
  void srkMolarVolumeMatchesExplicitTranslation() {
    assertMolarVolumeMatchesTranslation("methane", 300.0, 50.0, SystemSrkEos::new,
        SystemSrkEosvolcor::new, component -> ((ComponentSrkvolcor) component).calcc(),
        component -> ((ComponentSrkvolcor) component).calccT());
    assertMolarVolumeMatchesTranslation("n-heptane", 300.0, 50.0, SystemSrkEos::new,
        SystemSrkEosvolcor::new, component -> ((ComponentSrkvolcor) component).calcc(),
        component -> ((ComponentSrkvolcor) component).calccT());
  }

  @Test
  void srkLiquidPhaseDensityMatchesTranslatedVolume() {
    assertLiquidDensityMatchesTranslation("n-heptane", 300.0, 1.0, SystemSrkEos::new,
        SystemSrkEosvolcor::new, component -> ((ComponentSrkvolcor) component).calcc(),
        component -> ((ComponentSrkvolcor) component).calccT());
  }

  private void assertMolarVolumeMatchesTranslation(String componentName, double temperature,
      double pressure, SystemFactory baseFactory, SystemFactory translatedFactory,
      Function<ComponentEosInterface, Double> translationCorrelation,
      Function<ComponentEosInterface, Double> translationDerivative) {
    SystemInterface baseSystem =
        prepareSystem(baseFactory, componentName, temperature, pressure, Boolean.FALSE);
    double baseMolarVolume = baseSystem.getPhase(0).getMolarVolume();

    SystemInterface translatedSystem =
        prepareSystem(translatedFactory, componentName, temperature, pressure, null);
    double translatedMolarVolume = translatedSystem.getPhase(0).getMolarVolume();

    ComponentEosInterface component =
        (ComponentEosInterface) translatedSystem.getPhase(0).getComponent(0);
    double penelouxShift = component.getVolumeCorrection();
    Assertions.assertEquals(penelouxShift, translationCorrelation.apply(component), 1e-12);
    Assertions.assertEquals(component.getVolumeCorrectionT(),
        translationDerivative.apply(component), 1e-12);

    Assertions.assertEquals(baseMolarVolume - penelouxShift, translatedMolarVolume, 1e-10);
  }

  private void assertLiquidDensityMatchesTranslation(String componentName, double temperature,
      double pressure, SystemFactory baseFactory, SystemFactory translatedFactory,
      Function<ComponentEosInterface, Double> translationCorrelation,
      Function<ComponentEosInterface, Double> translationDerivative) {
    SystemInterface baseSystem =
        prepareSystem(baseFactory, componentName, temperature, pressure, Boolean.FALSE);

    int baseLiquidIndex = findLiquidPhaseIndex(baseSystem);
    Assertions.assertTrue(baseLiquidIndex >= 0, "Expected base system to contain a liquid phase");

    double baseLiquidMolarVolume = baseSystem.getPhase(baseLiquidIndex).getMolarVolume();

    SystemInterface translatedSystem =
        prepareSystem(translatedFactory, componentName, temperature, pressure, null);

    int translatedLiquidIndex = findPhaseIndexByType(translatedSystem,
        baseSystem.getPhase(baseLiquidIndex).getPhaseTypeName());
    Assertions.assertTrue(translatedLiquidIndex >= 0,
        "Expected translated system to contain a liquid phase of the same type");

    double translatedLiquidMolarVolume =
        translatedSystem.getPhase(translatedLiquidIndex).getMolarVolume();
    ComponentEosInterface component =
        (ComponentEosInterface) translatedSystem.getPhase(translatedLiquidIndex).getComponent(0);
    double penelouxShift = component.getVolumeCorrection();
    Assertions.assertEquals(penelouxShift, translationCorrelation.apply(component), 1e-12);
    Assertions.assertEquals(component.getVolumeCorrectionT(),
        translationDerivative.apply(component), 1e-12);

    Assertions.assertEquals(baseLiquidMolarVolume - penelouxShift, translatedLiquidMolarVolume, 1e-10);

    double baseLiquidMolarVolumeSI =
        baseSystem.getPhase(baseLiquidIndex).getMolarVolume("m3/mol");
    double translatedLiquidMolarVolumeSI =
        translatedSystem.getPhase(translatedLiquidIndex).getMolarVolume("m3/mol");
    double unitConversion = (baseLiquidMolarVolumeSI - translatedLiquidMolarVolumeSI)
        / (baseLiquidMolarVolume - translatedLiquidMolarVolume);
    double penelouxShiftSI = penelouxShift * unitConversion;
    Assertions.assertEquals(baseLiquidMolarVolumeSI - penelouxShiftSI,
        translatedLiquidMolarVolumeSI, 1e-16);

    double expectedTranslatedDensity =
        component.getMolarMass() / (baseLiquidMolarVolumeSI - penelouxShiftSI);
    double translatedDensity = translatedSystem.getPhase(translatedLiquidIndex).getDensity();
    Assertions.assertEquals(expectedTranslatedDensity, translatedDensity, 1e-8);
  }

  private SystemInterface prepareSystem(SystemFactory factory, String componentName,
      double temperature, double pressure, Boolean useVolumeCorrection) {
    SystemInterface system = factory.create(temperature, pressure);
    system.addComponent(componentName, 1.0);
    system.setMixingRule("classic");
    if (useVolumeCorrection != null) {
      system.useVolumeCorrection(useVolumeCorrection);
    }
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    return system;
  }

  private int findLiquidPhaseIndex(SystemInterface system) {
    int index = findPhaseIndexByType(system, "oil");
    if (index >= 0) {
      return index;
    }
    index = findPhaseIndexByType(system, "liquid");
    if (index >= 0) {
      return index;
    }

    double densestDensity = Double.NEGATIVE_INFINITY;
    int densestIndex = -1;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      double density = system.getPhase(phaseIndex).getDensity();
      if (density > densestDensity) {
        densestDensity = density;
        densestIndex = phaseIndex;
      }
    }
    return densestIndex;
  }

  private int findPhaseIndexByType(SystemInterface system, String phaseTypeName) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (system.getPhase(phaseIndex).getPhaseTypeName().equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    return -1;
  }

  @FunctionalInterface
  private interface SystemFactory {
    SystemInterface create(double temperature, double pressure);
  }
}
