package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedGeometry;
import neqsim.process.equipment.pipeline.twophasepipe.closure.InterfacialFriction;
import neqsim.process.equipment.pipeline.twophasepipe.closure.WallFriction;

/**
 * Quasi-steady momentum balance of a stratified liquid layer under a flowing gas.
 *
 * <p>
 * The pressure gradient is eliminated between the steady gas and liquid momentum balances of stratified flow, which
 * gives the combined momentum equation of Taitel and Dukler (1976), here per unit liquid area:
 * </p>
 *
 * <pre>
 * 0 = -tau_wL S_L / A_L + tau_i S_i (1 / A_L + 1 / A_G) + tau_wG S_G / A_G - (rho_L - rho_G) g d(z + h_L)/dx
 * </pre>
 *
 * <p>
 * with wall shears {@code tau_wk = f_k rho_k u_k |u_k| / 2}, interfacial shear
 * {@code tau_i = f_i rho_G (u_G - u_L) |u_G - u_L| / 2}, and {@code d(z + h_L)/dx} the gradient of the liquid surface
 * elevation (pipe elevation plus liquid level). The last term makes liquid drain downhill and come to rest with a level
 * surface in a sag. Using the combined equation keeps the liquid velocity independent of acoustic pressure
 * fluctuations. Gas and liquid inertia are neglected, which is appropriate for films and pools that respond within
 * seconds while the line state changes over minutes to hours.
 * </p>
 *
 * <p>
 * Geometry and friction closures are the ones used by {@link TwoFluidPipe}: circular-segment geometry from
 * {@link GeometryCalculator}, Fanning wall friction from {@link WallFriction} on the phase hydraulic diameters, and the
 * gas-wall friction factor multiplied by {@link InterfacialFriction#stratifiedWaveEnhancement(double, double, double)}
 * at the interface. The balance is solved for the liquid velocity by bisection; the residual decreases monotonically
 * with liquid velocity.
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class StratifiedLiquidMomentum {
  /** Gravitational acceleration in m/s2. */
  private static final double GRAVITY = 9.81;
  /** Search bound for the liquid velocity in m/s. */
  private static final double MAX_VELOCITY = 50.0;
  /** Geometry closure. */
  private static final GeometryCalculator GEOMETRY = new GeometryCalculator();
  /** Wall-friction closure. */
  private static final WallFriction WALL = new WallFriction();

  /** Utility class; not instantiated. */
  private StratifiedLiquidMomentum() {
  }

  /**
   * Liquid level of a stratified layer.
   *
   * @param holdup liquid holdup (area fraction), 0-1
   * @param diameter pipe inner diameter in m
   * @return liquid level above the pipe invert in m
   */
  public static double liquidLevel(double holdup, double diameter) {
    if (!(holdup > 0.0)) {
      return 0.0;
    }
    return GEOMETRY.calculateFromHoldup(Math.min(holdup, 1.0), diameter).liquidLevel;
  }

  /**
   * Residual of the combined momentum equation, Pa/m (positive accelerates the liquid in +x).
   *
   * @param geometry stratified geometry
   * @param diameter pipe inner diameter in m
   * @param roughness wall roughness in m
   * @param liquidVelocity liquid velocity in m/s
   * @param gasVelocity gas velocity in m/s
   * @param gasDensity gas density in kg/m3
   * @param liquidDensity liquid density in kg/m3
   * @param gasViscosity gas viscosity in Pa s
   * @param liquidViscosity liquid viscosity in Pa s
   * @param surfaceGradient gradient of pipe elevation plus liquid level, d(z + h_L)/dx
   * @return residual in Pa/m
   */
  static double residual(StratifiedGeometry geometry, double diameter, double roughness, double liquidVelocity,
      double gasVelocity, double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity,
      double surfaceGradient) {
    double reLiquid = liquidDensity * Math.abs(liquidVelocity) * geometry.liquidHydraulicDiameter / liquidViscosity;
    double fLiquid = WALL.calcFanningFrictionFactor(reLiquid, roughness,
        Math.max(geometry.liquidHydraulicDiameter, 1e-9));
    double tauWallLiquid = 0.5 * fLiquid * liquidDensity * liquidVelocity * Math.abs(liquidVelocity);
    double reGas = gasDensity * Math.abs(gasVelocity) * geometry.gasHydraulicDiameter / gasViscosity;
    double fGas = WALL.calcFanningFrictionFactor(reGas, roughness, Math.max(geometry.gasHydraulicDiameter, 1e-9));
    double tauWallGas = 0.5 * fGas * gasDensity * gasVelocity * Math.abs(gasVelocity);
    double superficialGas = gasVelocity * (1.0 - geometry.liquidHoldup);
    double fInterface = fGas
        * InterfacialFriction.stratifiedWaveEnhancement(superficialGas, gasDensity, geometry.liquidLevel / diameter);
    double slip = gasVelocity - liquidVelocity;
    double tauInterface = 0.5 * fInterface * gasDensity * slip * Math.abs(slip);
    return -tauWallLiquid * geometry.liquidWettedPerimeter / geometry.liquidArea
        + tauInterface * geometry.interfacialWidth * (1.0 / geometry.liquidArea + 1.0 / geometry.gasArea)
        + tauWallGas * geometry.gasWettedPerimeter / geometry.gasArea
        - (liquidDensity - gasDensity) * GRAVITY * surfaceGradient;
  }

  /**
   * Solves the force balance for the liquid velocity.
   *
   * @param holdup liquid holdup (area fraction), greater than 0 and below 1
   * @param diameter pipe inner diameter in m
   * @param roughness wall roughness in m
   * @param gasVelocity gas velocity in m/s (signed)
   * @param gasDensity gas density in kg/m3
   * @param liquidDensity liquid density in kg/m3
   * @param gasViscosity gas viscosity in Pa s
   * @param liquidViscosity liquid viscosity in Pa s
   * @param surfaceGradient gradient of pipe elevation plus liquid level, d(z + h_L)/dx
   * @return liquid velocity in m/s (signed); 0 for a vanishing layer
   */
  public static double liquidVelocity(double holdup, double diameter, double roughness, double gasVelocity,
      double gasDensity, double liquidDensity, double gasViscosity, double liquidViscosity, double surfaceGradient) {
    if (!(holdup > 1e-12) || holdup >= 1.0) {
      return 0.0;
    }
    StratifiedGeometry geometry = GEOMETRY.calculateFromHoldup(holdup, diameter);
    if (!(geometry.liquidArea > 0.0) || !(geometry.liquidWettedPerimeter > 0.0)) {
      return 0.0;
    }
    double low = -MAX_VELOCITY;
    double high = MAX_VELOCITY;
    double fLow = residual(geometry, diameter, roughness, low, gasVelocity, gasDensity, liquidDensity, gasViscosity,
        liquidViscosity, surfaceGradient);
    double fHigh = residual(geometry, diameter, roughness, high, gasVelocity, gasDensity, liquidDensity, gasViscosity,
        liquidViscosity, surfaceGradient);
    if (fLow <= 0.0) {
      return low;
    }
    if (fHigh >= 0.0) {
      return high;
    }
    for (int i = 0; i < 80 && high - low > 1e-10; i++) {
      double mid = 0.5 * (low + high);
      double f = residual(geometry, diameter, roughness, mid, gasVelocity, gasDensity, liquidDensity, gasViscosity,
          liquidViscosity, surfaceGradient);
      if (f > 0.0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Whether the stratified layer is stable against the Taitel-Dukler Kelvin-Helmholtz criterion.
   *
   * @param holdup liquid holdup, 0-1
   * @param diameter pipe inner diameter in m
   * @param gasVelocity gas velocity in m/s
   * @param gasDensity gas density in kg/m3
   * @param liquidDensity liquid density in kg/m3
   * @return true when the gas velocity is below the critical velocity for wave growth
   */
  public static boolean isStratifiedStable(double holdup, double diameter, double gasVelocity, double gasDensity,
      double liquidDensity) {
    if (!(holdup > 1e-12)) {
      return true;
    }
    return GEOMETRY.isStratifiedStable(gasVelocity, liquidLevel(holdup, diameter), diameter, gasDensity, liquidDensity,
        0.0);
  }
}
