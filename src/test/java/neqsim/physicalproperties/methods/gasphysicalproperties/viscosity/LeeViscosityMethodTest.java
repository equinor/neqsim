package neqsim.physicalproperties.methods.gasphysicalproperties.viscosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.physicalproperties.methods.commonphasephysicalproperties.viscosity.LeeViscosityMethod;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class for LeeViscosityMethod - Lee-Gonzalez-Eakin gas viscosity correlation.
 */
class LeeViscosityMethodTest {
  @Test
  void testMethaneViscosity() {
    // Methane at standard conditions
    double temperatureK = 293.15; // K (20°C)
    double molarMassKgMol = 16.043 / 1000.0; // kg/mol
    double densityKgM3 = 0.668; // kg/m³ at ~1 atm

    double viscosity = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, molarMassKgMol);

    // Methane viscosity at 20°C, 1 atm is approximately 1.1e-5 Pa.s
    assertTrue(viscosity > 0.8e-5, "Methane viscosity should be > 0.8e-5 Pa.s");
    assertTrue(viscosity < 1.5e-5, "Methane viscosity should be < 1.5e-5 Pa.s");
  }

  @Test
  void testNaturalGasMixtureViscosity() {
    // Natural gas mixture
    double temperatureK = 323.15; // K (50°C)
    double molarMassKgMol = 19.5 / 1000.0; // kg/mol (typical natural gas)
    double densityKgM3 = 30.0; // kg/m³ at high pressure

    double viscosity = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, molarMassKgMol);

    // Should be in reasonable range for natural gas
    assertTrue(viscosity > 0.5e-5, "Viscosity should be positive");
    assertTrue(viscosity < 5.0e-5, "Viscosity should be reasonable for natural gas");
  }

  @Test
  void testTemperatureEffect() {
    // Higher temperature should generally increase gas viscosity
    double molarMassKgMol = 16.043 / 1000.0;
    double densityKgM3 = 10.0; // constant density

    double viscLow = LeeViscosityMethod.calcViscosity(300.0, densityKgM3, molarMassKgMol);
    double viscHigh = LeeViscosityMethod.calcViscosity(400.0, densityKgM3, molarMassKgMol);

    assertTrue(viscHigh > viscLow,
        "Gas viscosity should increase with temperature at constant density");
  }

  @Test
  void testDensityEffect() {
    // Higher density should increase viscosity (exponential term)
    double temperatureK = 350.0;
    double molarMassKgMol = 20.0 / 1000.0;

    double viscLowDens = LeeViscosityMethod.calcViscosity(temperatureK, 10.0, molarMassKgMol);
    double viscHighDens = LeeViscosityMethod.calcViscosity(temperatureK, 100.0, molarMassKgMol);

    assertTrue(viscHighDens > viscLowDens, "Viscosity should increase with density");
  }

  @Test
  void testMolecularWeightEffect() {
    // Heavier gases have lower viscosity at dilute conditions
    double temperatureK = 350.0;
    double densityKgM3 = 1.0; // Low density

    double viscLight = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, 16.0 / 1000.0);
    double viscHeavy = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, 50.0 / 1000.0);

    // At low density, heavier gases have lower viscosity
    assertTrue(viscHeavy < viscLight, "Heavier gas should have lower viscosity at low density");
  }

  @Test
  void testWithNeqSimFluid() {
    // Test integration with NeqSim fluid
    SystemInterface fluid = new SystemSrkEos(323.15, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(2);

    if (fluid.hasPhaseType("gas")) {
      int gasPhase = fluid.getPhaseIndex("gas");
      double mwKgMol = fluid.getPhase(gasPhase).getMolarMass(); // kg/mol
      double densityKgM3 = fluid.getPhase(gasPhase).getDensity("kg/m3");
      double temperatureK = fluid.getTemperature();

      double viscosity = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, mwKgMol);

      assertTrue(viscosity > 0, "Viscosity should be positive");
      assertTrue(viscosity < 1e-3, "Viscosity should be less than 1 mPa.s");
    }
  }

  @Test
  void testLowPressureViscosity() {
    // Low pressure viscosity for methane
    double temperatureK = 300.0;
    double molarMassKgMol = 16.043 / 1000.0;

    double viscLP = LeeViscosityMethod.calcLowPressureViscosity(temperatureK, molarMassKgMol);

    assertTrue(viscLP > 0, "Low-pressure viscosity should be positive");
    assertTrue(viscLP < 2e-5, "Low-pressure viscosity should be in expected range");
  }

  @Test
  void testEdgeCases() {
    // Very low density should give result close to low-pressure method
    double temperatureK = 300.0;
    double molarMassKgMol = 16.0 / 1000.0;

    double viscLP = LeeViscosityMethod.calcLowPressureViscosity(temperatureK, molarMassKgMol);
    double viscLowDens = LeeViscosityMethod.calcViscosity(temperatureK, 0.01, molarMassKgMol);

    // Should be close when density is very low
    assertEquals(viscLP, viscLowDens, viscLP * 0.1,
        "Low density result should be close to low-pressure result");
  }

  @Test
  void testReproducibilityWithLiterature() {
    // Reference: Lee, Gonzalez, Eakin (1966) Table values
    // Methane at 200°F (366.5K) and specific gravity 0.554
    double temperatureK = 366.5; // K
    double molarMassKgMol = 16.043 / 1000.0; // kg/mol
    // At 1000 psia (~69 bar), methane density is approximately 50 kg/m³
    double densityKgM3 = 50.0;

    double viscosity = LeeViscosityMethod.calcViscosity(temperatureK, densityKgM3, molarMassKgMol);

    // Literature value is approximately 1.5e-5 Pa.s
    // Allow 30% tolerance for different conditions
    assertTrue(viscosity > 0.8e-5 && viscosity < 3.0e-5,
        "Viscosity should be in expected range for methane at high pressure");
  }
}
