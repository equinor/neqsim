package neqsim.process.util.fire;

import java.util.Arrays;

/**
 * Transient 1-D heat conduction through vessel walls.
 *
 * <p>
 * This class implements a finite difference solution for transient heat conduction through single
 * or composite (multi-layer) vessel walls. It is designed for modeling Type III/IV hydrogen storage
 * vessels with low thermal conductivity liners, but is applicable to any cylindrical vessel wall.
 * </p>
 *
 * <p>
 * The model solves the 1-D heat equation:
 * 
 * <pre>
 * rho * Cp * dT/dt = k * d2T/dx2
 * </pre>
 *
 * using an explicit finite difference scheme with configurable boundary conditions.
 *
 * <p>
 * Reference: Andreasen, A. (2021). HydDown: A Python package for calculation of hydrogen (or other
 * gas) pressure vessel filling and discharge. Journal of Open Source Software, 6(66), 3695.
 * </p>
 *
 * @author ESOL
 * @see <a href="https://doi.org/10.21105/joss.03695">HydDown - JOSS Paper</a>
 */
public class TransientWallHeatTransfer {

  /** Number of nodes in the spatial discretization. */
  private final int numNodes;

  /** Node spacing [m]. */
  private final double dx;

  /** Total wall thickness [m]. */
  private final double totalThickness;

  /** Temperature profile across wall [K]. */
  private double[] temperatureProfile;

  /** Thermal conductivity at each node [W/(m*K)]. */
  private final double[] thermalConductivity;

  /** Density at each node [kg/m^3]. */
  private final double[] density;

  /** Heat capacity at each node [J/(kg*K)]. */
  private final double[] heatCapacity;

  /** Thermal diffusivity at each node [m^2/s]. */
  private final double[] thermalDiffusivity;

  /**
   * Creates a transient wall heat transfer model for a single-layer wall.
   *
   * @param thickness Wall thickness [m]
   * @param thermalConductivity Thermal conductivity [W/(m*K)]
   * @param density Material density [kg/m^3]
   * @param heatCapacity Specific heat capacity [J/(kg*K)]
   * @param initialTemperatureK Initial uniform temperature [K]
   * @param numNodes Number of spatial nodes (minimum 3)
   */
  public TransientWallHeatTransfer(double thickness, double thermalConductivity, double density,
      double heatCapacity, double initialTemperatureK, int numNodes) {
    if (thickness <= 0.0 || thermalConductivity <= 0.0 || density <= 0.0 || heatCapacity <= 0.0) {
      throw new IllegalArgumentException("Wall properties must be positive");
    }
    if (numNodes < 3) {
      throw new IllegalArgumentException("Minimum 3 nodes required");
    }
    if (initialTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Initial temperature must be positive Kelvin");
    }

    this.numNodes = numNodes;
    this.totalThickness = thickness;
    this.dx = thickness / (numNodes - 1);

    this.temperatureProfile = new double[numNodes];
    Arrays.fill(this.temperatureProfile, initialTemperatureK);

    this.thermalConductivity = new double[numNodes];
    this.density = new double[numNodes];
    this.heatCapacity = new double[numNodes];
    this.thermalDiffusivity = new double[numNodes];

    double alpha = thermalConductivity / (density * heatCapacity);
    for (int i = 0; i < numNodes; i++) {
      this.thermalConductivity[i] = thermalConductivity;
      this.density[i] = density;
      this.heatCapacity[i] = heatCapacity;
      this.thermalDiffusivity[i] = alpha;
    }
  }

  /**
   * Creates a transient wall heat transfer model for a composite (two-layer) wall.
   *
   * <p>
   * This is typical for Type III/IV hydrogen vessels with an inner liner and outer shell.
   * </p>
   *
   * @param linerThickness Inner liner thickness [m]
   * @param linerK Liner thermal conductivity [W/(m*K)]
   * @param linerDensity Liner density [kg/m^3]
   * @param linerCp Liner heat capacity [J/(kg*K)]
   * @param shellThickness Outer shell thickness [m]
   * @param shellK Shell thermal conductivity [W/(m*K)]
   * @param shellDensity Shell density [kg/m^3]
   * @param shellCp Shell heat capacity [J/(kg*K)]
   * @param initialTemperatureK Initial uniform temperature [K]
   * @param numNodes Total number of spatial nodes (minimum 5)
   */
  public TransientWallHeatTransfer(double linerThickness, double linerK, double linerDensity,
      double linerCp, double shellThickness, double shellK, double shellDensity, double shellCp,
      double initialTemperatureK, int numNodes) {
    if (linerThickness <= 0.0 || shellThickness <= 0.0) {
      throw new IllegalArgumentException("Layer thicknesses must be positive");
    }
    if (numNodes < 5) {
      throw new IllegalArgumentException("Minimum 5 nodes required for composite wall");
    }
    if (initialTemperatureK <= 0.0) {
      throw new IllegalArgumentException("Initial temperature must be positive Kelvin");
    }

    this.numNodes = numNodes;
    this.totalThickness = linerThickness + shellThickness;
    this.dx = totalThickness / (numNodes - 1);

    this.temperatureProfile = new double[numNodes];
    Arrays.fill(this.temperatureProfile, initialTemperatureK);

    this.thermalConductivity = new double[numNodes];
    this.density = new double[numNodes];
    this.heatCapacity = new double[numNodes];
    this.thermalDiffusivity = new double[numNodes];

    // Assign properties based on position
    for (int i = 0; i < numNodes; i++) {
      double x = i * dx;
      if (x <= linerThickness) {
        this.thermalConductivity[i] = linerK;
        this.density[i] = linerDensity;
        this.heatCapacity[i] = linerCp;
      } else {
        this.thermalConductivity[i] = shellK;
        this.density[i] = shellDensity;
        this.heatCapacity[i] = shellCp;
      }
      this.thermalDiffusivity[i] =
          this.thermalConductivity[i] / (this.density[i] * this.heatCapacity[i]);
    }
  }

  /**
   * Advances the temperature profile by one time step using explicit finite differences.
   *
   * <p>
   * Uses convective (Robin) boundary conditions at both surfaces:
   * 
   * <pre>
   * -k * dT/dx = h_inner * (T_inner - T_fluid_inner) at x = 0
   * -k * dT/dx = h_outer * (T_outer - T_ambient) at x = L
   * </pre>
   *
   * @param dt Time step [s]
   * @param innerFluidTemperatureK Process fluid temperature [K]
   * @param innerFilmCoefficientWPerM2K Internal film coefficient [W/(m^2*K)]
   * @param outerAmbientTemperatureK Ambient/fire temperature [K]
   * @param outerFilmCoefficientWPerM2K External film coefficient [W/(m^2*K)]
   */
  public void advanceTimeStep(double dt, double innerFluidTemperatureK,
      double innerFilmCoefficientWPerM2K, double outerAmbientTemperatureK,
      double outerFilmCoefficientWPerM2K) {
    if (dt <= 0.0) {
      throw new IllegalArgumentException("Time step must be positive");
    }

    // Check stability (CFL condition for explicit scheme)
    double maxAlpha = Arrays.stream(thermalDiffusivity).max().orElse(1.0);
    double maxDt = 0.5 * dx * dx / maxAlpha;
    if (dt > maxDt) {
      // Subdivide time step if needed for stability
      int subSteps = (int) Math.ceil(dt / maxDt) + 1;
      double subDt = dt / subSteps;
      for (int s = 0; s < subSteps; s++) {
        advanceTimeStepInternal(subDt, innerFluidTemperatureK, innerFilmCoefficientWPerM2K,
            outerAmbientTemperatureK, outerFilmCoefficientWPerM2K);
      }
    } else {
      advanceTimeStepInternal(dt, innerFluidTemperatureK, innerFilmCoefficientWPerM2K,
          outerAmbientTemperatureK, outerFilmCoefficientWPerM2K);
    }
  }

  /**
   * Internal time step advancement (assumes stability is already checked).
   */
  private void advanceTimeStepInternal(double dt, double innerFluidTemperatureK,
      double innerFilmCoefficientWPerM2K, double outerAmbientTemperatureK,
      double outerFilmCoefficientWPerM2K) {
    double[] newTemp = new double[numNodes];

    // Interior nodes - explicit finite difference
    for (int i = 1; i < numNodes - 1; i++) {
      double alpha = thermalDiffusivity[i];
      double Fo = alpha * dt / (dx * dx); // Fourier number

      // Handle interface between materials with harmonic mean of conductivity
      double kLeft = 2.0 * thermalConductivity[i] * thermalConductivity[i - 1]
          / (thermalConductivity[i] + thermalConductivity[i - 1]);
      double kRight = 2.0 * thermalConductivity[i] * thermalConductivity[i + 1]
          / (thermalConductivity[i] + thermalConductivity[i + 1]);
      double kEff = (kLeft + kRight) / 2.0;

      double alphaEff = kEff / (density[i] * heatCapacity[i]);
      Fo = alphaEff * dt / (dx * dx);

      newTemp[i] = temperatureProfile[i] + Fo
          * (temperatureProfile[i - 1] - 2.0 * temperatureProfile[i] + temperatureProfile[i + 1]);
    }

    // Inner boundary (x = 0): convective BC with process fluid
    double kInner = thermalConductivity[0];
    double BiInner = innerFilmCoefficientWPerM2K * dx / kInner;
    double alphaInner = thermalDiffusivity[0];
    double FoInner = alphaInner * dt / (dx * dx);

    newTemp[0] = temperatureProfile[0] + 2.0 * FoInner * (temperatureProfile[1]
        - temperatureProfile[0] + BiInner * (innerFluidTemperatureK - temperatureProfile[0]));

    // Outer boundary (x = L): convective BC with ambient/fire
    int n = numNodes - 1;
    double kOuter = thermalConductivity[n];
    double BiOuter = outerFilmCoefficientWPerM2K * dx / kOuter;
    double alphaOuter = thermalDiffusivity[n];
    double FoOuter = alphaOuter * dt / (dx * dx);

    newTemp[n] = temperatureProfile[n] + 2.0 * FoOuter * (temperatureProfile[n - 1]
        - temperatureProfile[n] + BiOuter * (outerAmbientTemperatureK - temperatureProfile[n]));

    // Update temperature profile
    temperatureProfile = newTemp;
  }

  /**
   * Gets the current inner wall surface temperature (x = 0).
   *
   * @return Inner wall temperature [K]
   */
  public double getInnerWallTemperature() {
    return temperatureProfile[0];
  }

  /**
   * Gets the current outer wall surface temperature (x = L).
   *
   * @return Outer wall temperature [K]
   */
  public double getOuterWallTemperature() {
    return temperatureProfile[numNodes - 1];
  }

  /**
   * Gets the mean wall temperature (volume-averaged).
   *
   * @return Mean wall temperature [K]
   */
  public double getMeanWallTemperature() {
    double sum = 0.0;
    for (double t : temperatureProfile) {
      sum += t;
    }
    return sum / numNodes;
  }

  /**
   * Gets the complete temperature profile across the wall.
   *
   * @return Array of temperatures at each node [K]
   */
  public double[] getTemperatureProfile() {
    return Arrays.copyOf(temperatureProfile, numNodes);
  }

  /**
   * Gets the position array for the temperature profile.
   *
   * @return Array of positions [m] from inner wall (x=0) to outer wall (x=thickness)
   */
  public double[] getPositionArray() {
    double[] positions = new double[numNodes];
    for (int i = 0; i < numNodes; i++) {
      positions[i] = i * dx;
    }
    return positions;
  }

  /**
   * Calculates the heat flux through the wall at steady state.
   *
   * <p>
   * This is an approximation based on the current temperature gradient at the boundaries.
   * </p>
   *
   * @return Heat flux [W/m^2] (positive = heat flowing from outer to inner)
   */
  public double getHeatFlux() {
    // Use gradient at outer surface
    double dTdx = (temperatureProfile[numNodes - 1] - temperatureProfile[numNodes - 2]) / dx;
    return -thermalConductivity[numNodes - 1] * dTdx;
  }

  /**
   * Calculates the heat absorbed by the wall during a time step.
   *
   * @param wallAreaM2 Wall surface area [m^2]
   * @param previousMeanTemperatureK Previous mean temperature [K]
   * @return Heat absorbed [J]
   */
  public double getHeatAbsorbed(double wallAreaM2, double previousMeanTemperatureK) {
    double currentMean = getMeanWallTemperature();
    double deltaTMean = currentMean - previousMeanTemperatureK;

    // Calculate total thermal mass
    double thermalMass = 0.0;
    for (int i = 0; i < numNodes; i++) {
      thermalMass += density[i] * heatCapacity[i];
    }
    thermalMass = thermalMass / numNodes * totalThickness * wallAreaM2;

    return thermalMass * deltaTMean;
  }

  /**
   * Resets the temperature profile to a uniform value.
   *
   * @param temperatureK Uniform temperature [K]
   */
  public void resetTemperature(double temperatureK) {
    if (temperatureK <= 0.0) {
      throw new IllegalArgumentException("Temperature must be positive Kelvin");
    }
    Arrays.fill(temperatureProfile, temperatureK);
  }

  /**
   * Gets the number of spatial nodes.
   *
   * @return Number of nodes
   */
  public int getNumNodes() {
    return numNodes;
  }

  /**
   * Gets the total wall thickness.
   *
   * @return Total thickness [m]
   */
  public double getTotalThickness() {
    return totalThickness;
  }

  /**
   * Gets the node spacing.
   *
   * @return Node spacing [m]
   */
  public double getNodeSpacing() {
    return dx;
  }

  /**
   * Calculates the maximum stable time step for the explicit scheme.
   *
   * @return Maximum stable time step [s]
   */
  public double getMaxStableTimeStep() {
    double maxAlpha = Arrays.stream(thermalDiffusivity).max().orElse(1.0);
    return 0.5 * dx * dx / maxAlpha;
  }
}
