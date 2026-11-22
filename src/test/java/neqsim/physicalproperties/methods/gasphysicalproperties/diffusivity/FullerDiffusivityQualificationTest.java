package neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Independent Fuller parameter arithmetic and public property lifecycle regressions.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FullerDiffusivityQualificationTest extends neqsim.NeqSimTest {
  /**
   * Create a synthetic binary with explicitly prescribed masses to isolate volume lookup.
   *
   * @param name first species lookup identity
   * @return gas properties at 300 K and 1.01325 bara, masses 30 and 28 g/mol
   */
  private PhysicalProperties prescribed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300, 1.01325);
    fluid.addComponent("methane", 0.25);
    fluid.addComponent("nitrogen", 0.75);
    fluid.setMixingRule("classic");
    fluid.init(0);
    PhysicalProperties properties = fluid.getPhase(0).getPhysicalProperties();
    properties.getPhase().getComponent(0).setComponentName(name);
    properties.getPhase().getComponent(0).setMolarMass(0.030);
    properties.getPhase().getComponent(1).setMolarMass(0.028);
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    return properties;
  }

  /**
   * Frozen 70-digit Decimal evaluations using published atomic sums and special simple-gas values.
   *
   * @param name table identity
   * @param expected independent diffusion coefficient in m2/s
   */
  @ParameterizedTest
  @CsvSource({"methane, 0.0000182833674784442866", "ethane, 0.0000146889393624924563",
      "propane, 0.0000126945267909838466", "i-butane, 0.0000113631903301342956", "n-butane, 0.0000113631903301342956",
      "i-pentane, 0.0000103871077828427175", "n-pentane, 0.0000103871077828427175",
      "n-hexane, 0.00000962914082117686541", "n-heptane, 0.00000901710021561406617",
      "n-octane, 0.00000850867340419105019", "n-nonane, 0.00000807711028998203992",
      "n-decane, 0.00000770451849005305242", "nC10, 0.00000770451849005305242",
      "cyclohexane, 0.00000978511315555344690", "benzene, 0.0000111373368314434942",
      "toluene, 0.0000102148337207305401", "methanol, 0.0000169193676671526854", "ethanol, 0.0000139928762298007962",
      "acetone, 0.0000125809395228355911", "MEG, 0.0000133927069045795077", "DEG, 0.0000104791993159886656",
      "TEG, 0.00000891295002283377014", "helium, 0.0000349437681272237820", "nitrogen, 0.0000203043101581714838",
      "oxygen, 0.0000211701007622939821", "water, 0.0000227048499421167095", "CO2, 0.0000178527523429037525"})
  void independentVolumeValues(String name, double expected) {
    PhysicalProperties properties = prescribed(name);
    assertEquals(expected, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), expected * 2e-13);
  }

  /** Check case-insensitive lookup and symmetry without a new molecular-formula parser. */
  @Test
  void caseAndPairOrder() {
    PhysicalProperties lower = prescribed("benzene");
    PhysicalProperties upper = prescribed("BENZENE");
    double expected = lower.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    assertEquals(expected, upper.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), 0);
    assertEquals(expected, lower.diffusivityCalc.calcBinaryDiffusionCoefficient(1, 0, 0), 0);
  }

  /** Check exact pressure scaling, temperature exponent and repeat/state-order independence. */
  @Test
  void stateScalingAndRepeat() {
    PhysicalProperties properties = prescribed("ethane");
    double initial = properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    properties.getPhase().setPressure(2.0265);
    assertEquals(initial / 2, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), initial * 1e-14);
    properties.getPhase().setTemperature(400);
    assertEquals(initial / 2 * Math.pow(400.0 / 300, 1.75),
        properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), initial * 1e-14);
    properties.getPhase().setTemperature(300);
    properties.getPhase().setPressure(1.01325);
    assertEquals(initial, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), 0);
  }

  /**
   * A property clone must own every mutable gas diffusion matrix.
   *
   * @param model public gas diffusivity model key
   */
  @ParameterizedTest
  @ValueSource(strings = {"Fuller-Schettler-Giddings", "Chapman-Enskog", "Wilke Lee"})
  void cloneKeepsStoredBinaryResultsIndependent(String model) {
    PhysicalProperties original = prescribed("ethane");
    original.setDiffusionCoefficientModel(model);
    original.diffusivityCalc.calcDiffusionCoefficients(0, 0);
    double before = original.getDiffusionCoefficient(0, 1);
    PhysicalProperties copy = original.clone();
    copy.setPhase(prescribed("ethane").getPhase());
    copy.getPhase().setTemperature(400);
    copy.diffusivityCalc.calcDiffusionCoefficients(0, 0);
    assertNotEquals(before, copy.getDiffusionCoefficient(0, 1));
    assertEquals(before, original.getDiffusionCoefficient(0, 1), 0);
    Diffusivity source = (Diffusivity) original.diffusivityCalc;
    Diffusivity target = (Diffusivity) copy.diffusivityCalc;
    double[][][] sourceMatrices = {source.binaryDiffusionCoefficients, source.binaryLennardJonesOmega,
        source.binaryMolecularDiameter, source.binaryEnergyParameter, source.binaryMolecularMass};
    double[][][] targetMatrices = {target.binaryDiffusionCoefficients, target.binaryLennardJonesOmega,
        target.binaryMolecularDiameter, target.binaryEnergyParameter, target.binaryMolecularMass};
    for (int matrix = 0; matrix < sourceMatrices.length; matrix++) {
      assertNotSame(sourceMatrices[matrix], targetMatrices[matrix]);
      assertNotSame(sourceMatrices[matrix][0], targetMatrices[matrix][0]);
    }
    assertNotSame(source.effectiveDiffusionCoefficient, target.effectiveDiffusionCoefficient);
  }

  /**
   * Public flashed-fluid routing must give intensive coefficients for unequal amounts and compositions.
   *
   * @param amount total feed mol
   * @param fraction ethane mole fraction
   */
  @ParameterizedTest
  @CsvSource({"0.001, 0.2", "1, 0.2", "1000, 0.2", "0.001, 0.5", "1, 0.5", "1000, 0.5", "0.001, 0.8", "1, 0.8",
      "1000, 0.8"})
  void publicGasRoute(double amount, double fraction) {
    SystemSrkEos fluid = new SystemSrkEos(300, 1.01325);
    fluid.addComponent("ethane", amount * fraction);
    fluid.addComponent("nitrogen", amount * (1 - fraction));
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    PhysicalProperties properties = fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    properties.diffusivityCalc.calcDiffusionCoefficients(0, 0);
    double coefficient = properties.getDiffusionCoefficient("ethane", "nitrogen");
    assertTrue(Double.isFinite(coefficient) && coefficient > 0);
    PhysicalProperties reference = prescribed("ethane");
    reference.getPhase().getComponent(0).setMolarMass(properties.getPhase().getComponent("ethane").getMolarMass());
    reference.getPhase().getComponent(1).setMolarMass(properties.getPhase().getComponent("nitrogen").getMolarMass());
    assertEquals(reference.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), coefficient, coefficient * 1e-13);
  }

  /** Invalid state or missing/nonfinite mass must not be reported as a diffusivity. */
  @Test
  void invalidInputsAreUnavailable() {
    PhysicalProperties properties = prescribed("ethane");
    for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      properties.getPhase().setTemperature(value);
      assertTrue(Double.isNaN(properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0)));
      properties.getPhase().setTemperature(300);
      properties.getPhase().setPressure(value);
      assertTrue(Double.isNaN(properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0)));
      properties.getPhase().setPressure(1.01325);
      properties.getPhase().getComponent(0).setMolarMass(value);
      assertTrue(Double.isNaN(properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0)));
      properties.getPhase().getComponent(0).setMolarMass(0.030);
    }
  }

  /**
   * Compare the original CH4/He AF and TT fits, without treating fit errors as complete uncertainty.
   *
   * @param temperature K within the measured envelopes
   */
  @ParameterizedTest
  @ValueSource(doubles = {200, 250, 273.15})
  void methaneHeliumMeasuredFits(double temperature) {
    SystemSrkEos fluid = new SystemSrkEos(temperature, 1.01325);
    fluid.addComponent("methane", 0.001);
    fluid.addComponent("helium", 0.999);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    PhysicalProperties properties = fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    double actual = properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    double arrestedFlow = 0.618e-4 * Math.pow(temperature / 273.15, 1.68);
    double twinTube = 0.610e-4 * Math.pow(temperature / 273.15, 1.69);
    assertEquals(arrestedFlow, actual, arrestedFlow * 0.15);
    assertEquals(twinTube, actual, twinTube * 0.15);
  }

  /** Check opposite public component insertion order and stored symmetric coefficients. */
  @Test
  void publicReversedOrder() {
    SystemSrkEos fluid = new SystemSrkEos(300, 1.01325);
    fluid.addComponent("nitrogen", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    PhysicalProperties properties = fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
    properties.diffusivityCalc.calcDiffusionCoefficients(0, 0);
    PhysicalProperties reference = prescribed("ethane");
    reference.getPhase().getComponent(0).setMolarMass(properties.getPhase().getComponent("ethane").getMolarMass());
    reference.getPhase().getComponent(1).setMolarMass(properties.getPhase().getComponent("nitrogen").getMolarMass());
    double expected = reference.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    assertEquals(expected, properties.getDiffusionCoefficient("ethane", "nitrogen"), expected * 1e-13);
    assertEquals(properties.getDiffusionCoefficient(0, 1), properties.getDiffusionCoefficient(1, 0), 0);
  }

  /** Known volumes need no critical properties; unknown-component fallbacks remain estimates. */
  @Test
  void criticalDataAndApproximateFallbacks() {
    PhysicalProperties properties = prescribed("ethane");
    double known = properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    properties.getPhase().getComponent(0).setCriticalVolume(Double.NaN);
    assertEquals(known, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), 0);
    properties.getPhase().getComponent(0).setComponentName("unknown-structure");
    properties.getPhase().getComponent(0).setCriticalVolume(200);
    assertEquals(0.0000134741114571765069, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), 3e-18);
    properties.getPhase().getComponent(0).setCriticalVolume(0);
    assertEquals(0.0000174895928919224908, properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0), 4e-18);
    properties.getPhase().getComponent(0).setCriticalVolume(Double.POSITIVE_INFINITY);
    assertTrue(Double.isNaN(properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0)));
  }

  /** Warning boundaries do not themselves imply a qualified physical temperature range. */
  @Test
  void warningBoundaryNeighborsStayNumericallyFinite() {
    PhysicalProperties properties = prescribed("ethane");
    for (double temperature : new double[] {Math.nextDown(200.0), 200, Math.nextUp(200.0), Math.nextDown(2000.0), 2000,
        Math.nextUp(2000.0)}) {
      properties.getPhase().setTemperature(temperature);
      double coefficient = properties.diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
      assertTrue(Double.isFinite(coefficient) && coefficient > 0);
    }
  }
}
