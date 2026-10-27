package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction.WallFrictionResult;

/**
 * Native-force, analytical and passivity checks for the coupled frozen wall/interface source.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ConservativeWallFrictionTest {
  /** Every configured wall closure must retain its native phase or mixture driving velocity. */
  @Test
  void nativeResistanceMetadataReproducesEveryRegimeForceAndZeroDriver() {
    WallFriction friction = new WallFriction();
    for (FlowRegime regime : FlowRegime.values()) {
      for (double[] velocity : new double[][] {{3.0, 1.0}, {3.0, -1.0}, {-3.0, 1.0}, {-3.0, -1.0}, {0.0, 2.0},
          {2.0, 0.0}, {0.0, 0.0}, {3.0, -7.0}}) {
        WallFrictionResult wall = friction.calculate(regime, velocity[0], velocity[1], 50.0, 800.0, 1e-5, 0.002, 0.3,
            0.3, 1e-5);
        assertNativeForce(wall, velocity[0], velocity[1], 0.3);
      }
    }
  }

  /** Blending and the existing liquid-wetted slug remapping must carry the same native coefficient metadata. */
  @Test
  void blendedAndLiquidWettedSlugForcesRetainTheirDrivingVelocities() throws Exception {
    TwoFluidSection section = section();
    Map<FlowRegime, Double> weights = new EnumMap<>(FlowRegime.class);
    weights.put(FlowRegime.SLUG, 0.4);
    weights.put(FlowRegime.STRATIFIED_SMOOTH, 0.6);
    section.setRegimeWeights(weights);
    Method method = TwoFluidConservationEquations.class.getDeclaredMethod("calculateWallFriction",
        TwoFluidSection.class, Map.class);
    method.setAccessible(true);
    for (boolean shared : new boolean[] {false, true}) {
      TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
      equations.setSharedSlugForceBalanceEnabled(shared);
      WallFrictionResult wall = (WallFrictionResult) method.invoke(equations, section, weights);
      assertNativeForce(wall, section.getGasVelocity(), section.getLiquidVelocity(), section.getLiquidHoldup());
    }
  }

  /** A hand-solved coupled constant-resistance system provides an independent algebraic reference. */
  @Test
  void jointSourceMatchesAnalyticalTwoByTwoAndExternalWallImpulse() {
    WallFrictionResult wall = new WallFrictionResult();
    wall.gasWallResistance = 2.0;
    wall.liquidWallResistance = 1.0;
    double[] after = solve(new double[] {2.0, 3.0}, new double[] {8.0, -3.0}, wall, 0.3, 4.0, 0.5);
    double gasVelocity = 38.0 / 23.5;
    double liquidVelocity = 1.0 / 23.5;
    assertEquals(2.0 * gasVelocity, after[0], 1e-14);
    assertEquals(3.0 * liquidVelocity, after[1], 1e-14);
    assertEquals(-0.5 * (2.0 * gasVelocity + liquidVelocity), after[0] + after[1] - 5.0, 1e-14);
  }

  /** A native mixture wall may accelerate one falling phase while dissipating the combined kinetic energy. */
  @Test
  void mixtureWallIsRankOneRatherThanIndependentVelocityDamping() {
    WallFrictionResult wall = new WallFrictionResult();
    wall.gasMixtureResistance = 0.8;
    wall.liquidMixtureResistance = 0.2;
    double[] after = solve(new double[] {1.0, 4.0}, new double[] {2.0, -0.8}, wall, 0.2, 0.0, 1.0);
    double impulse = 1.56 / 1.65;
    assertEquals(2.0 - 0.8 * impulse, after[0], 1e-14);
    assertEquals(-0.8 - 0.2 * impulse, after[1], 1e-14);
    assertTrue(after[1] / 4.0 < -0.2, "The configured mixture law accelerates the falling liquid");
    assertTrue(kinetic(new double[] {1.0, 4.0}, after) < kinetic(new double[] {1.0, 4.0}, new double[] {2.0, -0.8}));
  }

  /** Stiff trace limits must not subtract two nearly equal squared interfacial resistances. */
  @Test
  void extremeTraceAndSimultaneouslySmallInertiasRetainTheConservativeLimit() {
    for (double mass : new double[] {1e-10, 1e-30, 1e-100, 1e-200}) {
      double[] masses = {119.0, mass};
      double[] after = solve(masses, new double[] {2380.0, 0.0}, new WallFrictionResult(), 1e-12, 1.0, 1.0);
      assertTrue(after[1] > 0.0);
      assertTrue(after[1] / mass <= 20.0);
      assertEquals(2380.0, after[0] + after[1], 0.0);
    }
    double[] after = solve(new double[] {1e-200, 1e-200}, new double[] {5e-200, -1e-200}, new WallFrictionResult(), 0.5,
        1.0, 1.0);
    assertEquals(2.0, after[0] / 1e-200, 1e-14);
    assertEquals(2.0, after[1] / 1e-200, 1e-14);
  }

  /** Interfacial dissipation must not conceal a native bubble-wall closure adding mechanical energy. */
  @Test
  void activeBubbleWallStateRejectsEvenWhenTotalKineticEnergyWouldFall() {
    WallFrictionResult wall = new WallFrictionResult();
    wall.liquidMixtureResistance = 1.0;
    assertThrows(IllegalStateException.class,
        () -> solve(new double[] {1.0, 4.0}, new double[] {2.0, -4.0}, wall, 0.2, 0.5, 1.0));
  }

  /** Mean and slip decay have independent analytical eigenmodes for equal linear resistances. */
  @Test
  void frozenSourceConvergesToAnalyticalMeanAndSlipDecayUnderTimeRefinement() {
    double exactGas = Math.exp(-0.4) + 2.0 * Math.exp(-1.6);
    double exactLiquid = Math.exp(-0.4) - 2.0 * Math.exp(-1.6);
    double previousError = Double.POSITIVE_INFINITY;
    for (int steps : new int[] {2, 4, 8, 16}) {
      WallFrictionResult wall = new WallFrictionResult();
      wall.gasWallResistance = 2.0;
      wall.liquidWallResistance = 2.0;
      double[] momenta = {3.0, -1.0};
      for (int step = 0; step < steps; step++) {
        momenta = solve(new double[] {1.0, 1.0}, momenta, wall, 0.5, 3.0, 0.2 / steps);
      }
      double error = Math.abs(momenta[0] - exactGas) + Math.abs(momenta[1] - exactLiquid);
      assertTrue(error < 0.7 * previousError);
      previousError = error;
    }
  }

  /**
   * Compare metadata with actual integrated native wall forces.
   *
   * @param wall native wall result
   * @param gasVelocity gas velocity
   * @param liquidVelocity bulk liquid velocity
   * @param alphaL liquid volume fraction
   */
  private static void assertNativeForce(WallFrictionResult wall, double gasVelocity, double liquidVelocity,
      double alphaL) {
    double mixture = (1.0 - alphaL) * gasVelocity + alphaL * liquidVelocity;
    double gas = wall.gasWallResistance * gasVelocity + wall.gasMixtureResistance * mixture;
    double liquid = wall.liquidWallResistance * liquidVelocity + wall.liquidMixtureResistance * mixture;
    assertEquals(wall.gasWallForcePerLength, gas, 2e-13 * Math.max(1.0, Math.abs(wall.gasWallForcePerLength)));
    assertEquals(wall.liquidWallForcePerLength, liquid, 2e-13 * Math.max(1.0, Math.abs(wall.liquidWallForcePerLength)));
  }

  /**
   * Invoke the actual production source algebra on one gas/bulk-liquid cell.
   *
   * @param masses gas and liquid mass per length
   * @param momenta gas and liquid momentum per length
   * @param wall frozen native wall coefficients
   * @param alphaL liquid holdup
   * @param drag interface resistance
   * @param dt interval in seconds
   * @return relaxed momenta
   */
  private static double[] solve(double[] masses, double[] momenta, WallFrictionResult wall, double alphaL, double drag,
      double dt) {
    return TwoFluidConservationEquations.solveConservativeBulkFriction(masses, momenta, wall, alphaL, drag, dt);
  }

  /**
   * Compute phase kinetic energy independently from the source implementation.
   *
   * @param masses conservative masses
   * @param momenta conservative momenta
   * @return kinetic energy per length
   */
  private static double kinetic(double[] masses, double[] momenta) {
    return 0.5 * (momenta[0] * momenta[0] / masses[0] + momenta[1] * momenta[1] / masses[1]);
  }

  /** @return a counterflowing two-phase cell with a blended slug/stratified closure */
  private static TwoFluidSection section() {
    TwoFluidSection section = new TwoFluidSection(5.0, 10.0, 0.3, 0.0);
    section.setGasDensity(50.0);
    section.setLiquidDensity(800.0);
    section.setOilDensity(800.0);
    section.setWaterDensity(1000.0);
    section.setGasHoldup(0.7);
    section.setLiquidHoldup(0.3);
    section.setOilHoldup(0.3);
    section.setWaterCut(0.0);
    section.setGasVelocity(3.0);
    section.setLiquidVelocity(-1.0);
    section.setOilVelocity(-1.0);
    section.setGasViscosity(1e-5);
    section.setLiquidViscosity(0.002);
    section.setSurfaceTension(0.02);
    section.updateConservativeVariables();
    section.updateStratifiedGeometry();
    section.setFlowRegime(FlowRegime.SLUG);
    return section;
  }
}
