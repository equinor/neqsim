package neqsim.process.equipment.distillation;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * A matrix-based solver for DistillationColumn using the Inside-Out algorithm approach. This solver
 * uses a tridiagonal matrix algorithm (TDMA) to solve component material balances.
 */
public class DistillationColumnMatrixSolver {
  static Logger logger = LogManager.getLogger(DistillationColumnMatrixSolver.class);

  private DistillationColumn column;
  private int nTrays;
  private int nComps;
  private String[] componentNames;
  private int[] liquidPhaseIndices;
  private int[] gasPhaseIndices;

  // Solver settings
  private int maxIterations = 1000;
  private double tolerance = 1e-3;
  private double dampingFactor = 0.1;
  private double prevError = 1e10;

  private double[] A;

  private double[] B;

  private double[] C;

  private double[] D;

  private double[] c_prime;

  private double[] d_prime;

  private double[] x_tdma;

  private double[][] feedFlows;
  private double[] feedVapor;
  private double[] feedLiquid;

  public DistillationColumnMatrixSolver(DistillationColumn column) {
    this.column = column;
  }

  /**
   * Solves the distillation column. This method iteratively calculates component material balances,
   * updates compositions and flow rates, and adjusts temperatures until convergence or maximum
   * iterations are reached.
   *
   * @param id the UUID for tracking the calculation identifier through product streams
   */
  public void solve(UUID id) {
    this.nTrays = column.trays.size();
    if (nTrays == 0) {
      return;
    }

    SystemInterface fluid = column.trays.get(0).getThermoSystem();
    this.nComps = fluid.getNumberOfComponents();
    this.componentNames = fluid.getComponentNames();

    liquidPhaseIndices = new int[nTrays];
    gasPhaseIndices = new int[nTrays];

    // Initialize cache arrays
    A = new double[nTrays];
    B = new double[nTrays];
    C = new double[nTrays];
    D = new double[nTrays];
    c_prime = new double[nTrays];
    d_prime = new double[nTrays];
    x_tdma = new double[nTrays];

    // Pre-calculate feeds
    feedFlows = new double[nTrays][nComps];
    feedVapor = new double[nTrays];
    feedLiquid = new double[nTrays];

    for (int j = 0; j < nTrays; j++) {
      feedVapor[j] = getFeedFlowVapor(j);
      feedLiquid[j] = getFeedFlowLiquid(j);
      for (int c = 0; c < nComps; c++) {
        feedFlows[j][c] = getFeedFlow(j, c);
      }
    }

    // Reset damping
    this.dampingFactor = 0.5;
    this.prevError = 1e10;

    // Initialize profiles if needed (assuming column.init() was called)

    for (int iter = 0; iter < maxIterations; iter++) {
      updatePhaseIndices();
      // 1. Calculate K-values and Temperatures/Flows from previous iteration
      // (or initialization)

      // 2. Solve Component Material Balances (Matrix Step)
      double[][] x = solveComponentBalances();

      // 3. Update Compositions and Flow Rates
      updateCompositionsAndFlows(x);

      // 4. Update Temperatures (Bubble Point / Enthalpy Balance)
      double error = updateTemperaturesAndEnergy();

      column.setError(error); // Update column error status

      if (error < tolerance) {
        logger.info("Matrix solver converged in " + iter + " iterations.");
        break;
      }

      prevError = error;
    }

    // Update column product streams
    column.gasOutStream
        .setThermoSystem(column.trays.get(nTrays - 1).getGasOutStream().getThermoSystem().clone());
    column.liquidOutStream
        .setThermoSystem(column.trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    column.gasOutStream.setCalculationIdentifier(id);
    column.liquidOutStream.setCalculationIdentifier(id);
  }

  private void updatePhaseIndices() {
    for (int j = 0; j < nTrays; j++) {
      SystemInterface system = column.trays.get(j).getThermoSystem();
      gasPhaseIndices[j] = -1;
      liquidPhaseIndices[j] = -1;

      if (system.hasPhaseType("gas")) {
        for (int i = 0; i < system.getNumberOfPhases(); i++) {
          if (system.getPhase(i).getPhaseTypeName().equals("gas")) {
            gasPhaseIndices[j] = i;
            break;
          }
        }
        liquidPhaseIndices[j] = (gasPhaseIndices[j] == 0) ? 1 : 0;
      } else {
        // Fallback
        if (system.getNumberOfPhases() == 1
            && !system.getPhase(0).getPhaseTypeName().equals("gas")) {
          liquidPhaseIndices[j] = 0;
        } else if (system.getNumberOfPhases() > 1) {
          liquidPhaseIndices[j] = 1; // Default
          gasPhaseIndices[j] = 0;
        }
      }
    }
  }

  /**
   * Solves the tridiagonal matrix for each component to find liquid molar flow rates. Returns
   * x[tray][component] (liquid mole fractions) or l[tray][component] (molar flows). Let's return
   * molar flows l[tray][component].
   *
   * @return liquid molar flow rates as l[tray][component]
   */
  private double[][] solveComponentBalances() {
    double[][] l_flows = new double[nTrays][nComps];

    // Pre-fetch V and L for all trays to avoid repeated lookups
    double[] V_trays = new double[nTrays];
    double[] L_trays = new double[nTrays];

    for (int j = 0; j < nTrays; j++) {
      SystemInterface system = column.trays.get(j).getThermoSystem();
      int gasIdx = gasPhaseIndices[j];
      int liqIdx = liquidPhaseIndices[j];

      V_trays[j] = (gasIdx != -1) ? system.getPhase(gasIdx).getNumberOfMolesInPhase() : 0.0;
      L_trays[j] = (liqIdx != -1) ? system.getPhase(liqIdx).getNumberOfMolesInPhase() : 0.0;
      if (L_trays[j] < 1e-12) {
        L_trays[j] = 1e-12; // Avoid division by zero
      }
    }

    // For each component, build and solve the tridiagonal system
    // A_j * l_{j-1} + B_j * l_j + C_j * l_{j+1} = D_j

    for (int c = 0; c < nComps; c++) {
      // Reuse A, B, C, D arrays

      for (int j = 0; j < nTrays; j++) {
        // Calculate Stripping Factor S = K * V / L
        double S_curr = 0.0;
        if (gasPhaseIndices[j] != -1) {
          double K = column.trays.get(j).getThermoSystem().getComponent(c).getK();
          S_curr = K * V_trays[j] / L_trays[j];
        }

        double S_prev = 0.0;
        if (j > 0 && gasPhaseIndices[j - 1] != -1) {
          double K_prev = column.trays.get(j - 1).getThermoSystem().getComponent(c).getK();
          S_prev = K_prev * V_trays[j - 1] / L_trays[j - 1];
        }

        // A corresponds to l_{j-1}
        A[j] = (j > 0) ? S_prev : 0.0;

        // B corresponds to l_j
        B[j] = -(1.0 + S_curr);

        // C corresponds to l_{j+1}
        C[j] = (j < nTrays - 1) ? 1.0 : 0.0;

        // D is feed term
        D[j] = -feedFlows[j][c];
      }

      // Solve TDMA
      solveTDMA(nTrays);

      for (int j = 0; j < nTrays; j++) {
        double l_new = Math.max(1e-20, x_tdma[j]); // Ensure non-negative flows

        // Damping to improve stability
        double l_old = 0.0;
        int liqIdx = liquidPhaseIndices[j];
        if (liqIdx != -1) {
          l_old = column.trays.get(j).getThermoSystem().getPhase(liqIdx).getComponent(c)
              .getNumberOfMolesInPhase();
        }

        // Use class-level dampingFactor
        l_flows[j][c] = dampingFactor * l_new + (1.0 - dampingFactor) * l_old;

        if (Double.isNaN(l_flows[j][c])) {
          l_flows[j][c] = l_old;
        }
      }
    }
    return l_flows;
  }

  // Removed getStrippingFactor and getFeedFlow as they are inlined/pre-calculated

  private void solveTDMA(int n) {
    // Uses class fields A, B, C, D, c_prime, d_prime, x_tdma

    c_prime[0] = C[0] / B[0];
    d_prime[0] = D[0] / B[0];

    for (int i = 1; i < n; i++) {
      double temp = B[i] - A[i] * c_prime[i - 1];
      // Avoid division by zero if temp is too small?
      // In distillation, diagonal dominance usually ensures temp != 0
      double invTemp = 1.0 / temp;

      if (i < n - 1) {
        c_prime[i] = C[i] * invTemp;
      }
      d_prime[i] = (D[i] - A[i] * d_prime[i - 1]) * invTemp;
    }

    x_tdma[n - 1] = d_prime[n - 1];
    for (int i = n - 2; i >= 0; i--) {
      x_tdma[i] = d_prime[i] - c_prime[i] * x_tdma[i + 1];
    }
  }

  private void updateCompositionsAndFlows(double[][] l_flows) {
    // 1. Calculate Sum-Rates Flows (L_SR, V_SR)
    double[] L_SR = new double[nTrays];
    double[] V_SR = new double[nTrays];
    double[][] v_flows = new double[nTrays][nComps];

    for (int j = 0; j < nTrays; j++) {
      SystemInterface system = column.trays.get(j).getThermoSystem();
      int gasIdx = gasPhaseIndices[j];
      int liqIdx = liquidPhaseIndices[j];

      double V_tray = (gasIdx != -1) ? system.getPhase(gasIdx).getNumberOfMolesInPhase() : 0.0;
      double L_tray = (liqIdx != -1) ? system.getPhase(liqIdx).getNumberOfMolesInPhase() : 0.0;
      if (L_tray < 1e-12) {
        L_tray = 1e-12;
      }

      for (int c = 0; c < nComps; c++) {
        L_SR[j] += l_flows[j][c];
      }

      // Calculate Vapor Flows based on Stripping Factors
      for (int c = 0; c < nComps; c++) {
        double S = 0.0;
        if (gasIdx != -1) {
          double K = system.getComponent(c).getK();
          S = K * V_tray / L_tray;
        }

        v_flows[j][c] = S * l_flows[j][c];
        V_SR[j] += v_flows[j][c];
      }
    }

    // 2. Calculate CMO Flows (L_CMO, V_CMO)
    double[] L_CMO = new double[nTrays];
    double[] V_CMO = new double[nTrays];

    // Anchors from Sum-Rates (reflecting equilibrium at ends)
    double V_bottom = V_SR[0];
    double L_top = L_SR[nTrays - 1];

    // Propagate V upwards: V_j = V_{j-1} + F^V_j
    // V_0 is anchor.
    V_CMO[0] = V_bottom;
    for (int j = 1; j < nTrays; j++) {
      double feedV = feedVapor[j];
      V_CMO[j] = V_CMO[j - 1] + feedV;
    }

    // Propagate L downwards: L_j = L_{j+1} + F^L_j
    // L_{N-1} is anchor.
    L_CMO[nTrays - 1] = L_top;
    for (int j = nTrays - 2; j >= 0; j--) {
      double feedL = feedLiquid[j];
      L_CMO[j] = L_CMO[j + 1] + feedL;
    }

    // 3. Blend Flows (Weighted towards CMO for stability)
    double cmoWeight = 0.95; // High weight for CMO

    for (int j = 0; j < nTrays; j++) {
      SimpleTray tray = column.trays.get(j);
      SystemInterface system = tray.getThermoSystem();
      int gasPhaseIndex = gasPhaseIndices[j];
      int liquidPhaseIndex = liquidPhaseIndices[j];

      double totalL = cmoWeight * L_CMO[j] + (1.0 - cmoWeight) * L_SR[j];
      double totalV = cmoWeight * V_CMO[j] + (1.0 - cmoWeight) * V_SR[j];

      // Apply damping with previous iteration
      double totalL_old =
          (liquidPhaseIndex != -1) ? system.getPhase(liquidPhaseIndex).getNumberOfMolesInPhase()
              : 0.0;
      double totalV_old =
          (gasPhaseIndex != -1) ? system.getPhase(gasPhaseIndex).getNumberOfMolesInPhase() : 0.0;

      totalL = dampingFactor * totalL + (1.0 - dampingFactor) * totalL_old;
      totalV = dampingFactor * totalV + (1.0 - dampingFactor) * totalV_old;

      // Update Liquid Composition and Moles
      if (liquidPhaseIndex != -1) {
        if (totalL > 1e-12) {
          double scale = totalL / L_SR[j]; // Scale component flows to match new Total L
          for (int c = 0; c < nComps; c++) {
            l_flows[j][c] *= scale;
            double x = l_flows[j][c] / totalL;
            system.getPhase(liquidPhaseIndex).getComponent(c).setx(x);
            system.getPhase(liquidPhaseIndex).getComponent(c)
                .setNumberOfMolesInPhase(l_flows[j][c]);
          }
        } else {
          for (int c = 0; c < nComps; c++) {
            system.getPhase(liquidPhaseIndex).getComponent(c).setNumberOfMolesInPhase(0.0);
          }
        }
      }

      // Update Vapor Composition and Moles
      if (gasPhaseIndex != -1) {
        if (totalV > 1e-12) {
          double scale = totalV / V_SR[j]; // Scale component flows to match new Total V
          for (int c = 0; c < nComps; c++) {
            v_flows[j][c] *= scale;
            double y = v_flows[j][c] / totalV;
            system.getPhase(gasPhaseIndex).getComponent(c).setx(y);
            system.getPhase(gasPhaseIndex).getComponent(c).setNumberOfMolesInPhase(v_flows[j][c]);
          }
        } else {
          for (int c = 0; c < nComps; c++) {
            system.getPhase(gasPhaseIndex).getComponent(c).setNumberOfMolesInPhase(0.0);
          }
        }
      }

      // Update Total System Flow and z
      double totalFlow = totalL + totalV;
      system.setTotalNumberOfMoles(totalFlow);
      if (totalFlow > 1e-12) {
        for (int c = 0; c < nComps; c++) {
          double z = (l_flows[j][c] + v_flows[j][c]) / totalFlow;
          system.getComponent(c).setz(z);
        }
      }

      // Skip init(1) here to save time. K-values will be updated in updateTemperaturesAndEnergy
      // or at the start of next iteration.
      // However, we might need to update some properties if used immediately.
      // But updateTemperaturesAndEnergy uses K (old) and x (new).
      // So we can skip this.
    }
  }

  private double getFeedFlowVapor(int trayIndex) {
    double vaporFlow = 0.0;
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem == null || !feedSystem.hasPhaseType("gas")) {
        continue;
      }
      vaporFlow += feedSystem.getPhase("gas").getFlowRate("mole/sec");
    }
    return vaporFlow;
  }

  private double getFeedFlowLiquid(int trayIndex) {
    double liquidFlow = 0.0;
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem == null) {
        continue;
      }
      double total = feedSystem.getFlowRate("mole/sec");
      if (feedSystem.hasPhaseType("gas")) {
        total -= feedSystem.getPhase("gas").getFlowRate("mole/sec");
      }
      if (total > 0.0) {
        liquidFlow += total;
      }
    }
    return liquidFlow;
  }

  private double getFeedFlow(int trayIndex, int componentIndex) {
    double componentFlow = 0.0;
    String componentName = componentNames[componentIndex];
    for (StreamInterface feed : column.getFeedStreams(trayIndex)) {
      SystemInterface feedSystem = feed.getThermoSystem();
      if (feedSystem == null || feedSystem.getComponent(componentName) == null) {
        continue;
      }
      componentFlow += feedSystem.getComponent(componentName).getFlowRate("mole/sec");
    }
    return componentFlow;
  }

  private double updateTemperaturesAndEnergy() {
    double maxError = 0.0;

    for (int j = 0; j < nTrays; j++) {
      SimpleTray tray = column.trays.get(j);
      SystemInterface system = tray.getThermoSystem();
      int liquidPhaseIndex = liquidPhaseIndices[j];
      double oldT = system.getTemperature();

      if (tray.isSetOutTemperature()) {
        continue;
      }

      if (liquidPhaseIndex != -1) {
        double sumYi = 0.0;
        for (int k = 0; k < nComps; k++) {
          sumYi += system.getComponent(k).getK()
              * system.getPhase(liquidPhaseIndex).getComponent(k).getx();
        }

        // Soft update: dT = T * (1 - sumYi) / (sumYi * factor)
        // If sumYi > 1, T needs to decrease. (1-sumYi) is negative. Correct.
        // Factor depends on d(lnK)/d(lnT). Approx 10-20.
        // double factor = 5.0;
        // double dT = oldT * (1.0 - sumYi) / (sumYi * factor);

        // Analytical update using dHvap
        // d(lnK)/dT = dHvap / (R * T^2)
        // sum(y_i) - 1 = sum(K_i * x_i) - 1
        // d(sum(y_i))/dT = sum(x_i * dK_i/dT) = sum(x_i * K_i * dHvap_i / (R * T^2))
        // Newton step: dT = - (sum(y_i) - 1) / d(sum(y_i))/dT
        // dT = (1 - sum(y_i)) / sum(y_i * dHvap_i / (R * T^2))
        // dT = (1 - sum(y_i)) * R * T^2 / sum(y_i * dHvap_i)

        // Rigorous Analytical Update
        // Requires init(2) for enthalpy
        system.init(2);

        double R = 8.314;
        int gasPhaseIndex = gasPhaseIndices[j];
        double dH = 30000.0;

        if (gasPhaseIndex != -1) {
          double h_gas = system.getPhase(gasPhaseIndex).getEnthalpy("J/mol");
          double h_liq = system.getPhase(liquidPhaseIndex).getEnthalpy("J/mol");
          dH = h_gas - h_liq;
          if (dH < 1000.0) {
            dH = 30000.0;
          }
        }

        // Log-Newton Update: dT = - ln(sumYi) * R * T^2 / dH
        double dT = -Math.log(sumYi) * R * oldT * oldT / dH;

        // Limit step
        if (dT > 5.0) {
          dT = 5.0;
        }
        if (dT < -5.0) {
          dT = -5.0;
        }

        double newT = oldT + dT;

        // Bounds
        if (newT < 50.0) {
          newT = 50.0;
        }
        if (newT > 1000.0) {
          newT = 1000.0;
        }

        maxError = Math.max(maxError, Math.abs(newT - oldT));

        tray.setTemperature(newT);
        system.setTemperature(newT);

        try {
          system.init(1);
        } catch (Exception e) {
        }
      }
    }
    return maxError;
  }
}
