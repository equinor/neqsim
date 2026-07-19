package neqsim.fluidmechanics.flownode;

/** Force-balance slip correlation for spherical bubbles and droplets. */
public final class DispersedPhaseSlipCalculator {
  private static final double GRAVITY = 9.80665;

  private DispersedPhaseSlipCalculator() {
  }

  /**
   * Calculate the magnitude of the terminal particle velocity relative to its continuous phase.
   *
   * <p>
   * The iterative Schiller-Naumann drag correlation is used: {@code Cd=24/Re*(1+0.15*Re^0.687)} below Re=1000 and
   * {@code Cd=0.44} above it. The result is a speed; density ordering is handled separately when projecting buoyancy or
   * settling onto the pipe axis.
   * </p>
   *
   * @param diameter particle Sauter mean diameter in m
   * @param continuousDensity continuous-phase density in kg/m3
   * @param dispersedDensity dispersed-phase density in kg/m3
   * @param continuousViscosity continuous-phase dynamic viscosity in Pa s
   * @return terminal relative speed in m/s
   */
  public static double terminalVelocityMagnitude(double diameter, double continuousDensity, double dispersedDensity,
      double continuousViscosity) {
    if (!(diameter > 0.0) || !(continuousDensity > 0.0) || !(continuousViscosity > 0.0) || !Double.isFinite(diameter)
        || !Double.isFinite(continuousDensity) || !Double.isFinite(dispersedDensity)
        || !Double.isFinite(continuousViscosity)) {
      return 0.0;
    }
    double densityDifference = Math.abs(dispersedDensity - continuousDensity);
    if (densityDifference < 1.0e-12) {
      return 0.0;
    }

    double velocity = diameter * diameter * densityDifference * GRAVITY / (18.0 * continuousViscosity);
    for (int iteration = 0; iteration < 50; iteration++) {
      double reynoldsNumber = continuousDensity * Math.max(velocity, 1.0e-30) * diameter / continuousViscosity;
      double dragCoefficient = reynoldsNumber < 1000.0
          ? 24.0 / reynoldsNumber * (1.0 + 0.15 * Math.pow(reynoldsNumber, 0.687))
          : 0.44;
      double updatedVelocity = Math
          .sqrt(4.0 * GRAVITY * diameter * densityDifference / (3.0 * dragCoefficient * continuousDensity));
      if (Math.abs(updatedVelocity - velocity) <= 1.0e-8 * Math.max(1.0, updatedVelocity)) {
        return updatedVelocity;
      }
      velocity = 0.5 * (velocity + updatedVelocity);
    }
    return velocity;
  }
}
