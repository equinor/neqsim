package neqsim.thermo.phase;

import java.util.function.Function;
import org.apache.logging.log4j.core.util.Assert;
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

  @Test
  void prPhaseCalculatesTemperatureDependentTranslation() {
    SystemInterface system = new SystemPrEosvolcor(300.0, 50.0);
    system.addComponent("methane", 1.0);
    system.setMixingRule("classic");
    system.init(0);

    double translation = 1.23e-4;
    double translationDerivative = -4.56e-7;

    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      ComponentPRvolcor component = (ComponentPRvolcor) system.getPhase(phaseIndex).getComponent(0);
      component.setVolumeCorrection(translation);
      component.setVolumeCorrectionT(translationDerivative);
    }

    system.init(0);

    PhasePrEosvolcor phase = (PhasePrEosvolcor) system.getPhase(0);
    ComponentPRvolcor component = (ComponentPRvolcor) phase.getComponent(0);

    Assertions.assertEquals(translation, component.getc(), 1e-12);
    Assertions.assertEquals(translationDerivative, component.getcT(), 1e-12);
    Assertions.assertEquals(translationDerivative, phase.getCT(), 1e-12);
    Assertions.assertEquals(translationDerivative,
        phase.calcCiT(0, phase, system.getTemperature(), system.getPressure(),
            phase.getNumberOfComponents()),
        1e-12);

    component.Finit(phase, system.getTemperature(), system.getPressure(),
        system.getTotalNumberOfMoles(), 1.0, phase.getNumberOfComponents(), 2);
    Assertions.assertEquals(translationDerivative, component.getCiT(), 1e-12);
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

    Assertions.assertEquals(baseLiquidMolarVolume - penelouxShift, translatedLiquidMolarVolume,
        1e-10);

    double baseLiquidMolarVolumeSI = baseSystem.getPhase(baseLiquidIndex).getMolarVolume("m3/mol");
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
    // system.prettyPrint();
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

  @Test
  void testFlashPR() {
    SystemInterface system = new SystemPrEos(298.15, 1.0);

    system.addComponent("n-heptane", 1.0);
    system.setMixingRule("classic");
    system.useVolumeCorrection(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    // system.prettyPrint();
    double density1 = system.getPhase(0).getPhysicalProperties().getDensity();

    SystemInterface system2 = new SystemPrEosvolcor(298.15, 1.0);
    system2.addComponent("n-heptane", 1.0);
    system2.setMixingRule("classic");

    ThermodynamicOperations ops2 = new ThermodynamicOperations(system2);
    system2.useVolumeCorrection(false);
    ops2.TPflash();
    system2.initProperties();
    // system2.prettyPrint();
    double density2 = system2.getPhase(0).getPhysicalProperties().getDensity();

    Assertions.assertEquals(density1, density2, 1e-10);
  }


  @Test
  void testFlashSrk() {
    SystemInterface system = new SystemSrkEos(308.15, 1.0);

    system.addComponent("n-heptane", 1.0);
    system.setMixingRule("classic");
    system.useVolumeCorrection(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    system.initProperties();
    // system.prettyPrint();
    double density1 = system.getPhase(0).getPhysicalProperties().getDensity();

    SystemInterface system2 = new SystemSrkEosvolcor(308.15, 1.0);
    system2.addComponent("n-heptane", 1.0);
    system2.setMixingRule("classic");

    ThermodynamicOperations ops2 = new ThermodynamicOperations(system2);
    ops2.TPflash();
    system2.initProperties();

    double density2 = system2.getPhase(0).getPhysicalProperties().getDensity();

    Assertions.assertEquals(density1, density2, 1e-10);


    double volcor = system2.getPhase(0).getComponent(0).getVolumeCorrection();

    system2.getPhase(0).getComponent(0).setVolumeCorrection(volcor);
    system2.getPhase(1).getComponent(0).setVolumeCorrection(volcor);

    ThermodynamicOperations ops3 = new ThermodynamicOperations(system2);
    system2.useVolumeCorrection(false);
    ops3.TPflash();
    system2.initProperties();

    double density3 = system2.getPhase(0).getPhysicalProperties().getDensity();

    Assertions.assertEquals(density1, density3, 1e-10);
  }
}
