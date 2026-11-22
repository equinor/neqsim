package neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSpanWagnerEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.spanwagner.NeqSimSpanWagner;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Published pressure-temperature references and lifecycle checks for the phase-bound pure-CO2 model.
 *
 * @author Even Solbraa
 * @version 1.1
 */
public class CO2ViscosityMethodTest {
  /**
   * Checks selected Table 8 values from Laesecke and Muzny (2017), away from the critical region.
   *
   * @param temperature temperature in K
   * @param pressure pressure in bara (the paper tabulates MPa)
   * @param expectedMpas published viscosity in mPa s
   */
  @ParameterizedTest
  @CsvSource({"300,1,0.01500", "400,200,0.03136", "300,400,0.1220", "700,1000,0.05764", "240,200,0.2068",
      "500,600,0.05307", "1000,1,0.04118"})
  void publishedPressureTemperatureValues(double temperature, double pressure, double expectedMpas) {
    SystemSpanWagnerEos fluid = new SystemSpanWagnerEos(temperature, pressure);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();
    assertTrue(fluid.getPhase(0).getPhysicalProperties().getViscosityModel() instanceof CO2ViscosityMethod);
    // Includes the EOS density calculation and the rounding of the printed recommendations.
    assertEquals(expectedMpas * 1e-3, fluid.getPhase(0).getViscosity("kg/msec"), expectedMpas * 1e-3 * 0.001);
  }

  /** Verifies fresh state evaluation and clone ownership without a transport-property cache. */
  @Test
  void repeatedAndClonedStates() {
    SystemSpanWagnerEos original = new SystemSpanWagnerEos(300, 10);
    new ThermodynamicOperations(original).TPflash();
    original.initPhysicalProperties();
    double before = original.getPhase(0).getPhysicalProperties().getViscosityModel().calcViscosity();
    SystemSpanWagnerEos copy = original.clone();
    copy.setTemperature(400);
    copy.setPressure(50);
    new ThermodynamicOperations(copy).TPflash();
    copy.initPhysicalProperties();
    double after = copy.getPhase(0).getPhysicalProperties().getViscosityModel().calcViscosity();
    assertTrue(after > before);
    assertEquals(before, original.getPhase(0).getPhysicalProperties().getViscosityModel().calcViscosity(), 1e-16);
    assertEquals(after, copy.getPhase(0).getPhysicalProperties().getViscosityModel().calcViscosity(), 1e-16);
  }

  /** Checks that NaN density follows the established Span-Wagner fallback instead of poisoning the result. */
  @Test
  void nonfiniteDensityUsesReferenceFallback() {
    SystemSpanWagnerEos fluid = new SystemSpanWagnerEos(300, 10);
    new ThermodynamicOperations(fluid).TPflash();
    PhaseInterface phase = fluid.getPhase(0);
    double molarDensity = NeqSimSpanWagner.getProperties(300, 10e5, phase.getType())[0];
    double expected = CO2ViscosityMethod.calculateViscosity(300, molarDensity * phase.getMolarMass());
    CO2ViscosityMethod method = new CO2ViscosityMethod(phase.getPhysicalProperties());
    phase.setMolarVolume(Double.NaN);
    assertEquals(expected, method.calcViscosity(), expected * 1e-12);
  }

  /** Rejects wrong species and multicomponent phases through an ordinary argument exception. */
  @Test
  void rejectsWrongSpeciesAndMixtures() {
    SystemInterface methane = new SystemSrkEos(300, 1);
    methane.addComponent("methane", 1);
    methane.setMixingRule("classic");
    methane.init(0);
    CO2ViscosityMethod wrongSpecies = new CO2ViscosityMethod(methane.getPhase(0).getPhysicalProperties());
    assertThrows(IllegalArgumentException.class, wrongSpecies::calcViscosity);
    SystemInterface mixture = new SystemSrkEos(300, 1);
    mixture.addComponent("CO2", 0.99);
    mixture.addComponent("nitrogen", 0.01);
    mixture.setMixingRule("classic");
    mixture.init(0);
    CO2ViscosityMethod mixed = new CO2ViscosityMethod(mixture.getPhase(0).getPhysicalProperties());
    assertThrows(IllegalArgumentException.class, mixed::calcViscosity);
  }
}
