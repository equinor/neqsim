package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling.ThermoProperties;

/**
 * Unit tests for ThermodynamicCoupling class.
 */
class ThermodynamicCouplingTest {

  private ThermodynamicCoupling coupling;
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    // Create a simple two-phase test fluid
    testFluid = new SystemSrkEos(298.15, 50.0); // 25°C, 50 bar
    testFluid.addComponent("methane", 0.9);
    testFluid.addComponent("n-heptane", 0.1);
    testFluid.setMixingRule("classic");
    testFluid.init(0);

    coupling = new ThermodynamicCoupling(testFluid);
  }

  @Test
  void testFlashPTReturnsValidProperties() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.converged, "Flash should converge");
    assertTrue(props.gasDensity > 0, "Gas density should be positive");
    assertTrue(props.liquidDensity > 0, "Liquid density should be positive");
    assertTrue(props.gasViscosity > 0, "Gas viscosity should be positive");
    assertTrue(props.liquidViscosity > 0, "Liquid viscosity should be positive");
  }

  @Test
  void testGasDensityIncreasesWithPressure() {
    ThermoProperties propsLowP = coupling.flashPT(10e5, 300.0);
    ThermoProperties propsHighP = coupling.flashPT(50e5, 300.0);

    assertTrue(propsHighP.gasDensity > propsLowP.gasDensity,
        "Gas density should increase with pressure");
  }

  @Test
  void testVaporFractionRangeIsValid() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasVaporFraction >= 0.0, "Vapor fraction should be >= 0");
    assertTrue(props.gasVaporFraction <= 1.0, "Vapor fraction should be <= 1");
  }

  @Test
  void testEnthalpyValuesAreRealistic() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Enthalpies can be negative or positive, but should be finite
    assertTrue(Double.isFinite(props.gasEnthalpy), "Gas enthalpy should be finite");
    assertTrue(Double.isFinite(props.liquidEnthalpy), "Liquid enthalpy should be finite");
  }

  @Test
  void testSurfaceTensionIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Surface tension should be positive for two-phase system
    assertTrue(props.surfaceTension >= 0.0, "Surface tension should be non-negative");
  }

  @Test
  void testViscosityOrdering() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Liquid viscosity typically higher than gas viscosity
    if (props.liquidViscosity > 0 && props.gasViscosity > 0) {
      assertTrue(props.liquidViscosity > props.gasViscosity,
          "Liquid viscosity should typically be higher than gas viscosity");
    }
  }

  @Test
  void testDensityOrdering() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    // Liquid density should be higher than gas density
    if (props.liquidDensity > 0 && props.gasDensity > 0) {
      assertTrue(props.liquidDensity > props.gasDensity,
          "Liquid density should be higher than gas density");
    }
  }

  @Test
  void testSoundSpeedIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasSoundSpeed > 0, "Gas sound speed should be positive");
    assertTrue(props.liquidSoundSpeed > 0, "Liquid sound speed should be positive");
  }

  @Test
  void testCompressibilityIsPositive() {
    ThermoProperties props = coupling.flashPT(30e5, 300.0);

    assertTrue(props.gasCompressibility > 0, "Gas compressibility should be positive");
    assertTrue(props.liquidCompressibility > 0, "Liquid compressibility should be positive");
  }

  @Test
  void testFlashWithDifferentTemperatures() {
    ThermoProperties propsLowT = coupling.flashPT(30e5, 250.0);
    ThermoProperties propsHighT = coupling.flashPT(30e5, 350.0);

    assertTrue(propsLowT.converged, "Low temperature flash should converge");
    assertTrue(propsHighT.converged, "High temperature flash should converge");

    // Vapor fraction should typically increase with temperature
    assertTrue(propsHighT.gasVaporFraction >= propsLowT.gasVaporFraction,
        "Vapor fraction should generally increase with temperature");
  }
}
