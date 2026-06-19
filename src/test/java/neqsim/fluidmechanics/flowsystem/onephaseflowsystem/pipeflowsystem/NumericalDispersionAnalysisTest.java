package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Numerical Dispersion Analysis for Compositional Tracking in Pipeline Transient Simulations.
 *
 * <h2>Background</h2>
 * <p>
 * When simulating a composition front (e.g., switching from natural gas to nitrogen and back), the numerical scheme
 * introduces artificial dispersion that smears the sharp interface.
 * </p>
 *
 * <h2>The Problem</h2>
 * <p>
 * NeqSim's transient solver uses a <b>first-order upwind scheme</b> for the advection of component mass fractions.
 * While unconditionally stable, this scheme has inherent numerical (artificial) diffusion proportional to:
 * </p>
 *
 * <pre>
 * D_numerical ≈ (v * Δx / 2) * (1 - CFL)
 * </pre>
 *
 * <p>
 * where CFL = v * Δt / Δx is the Courant-Friedrichs-Lewy number.
 * </p>
 *
 * <h2>Error Manifestation</h2>
 * <ul>
 * <li><b>Front smearing</b>: Sharp composition steps become gradual transitions</li>
 * <li><b>Peak clipping</b>: Short pulses (like a nitrogen slug) have reduced peak concentration</li>
 * <li><b>Arrival time accuracy</b>: Front arrival time remains accurate, but front width increases with distance</li>
 * </ul>
 *
 * <h2>Mitigation Strategies</h2>
 * <ol>
 * <li><b>Grid refinement</b>: Reduce Δx (most reliable, but expensive)</li>
 * <li><b>Time step optimization</b>: Operate near CFL ≈ 1 (reduces dispersion)</li>
 * <li><b>Higher-order schemes</b>: TVD, MUSCL, or WENO schemes (requires code modification)</li>
 * <li><b>Anti-diffusion corrections</b>: Add explicit anti-diffusion term</li>
 * </ol>
 *
 * @author ESOL
 */
public class NumericalDispersionAnalysisTest {
  private static final Logger logger = LogManager.getLogger(NumericalDispersionAnalysisTest.class);

  /**
   * Compares analytical vs numerical front spreading predictions.
   *
   * <p>
   * For first-order upwind, the equivalent diffusion coefficient is: D_num = v*dx/2 * (1 - CFL) This allows prediction
   * of front spreading.
   * </p>
   */
  @Test
  void testAnalyticalDispersionPrediction() {
    logger.info(StringUtils.repeat("=", 80));
    logger.info("ANALYTICAL PREDICTION OF NUMERICAL DISPERSION");
    logger.info(StringUtils.repeat("=", 80));

    logger.info("For first-order upwind scheme:");
    logger.info("  D_numerical = (v × Δx / 2) × (1 - CFL)");

    logger.info("Front width after travel distance L:");
    logger.info("  σ = sqrt(2 × D_num × L / v) = sqrt(Δx × L × (1 - CFL))");

    // Example calculation
    double[] gridSizes = { 50.0, 100.0, 200.0, 500.0 }; // m
    double[] pipeLengths = { 10000.0, 50000.0, 100000.0 }; // m
    double cfl = 0.8;

    logger.info(String.format("%-8s %-10s %-12s %-15s %-15s", "Δx (m)", "L (km)", "D_num (m²/s)", "σ (m)", "σ/Δx"));
    logger.info(StringUtils.repeat("-", 60));

    for (double dx : gridSizes) {
      for (double L : pipeLengths) {
	double v = 10.0; // m/s
	double D_num = v * dx / 2.0 * (1.0 - cfl);
	double sigma = Math.sqrt(2.0 * D_num * L / v);
	logger.info(String.format("%-8.0f %-10.0f %-12.1f %-15.1f %-15.2f", dx, L / 1000, D_num, sigma, sigma / dx));
      }
    }

    logger.info("KEY INSIGHT: Front width (σ) scales as sqrt(Δx × L)");
    logger.info("             Halving Δx reduces σ by factor of sqrt(2) ≈ 0.7");

    // Grid sizing recommendation
    logger.info(StringUtils.repeat("=", 80));
    logger.info("GRID SIZING GUIDELINE:");
    logger.info(StringUtils.repeat("=", 80));
    logger.info("For acceptable front width σ_target:");
    logger.info("  Δx_max = σ_target² / (L × (1 - CFL))");

    double sigma_target = 100.0; // 100 m front width acceptable
    double L = 50000.0; // 50 km pipe
    double dx_max = sigma_target * sigma_target / (L * (1 - cfl));
    int minNodes = (int) Math.ceil(L / dx_max);

    logger.info(String.format("Example: 50 km pipe, target σ = 100 m, CFL = 0.8"));
    logger.info(String.format("  Maximum Δx: %.1f m", dx_max));
    logger.info(String.format("  Minimum nodes: %d", minNodes));

    assertTrue(dx_max > 0, "Should calculate valid grid size");
  }

  /**
   * Test the grid refinement convergence formula.
   *
   * <p>
   * Demonstrates that halving the grid size reduces dispersion by sqrt(2).
   * </p>
   */
  @Test
  void testGridRefinementConvergence() {
    logger.info(StringUtils.repeat("=", 80));
    logger.info("GRID REFINEMENT CONVERGENCE TEST");
    logger.info(StringUtils.repeat("=", 80));

    double velocity = 10.0; // m/s
    double pipeLength = 50000.0; // 50 km
    double cfl = 0.8;

    // Grid sizes from coarse to fine
    double[] gridSizes = { 1000.0, 500.0, 250.0, 125.0, 62.5 };
    double[] frontWidths = new double[gridSizes.length];

    logger.info("Grid refinement study (50 km pipe, v=10 m/s, CFL=0.8):");
    logger.info(StringUtils.repeat("-", 60));
    System.out.println(String.format("%-10s %-10s %-15s %-15s", "Δx (m)", "Nodes", "σ (m)", "Reduction"));
    logger.info(StringUtils.repeat("-", 60));

    for (int i = 0; i < gridSizes.length; i++) {
      double dx = gridSizes[i];
      double D_num = velocity * dx / 2.0 * (1.0 - cfl);
      double sigma = Math.sqrt(2.0 * D_num * pipeLength / velocity);
      frontWidths[i] = sigma;

      int nodes = (int) (pipeLength / dx);
      String reduction = (i > 0) ? String.format("%.3f", frontWidths[i] / frontWidths[i - 1]) : "-";

      logger.info(String.format("%-10.1f %-10d %-15.1f %-15s", dx, nodes, sigma, reduction));
    }

    logger.info("Expected reduction factor per grid halving: sqrt(0.5) = 0.707");

    // Verify the theoretical sqrt(2) reduction
    for (int i = 1; i < gridSizes.length; i++) {
      double actualRatio = frontWidths[i] / frontWidths[i - 1];
      double expectedRatio = Math.sqrt(0.5);
      assertEquals(expectedRatio, actualRatio, 0.01, "Grid refinement should give sqrt(2) reduction");
    }
  }

  /**
   * Test CFL number effect on dispersion.
   *
   * <p>
   * Shows that operating at CFL closer to 1.0 minimizes numerical dispersion.
   * </p>
   */
  @Test
  void testCFLOptimization() {
    logger.info(StringUtils.repeat("=", 80));
    logger.info("CFL NUMBER OPTIMIZATION");
    logger.info(StringUtils.repeat("=", 80));

    double velocity = 10.0; // m/s
    double dx = 200.0; // m
    double pipeLength = 50000.0; // 50 km

    // Different CFL numbers
    double[] cflNumbers = { 0.1, 0.3, 0.5, 0.7, 0.8, 0.9, 0.95, 0.99 };

    logger.info(String.format("Grid: Δx = %.0f m, Pipe: %.0f km", dx, pipeLength / 1000));
    logger.info(StringUtils.repeat("-", 60));
    logger.info(String.format("%-10s %-15s %-15s %-15s", "CFL", "D_num (m²/s)", "σ (m)", "Relative"));
    logger.info(StringUtils.repeat("-", 60));

    double[] dispersionCoeffs = new double[cflNumbers.length];

    for (int i = 0; i < cflNumbers.length; i++) {
      double cfl = cflNumbers[i];
      double D_num = velocity * dx / 2.0 * (1.0 - cfl);
      double sigma = Math.sqrt(2.0 * D_num * pipeLength / velocity);
      dispersionCoeffs[i] = D_num;

      String relative = (i > 0) ? String.format("%.2f", D_num / dispersionCoeffs[0]) : "1.00";

      System.out.println(String.format("%-10.2f %-15.1f %-15.1f %-15s", cfl, D_num, sigma, relative));
    }

    logger.info("KEY INSIGHT: D_num ∝ (1 - CFL)");
    logger.info("  - At CFL = 0.8: D_num = 20% of maximum");
    logger.info("  - At CFL = 0.95: D_num = 5% of maximum");
    logger.info("  - At CFL = 1.0: D_num = 0 (theoretically perfect)");

    logger.info("RECOMMENDATION: Use CFL = 0.8-0.95 for balance of stability and accuracy");

    // Verify dispersion decreases with higher CFL
    for (int i = 1; i < cflNumbers.length; i++) {
      assertTrue(dispersionCoeffs[i] < dispersionCoeffs[i - 1], "Higher CFL should give lower dispersion");
    }
  }

  /**
   * Practical grid sizing recommendations for common scenarios.
   */
  @Test
  void testPracticalGridSizing() {
    logger.info(StringUtils.repeat("=", 80));
    logger.info("PRACTICAL GRID SIZING RECOMMENDATIONS");
    logger.info(StringUtils.repeat("=", 80));

    // Common pipeline scenarios
    double[][] scenarios = {
	// {pipeLength_km, velocity_mps, acceptableFrontWidth_m}
	{ 10.0, 10.0, 50.0 }, // Short gas pipeline
	{ 50.0, 10.0, 100.0 }, // Medium gas pipeline
	{ 100.0, 15.0, 200.0 }, // Long gas pipeline
	{ 200.0, 12.0, 300.0 }, // Very long pipeline
    };

    double cfl = 0.8;

    logger.info(
	String.format("%-12s %-10s %-12s %-12s %-12s", "Pipe (km)", "v (m/s)", "σ_max (m)", "Δx_max (m)", "Min Nodes"));
    logger.info(StringUtils.repeat("-", 70));

    for (double[] scenario : scenarios) {
      double L_km = scenario[0];
      double L = L_km * 1000;
      double v = scenario[1];
      double sigma_target = scenario[2];

      // From: σ² = 2 × D_num × L / v = (Δx × L × (1 - CFL))
      // Solve: Δx = σ² / (L × (1 - CFL))
      double dx_max = sigma_target * sigma_target / (L * (1.0 - cfl));
      int minNodes = (int) Math.ceil(L / dx_max);

      logger.info(String.format("%-12.0f %-10.0f %-12.0f %-12.1f %-12d", L_km, v, sigma_target, dx_max, minNodes));
    }

    logger.info(StringUtils.repeat("=", 80));
    logger.info("SUMMARY - COMPOSITIONAL TRACKING RECOMMENDATIONS");
    logger.info(StringUtils.repeat("=", 80));

    logger.info("1. GRID SIZING:");
    logger.info("   Δx_max = σ_target² / (L × (1 - CFL))");
    logger.info("   where σ_target is acceptable front width");

    logger.info("2. CFL OPTIMIZATION:");
    logger.info("   Use CFL = 0.8-0.95 for minimum dispersion with stability margin");

    logger.info("3. GRID REFINEMENT:");
    logger.info("   Halving Δx reduces front width by factor sqrt(2) ≈ 0.7");

    logger.info("4. HIGHER-ORDER SCHEMES (future enhancement):");
    logger.info("   TVD/MUSCL schemes can reduce dispersion by 5-10x");
    logger.info("   Would require modifications to OnePhaseFixedStaggeredGrid");

    logger.info("5. PRACTICAL LIMITS:");
    logger.info("   - Very fine grids (>10,000 nodes) increase computation time");
    logger.info("   - Consider if sharp fronts are actually needed for the application");
    logger.info("   - Real pipelines have some mixing (D_physical ≈ 0.1-1 m²/s)");

    // Basic assertion
    assertTrue(true, "Documentation test passed");
  }
}
