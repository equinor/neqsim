package neqsim.physicalproperties.interfaceproperties.surfacetension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression coverage for incomplete profiles and invalid interfacial-tension results.
 *
 * @author NeqSim development team
 * @version 1.0
 */
class SurfaceTensionFailureTest {
  /**
   * Creates a flashed two-phase SRK mixture with initialized properties.
   *
   * @param first first component
   * @param second second component
   * @param fraction mole fraction of the first component
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return the two-phase fluid
   */
  private static SystemInterface fluid(String first, String second, double fraction, double temperature,
      double pressure) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent(first, fraction);
    fluid.addComponent(second, 1.0 - fraction);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    assertEquals(2, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Creates each public model implementation.
   *
   * @param name model name
   * @param fluid flashed fluid
   * @return the model
   */
  private static SurfaceTension model(String name, SystemInterface fluid) {
    switch (name) {
    case "Parachor":
      return new ParachorSurfaceTension(fluid);
    case "Firozabadi Ramley":
      return new FirozabadiRamleyInterfaceTension(fluid);
    case "Simple Gradient Theory":
      return new GTSurfaceTensionSimple(fluid);
    case "Linear Gradient Theory":
      return new LGTSurfaceTension(fluid);
    case "Full Gradient Theory":
      return new GTSurfaceTension(fluid);
    default:
      return new CDFTSurfaceTension(fluid);
    }
  }

  /**
   * Both reported failures must stop without exposing an incomplete or previous profile.
   *
   * @param carbonDioxide whether to use the CO2/water reproduction
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void failedSimpleGradientProfileIsRejectedAndCleared(boolean carbonDioxide) {
    SystemInterface failed = carbonDioxide ? fluid("CO2", "water", 0.02, 313.15, 50.0)
        : fluid("n-heptane", "water", 0.5, 300.0, 1.0);
    GTSurfaceTensionSimple calculator = new GTSurfaceTensionSimple(failed);
    double fallback = carbonDioxide ? new ParachorSurfaceTension(failed).calcSurfaceTension(0, 1)
        : new FirozabadiRamleyInterfaceTension(failed).calcSurfaceTension(0, 1);
    assertEquals(fallback, calculator.calcSurfaceTension(0, 1), 1e-12);
    assertThrows(IllegalStateException.class, () -> calculator.getMolarDensity(0));
    assertThrows(IllegalStateException.class, calculator::getMolarDensityTotal);
    assertThrows(IllegalStateException.class, calculator::getPressure);
    assertThrows(IllegalStateException.class, calculator::getz);
    assertThrows(IllegalStateException.class, calculator::getDmudn2);
    assertThrows(IllegalStateException.class, () -> calculator.getInfluenceParameter(fallback, 0));
    assertTrue(calculator.calcInfluenceParameter);

    calculator.system = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    assertEquals(0.015989522215911635, calculator.calcSurfaceTension(0, 1), 2e-8);
    assertEquals(200, calculator.getPressure().length);
    for (double density : calculator.getMolarDensityTotal()) {
      assertTrue(Double.isFinite(density) && density > 0.0);
    }
    calculator.system = failed;
    assertEquals(fallback, calculator.calcSurfaceTension(0, 1), 1e-12);
    assertThrows(IllegalStateException.class, calculator::getPressure);
  }

  /** Linear GT must not report an empty integration grid as a completed zero integral. */
  @Test
  void failedLinearGradientProfileIsRejectedAndCleared() {
    SystemInterface failed = fluid("CO2", "water", 0.02, 313.15, 50.0);
    LGTSurfaceTension calculator = new LGTSurfaceTension(failed);
    assertEquals(0.0628784322609245, calculator.calcSurfaceTension(0, 1), 1e-7);
    assertEquals(200, calculator.getPressure().length);
    calculator.ite_step = 1;
    assertEquals(new ParachorSurfaceTension(failed).calcSurfaceTension(0, 1), calculator.calcSurfaceTension(0, 1),
        1e-12);
    assertThrows(IllegalStateException.class, () -> calculator.getMolarDensity(0));
    assertThrows(IllegalStateException.class, calculator::getMolarDensityTotal);
    assertThrows(IllegalStateException.class, calculator::getPressure);
    assertThrows(IllegalStateException.class, calculator::getz);
    calculator.ite_step = 200;
    assertEquals(0.0628784322609245, calculator.calcSurfaceTension(0, 1), 1e-7);
    assertEquals(200, calculator.getPressure().length);
  }

  /**
   * Every model must reject inactive and identical phase indices.
   *
   * @param name model name
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void invalidPhasePairIsRejected(String name) {
    SurfaceTension calculator = model(name, fluid("methane", "n-decane", 0.6, 310.0, 50.0));
    assertThrows(IllegalArgumentException.class, () -> calculator.calcSurfaceTension(0, 0));
    assertThrows(IllegalArgumentException.class, () -> calculator.calcSurfaceTension(0, 2));
    assertThrows(IllegalArgumentException.class, () -> calculator.calcSurfaceTension(-1, 1));
  }

  /**
   * Non-finite bulk state must not become a finite correlation result or fallback.
   *
   * @param name model name
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void invalidBulkStateIsRejected(String name) {
    SystemInterface fluid = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    SurfaceTension calculator = model(name, fluid);
    fluid.getPhase(1).setMolarVolume(Double.NaN);
    assertThrows(IllegalStateException.class, () -> calculator.calcSurfaceTension(0, 1));
  }

  /**
   * A finite bulk density beyond the cubic EOS covolume is rejected before either the model or its fallback.
   *
   * @param name model name
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void bulkCovolumeViolationIsRejected(String name) {
    SystemInterface fluid = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    double covolumePerMole = fluid.getPhase(1).getB() / fluid.getPhase(1).getNumberOfMolesInPhase();
    fluid.getPhase(1).setMolarVolume(0.5 * covolumePerMole);
    assertThrows(IllegalStateException.class, () -> model(name, fluid).calcSurfaceTension(0, 1));
  }

  /**
   * All selectors still calculate a complete physical hydrocarbon interface.
   *
   * @param name model name
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void validHydrocarbonInterfaceStillWorks(String name) {
    SystemInterface fluid = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    double sigma = model(name, fluid).calcSurfaceTension(0, 1);
    assertTrue(Double.isFinite(sigma) && sigma > 0.0 && sigma < 1.0, name);
    fluid.getInterphaseProperties().setInterfacialTensionModel("gas", "oil", name);
    assertEquals(sigma, fluid.getInterfacialTension(0, 1), 1e-10);
  }

  /** Numbered model 1 must propagate the reported aqueous failure through the system API. */
  @Test
  void numberedModelPropagatesProfileFailure() {
    SystemInterface fluid = fluid("CO2", "water", 0.02, 313.15, 50.0);
    fluid.getInterphaseProperties().setInterfacialTensionModel(1);
    assertEquals(new ParachorSurfaceTension(fluid).calcSurfaceTension(0, 1), fluid.getInterfacialTension(0, 1), 1e-12);
  }

  /** A failed cDFT evaluation must use its documented correlation instead of returning NaN. */
  @Test
  void nonFiniteCdftUsesValidatedParachorFallback() {
    SystemInterface fluid = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    CDFTSurfaceTension calculator = new CDFTSurfaceTension(fluid);
    calculator.setUsePredictiveMode(false);
    calculator.setAttractiveRangeFactor(Double.NaN);
    assertEquals(new ParachorSurfaceTension(fluid).calcSurfaceTension(0, 1), calculator.calcSurfaceTension(0, 1),
        1e-12);
  }

  /** Overflow in a correlation is a failed calculation, not an infinite interfacial tension. */
  @Test
  void parachorOverflowIsRejected() {
    SystemInterface fluid = fluid("methane", "n-decane", 0.6, 310.0, 50.0);
    fluid.getPhase(0).getComponent(0).setParachorParameter(Double.MAX_VALUE);
    assertThrows(IllegalStateException.class, () -> new ParachorSurfaceTension(fluid).calcSurfaceTension(0, 1));
  }

  /** A failed fallback also raises an exception and retains its original numerical failure as diagnostic evidence. */
  @Test
  void failedFallbackCannotBecomeAZeroResult() {
    SystemInterface fluid = fluid("CO2", "water", 0.02, 313.15, 50.0);
    fluid.getPhase(0).getComponent(0).setParachorParameter(Double.NaN);
    GTSurfaceTensionSimple calculator = new GTSurfaceTensionSimple(fluid);
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> calculator.calcSurfaceTension(0, 1));
    assertEquals(1, failure.getSuppressed().length);
    assertThrows(IllegalStateException.class, calculator::getPressure);
  }

  /**
   * All models preserve the indexed API's single-phase no-interface result.
   *
   * @param name model name
   */
  @ParameterizedTest
  @ValueSource(strings = {"Parachor", "Firozabadi Ramley", "Simple Gradient Theory", "Linear Gradient Theory",
      "Full Gradient Theory", "cDFT"})
  void singlePhaseRemainsANoInterfaceResult(String name) {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule(2);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(0.0, model(name, fluid).calcSurfaceTension(0, 1), 0.0);
    fluid.getInterphaseProperties().setInterfacialTensionModel(4);
    assertEquals(0.0, fluid.getInterfacialTension(0, 1), 0.0);
  }
}
