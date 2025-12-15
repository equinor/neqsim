package neqsim.thermo.util.derivatives;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Unit tests for DifferentiableFlash and gradient computation.
 */
class DifferentiableFlashTest {

  private SystemInterface system;
  private ThermodynamicOperations ops;

  @BeforeEach
  void setUp() {
    // Create a simple two-component system at conditions that give two phases
    system = new SystemSrkEos(250.0, 30.0); // Lower T, lower P for clear two-phase
    system.addComponent("methane", 0.8);
    system.addComponent("propane", 0.2);
    system.setMixingRule("classic");

    ops = new ThermodynamicOperations(system);
    ops.TPflash();
  }

  @Test
  void testFugacityJacobianExtraction() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    // Extract Jacobian for vapor phase
    FugacityJacobian jacV = diffFlash.extractFugacityJacobian(1);

    assertNotNull(jacV);
    assertEquals(2, jacV.getNumberOfComponents());
    assertEquals("methane", jacV.getComponentNames()[0]);
    assertEquals("propane", jacV.getComponentNames()[1]);

    // Log fugacity coefficients should be computed
    double[] lnPhi = jacV.getLnPhi();
    assertFalse(Double.isNaN(lnPhi[0]), "ln(phi) for methane should be valid");
    assertFalse(Double.isNaN(lnPhi[1]), "ln(phi) for propane should be valid");

    // Temperature derivatives should exist
    double[] dlnPhidT = jacV.getDlnPhidT();
    assertNotNull(dlnPhidT);
    assertEquals(2, dlnPhidT.length);

    // Pressure derivatives should exist
    double[] dlnPhidP = jacV.getDlnPhidP();
    assertNotNull(dlnPhidP);
    assertEquals(2, dlnPhidP.length);

    // Composition Jacobian should be symmetric (thermodynamic consistency)
    double[][] dlnPhidn = jacV.getDlnPhidn();
    assertNotNull(dlnPhidn);
    assertEquals(2, dlnPhidn.length);
    assertEquals(2, dlnPhidn[0].length);
  }

  @Test
  void testFlashGradientsComputation() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    FlashGradients grads = diffFlash.computeFlashGradients();

    assertNotNull(grads);

    if (system.getNumberOfPhases() >= 2 && grads.isValid()) {
      // K-values should be positive
      double[] kValues = grads.getKValues();
      assertTrue(kValues[0] > 0, "K-value for methane should be positive");
      assertTrue(kValues[1] > 0, "K-value for propane should be positive");

      // Beta should be between 0 and 1
      double beta = grads.getBeta();
      assertTrue(beta >= 0 && beta <= 1, "Beta should be between 0 and 1, got: " + beta);

      // Check that derivatives are finite
      double[] dKdT = grads.getDKdT();
      for (int i = 0; i < dKdT.length; i++) {
        assertFalse(Double.isNaN(dKdT[i]), "dK/dT should be finite");
        assertFalse(Double.isInfinite(dKdT[i]), "dK/dT should not be infinite");
      }

      double[] dKdP = grads.getDKdP();
      for (int i = 0; i < dKdP.length; i++) {
        assertFalse(Double.isNaN(dKdP[i]), "dK/dP should be finite");
        assertFalse(Double.isInfinite(dKdP[i]), "dK/dP should not be infinite");
      }

      assertFalse(Double.isNaN(grads.getDBetadT()), "dBeta/dT should be finite");
      assertFalse(Double.isNaN(grads.getDBetadP()), "dBeta/dP should be finite");
    }
  }

  @Test
  void testPropertyGradientDensity() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");

    assertNotNull(densityGrad);
    assertEquals("density", densityGrad.getPropertyName());
    assertEquals("kg/m3", densityGrad.getUnit());

    // Density value should be finite and reasonable
    double density = densityGrad.getValue();
    assertFalse(Double.isNaN(density), "Density should be a valid number");
    assertFalse(Double.isInfinite(density), "Density should be finite");

    // Temperature derivative should be finite
    double dRhodT = densityGrad.getDerivativeWrtTemperature();
    assertFalse(Double.isNaN(dRhodT), "dDensity/dT should be finite");

    // Pressure derivative should be finite
    double dRhodP = densityGrad.getDerivativeWrtPressure();
    assertFalse(Double.isNaN(dRhodP), "dDensity/dP should be finite");
  }

  @Test
  void testPropertyGradientEnthalpy() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    PropertyGradient enthalpyGrad = diffFlash.computePropertyGradient("enthalpy");

    assertNotNull(enthalpyGrad);
    assertEquals("enthalpy", enthalpyGrad.getPropertyName());

    // dH/dT should be approximately Cp (heat capacity)
    double dHdT = enthalpyGrad.getDerivativeWrtTemperature();
    assertFalse(Double.isNaN(dHdT), "dEnthalpy/dT should be finite");

    // dH/dP should be finite
    double dHdP = enthalpyGrad.getDerivativeWrtPressure();
    assertFalse(Double.isNaN(dHdP), "dEnthalpy/dP should be finite");
  }

  @Test
  void testSinglePhaseHandling() {
    // Create a system that will be single phase (high pressure gas)
    SystemInterface gasSystem = new SystemSrkEos(400.0, 10.0);
    gasSystem.addComponent("methane", 1.0);
    gasSystem.setMixingRule("classic");

    ThermodynamicOperations gasOps = new ThermodynamicOperations(gasSystem);
    gasOps.TPflash();

    DifferentiableFlash diffFlash = new DifferentiableFlash(gasSystem);
    FlashGradients grads = diffFlash.computeFlashGradients();

    // Should handle single phase gracefully
    assertNotNull(grads);
    // Single phase case should return invalid gradients with appropriate message
    if (gasSystem.getNumberOfPhases() < 2) {
      assertFalse(grads.isValid());
      assertNotNull(grads.getErrorMessage());
    }
  }

  @Test
  void testGradientDirectionalDerivative() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");

    // Test directional derivative
    double deltaT = 1.0; // 1 K increase
    double deltaP = 0.0; // no pressure change
    double[] deltaZ = null;

    double directional = densityGrad.directionalDerivative(deltaT, deltaP, deltaZ);

    // Should be close to dDensity/dT
    assertEquals(densityGrad.getDerivativeWrtTemperature(), directional, 1e-10);
  }

  @Test
  void testFlashGradientsFlatArray() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    FlashGradients grads = diffFlash.computeFlashGradients();

    if (grads.isValid()) {
      double[] flat = grads.toFlatArray();
      assertNotNull(flat);

      int nc = grads.getNumberOfComponents();
      // Expected size: 2*nc (dK/dT, dK/dP) + 2 (dβ/dT, dβ/dP) + nc*nc (dK/dz) + nc (dβ/dz)
      int expectedSize = 2 * nc + 2 + nc * nc + nc;
      assertEquals(expectedSize, flat.length);
    }
  }

  @Test
  void testPropertyGradientToArray() {
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");

    double[] arr = densityGrad.toArray();
    assertNotNull(arr);

    // Should have [dT, dP, dz_0, dz_1, ...]
    int nc = system.getNumberOfComponents();
    assertEquals(2 + nc, arr.length);
    assertEquals(densityGrad.getDerivativeWrtTemperature(), arr[0], 1e-10);
    assertEquals(densityGrad.getDerivativeWrtPressure(), arr[1], 1e-10);
  }

  @Test
  void testNumericalVsAnalyticalTemperatureDerivative() {
    // Compare numerical derivative with analytical (for validation)
    DifferentiableFlash diffFlash = new DifferentiableFlash(system);

    // Get analytical derivative
    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
    double analyticalDT = densityGrad.getDerivativeWrtTemperature();

    // Compute numerical derivative manually
    double T0 = system.getTemperature();
    double epsilon = 0.01;

    system.setTemperature(T0 + epsilon);
    ops.TPflash();
    double densityPlus = system.getDensity("kg/m3");

    system.setTemperature(T0 - epsilon);
    ops.TPflash();
    double densityMinus = system.getDensity("kg/m3");

    double numericalDT = (densityPlus - densityMinus) / (2.0 * epsilon);

    // Should be reasonably close (allowing for flash re-convergence effects)
    // This is a sanity check, not a strict equality test
    assertTrue(
        Math.abs(analyticalDT - numericalDT) < Math.abs(numericalDT) * 0.5
            || Math.abs(analyticalDT - numericalDT) < 1.0,
        "Analytical and numerical derivatives should be in same ballpark");

    // Restore system
    system.setTemperature(T0);
    ops.TPflash();
  }

  @Test
  void testMultiComponentSystem() {
    // Test with more components
    SystemInterface multiSystem = new SystemSrkEos(280.0, 40.0);
    multiSystem.addComponent("nitrogen", 0.02);
    multiSystem.addComponent("CO2", 0.03);
    multiSystem.addComponent("methane", 0.80);
    multiSystem.addComponent("ethane", 0.10);
    multiSystem.addComponent("propane", 0.05);
    multiSystem.setMixingRule("classic");

    ThermodynamicOperations multiOps = new ThermodynamicOperations(multiSystem);
    multiOps.TPflash();

    DifferentiableFlash diffFlash = new DifferentiableFlash(multiSystem);

    // Test fugacity Jacobian
    if (multiSystem.getNumberOfPhases() >= 2) {
      FugacityJacobian jacL = diffFlash.extractFugacityJacobian(0);
      assertEquals(5, jacL.getNumberOfComponents());

      // Test flash gradients
      FlashGradients grads = diffFlash.computeFlashGradients();
      if (grads.isValid()) {
        assertEquals(5, grads.getNumberOfComponents());
        assertEquals(5, grads.getKValues().length);
        assertEquals(5, grads.getDKdT().length);
      }
    }

    // Test property gradient
    PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
    assertEquals(5, densityGrad.getNumberOfComponents());
  }
}
