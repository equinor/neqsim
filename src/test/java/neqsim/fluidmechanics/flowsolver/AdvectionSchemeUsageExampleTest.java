package neqsim.fluidmechanics.flowsolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Example demonstrating how to use different advection schemes for compositional tracking.
 *
 * <p>
 * This test serves as documentation and shows practical usage of the advection scheme API.
 * </p>
 *
 * @author ESOL
 */
public class AdvectionSchemeUsageExampleTest {
  private static final Logger logger = LogManager.getLogger(AdvectionSchemeUsageExampleTest.class);
  private SystemInterface naturalGas;
  private SystemInterface nitrogen;

  @BeforeEach
  void setUp() {
    // Create natural gas composition
    naturalGas = new SystemSrkEos(280.0, 50.0);
    naturalGas.addComponent("methane", 0.90);
    naturalGas.addComponent("ethane", 0.05);
    naturalGas.addComponent("propane", 0.03);
    naturalGas.addComponent("nitrogen", 0.02);
    naturalGas.createDatabase(true);
    naturalGas.setMixingRule("classic");
    naturalGas.init(0);
    naturalGas.init(1);

    // Create nitrogen for gas switching
    nitrogen = new SystemSrkEos(280.0, 50.0);
    nitrogen.addComponent("methane", 0.02);
    nitrogen.addComponent("ethane", 0.01);
    nitrogen.addComponent("propane", 0.00);
    nitrogen.addComponent("nitrogen", 0.97);
    nitrogen.createDatabase(true);
    nitrogen.setMixingRule("classic");
    nitrogen.init(0);
    nitrogen.init(1);
  }

  @Test
  @DisplayName("Example: Configure advection scheme for pipeline simulation")
  void demonstrateAdvectionSchemeConfiguration() {
    // Create a pipe flow system
    PipeFlowSystem pipe = new PipeFlowSystem();

    // Print available schemes and their properties
    logger.info("=== Available Advection Schemes ===\n");

    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      logger.info(scheme.getDisplayName() + ":");
      logger.info("  Order of accuracy: " + scheme.getOrder());
      logger.info("  Max CFL: " + scheme.getMaxCFL());
      logger.info("  Uses TVD limiter: " + scheme.usesTVD());
      logger.info("  Dispersion reduction: "
          + String.format("%.0f×", 1.0 / scheme.getDispersionReductionFactor()));

    }

    // Example: Setting different schemes
    logger.info("=== Scheme Selection Examples ===\n");

    // Default scheme (most stable, highest dispersion)
    pipe.setAdvectionScheme(AdvectionScheme.FIRST_ORDER_UPWIND);
    logger.info("1. FIRST_ORDER_UPWIND (default)");
    logger.info("   Best for: Initial testing, very coarse grids, stability focus");
    logger.info("   Dispersion: High (front spreads ~40m over 100m pipe with 1m grid)\n");

    // Recommended scheme for compositional tracking
    pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
    logger.info("2. TVD_VAN_LEER (recommended for compositional tracking)");
    logger.info("   Best for: General compositional tracking, gas switching scenarios");
    logger.info("   Dispersion: ~7× lower than first-order upwind");
    logger.info("   Properties: Monotone, never creates new extrema, smooth limiter\n");

    // Most accurate for sharp fronts
    pipe.setAdvectionScheme(AdvectionScheme.TVD_SUPERBEE);
    logger.info("3. TVD_SUPERBEE (sharpest front preservation)");
    logger.info("   Best for: Sharp composition fronts, phase transitions");
    logger.info("   Dispersion: ~12× lower than first-order upwind");
    logger.info("   Caution: May be too compressive for smooth solutions\n");

    // Most conservative TVD scheme
    pipe.setAdvectionScheme(AdvectionScheme.TVD_MINMOD);
    logger.info("4. TVD_MINMOD (most conservative TVD)");
    logger.info("   Best for: Problems with strong shocks, maximum stability with TVD");
    logger.info("   Dispersion: ~3× lower than first-order upwind\n");

    // High accuracy for smooth solutions
    pipe.setAdvectionScheme(AdvectionScheme.QUICK);
    logger.info("5. QUICK (highest formal accuracy)");
    logger.info("   Best for: Smooth composition profiles, high accuracy needs");
    logger.info("   Dispersion: ~20× lower than first-order upwind");
    logger.info("   Caution: May oscillate near discontinuities (not monotone)\n");
  }

  @Test
  @DisplayName("Example: Choosing scheme based on application")
  void demonstrateSchemeSelectionGuidelines() {
    logger.info("=== Scheme Selection Guidelines ===\n");

    logger.info("SCENARIO: Gas switching in export pipeline");
    logger.info("  Problem: Tracking natural gas to nitrogen interface");
    logger.info("  Requirements: Sharp front, no oscillations");
    logger.info("  Recommended: TVD_VAN_LEER or TVD_SUPERBEE");


    logger.info("SCENARIO: Pipeline commissioning with gradually changing composition");
    logger.info("  Problem: Smooth composition gradient transport");
    logger.info("  Requirements: High accuracy, smooth profile");
    logger.info("  Recommended: QUICK or SECOND_ORDER_UPWIND");


    logger.info("SCENARIO: Rapid transient with shocks");
    logger.info("  Problem: Fast pressure/composition changes");
    logger.info("  Requirements: Stability first, then accuracy");
    logger.info("  Recommended: TVD_MINMOD or TVD_VAN_ALBADA");


    logger.info("SCENARIO: Coarse grid for quick estimates");
    logger.info("  Problem: Large grid spacing, few nodes");
    logger.info("  Requirements: Maximum stability");
    logger.info("  Recommended: FIRST_ORDER_UPWIND");


    logger.info("=== Grid Sizing with Higher-Order Schemes ===\n");

    // Calculate required grid for target front width
    double targetFrontWidth = 50.0; // meters
    double pipeLength = 50000.0; // 50 km
    double velocity = 10.0; // m/s
    double CFL = 0.8;

    logger.info("Target front width: " + targetFrontWidth + " m");
    logger.info("Pipe length: " + (pipeLength / 1000) + " km");
    logger.info("Velocity: " + velocity + " m/s");


    for (AdvectionScheme scheme : new AdvectionScheme[] {AdvectionScheme.FIRST_ORDER_UPWIND,
        AdvectionScheme.TVD_VAN_LEER, AdvectionScheme.TVD_SUPERBEE}) {
      // sigma = sqrt(2 * D_num * t) where D_num = (v*dx/2)*(1-CFL)*reductionFactor
      // sigma_target = sqrt(dx * L * (1-CFL) * reductionFactor)
      // dx = sigma_target^2 / (L * (1-CFL) * reductionFactor)
      double reductionFactor = scheme.getDispersionReductionFactor();
      double maxDx =
          targetFrontWidth * targetFrontWidth / (pipeLength * (1 - CFL) * reductionFactor);
      int minNodes = (int) Math.ceil(pipeLength / maxDx);

      logger.info(scheme.getDisplayName() + ":");
      logger.info("  Max grid spacing: " + String.format("%.1f m", maxDx));
      logger.info("  Min nodes needed: " + minNodes);

    }
  }

  @Test
  @DisplayName("Example: Flux limiter behavior")
  void demonstrateFluxLimiterBehavior() {
    logger.info("=== Flux Limiter Behavior ===\n");

    logger.info("The gradient ratio r = (φ_i - φ_{i-1}) / (φ_{i+1} - φ_i)");

    logger.info("r < 0: Local extremum (minimum or maximum)");
    logger.info("r = 0: Flat upstream profile");
    logger.info("r = 1: Linear/smooth profile");
    logger.info("r > 1: Steepening front");
    logger.info("r → ∞: Approaching discontinuity from upstream");


    logger.info("Limiter values φ(r) for different r:");
    logger.info(String.format("%-8s %-10s %-10s %-10s %-10s", "r", "Minmod", "Van Leer", "Superbee",
        "Van Albada"));
    logger.info("---------------------------------------------------");

    for (double r : new double[] {-1.0, 0.0, 0.5, 1.0, 2.0, 5.0, 10.0}) {
      System.out
          .println(String.format("%-8.1f %-10.3f %-10.3f %-10.3f %-10.3f", r, FluxLimiter.minmod(r),
              FluxLimiter.vanLeer(r), FluxLimiter.superbee(r), FluxLimiter.vanAlbada(r)));
    }


    logger.info("Key observations:");
    logger.info("- All limiters = 0 for r < 0 (preserves monotonicity)");
    logger.info("- All limiters = 1 for r = 1 (second-order on smooth solutions)");
    logger.info("- Superbee is most aggressive (least diffusive)");
    logger.info("- Minmod is most conservative (most diffusive of TVD)");
  }
}
