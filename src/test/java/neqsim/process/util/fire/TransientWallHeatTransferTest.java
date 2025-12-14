package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for TransientWallHeatTransfer class.
 *
 * @author ESOL
 */
public class TransientWallHeatTransferTest {

  private TransientWallHeatTransfer wallModel;

  @BeforeEach
  void setUp() {
    // Create a simple steel wall model
    // thickness = 10mm, k = 50 W/(m*K), rho = 7800 kg/m³, Cp = 500 J/(kg*K)
    // initialTemp = 300K, 20 nodes
    wallModel = new TransientWallHeatTransfer(0.010, 50.0, 7800.0, 500.0, 300.0, 20);
  }

  @Test
  void testInitialTemperatureUniform() {
    // After construction, all temperatures should be at initial value
    assertEquals(300.0, wallModel.getInnerWallTemperature(), 0.01);
    assertEquals(300.0, wallModel.getOuterWallTemperature(), 0.01);
    assertEquals(300.0, wallModel.getMeanWallTemperature(), 0.01);
  }

  @Test
  void testSteadyStateHeatConduction() {
    // With fixed inner and outer boundary temperatures, should approach steady state
    double T_inner_fluid = 400.0; // Hot inside
    double T_outer_ambient = 300.0; // Cold outside

    // Use high heat transfer coefficients to approximate fixed temperatures
    double h_inner = 10000.0; // W/(m²*K)
    double h_outer = 10000.0;

    // Run for many time steps to reach steady state
    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 10000; i++) {
      wallModel.advanceTimeStep(0.001, T_inner_fluid, h_inner, T_outer_ambient, h_outer);
    }

    // At steady state, inner wall should be close to inner fluid, outer to ambient
    double T_in = wallModel.getInnerWallTemperature();
    double T_out = wallModel.getOuterWallTemperature();
    double T_mean = wallModel.getMeanWallTemperature();

    // The mean should be between inner fluid and outer ambient
    assertTrue(T_mean > T_outer_ambient && T_mean < T_inner_fluid,
        "Mean temp should be between boundaries. Mean: " + T_mean);
    // Inner should be hotter than outer (heat flows outward)
    assertTrue(T_in > T_out,
        "Inner should be hotter than outer. Inner: " + T_in + ", Outer: " + T_out);
  }

  @Test
  void testHeatFluxFromOutside() {
    // If outside is hotter, heat should flow inward (fire scenario)
    double T_inner_fluid = 300.0; // Gas inside at 300K
    double T_outer_ambient = 800.0; // Fire outside at 800K
    double h_inner = 50.0; // W/(m²*K) - natural convection inside
    double h_outer = 200.0; // W/(m²*K) - fire convection outside

    double T_in_initial = wallModel.getInnerWallTemperature();

    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 500; i++) {
      wallModel.advanceTimeStep(0.01, T_inner_fluid, h_inner, T_outer_ambient, h_outer);
    }

    double T_in_final = wallModel.getInnerWallTemperature();
    double T_out_final = wallModel.getOuterWallTemperature();

    assertTrue(T_in_final > T_in_initial, "Inner wall should heat up when outside is hot. Initial: "
        + T_in_initial + ", Final: " + T_in_final);
    assertTrue(T_out_final > T_in_final, "Outer wall should be hotter than inner wall");
  }

  @Test
  void testAdiabaticInnerBoundary() {
    // With h_inner = 0, inner boundary is adiabatic
    double T_outer_ambient = 600.0;
    double h_inner = 0.0; // Adiabatic (no heat transfer to inside fluid)
    double h_outer = 200.0;

    // Heat from outside, adiabatic inside - run longer
    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 5000; i++) {
      wallModel.advanceTimeStep(0.01, 300.0, h_inner, T_outer_ambient, h_outer);
    }

    // Wall should heat up significantly
    double T_mean = wallModel.getMeanWallTemperature();
    assertTrue(T_mean > 350.0, "Wall should heat up with adiabatic inner BC. Mean: " + T_mean);
  }

  @Test
  void testTemperatureProfileMonotonic() {
    // For unidirectional heat flow from outside, profile should be monotonic
    // (outer hotter than inner)
    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 500; i++) {
      wallModel.advanceTimeStep(0.01, 300.0, 100.0, 700.0, 200.0);
    }

    double[] profile = wallModel.getTemperatureProfile();

    // Profile should be increasing from inside (index 0) to outside (last index)
    for (int i = 1; i < profile.length; i++) {
      assertTrue(profile[i] >= profile[i - 1] - 0.5,
          "Temperature profile should be monotonically increasing from inside to outside");
    }
  }

  @Test
  void testCompositeWall() {
    // Create composite wall (e.g., Type III vessel with liner)
    // Inner layer: HDPE liner, k = 0.5 W/(m*K), 5mm thick
    // Outer layer: Carbon fiber composite, k = 5 W/(m*K), 20mm thick
    TransientWallHeatTransfer compositeWall =
        new TransientWallHeatTransfer(0.005, 0.5, 950.0, 2000.0, // Inner layer (HDPE): thickness,
                                                                 // k, rho, Cp
            0.020, 5.0, 1600.0, 1000.0, // Outer layer (CFRP): thickness, k, rho, Cp
            293.0, // Initial temperature
            20); // Total nodes

    // Fire scenario: cold gas inside, hot fire outside
    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 1000; i++) {
      compositeWall.advanceTimeStep(0.01, 350.0, 50.0, 800.0, 200.0);
    }

    double T_inner = compositeWall.getInnerWallTemperature();
    double T_outer = compositeWall.getOuterWallTemperature();

    assertTrue(T_outer > T_inner, "Outer should be hotter than inner for fire case. Inner: "
        + T_inner + ", Outer: " + T_outer);
    assertTrue(T_inner < 600.0, "Liner should provide thermal protection. Inner: " + T_inner);
  }

  @Test
  void testThermalPenetration() {
    // Steel: alpha = k / (rho * Cp) = 50 / (7800 * 500) = 1.28e-5 m²/s
    // Test that heat penetrates from outer to inner surface over time

    TransientWallHeatTransfer thickWall =
        new TransientWallHeatTransfer(0.010, 50.0, 7800.0, 500.0, 300.0, 20);

    // Apply heat to outer surface
    // advanceTimeStep(dt, innerFluidTemp, innerH, outerTemp, outerH)
    for (int i = 0; i < 500; i++) {
      thickWall.advanceTimeStep(0.01, 300.0, 0.0, 700.0, 500.0);
    }

    double T_inner = thickWall.getInnerWallTemperature();
    double T_outer = thickWall.getOuterWallTemperature();

    // With heat from outside and adiabatic inside, outer should be hotter
    assertTrue(T_outer > T_inner, "Outer should be hotter than inner during heating. Outer: "
        + T_outer + ", Inner: " + T_inner);
    // Wall should be heating up
    assertTrue(thickWall.getMeanWallTemperature() > 300.0,
        "Wall should be heating up from initial 300K");
  }
}
