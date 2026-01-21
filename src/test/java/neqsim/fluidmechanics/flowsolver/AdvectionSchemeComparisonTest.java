package neqsim.fluidmechanics.flowsolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests comparing different advection schemes for numerical dispersion.
 * 
 * <p>
 * This test suite validates that higher-order schemes reduce numerical dispersion compared to
 * first-order upwind, while TVD schemes maintain monotonicity.
 * </p>
 *
 * @author ESOL
 */
public class AdvectionSchemeComparisonTest {
  private SystemInterface testFluidMethane;
  private SystemInterface testFluidNitrogen;
  private double pipeLength = 100.0; // meters
  private double pipeDiameter = 0.1; // meters

  @BeforeEach
  void setUp() {
    // Create methane-rich gas
    testFluidMethane = new SystemSrkEos(280.0, 50.0);
    testFluidMethane.addComponent("methane", 0.95);
    testFluidMethane.addComponent("ethane", 0.04);
    testFluidMethane.addComponent("nitrogen", 0.01);
    testFluidMethane.createDatabase(true);
    testFluidMethane.setMixingRule("classic");
    testFluidMethane.init(0);
    testFluidMethane.init(1);

    // Create nitrogen-rich gas
    testFluidNitrogen = new SystemSrkEos(280.0, 50.0);
    testFluidNitrogen.addComponent("methane", 0.05);
    testFluidNitrogen.addComponent("ethane", 0.01);
    testFluidNitrogen.addComponent("nitrogen", 0.94);
    testFluidNitrogen.createDatabase(true);
    testFluidNitrogen.setMixingRule("classic");
    testFluidNitrogen.init(0);
    testFluidNitrogen.init(1);
  }

  @Test
  @DisplayName("AdvectionScheme enum should have correct properties")
  void testAdvectionSchemeProperties() {
    // First-order upwind
    assertEquals(1, AdvectionScheme.FIRST_ORDER_UPWIND.getOrder());
    assertEquals(1.0, AdvectionScheme.FIRST_ORDER_UPWIND.getDispersionReductionFactor());
    assertEquals(false, AdvectionScheme.FIRST_ORDER_UPWIND.usesTVD());

    // TVD Van Leer
    assertEquals(2, AdvectionScheme.TVD_VAN_LEER.getOrder());
    assertTrue(AdvectionScheme.TVD_VAN_LEER.usesTVD());
    assertTrue(AdvectionScheme.TVD_VAN_LEER.getDispersionReductionFactor() < 0.5);

    // QUICK
    assertEquals(3, AdvectionScheme.QUICK.getOrder());
    assertEquals(false, AdvectionScheme.QUICK.usesTVD());
    assertTrue(AdvectionScheme.QUICK.getDispersionReductionFactor() < 0.1);

    // TVD Superbee should be least diffusive TVD
    assertTrue(
        AdvectionScheme.TVD_SUPERBEE.getDispersionReductionFactor() < AdvectionScheme.TVD_MINMOD
            .getDispersionReductionFactor());
  }

  @Test
  @DisplayName("FluxLimiter functions should be symmetric and bounded")
  void testFluxLimiterProperties() {
    // Limiter should be 0 for negative r (local extremum)
    assertEquals(0.0, FluxLimiter.minmod(-1.0), 1e-10);
    assertEquals(0.0, FluxLimiter.vanLeer(-1.0), 1e-10);
    assertEquals(0.0, FluxLimiter.superbee(-1.0), 1e-10);
    assertEquals(0.0, FluxLimiter.vanAlbada(-1.0), 1e-10);

    // Limiter should be 1 for r = 1 (smooth linear solution)
    assertEquals(1.0, FluxLimiter.minmod(1.0), 1e-10);
    assertEquals(1.0, FluxLimiter.vanLeer(1.0), 1e-10);
    assertEquals(1.0, FluxLimiter.superbee(1.0), 1e-10);
    assertEquals(1.0, FluxLimiter.vanAlbada(1.0), 1e-10);

    // Superbee should be more aggressive than Minmod
    double r = 2.0;
    assertTrue(FluxLimiter.superbee(r) >= FluxLimiter.minmod(r));
    assertTrue(FluxLimiter.superbee(r) >= FluxLimiter.vanLeer(r));
  }

  @Test
  @DisplayName("Gradient ratio calculation should be correct")
  void testGradientRatioCalculation() {
    // Constant field: r should be 1
    double r1 = FluxLimiter.gradientRatio(1.0, 1.0, 1.0);
    assertEquals(1.0, r1, 1e-10);

    // Linear increasing: r should be 1
    double r2 = FluxLimiter.gradientRatio(0.0, 1.0, 2.0);
    assertEquals(1.0, r2, 1e-10);

    // Local minimum: r should be negative
    double r3 = FluxLimiter.gradientRatio(0.5, 0.0, 0.5);
    assertTrue(r3 < 0);

    // Sharp front (step): r should be very small or very large
    double r4 = FluxLimiter.gradientRatio(0.0, 0.0, 1.0);
    assertTrue(r4 <= 0 || Double.isInfinite(r4));
  }

  @Test
  @DisplayName("FlowSystem should accept and return advection scheme")
  void testFlowSystemAdvectionScheme() {
    PipeFlowSystem pipeSystem = new PipeFlowSystem();

    // Default should be first-order upwind
    assertEquals(AdvectionScheme.FIRST_ORDER_UPWIND, pipeSystem.getAdvectionScheme());

    // Should be able to set different schemes
    pipeSystem.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
    assertEquals(AdvectionScheme.TVD_VAN_LEER, pipeSystem.getAdvectionScheme());

    pipeSystem.setAdvectionScheme(AdvectionScheme.QUICK);
    assertEquals(AdvectionScheme.QUICK, pipeSystem.getAdvectionScheme());
  }

  @Test
  @DisplayName("Calculate theoretical dispersion for different schemes")
  void testTheoreticalDispersion() {
    // Test parameters
    double velocity = 5.0; // m/s
    double dx = 1.0; // m
    double CFL = 0.5;

    // First-order upwind dispersion
    double D_upwind = (velocity * dx / 2.0) * (1.0 - CFL);
    assertEquals(1.25, D_upwind, 0.01); // 2.5 * 0.5 = 1.25 m²/s

    // Higher-order schemes should reduce this
    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      double D_scheme = D_upwind * scheme.getDispersionReductionFactor();
      assertTrue(D_scheme <= D_upwind, "Scheme " + scheme + " should not increase dispersion");

      if (scheme != AdvectionScheme.FIRST_ORDER_UPWIND) {
        assertTrue(D_scheme < D_upwind,
            "Higher-order scheme " + scheme + " should reduce dispersion");
      }
    }
  }

  @Test
  @DisplayName("All advection schemes should have valid display names")
  void testAdvectionSchemeDisplayNames() {
    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      assertTrue(scheme.getDisplayName() != null && !scheme.getDisplayName().isEmpty(),
          "Scheme " + scheme + " should have a display name");
      assertTrue(scheme.toString().equals(scheme.getDisplayName()),
          "toString should return display name");
    }
  }

  @Test
  @DisplayName("CFL limits should be appropriate for each scheme")
  void testCFLLimits() {
    // First-order upwind is unconditionally stable
    assertEquals(1.0, AdvectionScheme.FIRST_ORDER_UPWIND.getMaxCFL());

    // Higher-order schemes may have lower CFL limits
    assertTrue(AdvectionScheme.QUICK.getMaxCFL() <= 1.0);
    assertTrue(AdvectionScheme.SECOND_ORDER_UPWIND.getMaxCFL() <= 1.0);

    // TVD schemes should be stable up to CFL = 1
    assertEquals(1.0, AdvectionScheme.TVD_VAN_LEER.getMaxCFL());
    assertEquals(1.0, AdvectionScheme.TVD_MINMOD.getMaxCFL());
  }

  @Test
  @DisplayName("Minmod limiter should be bounded by neighboring values")
  void testMinmodBoundedness() {
    // minmod(a, b) should be between 0 and min(|a|, |b|) with sign
    double a = 2.0;
    double b = 3.0;
    double mm = FluxLimiter.minmod2(a, b);
    assertTrue(mm >= 0 && mm <= Math.min(a, b));

    // Opposite signs should give 0
    double mm2 = FluxLimiter.minmod2(2.0, -3.0);
    assertEquals(0.0, mm2, 1e-10);
  }

  @Test
  @DisplayName("MC limiter should be bounded correctly")
  void testMCLimiterBounds() {
    // MC limiter: φ(r) = max(0, min(2r, (1+r)/2, 2))
    double r = 1.0;
    double mcValue = FluxLimiter.mc(r);

    // At r=1, MC should be 1
    assertEquals(1.0, mcValue, 1e-10);

    // At r=0, MC should be 0
    assertEquals(0.0, FluxLimiter.mc(0.0), 1e-10);

    // For any r > 0, MC should be bounded by [0, 2]
    for (double testR : new double[] {0.1, 0.5, 1.0, 2.0, 5.0, 10.0}) {
      double mcTest = FluxLimiter.mc(testR);
      assertTrue(mcTest >= 0 && mcTest <= 2.0, "MC limiter should be in [0, 2] for r=" + testR);
    }
  }

  @Test
  @DisplayName("TVD schemes should reduce front spreading")
  void testTVDReducesFrontSpreading() {
    // Analytical estimate of front width after transport
    double pipeLength = 100.0; // m
    double velocity = 5.0; // m/s
    double dx = 1.0; // m
    double transportTime = pipeLength / velocity; // seconds

    // First-order upwind front width (4-sigma of diffusion)
    double D_upwind = velocity * dx / 2.0; // at CFL << 1
    double sigma_upwind = Math.sqrt(2.0 * D_upwind * transportTime);
    double frontWidth_upwind = 4.0 * sigma_upwind;

    // TVD Van Leer should reduce this significantly
    double D_tvd = D_upwind * AdvectionScheme.TVD_VAN_LEER.getDispersionReductionFactor();
    double sigma_tvd = Math.sqrt(2.0 * D_tvd * transportTime);
    double frontWidth_tvd = 4.0 * sigma_tvd;

    assertTrue(frontWidth_tvd < frontWidth_upwind * 0.5,
        "TVD should reduce front width by more than 50%");

    // Report for documentation
    System.out.println("=== Front Width Comparison (100m pipe, 5 m/s, dx=1m) ===");
    System.out
        .println("First-Order Upwind front width: " + String.format("%.1f m", frontWidth_upwind));
    System.out.println("TVD Van Leer front width: " + String.format("%.1f m", frontWidth_tvd));
    System.out
        .println("Reduction factor: " + String.format("%.1f×", frontWidth_upwind / frontWidth_tvd));
  }
}
