package neqsim.process.util.heattransfer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One-dimensional transient heat-conduction solver for a multi-layer (composite) vessel wall.
 *
 * <p>
 * The wall is discretized into a stack of material layers (for example an inner polymer liner, a composite overwrap and
 * an outer steel shell). Each layer is split into a number of control volumes and the resulting non-uniform grid is
 * integrated in time with the unconditionally stable Crank-Nicolson scheme. A tridiagonal system is assembled each step
 * and solved with the Thomas algorithm.
 * </p>
 *
 * <p>
 * The outer face receives a prescribed incident heat flux (the absorbed fire flux, a Neumann boundary condition) and
 * the inner face exchanges heat with the contained fluid through a convective film coefficient (a Robin boundary
 * condition). Because the solver resolves the temperature gradient across the wall thickness, it captures the through-
 * wall thermal lag that the single-node lumped-capacitance model misses once the Biot number is no longer small. The
 * static {@link #biotNumber(double, double, double)} helper supports that screening: the lumped model is adequate for
 * {@code Bi < 0.1}, and this distributed solver should be preferred above it.
 * </p>
 *
 * <p>
 * Wetted and unwetted zones of a partially filled vessel are represented by two independent instances driven with the
 * same outer fire flux but with different inner film coefficients and fluid temperatures.
 * </p>
 *
 * <p>
 * <b>References:</b> Crank and Nicolson (1947), Proc. Camb. Phil. Soc. 43, 50; Patankar (1980), "Numerical Heat
 * Transfer and Fluid Flow"; API 521 §4.3 (fire heat input); BS EN ISO 23251.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CompositeWallConduction implements Serializable {
  private static final long serialVersionUID = 1L;

  private final int nodesPerLayer;
  private final List<double[]> layers = new ArrayList<double[]>();
  private final List<String> layerNames = new ArrayList<String>();

  private double[] nodePositionM;
  private double[] nodeConductivity;
  private double[] nodeHeatCapacityVolumetric;
  private double[] temperatureK;

  /**
   * Creates a composite wall conduction solver.
   *
   * @param nodesPerLayer number of control volumes used to discretize each material layer; must be at least two
   * @throws IllegalArgumentException if {@code nodesPerLayer} is less than two
   */
  public CompositeWallConduction(int nodesPerLayer) {
    if (nodesPerLayer < 2) {
      throw new IllegalArgumentException("nodesPerLayer must be at least 2");
    }
    this.nodesPerLayer = nodesPerLayer;
  }

  /**
   * Adds a material layer to the wall stack, ordered from the outer (fire-exposed) face inward.
   *
   * @param name descriptive layer name; must not be empty
   * @param thicknessM layer thickness in m; must be positive
   * @param conductivity thermal conductivity in W/(m·K); must be positive
   * @param density density in kg/m³; must be positive
   * @param specificHeat specific heat capacity in J/(kg·K); must be positive
   * @return this solver for chaining
   * @throws IllegalArgumentException if any argument is invalid
   */
  public CompositeWallConduction addLayer(String name, double thicknessM, double conductivity, double density,
      double specificHeat) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must not be empty");
    }
    if (thicknessM <= 0.0 || conductivity <= 0.0 || density <= 0.0 || specificHeat <= 0.0) {
      throw new IllegalArgumentException("thickness, conductivity, density and specificHeat must be positive");
    }
    layers.add(new double[] { thicknessM, conductivity, density, specificHeat });
    layerNames.add(name.trim());
    nodePositionM = null;
    return this;
  }

  /**
   * Builds the discretization grid and sets a uniform initial temperature in all nodes.
   *
   * @param uniformTemperatureK initial temperature in K applied to every node; must be positive
   * @return this solver for chaining
   * @throws IllegalStateException if no layers have been added
   * @throws IllegalArgumentException if {@code uniformTemperatureK} is not positive
   */
  public CompositeWallConduction initialize(double uniformTemperatureK) {
    if (layers.isEmpty()) {
      throw new IllegalStateException("at least one layer must be added before initialize(...)");
    }
    if (uniformTemperatureK <= 0.0) {
      throw new IllegalArgumentException("uniformTemperatureK must be positive");
    }
    buildGrid();
    for (int i = 0; i < temperatureK.length; i++) {
      temperatureK[i] = uniformTemperatureK;
    }
    return this;
  }

  /**
   * Advances the wall temperature field by one Crank-Nicolson time step.
   *
   * <p>
   * The outer face is driven by the absorbed flux {@code outerFluxWPerM2} (positive into the wall) and the inner face
   * exchanges heat with the contained fluid by convection {@code q = innerFilmCoeff * (fluidT - innerSurfaceT)}.
   * </p>
   *
   * @param dt time step in s; must be positive
   * @param outerFluxWPerM2 absorbed incident flux on the outer face in W/m² (positive into the wall)
   * @param innerFilmCoeff inner film heat-transfer coefficient in W/(m²·K); must be non-negative
   * @param innerFluidTempK contained-fluid temperature in K used for the inner convective boundary; must be positive
   * @throws IllegalStateException if the grid has not been initialized
   * @throws IllegalArgumentException if {@code dt}, {@code innerFilmCoeff} or {@code innerFluidTempK} is invalid
   */
  public void step(double dt, double outerFluxWPerM2, double innerFilmCoeff, double innerFluidTempK) {
    if (temperatureK == null) {
      throw new IllegalStateException("initialize(...) must be called before step(...)");
    }
    if (dt <= 0.0) {
      throw new IllegalArgumentException("dt must be positive");
    }
    if (innerFilmCoeff < 0.0) {
      throw new IllegalArgumentException("innerFilmCoeff must be non-negative");
    }
    if (innerFluidTempK <= 0.0) {
      throw new IllegalArgumentException("innerFluidTempK must be positive");
    }
    int n = temperatureK.length;
    double[] told = temperatureK;
    double[] capacity = new double[n];
    double[] conductance = new double[n - 1];
    computeCapacityAndConductance(capacity, conductance);

    double[] a = new double[n];
    double[] b = new double[n];
    double[] c = new double[n];
    double[] d = new double[n];

    for (int i = 0; i < n; i++) {
      double gWest = i > 0 ? conductance[i - 1] : 0.0;
      double gEast = i < n - 1 ? conductance[i] : 0.0;
      double cdt = capacity[i] / dt;
      a[i] = -0.5 * gWest;
      c[i] = -0.5 * gEast;
      b[i] = cdt + 0.5 * (gWest + gEast);
      double explicitWest = i > 0 ? gWest * (told[i - 1] - told[i]) : 0.0;
      double explicitEast = i < n - 1 ? gEast * (told[i + 1] - told[i]) : 0.0;
      d[i] = cdt * told[i] + 0.5 * (explicitWest + explicitEast);
    }

    // Outer Neumann boundary: prescribed absorbed flux into node 0 (full source, CN-invariant).
    d[0] += outerFluxWPerM2;

    // Inner Robin boundary: convective exchange with the contained fluid at node n-1.
    b[n - 1] += 0.5 * innerFilmCoeff;
    d[n - 1] += innerFilmCoeff * innerFluidTempK - 0.5 * innerFilmCoeff * told[n - 1];

    temperatureK = solveTridiagonal(a, b, c, d);
  }

  /**
   * Gets the current node temperatures from the outer face to the inner face.
   *
   * @return a copy of the node temperature field in K
   * @throws IllegalStateException if the grid has not been initialized
   */
  public double[] getTemperaturesK() {
    requireInitialized();
    return java.util.Arrays.copyOf(temperatureK, temperatureK.length);
  }

  /**
   * Gets the node centre positions measured from the outer face.
   *
   * @return a copy of the node positions in m
   * @throws IllegalStateException if the grid has not been initialized
   */
  public double[] getNodePositionsM() {
    requireInitialized();
    return java.util.Arrays.copyOf(nodePositionM, nodePositionM.length);
  }

  /**
   * Gets the outer-face (fire-exposed) surface temperature.
   *
   * @return outer-face temperature in K
   * @throws IllegalStateException if the grid has not been initialized
   */
  public double getOuterSurfaceTemperatureK() {
    requireInitialized();
    return temperatureK[0];
  }

  /**
   * Gets the inner-face (fluid side) surface temperature.
   *
   * @return inner-face temperature in K
   * @throws IllegalStateException if the grid has not been initialized
   */
  public double getInnerSurfaceTemperatureK() {
    requireInitialized();
    return temperatureK[temperatureK.length - 1];
  }

  /**
   * Gets the mass-weighted mean wall temperature.
   *
   * @return mean wall temperature in K
   * @throws IllegalStateException if the grid has not been initialized
   */
  public double getMeanTemperatureK() {
    requireInitialized();
    int n = temperatureK.length;
    double[] capacity = new double[n];
    double[] conductance = new double[n - 1];
    computeCapacityAndConductance(capacity, conductance);
    double sum = 0.0;
    double weight = 0.0;
    for (int i = 0; i < n; i++) {
      sum += capacity[i] * temperatureK[i];
      weight += capacity[i];
    }
    return weight > 0.0 ? sum / weight : temperatureK[0];
  }

  /**
   * Gets the total wall thickness across all layers.
   *
   * @return total thickness in m
   */
  public double getTotalThicknessM() {
    double total = 0.0;
    for (int i = 0; i < layers.size(); i++) {
      total += layers.get(i)[0];
    }
    return total;
  }

  /**
   * Computes the Biot number used to decide whether through-wall conduction must be resolved.
   *
   * <p>
   * {@code Bi = h * L / k}. The single-node lumped-capacitance approximation is acceptable for {@code Bi < 0.1};
   * otherwise this distributed solver should be used.
   * </p>
   *
   * @param filmCoefficient surface heat-transfer coefficient in W/(m²·K); must be positive
   * @param characteristicLengthM characteristic conduction length in m; must be positive
   * @param conductivity wall thermal conductivity in W/(m·K); must be positive
   * @return Biot number (dimensionless)
   * @throws IllegalArgumentException if any argument is not positive
   */
  public static double biotNumber(double filmCoefficient, double characteristicLengthM, double conductivity) {
    if (filmCoefficient <= 0.0 || characteristicLengthM <= 0.0 || conductivity <= 0.0) {
      throw new IllegalArgumentException("filmCoefficient, characteristicLengthM and conductivity must be positive");
    }
    return filmCoefficient * characteristicLengthM / conductivity;
  }

  /**
   * Builds the global non-uniform node grid from the stacked layers.
   */
  private void buildGrid() {
    int n = layers.size() * nodesPerLayer;
    nodePositionM = new double[n];
    nodeConductivity = new double[n];
    double[] nodeRhoCp = new double[n];
    temperatureK = new double[n];
    double offset = 0.0;
    int node = 0;
    for (int layer = 0; layer < layers.size(); layer++) {
      double[] props = layers.get(layer);
      double thickness = props[0];
      double k = props[1];
      double rho = props[2];
      double cp = props[3];
      double dx = thickness / nodesPerLayer;
      for (int j = 0; j < nodesPerLayer; j++) {
        nodePositionM[node] = offset + (j + 0.5) * dx;
        nodeConductivity[node] = k;
        nodeRhoCp[node] = rho * cp;
        node++;
      }
      offset += thickness;
    }
    nodeHeatCapacityVolumetric = nodeRhoCp;
  }

  /**
   * Computes per-node heat capacities (per unit wall area) and inter-node conductances.
   *
   * @param capacity output array of node heat capacities in J/(m²·K)
   * @param conductance output array of inter-node conductances in W/(m²·K)
   */
  private void computeCapacityAndConductance(double[] capacity, double[] conductance) {
    int n = temperatureK.length;
    for (int i = 0; i < n - 1; i++) {
      double dxEast = nodePositionM[i + 1] - nodePositionM[i];
      double kHarmonic = 2.0 * nodeConductivity[i] * nodeConductivity[i + 1]
          / (nodeConductivity[i] + nodeConductivity[i + 1]);
      conductance[i] = kHarmonic / dxEast;
    }
    for (int i = 0; i < n; i++) {
      double width;
      if (i == 0) {
        width = 0.5 * (nodePositionM[1] - nodePositionM[0]) + nodePositionM[0];
      } else if (i == n - 1) {
        width = 0.5 * (nodePositionM[n - 1] - nodePositionM[n - 2]) + (getTotalThicknessM() - nodePositionM[n - 1]);
      } else {
        width = 0.5 * (nodePositionM[i + 1] - nodePositionM[i - 1]);
      }
      capacity[i] = nodeHeatCapacityVolumetric[i] * width;
    }
  }

  /**
   * Solves a tridiagonal linear system with the Thomas algorithm.
   *
   * @param a sub-diagonal coefficients (a[0] unused)
   * @param b diagonal coefficients
   * @param c super-diagonal coefficients (c[n-1] unused)
   * @param d right-hand side
   * @return solution vector
   */
  private double[] solveTridiagonal(double[] a, double[] b, double[] c, double[] d) {
    int n = b.length;
    double[] cPrime = new double[n];
    double[] dPrime = new double[n];
    cPrime[0] = c[0] / b[0];
    dPrime[0] = d[0] / b[0];
    for (int i = 1; i < n; i++) {
      double m = b[i] - a[i] * cPrime[i - 1];
      cPrime[i] = c[i] / m;
      dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / m;
    }
    double[] x = new double[n];
    x[n - 1] = dPrime[n - 1];
    for (int i = n - 2; i >= 0; i--) {
      x[i] = dPrime[i] - cPrime[i] * x[i + 1];
    }
    return x;
  }

  /**
   * Verifies that the grid has been initialized.
   *
   * @throws IllegalStateException if {@link #initialize(double)} has not been called
   */
  private void requireInitialized() {
    if (temperatureK == null) {
      throw new IllegalStateException("initialize(...) must be called first");
    }
  }
}
