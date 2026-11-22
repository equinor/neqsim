package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Validates CPA association-site temperature, volume, and composition sensitivities and their lifecycle.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseSrkCPASiteDerivativeTest {
  /** Rejects allocated but uninitialized association matrices before solving a mixed derivative. */
  @Test
  void mixedCompositionDerivativesRejectLevelZero() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(0);

    IllegalStateException failure = assertThrows(IllegalStateException.class,
        phase::calcAssociationSiteCompositionDerivatives);
    assertTrue(failure.getMessage().contains("derivative level one or higher"));
  }

  /** Refreshes amount derivatives and the current state after a lower initialization level. */
  @Test
  void mixedCompositionDerivativesRefreshLevelTwoState() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(2);
    PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(0);
    phase.setTemperature(310.0);
    PhaseSrkCPA reference = phase.clone();
    reference.init(reference.getNumberOfMolesInPhase() / reference.getBeta(), reference.getNumberOfComponents(), 3,
        reference.getType(), reference.getBeta());
    reference.calcAssociationSiteCompositionDerivatives();

    phase.calcAssociationSiteCompositionDerivatives();
    ComponentSrkCPA expected = (ComponentSrkCPA) reference.getComponent(0);
    ComponentSrkCPA actual = (ComponentSrkCPA) phase.getComponent(0);
    for (int site = 0; site < actual.getNumberOfAssociationSites(); site++) {
      assertEquals(expected.getXsite()[site], actual.getXsite()[site], 1.0e-12);
      assertEquals(expected.getXsitednidT(site, 0), actual.getXsitednidT(site, 0), 1.0e-12);
      assertEquals(expected.getXsitednidV(site, 0), actual.getXsitednidV(site, 0), 1.0e-12);
      assertEquals(expected.getXsitednidnj(site, 0, 0), actual.getXsitednidnj(site, 0, 0), 1.0e-12);
      assertTrue(Double.isFinite(actual.getXsitednidnj(site, 0, 0)));
    }
  }

  /**
   * Preserves cloning of saved components whose optional fields were absent in an earlier version.
   *
   * @throws ReflectiveOperationException if the simulated legacy fields cannot be accessed
   */
  @Test
  void cloneAcceptsLegacySerializedMixedDerivativeStorage() throws ReflectiveOperationException {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    ComponentSrkCPA original = component(fluid, "water", 0);
    // Missing fields in a serialVersionUID-compatible snapshot deserialize as null.
    for (String name : new String[] {"xsitednidT", "xsitednidV", "xsitednidnj"}) {
      Field field = ComponentSrkCPA.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(original, null);
    }
    ComponentSrkCPA restored = SerializationUtils.deserialize(SerializationUtils.serialize(original));

    ComponentSrkCPA copy = restored.clone();
    assertNotNull(copy);
    assertEquals(restored.getXsite()[0], copy.getXsite()[0], 0.0);
    copy.allocateAssociationSiteCompositionDerivatives(1);
    copy.setXsitednidT(0, 0, 0.125);
    copy.setXsitednidV(0, 0, 0.25);
    copy.setXsitednidnj(0, 0, 0, 0.5);
    assertEquals(0.125, copy.getXsitednidT(0, 0), 0.0);
    assertEquals(0.25, copy.getXsitednidV(0, 0), 0.0);
    assertEquals(0.5, copy.getXsitednidnj(0, 0, 0), 0.0);
  }

  /** Checks that phase clones own all nested optional mixed-derivative storage. */
  @Test
  void mixedCompositionDerivativeStorageIsIndependentInClones() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(0);
    phase.calcAssociationSiteCompositionDerivatives();
    ComponentSrkCPA original = (ComponentSrkCPA) phase.getComponent(0);
    ComponentSrkCPA copy = (ComponentSrkCPA) phase.clone().getComponent(0);
    double temperature = original.getXsitednidT(0, 0);
    double volume = original.getXsitednidV(0, 0);
    double amount = original.getXsitednidnj(0, 0, 0);

    copy.setXsitednidT(0, 0, temperature + 1.0);
    copy.setXsitednidV(0, 0, volume + 1.0);
    copy.setXsitednidnj(0, 0, 0, amount + 1.0);
    assertEquals(temperature, original.getXsitednidT(0, 0), 0.0);
    assertEquals(volume, original.getXsitednidV(0, 0), 0.0);
    assertEquals(amount, original.getXsitednidnj(0, 0, 0), 0.0);
  }

  /** Compares pure-water analytical site sensitivities with fixed-volume differences. */
  @Test
  void pureWaterSecondTemperatureDerivativeMatchesFiniteDifference() {
    SystemInterface fluid = new SystemSrkCPAstatoil(298.15, 1.01325);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule(10);
    assertTemperatureDerivatives(fluid, "water", 0, 0.01);
    assertVolumeDerivatives(fluid, "water", 0);
    assertCompositionDerivatives(fluid, "water", "water", "water", 0);
  }

  /** Compares K15 electrolyte-CPA water-site sensitivities with fixed-volume differences. */
  @Test
  void k15WaterSecondTemperatureDerivativeMatchesFiniteDifference() {
    SystemInterface fluid = new SystemElectrolyteCPAMM(298.15, 1.01325);
    fluid.addComponent("CO2", 0.1);
    fluid.addComponent("water", 1.0 / 0.01801528);
    double saltMoles = (14.97 / (100.0 - 14.97)) / 0.0745513;
    fluid.addComponent("K+", saltMoles);
    fluid.addComponent("Cl-", saltMoles);
    fluid.setMixingRule(10);
    assertTemperatureDerivatives(fluid, "water", 0, 0.01);
    assertVolumeDerivatives(fluid, "water", 0);
    assertCompositionDerivatives(fluid, "water", "CO2", "water", 0);
  }

  /**
   * Checks opt-in mixed association-site composition derivatives at fixed native EOS volume.
   *
   * @param fluid configured CPA system
   * @param siteComponentName component owning the inspected association site
   * @param firstAmountName first amount-coordinate component
   * @param secondAmountName second amount-coordinate component
   * @param phaseIndex phase containing the association sites
   */
  private void assertCompositionDerivatives(SystemInterface fluid, String siteComponentName, String firstAmountName,
      String secondAmountName, int phaseIndex) {
    fluid.init(0);
    fluid.init(3);
    fluid.setUseTVasIndependentVariables(true);
    fluid.init(3);

    PhaseSrkCPA phase = (PhaseSrkCPA) fluid.getPhase(phaseIndex);
    double volume = phase.getTotalVolume();
    phase.calcAssociationSiteCompositionDerivatives();
    assertEquals(volume, phase.getTotalVolume(), 0.0, "Mixed derivatives must preserve native constant volume");
    ComponentSrkCPA siteComponent = component(fluid, siteComponentName, phaseIndex);
    int first = phase.getComponent(firstAmountName).getComponentNumber();
    int second = phase.getComponent(secondAmountName).getComponentNumber();
    double firstStep = amountStep(phase, first);
    double secondStep = amountStep(phase, second);

    double[] plusFirst = siteSensitivitiesAtAmount(fluid, siteComponentName, phaseIndex, first, firstStep);
    double[] minusFirst = siteSensitivitiesAtAmount(fluid, siteComponentName, phaseIndex, first, -firstStep);
    double numericalAmountTemperature = (plusFirst[0] - minusFirst[0]) / (2.0 * firstStep);
    double numericalAmountVolume = (plusFirst[1] - minusFirst[1]) / (2.0 * firstStep);
    double numericalFirstAmount = (plusFirst[2 + phase.getNumberOfComponents()]
        - minusFirst[2 + phase.getNumberOfComponents()]) / (2.0 * firstStep);
    double numericalLngAmount = (plusFirst[3 + phase.getNumberOfComponents()]
        - minusFirst[3 + phase.getNumberOfComponents()]) / (2.0 * firstStep);
    double numericalBAmount = (plusFirst[4 + phase.getNumberOfComponents()]
        - minusFirst[4 + phase.getNumberOfComponents()]) / (2.0 * firstStep);
    double numericalMoleAmount = (plusFirst[5 + phase.getNumberOfComponents()]
        - minusFirst[5 + phase.getNumberOfComponents()]) / (2.0 * firstStep);

    double[] plusSecond = siteSensitivitiesAtAmount(fluid, siteComponentName, phaseIndex, second, secondStep);
    double[] minusSecond = siteSensitivitiesAtAmount(fluid, siteComponentName, phaseIndex, second, -secondStep);
    double numericalAmountAmount = (plusSecond[2 + first] - minusSecond[2 + first]) / (2.0 * secondStep);

    assertEquals(1.0, numericalMoleAmount, 1.0e-9);
    assertEquals(numericalBAmount,
        phase.calcBi(first, phase, phase.getTemperature(), phase.getPressure(), phase.getNumberOfComponents()),
        tolerance(numericalBAmount, 2.0e-4, 1.0e-12));
    assertEquals(numericalLngAmount, ((ComponentSrkCPA) phase.getComponent(first)).calc_lngi(phase),
        tolerance(numericalLngAmount, 2.0e-4, 1.0e-9));
    assertEquals(numericalFirstAmount, siteComponent.getXsitedni(0, first),
        tolerance(numericalFirstAmount, 2.0e-4, 1.0e-9));
    assertEquals(numericalAmountTemperature, siteComponent.getXsitednidT(0, first),
        tolerance(numericalAmountTemperature, 2.0e-4, 1.0e-10));
    assertEquals(numericalAmountVolume, siteComponent.getXsitednidV(0, first),
        tolerance(numericalAmountVolume, 2.0e-4, 1.0e-8));
    assertEquals(numericalAmountAmount, siteComponent.getXsitednidnj(0, first, second),
        tolerance(numericalAmountAmount, 3.0e-4, 1.0e-9));
    assertEquals(siteComponent.getXsitednidnj(0, first, second), siteComponent.getXsitednidnj(0, second, first), 0.0);
  }

  /**
   * Initializes a fixed-volume state and checks its first and second site sensitivities.
   *
   * @param fluid configured CPA system
   * @param componentName associating component name
   * @param phaseIndex phase containing the association sites
   * @param step central-difference temperature step in K
   */
  private void assertTemperatureDerivatives(SystemInterface fluid, String componentName, int phaseIndex, double step) {
    fluid.init(0);
    fluid.init(3);
    fluid.setUseTVasIndependentVariables(true);
    fluid.init(3);

    ComponentSrkCPA component = component(fluid, componentName, phaseIndex);
    double x = component.getXsite()[0];
    double analyticalFirst = component.getXsitedT()[0];
    double analyticalSecond = component.getXsitedTdT()[0];
    double plus = siteFractionAtTemperature(fluid, componentName, phaseIndex, fluid.getTemperature() + step);
    double minus = siteFractionAtTemperature(fluid, componentName, phaseIndex, fluid.getTemperature() - step);
    double numericalFirst = (plus - minus) / (2.0 * step);
    double numericalSecond = (plus - 2.0 * x + minus) / (step * step);

    assertTrue(Math.abs(analyticalSecond) > 1.0e-12, "Second site sensitivity must be populated: X=" + x + ", X_T="
        + analyticalFirst + ", X_TT=" + analyticalSecond + ", numerical X_TT=" + numericalSecond);
    assertEquals(numericalFirst, analyticalFirst, tolerance(numericalFirst, 5.0e-5, 1.0e-10));
    assertEquals(numericalSecond, analyticalSecond, tolerance(numericalSecond, 5.0e-4, 1.0e-9));
  }

  /**
   * Checks second/third volume and mixed temperature-volume site sensitivities.
   *
   * @param fluid configured CPA system
   * @param componentName associating component name
   * @param phaseIndex phase containing the association sites
   */
  private void assertVolumeDerivatives(SystemInterface fluid, String componentName, int phaseIndex) {
    fluid.init(3);
    double volume = fluid.getPhase(phaseIndex).getTotalVolume();
    double volumeStep = Math.max(Math.abs(volume) * 1.0e-4, 1.0e-6);
    double temperatureStep = 0.01;

    ComponentSrkCPA component = component(fluid, componentName, phaseIndex);
    double analyticalSecond = component.getXsitedVdV()[0];
    double analyticalThird = component.getXsitedVdVdV()[0];
    double analyticalMixed = component.getXsitedTdV()[0];

    double[] plusVolume = siteSensitivitiesAtVolume(fluid, componentName, phaseIndex, volume + volumeStep);
    double[] minusVolume = siteSensitivitiesAtVolume(fluid, componentName, phaseIndex, volume - volumeStep);
    double numericalSecond = (plusVolume[0] - minusVolume[0]) / (2.0 * volumeStep);
    double numericalThird = (plusVolume[1] - minusVolume[1]) / (2.0 * volumeStep);
    double numericalMixedFromVolume = (plusVolume[2] - minusVolume[2]) / (2.0 * volumeStep);

    double plusTemperatureVolumeDerivative = volumeDerivativeAtTemperature(fluid, componentName, phaseIndex,
        fluid.getTemperature() + temperatureStep);
    double minusTemperatureVolumeDerivative = volumeDerivativeAtTemperature(fluid, componentName, phaseIndex,
        fluid.getTemperature() - temperatureStep);
    double numericalMixedFromTemperature = (plusTemperatureVolumeDerivative - minusTemperatureVolumeDerivative)
        / (2.0 * temperatureStep);

    assertTrue(Math.abs(analyticalSecond) > 1.0e-12, "Second volume site sensitivity must be populated");
    assertTrue(Double.isFinite(analyticalThird), "Third volume site sensitivity must be finite");
    assertTrue(Math.abs(analyticalMixed) > 1.0e-12, "Mixed temperature-volume site sensitivity must be populated");
    assertEquals(numericalSecond, analyticalSecond, tolerance(numericalSecond, 5.0e-4, 1.0e-10));
    assertEquals(numericalThird, analyticalThird, tolerance(numericalThird, 2.0e-3, 1.0e-9));
    assertEquals(numericalMixedFromVolume, analyticalMixed, tolerance(numericalMixedFromVolume, 5.0e-4, 1.0e-10));
    assertEquals(numericalMixedFromTemperature, analyticalMixed,
        tolerance(numericalMixedFromTemperature, 5.0e-4, 1.0e-10));
  }

  /**
   * Returns one CPA component from a phase.
   *
   * @param fluid initialized CPA system
   * @param componentName component name
   * @param phaseIndex phase index
   * @return CPA component
   */
  private ComponentSrkCPA component(SystemInterface fluid, String componentName, int phaseIndex) {
    return (ComponentSrkCPA) fluid.getPhase(phaseIndex).getComponent(componentName);
  }

  /**
   * Evaluates one association-site fraction after a fixed-volume temperature perturbation.
   *
   * @param base initialized fixed-volume system
   * @param componentName component name
   * @param phaseIndex phase index
   * @param temperature perturbed temperature in K
   * @return first site fraction
   */
  private double siteFractionAtTemperature(SystemInterface base, String componentName, int phaseIndex,
      double temperature) {
    SystemInterface perturbed = base.clone();
    perturbed.setTemperature(temperature);
    perturbed.init(3);
    return component(perturbed, componentName, phaseIndex).getXsite()[0];
  }

  /**
   * Evaluates volume sensitivities after a fixed-volume perturbation.
   *
   * @param base initialized fixed-volume system
   * @param componentName component name
   * @param phaseIndex phase index
   * @param volume perturbed native EOS volume
   * @return first volume, second volume, and first temperature sensitivities
   */
  private double[] siteSensitivitiesAtVolume(SystemInterface base, String componentName, int phaseIndex,
      double volume) {
    SystemInterface perturbed = base.clone();
    perturbed.getPhase(phaseIndex).setTotalVolume(volume);
    perturbed.init(3);
    ComponentSrkCPA component = component(perturbed, componentName, phaseIndex);
    return new double[] {component.getXsitedV()[0], component.getXsitedVdV()[0], component.getXsitedT()[0]};
  }

  /**
   * Evaluates the first volume sensitivity after a fixed-volume temperature perturbation.
   *
   * @param base initialized fixed-volume system
   * @param componentName component name
   * @param phaseIndex phase index
   * @param temperature perturbed temperature in K
   * @return first volume sensitivity
   */
  private double volumeDerivativeAtTemperature(SystemInterface base, String componentName, int phaseIndex,
      double temperature) {
    SystemInterface perturbed = base.clone();
    perturbed.setTemperature(temperature);
    perturbed.init(3);
    return component(perturbed, componentName, phaseIndex).getXsitedV()[0];
  }

  /**
   * Evaluates first site sensitivities after an amount perturbation at fixed native EOS volume.
   *
   * @param base initialized fixed-volume system
   * @param componentName component owning the inspected association site
   * @param phaseIndex phase index
   * @param amountComponent perturbed component index
   * @param amountIncrement signed amount perturbation in mol
   * @return temperature, volume, and each amount sensitivity of the first site
   */
  private double[] siteSensitivitiesAtAmount(SystemInterface base, String componentName, int phaseIndex,
      int amountComponent, double amountIncrement) {
    PhaseSrkCPA perturbed = ((PhaseSrkCPA) base.getPhase(phaseIndex)).clone();
    double volume = perturbed.getTotalVolume();
    perturbed.addMoles(amountComponent, amountIncrement);
    for (int componentIndex = 0; componentIndex < perturbed.getNumberOfComponents(); componentIndex++) {
      double moleFraction = perturbed.getComponent(componentIndex).getNumberOfMolesInPhase()
          / perturbed.getNumberOfMolesInPhase();
      perturbed.getComponent(componentIndex).setx(moleFraction);
      perturbed.getComponent(componentIndex).setz(moleFraction);
    }
    perturbed.setTotalVolume(volume);
    perturbed.init(perturbed.getNumberOfMolesInPhase() / perturbed.getBeta(), perturbed.getNumberOfComponents(), 3,
        perturbed.getBeta());
    ComponentSrkCPA component = (ComponentSrkCPA) perturbed.getComponent(componentName);
    double[] result = new double[6 + perturbed.getNumberOfComponents()];
    result[0] = component.getXsitedT()[0];
    result[1] = component.getXsitedV()[0];
    for (int amount = 0; amount < perturbed.getNumberOfComponents(); amount++) {
      result[2 + amount] = component.getXsitedni(0, amount);
    }
    result[2 + perturbed.getNumberOfComponents()] = component.getXsite()[0];
    result[3 + perturbed.getNumberOfComponents()] = Math.log(perturbed.getGcpa());
    result[4 + perturbed.getNumberOfComponents()] = perturbed.getB();
    result[5 + perturbed.getNumberOfComponents()] = perturbed.getComponent(amountComponent).getNumberOfMolesInPhase();
    return result;
  }

  /**
   * Selects a scale-aware central-difference amount step.
   *
   * @param phase phase containing the perturbed component
   * @param component component index
   * @return amount step in mol
   */
  private double amountStep(PhaseSrkCPA phase, int component) {
    return Math.max(1.0, phase.getComponent(component).getNumberOfMolesInPhase()) * 1.0e-6;
  }

  /**
   * Combines a relative and absolute comparison tolerance.
   *
   * @param value reference value
   * @param relative relative tolerance
   * @param absolute absolute tolerance
   * @return comparison tolerance
   */
  private double tolerance(double value, double relative, double absolute) {
    return Math.max(absolute, Math.abs(value) * relative);
  }
}
