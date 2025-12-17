package neqsim.fluidmechanics.flowsolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem.PipeFlowSystem;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
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
    System.out.println("=== Available Advection Schemes ===\n");

    for (AdvectionScheme scheme : AdvectionScheme.values()) {
      System.out.println(scheme.getDisplayName() + ":");
      System.out.println("  Order of accuracy: " + scheme.getOrder());
      System.out.println("  Max CFL: " + scheme.getMaxCFL());
      System.out.println("  Uses TVD limiter: " + scheme.usesTVD());
      System.out.println("  Dispersion reduction: "
          + String.format("%.0f×", 1.0 / scheme.getDispersionReductionFactor()));
      System.out.println();
    }

    // Example: Setting different schemes
    System.out.println("=== Scheme Selection Examples ===\n");

    // Default scheme (most stable, highest dispersion)
    pipe.setAdvectionScheme(AdvectionScheme.FIRST_ORDER_UPWIND);
    System.out.println("1. FIRST_ORDER_UPWIND (default)");
    System.out.println("   Best for: Initial testing, very coarse grids, stability focus");
    System.out.println("   Dispersion: High (front spreads ~40m over 100m pipe with 1m grid)\n");

    // Recommended scheme for compositional tracking
    pipe.setAdvectionScheme(AdvectionScheme.TVD_VAN_LEER);
    System.out.println("2. TVD_VAN_LEER (recommended for compositional tracking)");
    System.out.println("   Best for: General compositional tracking, gas switching scenarios");
    System.out.println("   Dispersion: ~7× lower than first-order upwind");
    System.out.println("   Properties: Monotone, never creates new extrema, smooth limiter\n");

    // Most accurate for sharp fronts
    pipe.setAdvectionScheme(AdvectionScheme.TVD_SUPERBEE);
    System.out.println("3. TVD_SUPERBEE (sharpest front preservation)");
    System.out.println("   Best for: Sharp composition fronts, phase transitions");
    System.out.println("   Dispersion: ~12× lower than first-order upwind");
    System.out.println("   Caution: May be too compressive for smooth solutions\n");

    // Most conservative TVD scheme
    pipe.setAdvectionScheme(AdvectionScheme.TVD_MINMOD);
    System.out.println("4. TVD_MINMOD (most conservative TVD)");
    System.out.println("   Best for: Problems with strong shocks, maximum stability with TVD");
    System.out.println("   Dispersion: ~3× lower than first-order upwind\n");

    // High accuracy for smooth solutions
    pipe.setAdvectionScheme(AdvectionScheme.QUICK);
    System.out.println("5. QUICK (highest formal accuracy)");
    System.out.println("   Best for: Smooth composition profiles, high accuracy needs");
    System.out.println("   Dispersion: ~20× lower than first-order upwind");
    System.out.println("   Caution: May oscillate near discontinuities (not monotone)\n");
  }

  @Test
  @DisplayName("Example: Choosing scheme based on application")
  void demonstrateSchemeSelectionGuidelines() {
    System.out.println("=== Scheme Selection Guidelines ===\n");

    System.out.println("SCENARIO: Gas switching in export pipeline");
    System.out.println("  Problem: Tracking natural gas to nitrogen interface");
    System.out.println("  Requirements: Sharp front, no oscillations");
    System.out.println("  Recommended: TVD_VAN_LEER or TVD_SUPERBEE");
    System.out.println();

    System.out.println("SCENARIO: Pipeline commissioning with gradually changing composition");
    System.out.println("  Problem: Smooth composition gradient transport");
    System.out.println("  Requirements: High accuracy, smooth profile");
    System.out.println("  Recommended: QUICK or SECOND_ORDER_UPWIND");
    System.out.println();

    System.out.println("SCENARIO: Rapid transient with shocks");
    System.out.println("  Problem: Fast pressure/composition changes");
    System.out.println("  Requirements: Stability first, then accuracy");
    System.out.println("  Recommended: TVD_MINMOD or TVD_VAN_ALBADA");
    System.out.println();

    System.out.println("SCENARIO: Coarse grid for quick estimates");
    System.out.println("  Problem: Large grid spacing, few nodes");
    System.out.println("  Requirements: Maximum stability");
    System.out.println("  Recommended: FIRST_ORDER_UPWIND");
    System.out.println();

    System.out.println("=== Grid Sizing with Higher-Order Schemes ===\n");

    // Calculate required grid for target front width
    double targetFrontWidth = 50.0; // meters
    double pipeLength = 50000.0; // 50 km
    double velocity = 10.0; // m/s
    double CFL = 0.8;

    System.out.println("Target front width: " + targetFrontWidth + " m");
    System.out.println("Pipe length: " + (pipeLength / 1000) + " km");
    System.out.println("Velocity: " + velocity + " m/s");
    System.out.println();

    for (AdvectionScheme scheme : new AdvectionScheme[] {AdvectionScheme.FIRST_ORDER_UPWIND,
        AdvectionScheme.TVD_VAN_LEER, AdvectionScheme.TVD_SUPERBEE}) {
      // sigma = sqrt(2 * D_num * t) where D_num = (v*dx/2)*(1-CFL)*reductionFactor
      // sigma_target = sqrt(dx * L * (1-CFL) * reductionFactor)
      // dx = sigma_target^2 / (L * (1-CFL) * reductionFactor)
      double reductionFactor = scheme.getDispersionReductionFactor();
      double maxDx =
          targetFrontWidth * targetFrontWidth / (pipeLength * (1 - CFL) * reductionFactor);
      int minNodes = (int) Math.ceil(pipeLength / maxDx);

      System.out.println(scheme.getDisplayName() + ":");
      System.out.println("  Max grid spacing: " + String.format("%.1f m", maxDx));
      System.out.println("  Min nodes needed: " + minNodes);
      System.out.println();
    }
  }

  @Test
  @DisplayName("Example: Flux limiter behavior")
  void demonstrateFluxLimiterBehavior() {
    System.out.println("=== Flux Limiter Behavior ===\n");

    System.out.println("The gradient ratio r = (φ_i - φ_{i-1}) / (φ_{i+1} - φ_i)");
    System.out.println();
    System.out.println("r < 0: Local extremum (minimum or maximum)");
    System.out.println("r = 0: Flat upstream profile");
    System.out.println("r = 1: Linear/smooth profile");
    System.out.println("r > 1: Steepening front");
    System.out.println("r → ∞: Approaching discontinuity from upstream");
    System.out.println();

    System.out.println("Limiter values φ(r) for different r:");
    System.out.println(String.format("%-8s %-10s %-10s %-10s %-10s", "r", "Minmod", "Van Leer",
        "Superbee", "Van Albada"));
    System.out.println("---------------------------------------------------");

    for (double r : new double[] {-1.0, 0.0, 0.5, 1.0, 2.0, 5.0, 10.0}) {
      System.out
          .println(String.format("%-8.1f %-10.3f %-10.3f %-10.3f %-10.3f", r, FluxLimiter.minmod(r),
              FluxLimiter.vanLeer(r), FluxLimiter.superbee(r), FluxLimiter.vanAlbada(r)));
    }

    System.out.println();
    System.out.println("Key observations:");
    System.out.println("- All limiters = 0 for r < 0 (preserves monotonicity)");
    System.out.println("- All limiters = 1 for r = 1 (second-order on smooth solutions)");
    System.out.println("- Superbee is most aggressive (least diffusive)");
    System.out.println("- Minmod is most conservative (most diffusive of TVD)");
  }
}
