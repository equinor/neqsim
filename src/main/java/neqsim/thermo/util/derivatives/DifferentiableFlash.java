package neqsim.thermo.util.derivatives;

import java.io.Serializable;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Computes gradients of flash calculation results using the implicit function theorem.
 *
 * <p>
 * This class provides exact derivatives of phase equilibrium results without differentiating
 * through the iterative flash solver. At equilibrium, the residual equations F(y; θ) = 0 are
 * satisfied. The implicit function theorem gives:
 * </p>
 * 
 * <pre>
 * dy/dθ = -(∂F/∂y)^(-1) * (∂F/∂θ)
 * </pre>
 *
 * <p>
 * where y = (K_1, ..., K_n, β) are the solution variables and θ = (T, P, z) are parameters.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * {@code
 * // Run flash calculation
 * SystemInterface system = new SystemSrkEos(300, 50);
 * system.addComponent("methane", 1.0);
 * system.addComponent("ethane", 0.5);
 * ThermodynamicOperations ops = new ThermodynamicOperations(system);
 * ops.TPflash();
 *
 * // Compute gradients
 * DifferentiableFlash diffFlash = new DifferentiableFlash(system);
 * FlashGradients grads = diffFlash.computeFlashGradients();
 *
 * // Use gradients
 * double dBeta_dT = grads.getDBetadT();
 * double[] dK_dP = grads.getDKdP();
 *
 * // Or compute property gradients directly
 * PropertyGradient densityGrad = diffFlash.computePropertyGradient("density");
 * }
 * </pre>
 *
 * <h2>Supported Properties:</h2>
 * <ul>
 * <li>density - mixture density [kg/m³]</li>
 * <li>enthalpy - mixture enthalpy [J/mol]</li>
 * <li>entropy - mixture entropy [J/mol/K]</li>
 * <li>Cp - heat capacity at constant pressure [J/mol/K]</li>
 * <li>compressibility - Z-factor [-]</li>
 * <li>molarvolume - molar volume [m³/mol]</li>
 * </ul>
 *
 * @author ESOL
 * @since 3.0
 */
public class DifferentiableFlash implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(DifferentiableFlash.class);

  /** The thermodynamic system at equilibrium. */
  private final SystemInterface system;

  /** Cached flash gradients. */
  private FlashGradients cachedFlashGradients;

  /** Cached fugacity Jacobians for each phase. */
  private FugacityJacobian[] cachedFugacityJacobians;

  /** Whether gradients have been computed. */
  private boolean gradientsComputed = false;

  /** Small perturbation for numerical derivatives. */
  private static final double EPSILON = 1e-6;

  /**
   * Constructor for DifferentiableFlash.
   *
   * @param system thermodynamic system (must be at equilibrium, i.e., flash already performed)
   */
  public DifferentiableFlash(SystemInterface system) {
    if (system == null) {
      throw new IllegalArgumentException("System cannot be null");
    }
    this.system = system;
  }

  /**
   * Extract fugacity Jacobian from a phase using existing NeqSim derivatives.
   *
   * @param phaseIndex phase index
   * @return FugacityJacobian for the phase
   */
  public FugacityJacobian extractFugacityJacobian(int phaseIndex) {
    PhaseInterface phase = system.getPhase(phaseIndex);
    int nc = system.getNumberOfComponents();

    double[] lnPhi = new double[nc];
    double[] dlnPhidT = new double[nc];
    double[] dlnPhidP = new double[nc];
    double[][] dlnPhidn = new double[nc][nc];
    String[] componentNames = new String[nc];

    for (int i = 0; i < nc; i++) {
      componentNames[i] = phase.getComponent(i).getComponentName();
      lnPhi[i] = phase.getComponent(i).getLogFugacityCoefficient();

      // NeqSim stores d(ln f)/dT and d(ln f)/dP, which equal d(ln φ)/dT and d(ln φ)/dP
      // since ln(f) = ln(φ) + ln(x*P)
      dlnPhidT[i] = phase.getComponent(i).getdfugdt();
      dlnPhidP[i] = phase.getComponent(i).getdfugdp();

      // Composition derivatives
      for (int j = 0; j < nc; j++) {
        dlnPhidn[i][j] = phase.getComponent(i).getdfugdn(j);
      }
    }

    String phaseType = phase.getType().toString();
    return new FugacityJacobian(phaseIndex, phaseType, lnPhi, dlnPhidT, dlnPhidP, dlnPhidn,
        componentNames);
  }

  /**
   * Compute gradients of flash results using implicit function theorem.
   *
   * <p>
   * The equilibrium conditions are:
   * </p>
   * <ul>
   * <li>F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V) = 0 for i = 1...nc</li>
   * <li>F_{nc+1} = Σ z_i*(K_i - 1)/(1 + β*(K_i - 1)) = 0 (Rachford-Rice)</li>
   * </ul>
   *
   * @return FlashGradients containing all derivatives
   */
  public FlashGradients computeFlashGradients() {
    if (gradientsComputed && cachedFlashGradients != null) {
      return cachedFlashGradients;
    }

    int nc = system.getNumberOfComponents();

    // Check if we have two phases
    if (system.getNumberOfPhases() < 2) {
      cachedFlashGradients =
          new FlashGradients(nc, "Single phase - no meaningful K-value gradients");
      return cachedFlashGradients;
    }

    try {
      // Identify liquid and vapor phase indices BEFORE calling init(3)
      // NeqSim phase ordering can vary - typically phase 0 is gas after flash
      int liquidPhaseIndex = -1;
      int vaporPhaseIndex = -1;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        if (system.getPhase(p).getType() == neqsim.thermo.phase.PhaseType.GAS) {
          vaporPhaseIndex = p;
        } else {
          // Treat any non-gas phase as liquid (could be liquid, oil, or aqueous)
          liquidPhaseIndex = p;
        }
      }

      // Fallback if phase types not set correctly
      if (liquidPhaseIndex < 0 || vaporPhaseIndex < 0) {
        // Assume standard ordering: lower density phase is vapor
        if (system.getPhase(0).getDensity() < system.getPhase(1).getDensity()) {
          vaporPhaseIndex = 0;
          liquidPhaseIndex = 1;
        } else {
          vaporPhaseIndex = 1;
          liquidPhaseIndex = 0;
        }
      }

      // CRITICAL: Call init(3) to compute fugacity derivatives including composition derivatives
      // This must be called AFTER phase identification but BEFORE extracting Jacobians
      // init(3) populates dfugdn[i][j] = ∂ln(φ_i)/∂n_j which are needed for the Jacobian
      system.init(3);

      // Extract fugacity Jacobians
      FugacityJacobian jacL = extractFugacityJacobian(liquidPhaseIndex);
      FugacityJacobian jacV = extractFugacityJacobian(vaporPhaseIndex);

      cachedFugacityJacobians = new FugacityJacobian[] {jacL, jacV};

      // Get current state
      double[] kValues = new double[nc];
      double[] z = new double[nc];
      double[] x = new double[nc]; // liquid composition
      double[] y = new double[nc]; // vapor composition
      String[] componentNames = new String[nc];

      for (int i = 0; i < nc; i++) {
        componentNames[i] = system.getPhase(liquidPhaseIndex).getComponent(i).getComponentName();
        x[i] = system.getPhase(liquidPhaseIndex).getComponent(i).getx();
        y[i] = system.getPhase(vaporPhaseIndex).getComponent(i).getx();
        kValues[i] = y[i] / Math.max(x[i], 1e-20);
        z[i] = system.getComponent(i).getz();
      }

      // getBeta() returns vapor mole fraction = moles_vapor / total_moles
      double beta = system.getPhase(vaporPhaseIndex).getBeta();

      // Build Jacobian of equilibrium equations: ∂F/∂y where y = (K_1...K_nc, β)
      // F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V) = 0
      // F_{nc+1} = Rachford-Rice equation

      double[][] dFdy = new double[nc + 1][nc + 1];
      double[][] dFdT = new double[nc + 1][1];
      double[][] dFdP = new double[nc + 1][1];
      double[][] dFdz = new double[nc + 1][nc];

      // Equilibrium equations: F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V) = 0
      // At equilibrium: ln(K_i) = ln(φ_i^L) - ln(φ_i^V)
      //
      // The chain rule for fugacity coefficient derivatives requires careful treatment:
      // dfugdn returns ∂ln(φ_i)/∂n_j [units: 1/mol]
      // We need: ∂ln(φ_i)/∂K_j = Σ_k (∂ln(φ_i)/∂n_k) * (∂n_k/∂K_j)
      //
      // For liquid phase: n_k^L = (1-β) * n_total * x_k
      // For vapor phase: n_k^V = β * n_total * y_k
      //
      // ∂n_k^L/∂K_j = (1-β) * n_total * ∂x_k/∂K_j (at constant β)
      // ∂n_k^V/∂K_j = β * n_total * ∂y_k/∂K_j (at constant β)

      double nTotal = system.getTotalNumberOfMoles();
      double nLiquid = system.getPhase(liquidPhaseIndex).getNumberOfMolesInPhase();
      double nVapor = system.getPhase(vaporPhaseIndex).getNumberOfMolesInPhase();

      for (int i = 0; i < nc; i++) {
        // ∂F_i/∂K_j - using chain rule through composition
        for (int j = 0; j < nc; j++) {
          // Derivative of ln(K_i) term
          double dlnK_dK = (i == j) ? 1.0 / kValues[i] : 0.0;

          // Derivative through fugacity coefficients via composition change
          // ∂n_k/∂K_j for liquid and vapor phases
          double dxj_dKj = computeDxDK(j, z, kValues, beta);
          double dyj_dKj = computeDyDK(j, z, kValues, beta);

          // Convert to molar derivatives: ∂n_j/∂K_j = n_phase * ∂x_j/∂K_j (at constant total moles)
          double dnLj_dKj = nLiquid * dxj_dKj;
          double dnVj_dKj = nVapor * dyj_dKj;

          // Chain rule: ∂ln(φ_i)/∂K_j = (∂ln(φ_i)/∂n_j) * (∂n_j/∂K_j)
          // Note: dfugdn[i][j] = ∂ln(φ_i)/∂n_j
          double dlnPhiL_dKj = jacL.getDlnPhidn(i, j) * dnLj_dKj;
          double dlnPhiV_dKj = jacV.getDlnPhidn(i, j) * dnVj_dKj;

          // F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V)
          // ∂F_i/∂K_j = δ_ij/K_i - dlnPhiL_dKj + dlnPhiV_dKj
          dFdy[i][j] = dlnK_dK - dlnPhiL_dKj + dlnPhiV_dKj;
        }

        // ∂F_i/∂β - composition changes with vapor fraction
        double dlnPhiL_dBeta = 0.0;
        double dlnPhiV_dBeta = 0.0;
        for (int k = 0; k < nc; k++) {
          double dxk_dBeta = computeDxDBeta(k, z, kValues, beta);
          double dyk_dBeta = computeDyDBeta(k, z, kValues, beta);

          // Convert to molar derivatives
          double dnLk_dBeta = nLiquid * dxk_dBeta;
          double dnVk_dBeta = nVapor * dyk_dBeta;

          dlnPhiL_dBeta += jacL.getDlnPhidn(i, k) * dnLk_dBeta;
          dlnPhiV_dBeta += jacV.getDlnPhidn(i, k) * dnVk_dBeta;
        }
        // F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V)
        // ∂F_i/∂β = 0 - dlnPhiL_dBeta + dlnPhiV_dBeta
        dFdy[i][nc] = -dlnPhiL_dBeta + dlnPhiV_dBeta;

        // ∂F_i/∂T: F_i = ln(K_i) - ln(φ_i^L) + ln(φ_i^V)
        // At fixed composition, ∂F_i/∂T = -∂ln(φ_i^L)/∂T + ∂ln(φ_i^V)/∂T
        dFdT[i][0] = -jacL.getDlnPhidT(i) + jacV.getDlnPhidT(i);

        // ∂F_i/∂P: similarly
        dFdP[i][0] = -jacL.getDlnPhidP(i) + jacV.getDlnPhidP(i);

        // ∂F_i/∂z_j = 0 (feed composition doesn't directly affect fugacity at fixed x, y)
        for (int j = 0; j < nc; j++) {
          dFdz[i][j] = 0.0;
        }
      }

      // Rachford-Rice equation: Σ z_i*(K_i - 1)/(1 + β*(K_i - 1)) = 0
      // ∂RR/∂K_j
      for (int j = 0; j < nc; j++) {
        double denom = 1.0 + beta * (kValues[j] - 1.0);
        dFdy[nc][j] = z[j] / denom - z[j] * (kValues[j] - 1.0) * beta / (denom * denom);
      }

      // ∂RR/∂β
      double dRRdBeta = 0.0;
      for (int i = 0; i < nc; i++) {
        double Ki_m1 = kValues[i] - 1.0;
        double denom = 1.0 + beta * Ki_m1;
        dRRdBeta -= z[i] * Ki_m1 * Ki_m1 / (denom * denom);
      }
      dFdy[nc][nc] = dRRdBeta;

      // ∂RR/∂T = 0, ∂RR/∂P = 0 (direct effect, not through K)
      dFdT[nc][0] = 0.0;
      dFdP[nc][0] = 0.0;

      // ∂RR/∂z_j
      for (int j = 0; j < nc; j++) {
        double denom = 1.0 + beta * (kValues[j] - 1.0);
        dFdz[nc][j] = (kValues[j] - 1.0) / denom;
      }

      // Solve linear system: dy/dθ = -(∂F/∂y)^(-1) * (∂F/∂θ)
      double[][] invJac = invertMatrix(dFdy);
      if (invJac == null) {
        cachedFlashGradients = new FlashGradients(nc, "Jacobian is singular");
        return cachedFlashGradients;
      }

      // Compute dy/dT = -invJac * dF/dT
      double[] dydT = matrixVectorMultiply(invJac, getColumn(dFdT, 0));
      negate(dydT);

      // Compute dy/dP = -invJac * dF/dP
      double[] dydP = matrixVectorMultiply(invJac, getColumn(dFdP, 0));
      negate(dydP);

      // Compute dy/dz = -invJac * dF/dz
      double[][] dydz = matrixMultiply(invJac, dFdz);
      negateMatrix(dydz);

      // Extract gradients
      double[] dKdT = new double[nc];
      double[] dKdP = new double[nc];
      double[][] dKdz = new double[nc][nc];
      double dBetadT = dydT[nc];
      double dBetadP = dydP[nc];
      double[] dBetadz = new double[nc];

      for (int i = 0; i < nc; i++) {
        dKdT[i] = dydT[i];
        dKdP[i] = dydP[i];
        dBetadz[i] = dydz[nc][i];
        for (int j = 0; j < nc; j++) {
          dKdz[i][j] = dydz[i][j];
        }
      }

      cachedFlashGradients = new FlashGradients(kValues, beta, dKdT, dKdP, dKdz, dBetadT, dBetadP,
          dBetadz, componentNames);
      gradientsComputed = true;

      return cachedFlashGradients;

    } catch (Exception e) {
      logger.error("Error computing flash gradients", e);
      cachedFlashGradients = new FlashGradients(nc, "Error: " + e.getMessage());
      return cachedFlashGradients;
    }
  }

  /**
   * Compute gradient of a thermodynamic property.
   *
   * @param propertyName name of the property ("density", "enthalpy", "entropy", etc.)
   * @return PropertyGradient containing derivatives
   */
  public PropertyGradient computePropertyGradient(String propertyName) {
    // Ensure physical properties are initialized
    system.initProperties();

    int nc = system.getNumberOfComponents();
    String[] componentNames = new String[nc];
    for (int i = 0; i < nc; i++) {
      componentNames[i] = system.getComponent(i).getComponentName();
    }

    // Get current property value
    double value = getPropertyValue(propertyName);
    String unit = getPropertyUnit(propertyName);

    // Compute numerical derivatives (can be replaced with analytical when available)
    double dT = computeNumericalDerivativeT(propertyName);
    double dP = computeNumericalDerivativeP(propertyName);
    double[] dz = computeNumericalDerivativeZ(propertyName);

    return new PropertyGradient(propertyName, unit, value, dT, dP, dz, componentNames);
  }

  /**
   * Get cached fugacity Jacobians.
   *
   * @return array of FugacityJacobian (index 0 = liquid, 1 = vapor), or null if not computed
   */
  public FugacityJacobian[] getFugacityJacobians() {
    if (cachedFugacityJacobians == null) {
      computeFlashGradients();
    }
    return cachedFugacityJacobians;
  }

  // ==================== Helper Methods ====================

  private double computeDxDK(int j, double[] z, double[] K, double beta) {
    // x_i = z_i / (1 + β*(K_i - 1))
    // dx_j/dK_j = -z_j * β / (1 + β*(K_j-1))^2
    double denom = 1.0 + beta * (K[j] - 1.0);
    return -z[j] * beta / (denom * denom);
  }

  private double computeDyDK(int j, double[] z, double[] K, double beta) {
    // y_j = K_j * x_j
    // dy_j/dK_j = x_j + K_j * dx_j/dK_j
    double denom = 1.0 + beta * (K[j] - 1.0);
    double x_j = z[j] / denom;
    double dxdK = computeDxDK(j, z, K, beta);
    return x_j + K[j] * dxdK;
  }

  private double computeDxDBeta(int i, double[] z, double[] K, double beta) {
    // dx_i/dβ = -z_i * (K_i - 1) / (1 + β*(K_i-1))^2
    double Ki_m1 = K[i] - 1.0;
    double denom = 1.0 + beta * Ki_m1;
    return -z[i] * Ki_m1 / (denom * denom);
  }

  private double computeDyDBeta(int i, double[] z, double[] K, double beta) {
    // y_i = K_i * x_i
    // dy_i/dβ = K_i * dx_i/dβ
    return K[i] * computeDxDBeta(i, z, K, beta);
  }

  private double getPropertyValue(String propertyName) {
    switch (propertyName.toLowerCase()) {
      case "density":
        return system.getDensity("kg/m3");
      case "enthalpy":
        return system.getEnthalpy("J/mol");
      case "entropy":
        return system.getEntropy("J/molK");
      case "cp":
        return system.getCp("J/molK");
      case "cv":
        return system.getCv("J/molK");
      case "compressibility":
      case "z":
        return system.getZ();
      case "molarvolume":
        return system.getMolarVolume();
      case "molarmass":
        return system.getMolarMass("kg/mol");
      case "viscosity":
        return system.getViscosity("kg/msec");
      case "thermalconductivity":
        return system.getThermalConductivity("W/mK");
      case "soundspeed":
        return system.getSoundSpeed("m/s");
      case "joulethomson":
        return system.getJouleThomsonCoefficient("K/bar");
      case "kappa":
      case "cpcvratio":
        return system.getKappa();
      case "gamma":
        return system.getGamma();
      case "gibbsenergy":
        return system.getGibbsEnergy();
      case "internalenergy":
        return system.getInternalEnergy("J/mol");
      case "beta":
      case "vaporfraction":
        return system.getBeta();
      default:
        throw new IllegalArgumentException("Unknown property: " + propertyName
            + ". Supported: density, enthalpy, entropy, cp, cv, compressibility, molarvolume, "
            + "molarmass, viscosity, thermalconductivity, soundspeed, joulethomson, kappa, "
            + "gamma, gibbsenergy, internalenergy, beta");
    }
  }

  private String getPropertyUnit(String propertyName) {
    switch (propertyName.toLowerCase()) {
      case "density":
        return "kg/m3";
      case "enthalpy":
        return "J/mol";
      case "entropy":
      case "cp":
      case "cv":
        return "J/mol/K";
      case "compressibility":
      case "z":
      case "kappa":
      case "cpcvratio":
      case "gamma":
      case "beta":
      case "vaporfraction":
        return "-";
      case "molarvolume":
        return "m3/mol";
      case "molarmass":
        return "kg/mol";
      case "viscosity":
        return "kg/m/s";
      case "thermalconductivity":
        return "W/m/K";
      case "soundspeed":
        return "m/s";
      case "joulethomson":
        return "K/bar";
      case "gibbsenergy":
      case "internalenergy":
        return "J/mol";
      default:
        return "";
    }
  }

  private double computeNumericalDerivativeT(String propertyName) {
    double T0 = system.getTemperature();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    system.setTemperature(T0 + EPSILON);
    ops.TPflash(); // Re-run flash to update equilibrium
    system.init(3);
    system.initProperties();
    double valuePlus = getPropertyValue(propertyName);

    system.setTemperature(T0 - EPSILON);
    ops.TPflash(); // Re-run flash to update equilibrium
    system.init(3);
    system.initProperties();
    double valueMinus = getPropertyValue(propertyName);

    // Restore
    system.setTemperature(T0);
    ops.TPflash(); // Re-run flash to restore original state
    system.init(3);
    system.initProperties();

    return (valuePlus - valueMinus) / (2.0 * EPSILON);
  }

  private double computeNumericalDerivativeP(String propertyName) {
    double P0 = system.getPressure();
    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    system.setPressure(P0 + EPSILON);
    ops.TPflash(); // Re-run flash to update equilibrium
    system.init(3);
    system.initProperties();
    double valuePlus = getPropertyValue(propertyName);

    system.setPressure(P0 - EPSILON);
    ops.TPflash(); // Re-run flash to update equilibrium
    system.init(3);
    system.initProperties();
    double valueMinus = getPropertyValue(propertyName);

    // Restore
    system.setPressure(P0);
    ops.TPflash(); // Re-run flash to restore original state
    system.init(3);
    system.initProperties();

    return (valuePlus - valueMinus) / (2.0 * EPSILON);
  }

  private double[] computeNumericalDerivativeZ(String propertyName) {
    int nc = system.getNumberOfComponents();
    double[] dz = new double[nc];

    // For now, return zeros - full composition derivatives require more complex perturbation
    // This would need to re-run flash with perturbed feed
    return dz;
  }

  // ==================== Linear Algebra Helpers ====================

  private double[][] invertMatrix(double[][] matrix) {
    int n = matrix.length;
    double[][] augmented = new double[n][2 * n];

    // Create augmented matrix [A | I]
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        augmented[i][j] = matrix[i][j];
      }
      augmented[i][n + i] = 1.0;
    }

    // Gaussian elimination with partial pivoting
    for (int col = 0; col < n; col++) {
      // Find pivot
      int maxRow = col;
      double maxVal = Math.abs(augmented[col][col]);
      for (int row = col + 1; row < n; row++) {
        if (Math.abs(augmented[row][col]) > maxVal) {
          maxVal = Math.abs(augmented[row][col]);
          maxRow = row;
        }
      }

      // Check for singularity
      if (maxVal < 1e-15) {
        return null;
      }

      // Swap rows
      double[] temp = augmented[col];
      augmented[col] = augmented[maxRow];
      augmented[maxRow] = temp;

      // Scale pivot row
      double pivot = augmented[col][col];
      for (int j = 0; j < 2 * n; j++) {
        augmented[col][j] /= pivot;
      }

      // Eliminate column
      for (int row = 0; row < n; row++) {
        if (row != col) {
          double factor = augmented[row][col];
          for (int j = 0; j < 2 * n; j++) {
            augmented[row][j] -= factor * augmented[col][j];
          }
        }
      }
    }

    // Extract inverse
    double[][] inverse = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        inverse[i][j] = augmented[i][n + j];
      }
    }
    return inverse;
  }

  private double[] matrixVectorMultiply(double[][] matrix, double[] vector) {
    int n = matrix.length;
    double[] result = new double[n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < vector.length; j++) {
        result[i] += matrix[i][j] * vector[j];
      }
    }
    return result;
  }

  private double[][] matrixMultiply(double[][] a, double[][] b) {
    int m = a.length;
    int n = b[0].length;
    int k = b.length;
    double[][] result = new double[m][n];
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < n; j++) {
        for (int l = 0; l < k; l++) {
          result[i][j] += a[i][l] * b[l][j];
        }
      }
    }
    return result;
  }

  private double[] getColumn(double[][] matrix, int col) {
    double[] result = new double[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      result[i] = matrix[i][col];
    }
    return result;
  }

  private void negate(double[] vector) {
    for (int i = 0; i < vector.length; i++) {
      vector[i] = -vector[i];
    }
  }

  private void negateMatrix(double[][] matrix) {
    for (int i = 0; i < matrix.length; i++) {
      for (int j = 0; j < matrix[i].length; j++) {
        matrix[i][j] = -matrix[i][j];
      }
    }
  }
}
