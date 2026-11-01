package neqsim.thermo.util.derivatives;

import java.io.Serializable;
import java.util.Locale;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.linsol.LinearSolverDense;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Sensitivities of a converged nonreactive, single-phase or vapor-liquid equilibrium.
 *
 * <p>
 * Flash sensitivities solve the implicit equilibrium Jacobian in log K and vapor fraction. Composition columns use the
 * normalized addition z(h) = (z + h e_j)/(1+h), at fixed total moles. For a tangent direction with sum(dz) = 0,
 * multiplying these columns by dz gives the constrained composition derivative. Pressure derivatives are per bar.
 * </p>
 *
 * <p>
 * Batched property sensitivities combine the implicit phase-split derivative with centered local EOS/property
 * evaluations, without rerunning a flash. The scalar property API retains full equilibrium finite differences. A batch
 * shares the Jacobian factorization and perturbed states across properties. All calculations use detached snapshots of
 * the supplied current fluid; results are not cached across calls. Reactions, ions, solids, liquid-liquid splits and
 * three-phase states are outside this interface.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DifferentiableFlash implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Current caller-owned equilibrium; never initialized or perturbed by this wrapper. */
  private final SystemInterface system;

  /**
   * Creates a derivative wrapper for an already flashed fluid.
   *
   * @param system current thermodynamic equilibrium
   * @throws IllegalArgumentException if the system is null
   */
  public DifferentiableFlash(SystemInterface system) {
    if (system == null) {
      throw new IllegalArgumentException("System cannot be null");
    }
    this.system = system;
  }

  /**
   * Extracts current fugacity derivatives on a detached snapshot.
   *
   * @param phaseIndex active phase index
   * @return fugacity Jacobian, with pressure derivatives per bar
   * @throws IllegalArgumentException if the index or fluid is unsupported
   */
  public FugacityJacobian extractFugacityJacobian(int phaseIndex) {
    SystemInterface work = snapshot();
    if (phaseIndex < 0 || phaseIndex >= work.getNumberOfPhases()) {
      throw new IllegalArgumentException("Phase index must identify an active phase");
    }
    work.init(3);
    return extract(work, phaseIndex);
  }

  /**
   * Extracts derivatives from an initialized local state.
   *
   * @param work initialized snapshot
   * @param phaseIndex active phase index
   * @return detached derivative arrays
   */
  private FugacityJacobian extract(SystemInterface work, int phaseIndex) {
    PhaseInterface phase = work.getPhase(phaseIndex);
    int nc = work.getNumberOfComponents();
    double[] phi = new double[nc];
    double[] dt = new double[nc];
    double[] dp = new double[nc];
    double[][] dn = new double[nc][nc];
    String[] names = new String[nc];
    for (int i = 0; i < nc; i++) {
      ComponentInterface c = phase.getComponent(i);
      names[i] = c.getComponentName();
      phi[i] = c.getLogFugacityCoefficient();
      dt[i] = c.getdfugdt();
      dp[i] = c.getdfugdp();
      for (int j = 0; j < nc; j++) {
        dn[i][j] = c.getdfugdn(j);
      }
    }
    return new FugacityJacobian(phaseIndex, phase.getType().toString(), phi, dt, dp, dn, names);
  }

  /**
   * Computes current VLE sensitivities, rejecting unsupported or singular equilibria.
   *
   * @return gradients; inspect isValid before reading any derivative
   */
  public FlashGradients computeFlashGradients() {
    try {
      SystemInterface work = snapshot();
      work.init(3);
      return flashGradients(work);
    } catch (RuntimeException ex) {
      return new FlashGradients(system.getNumberOfComponents(), ex.getMessage());
    }
  }

  /**
   * Builds and solves all temperature, pressure and normalized-feed columns with one LU factorization.
   *
   * @param work initialized equilibrium snapshot
   * @return verified finite sensitivities
   * @throws IllegalArgumentException if the state is not an interior VLE equilibrium
   * @throws IllegalStateException if the derivative system cannot be solved
   */
  private FlashGradients flashGradients(SystemInterface work) {
    int nc = work.getNumberOfComponents();
    if (work.getNumberOfPhases() != 2) {
      throw new IllegalArgumentException("Flash gradients require exactly two vapor-liquid phases");
    }
    int vapor = vaporIndex(work);
    if (vapor < 0) {
      throw new IllegalArgumentException("Flash gradients require a gas and a liquid phase");
    }
    int liquid = 1 - vapor;
    double beta = work.getPhase(vapor).getBeta();
    if (!(beta > 1.0e-12 && beta < 1.0 - 1.0e-12)) {
      throw new IllegalArgumentException("Phase-boundary sensitivities require a one-sided formulation");
    }
    FugacityJacobian jl = extract(work, liquid);
    FugacityJacobian jv = extract(work, vapor);
    double nl = work.getPhase(liquid).getNumberOfMolesInPhase();
    double nv = work.getPhase(vapor).getNumberOfMolesInPhase();
    double[] z = work.getMolarComposition();
    double[] k = new double[nc];
    double[] denom = new double[nc];
    String[] names = new String[nc];
    double rr = 0.0;
    for (int i = 0; i < nc; i++) {
      double x = work.getPhase(liquid).getComponent(i).getx();
      double y = work.getPhase(vapor).getComponent(i).getx();
      if (!(x > 0.0 && y > 0.0 && z[i] > 0.0)) {
        throw new IllegalArgumentException("Two-phase sensitivities require positive active component fractions");
      }
      k[i] = y / x;
      denom[i] = 1.0 + beta * (k[i] - 1.0);
      names[i] = work.getComponent(i).getComponentName();
      double residual = Math.log(k[i]) - jl.getLnPhi(i) + jv.getLnPhi(i);
      if (!Double.isFinite(residual) || Math.abs(residual) > 1.0e-7) {
        throw new IllegalArgumentException("Flash gradients require converged interphase fugacities");
      }
      rr += z[i] * (k[i] - 1.0) / denom[i];
    }
    DMatrixRMaj a = new DMatrixRMaj(nc + 1, nc + 1);
    DMatrixRMaj rhs = new DMatrixRMaj(nc + 1, nc + 2);
    for (int i = 0; i < nc; i++) {
      double betaColumn = 0.0;
      double[] feed = new double[nc];
      double meanFeed = 0.0;
      for (int j = 0; j < nc; j++) {
        double dxdk = -z[j] * beta * k[j] / (denom[j] * denom[j]);
        double dydk = k[j] * (z[j] / denom[j] + dxdk);
        a.set(i, j, (i == j ? 1.0 : 0.0) - nl * jl.getDlnPhidn(i, j) * dxdk + nv * jv.getDlnPhidn(i, j) * dydk);
        double dxdb = -z[j] * (k[j] - 1.0) / (denom[j] * denom[j]);
        betaColumn += -nl * jl.getDlnPhidn(i, j) * dxdb + nv * jv.getDlnPhidn(i, j) * k[j] * dxdb;
        feed[j] = (-nl * jl.getDlnPhidn(i, j) + nv * jv.getDlnPhidn(i, j) * k[j]) / denom[j];
        meanFeed += z[j] * feed[j];
      }
      a.set(i, nc, betaColumn);
      rhs.set(i, 0, jl.getDlnPhidT(i) - jv.getDlnPhidT(i));
      rhs.set(i, 1, jl.getDlnPhidP(i) - jv.getDlnPhidP(i));
      for (int j = 0; j < nc; j++) {
        rhs.set(i, j + 2, meanFeed - feed[j]);
      }
    }
    double rrBeta = 0.0;
    for (int j = 0; j < nc; j++) {
      a.set(nc, j, z[j] * k[j] / (denom[j] * denom[j]));
      rrBeta -= z[j] * Math.pow((k[j] - 1.0) / denom[j], 2.0);
      rhs.set(nc, j + 2, rr - (k[j] - 1.0) / denom[j]);
    }
    a.set(nc, nc, rrBeta);
    DMatrixRMaj solution = solve(a, rhs);
    double[] kt = new double[nc];
    double[] kp = new double[nc];
    double[][] kz = new double[nc][nc];
    double[] bz = new double[nc];
    for (int i = 0; i < nc; i++) {
      kt[i] = k[i] * solution.get(i, 0);
      kp[i] = k[i] * solution.get(i, 1);
      bz[i] = solution.get(nc, i + 2);
      for (int j = 0; j < nc; j++) {
        kz[i][j] = k[i] * solution.get(i, j + 2);
        requireFinite(kz[i][j]);
      }
      requireFinite(kt[i]);
      requireFinite(kp[i]);
    }
    return new FlashGradients(k, beta, kt, kp, kz, solution.get(nc, 0), solution.get(nc, 1), bz, names);
  }

  /**
   * Solves a multi-right-hand-side system without forming an inverse and checks backward error.
   *
   * @param a coefficient matrix, retained for verification
   * @param rhs right hand sides
   * @return finite solution with small scaled residual
   * @throws IllegalStateException if factorization or residual verification fails
   */
  private DMatrixRMaj solve(DMatrixRMaj a, DMatrixRMaj rhs) {
    for (double value : a.data) {
      if (!Double.isFinite(value)) {
        throw new IllegalStateException("Non-finite equilibrium Jacobian");
      }
    }
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(a.numRows);
    if (!solver.setA(a.copy())) {
      throw new IllegalStateException("Singular equilibrium Jacobian");
    }
    DMatrixRMaj result = new DMatrixRMaj(a.numCols, rhs.numCols);
    solver.solve(rhs.copy(), result);
    for (int col = 0; col < rhs.numCols; col++) {
      for (int row = 0; row < a.numRows; row++) {
        double residual = -rhs.get(row, col);
        double scale = Math.abs(rhs.get(row, col));
        for (int j = 0; j < a.numCols; j++) {
          double term = a.get(row, j) * result.get(j, col);
          residual += term;
          scale += Math.abs(term);
        }
        if (!Double.isFinite(residual) || Math.abs(residual) > 1.0e-10 * Math.max(1.0, scale)) {
          throw new IllegalStateException("Equilibrium sensitivity linear solve failed its residual check");
        }
      }
    }
    return result;
  }

  /**
   * Computes a scalar property sensitivity with full equilibrium finite differences on detached clones. The additive
   * batch API provides faster implicit/local property sensitivities.
   *
   * @param propertyName supported property name
   * @return molar or intensive property gradients with normalized composition columns
   * @throws IllegalArgumentException if the property or state is unsupported
   * @throws IllegalStateException if equilibrium differentiation fails
   */
  public PropertyGradient computePropertyGradient(String propertyName) {
    if (propertyName == null) {
      throw new IllegalArgumentException("Property name cannot be null");
    }
    String name = propertyName.toLowerCase(Locale.ROOT);
    unit(name);
    if ("molarmass".equals(name)) {
      return computePropertyGradients(new String[] {propertyName})[0];
    }
    SystemInterface work = snapshot();
    validatePropertyComposition(work, new String[] {name});
    work.init(work.getNumberOfPhases() == 2 ? 3 : 2);
    if (work.getNumberOfPhases() == 2) {
      flashGradients(work);
    }
    initializeProperties(work, new String[] {name});
    double baseValue = value(work, name);
    requireFinite(baseValue);
    int nc = work.getNumberOfComponents();
    double[] derivatives = new double[nc + 2];
    String[] components = new String[nc];
    for (int j = 0; j < nc; j++) {
      components[j] = work.getComponent(j).getComponentName();
    }
    for (int direction = 0; direction < nc + 2; direction++) {
      double h = direction == 0 ? 1.0e-5 * work.getTemperature()
          : direction == 1 ? 1.0e-5 * work.getPressure() : 1.0e-5;
      double z = direction < 2 ? 1.0 : work.getComponent(direction - 2).getz();
      if (direction >= 2 && z > 0.0) {
        h = Math.min(h, 0.1 * z);
      }
      boolean oneSided = direction >= 2 && z == 0.0;
      double plus = value(reflash(work, direction, h, name), name);
      double minus = value(reflash(work, direction, oneSided ? 2.0 * h : -h, name), name);
      derivatives[direction] = oneSided ? (-3.0 * baseValue + 4.0 * plus - minus) / (2.0 * h)
          : (plus - minus) / (2.0 * h);
      requireFinite(derivatives[direction]);
    }
    double[] dz = new double[nc];
    System.arraycopy(derivatives, 2, dz, 0, nc);
    return new PropertyGradient(propertyName, unit(name), baseValue, derivatives[0], derivatives[1], dz, components);
  }

  /**
   * Evaluates the scalar API's independent full-reflash perturbation.
   *
   * @param work baseline equilibrium
   * @param direction T, P or normalized feed column
   * @param h signed step
   * @param name requested property
   * @return freshly equilibrated local fluid
   * @throws IllegalStateException if the perturbation changes topology
   */
  private SystemInterface reflash(SystemInterface work, int direction, double h, String name) {
    SystemInterface perturbed = work.clone();
    if (direction == 0) {
      perturbed.setTemperature(work.getTemperature() + h);
    } else if (direction == 1) {
      perturbed.setPressure(work.getPressure() + h);
    } else {
      double[] z = work.getMolarComposition();
      for (int i = 0; i < z.length; i++) {
        z[i] = (z[i] + (i == direction - 2 ? h : 0.0)) / (1.0 + h);
      }
      perturbed.setMolarComposition(z);
    }
    new ThermodynamicOperations(perturbed).TPflash();
    if (perturbed.getNumberOfPhases() != work.getNumberOfPhases()
        || (vaporIndex(perturbed) < 0) != (vaporIndex(work) < 0)) {
      throw new IllegalStateException("Property perturbation crosses a phase boundary");
    }
    perturbed.init(2);
    initializeProperties(perturbed, new String[] {name});
    return perturbed;
  }

  /**
   * Rejects mathematically singular zero-feed entropy and Gibbs composition derivatives.
   *
   * @param work baseline equilibrium
   * @param names requested property names
   * @throws IllegalArgumentException if a requested composition derivative is singular
   */
  private void validatePropertyComposition(SystemInterface work, String[] names) {
    for (String name : names) {
      if ("entropy".equals(name) || "gibbsenergy".equals(name)) {
        for (int j = 0; j < work.getNumberOfComponents(); j++) {
          if (work.getComponent(j).getz() == 0.0) {
            throw new IllegalArgumentException("Entropy/Gibbs composition derivatives are singular at zero feed");
          }
        }
      }
    }
  }

  /**
   * Computes property gradients sharing one implicit solve and one set of local perturbed states.
   *
   * @param propertyNames supported property names, in requested output order
   * @return gradients in the same order
   * @throws IllegalArgumentException if an input name or state is unsupported
   * @throws IllegalStateException if the local derivative is undefined or non-finite
   */
  public PropertyGradient[] computePropertyGradients(String[] propertyNames) {
    if (propertyNames == null) {
      throw new IllegalArgumentException("Property names cannot be null");
    }
    if (propertyNames.length == 0) {
      return new PropertyGradient[0];
    }
    String[] names = new String[propertyNames.length];
    for (int i = 0; i < names.length; i++) {
      if (propertyNames[i] == null) {
        throw new IllegalArgumentException("Property name cannot be null");
      }
      names[i] = propertyNames[i].toLowerCase(Locale.ROOT);
      unit(names[i]);
    }
    SystemInterface work = snapshot();
    validatePropertyComposition(work, names);
    work.init(work.getNumberOfPhases() == 2 ? 3 : 2);
    FlashGradients gradients = work.getNumberOfPhases() == 2 ? flashGradients(work) : null;
    initializeProperties(work, names);
    double[] values = values(work, names);
    int nc = work.getNumberOfComponents();
    double[][] derivatives = new double[names.length][nc + 2];
    boolean localEvaluation = false;
    for (String name : names) {
      localEvaluation |= !"molarmass".equals(name) && !"beta".equals(name) && !"vaporfraction".equals(name);
    }
    for (int direction = 0; direction < nc + 2; direction++) {
      if (!localEvaluation) {
        continue;
      }
      double[][] dx = phaseDirections(work, gradients, direction);
      double db = gradients == null ? 0.0 : betaDirection(gradients, direction);
      double h = direction == 0 ? 1.0e-5 * work.getTemperature()
          : direction == 1 ? 1.0e-5 * work.getPressure() : 1.0e-5;
      boolean oneSided = direction >= 2 && work.getComponent(direction - 2).getz() == 0.0;
      for (int p = 0; p < work.getNumberOfPhases(); p++) {
        double bp = work.getPhase(p).getBeta();
        if (db != 0.0) {
          h = Math.min(h, 0.1 * Math.min(bp, 1.0 - bp) / Math.abs(db));
        }
        for (int j = 0; j < nc; j++) {
          double x = work.getPhase(p).getComponent(j).getx();
          if (x > 0.0 && dx[p][j] != 0.0) {
            h = Math.min(h, 0.1 * x / Math.abs(dx[p][j]));
          }
        }
      }
      if (!(h > 1.0e-14) || !Double.isFinite(h)) {
        throw new IllegalStateException("Property gradient is unresolved at this phase/composition boundary");
      }
      double[] plus = values(shift(work, gradients, dx, db, direction, h, names), names);
      double[] minus = values(shift(work, gradients, dx, db, direction, oneSided ? 2.0 * h : -h, names), names);
      for (int i = 0; i < names.length; i++) {
        derivatives[i][direction] = oneSided ? (-3.0 * values[i] + 4.0 * plus[i] - minus[i]) / (2.0 * h)
            : (plus[i] - minus[i]) / (2.0 * h);
      }
    }
    PropertyGradient[] result = new PropertyGradient[names.length];
    String[] components = new String[nc];
    for (int j = 0; j < nc; j++) {
      components[j] = work.getComponent(j).getComponentName();
    }
    for (int i = 0; i < names.length; i++) {
      double[] dz = new double[nc];
      for (int j = 0; j < nc; j++) {
        dz[j] = derivatives[i][j + 2];
      }
      if ("molarmass".equals(names[i])) {
        derivatives[i][0] = 0.0;
        derivatives[i][1] = 0.0;
        for (int j = 0; j < nc; j++) {
          dz[j] = work.getComponent(j).getMolarMass() - values[i];
        }
      } else if ("beta".equals(names[i]) || "vaporfraction".equals(names[i])) {
        derivatives[i][0] = gradients == null ? 0.0 : gradients.getDBetadT();
        derivatives[i][1] = gradients == null ? 0.0 : gradients.getDBetadP();
        dz = gradients == null ? new double[nc] : gradients.getDBetadz();
      }
      for (double derivative : dz) {
        requireFinite(derivative);
      }
      requireFinite(derivatives[i][0]);
      requireFinite(derivatives[i][1]);
      result[i] = new PropertyGradient(propertyNames[i], unit(names[i]), values[i], derivatives[i][0],
          derivatives[i][1], dz, components);
    }
    return result;
  }

  /**
   * Derives phase compositions along a temperature, pressure or normalized feed direction.
   *
   * @param work equilibrium snapshot
   * @param g implicit sensitivities, or null for a single phase
   * @param direction zero for T, one for P, otherwise component index plus two
   * @return phase composition directional derivatives
   */
  private double[][] phaseDirections(SystemInterface work, FlashGradients g, int direction) {
    int nc = work.getNumberOfComponents();
    double[][] result = new double[work.getNumberOfPhases()][nc];
    int vapor = vaporIndex(work);
    for (int i = 0; i < nc; i++) {
      double z = work.getComponent(i).getz();
      double dz = direction < 2 ? 0.0 : (direction - 2 == i ? 1.0 : 0.0) - z;
      if (g == null) {
        result[0][i] = dz;
      } else {
        double k = g.getKValue(i);
        double dk = direction == 0 ? g.getDKdT(i) : direction == 1 ? g.getDKdP(i) : g.getDKdz(i, direction - 2);
        double d = 1.0 + g.getBeta() * (k - 1.0);
        double x = work.getPhase(1 - vapor).getComponent(i).getx();
        double dx = (dz - x * ((k - 1.0) * betaDirection(g, direction) + g.getBeta() * dk)) / d;
        result[1 - vapor][i] = dx;
        result[vapor][i] = k * dx + x * dk;
      }
    }
    return result;
  }

  /**
   * Selects the vapor-fraction directional derivative.
   *
   * @param g VLE sensitivities
   * @param direction input column
   * @return vapor-fraction derivative
   */
  private double betaDirection(FlashGradients g, int direction) {
    return direction == 0 ? g.getDBetadT() : direction == 1 ? g.getDBetadP() : g.getDBetadz(direction - 2);
  }

  /**
   * Evaluates a local tangent state, preserving phase identity and total inventory without a flash.
   *
   * @param work baseline snapshot
   * @param g VLE derivatives, or null
   * @param dx phase composition directions
   * @param db vapor-fraction direction
   * @param direction input column
   * @param h signed step
   * @param names requested properties
   * @return locally initialized state
   * @throws IllegalStateException if the local step leaves the physical domain
   */
  private SystemInterface shift(SystemInterface work, FlashGradients g, double[][] dx, double db, int direction,
      double h, String[] names) {
    SystemInterface shifted = work.clone();
    shifted.setTemperature(work.getTemperature() + (direction == 0 ? h : 0.0));
    shifted.setPressure(work.getPressure() + (direction == 1 ? h : 0.0));
    int nc = work.getNumberOfComponents();
    int vapor = vaporIndex(work);
    double[] feed = new double[nc];
    for (int p = 0; p < work.getNumberOfPhases(); p++) {
      double bp = work.getPhase(p).getBeta() + (g == null ? 0.0 : (p == vapor ? h * db : -h * db));
      shifted.setBeta(p, bp);
      double sum = 0.0;
      double[] x = new double[nc];
      for (int i = 0; i < nc; i++) {
        x[i] = work.getPhase(p).getComponent(i).getx() + h * dx[p][i];
        if (!(x[i] >= 0.0) || !Double.isFinite(x[i])) {
          throw new IllegalStateException("Property tangent step left the composition domain");
        }
        sum += x[i];
      }
      for (int i = 0; i < nc; i++) {
        x[i] /= sum;
        shifted.getPhase(p).getComponent(i).setx(x[i]);
        feed[i] += bp * x[i];
      }
    }
    for (PhaseInterface phase : shifted.getPhases()) {
      if (phase != null) {
        for (int i = 0; i < nc; i++) {
          phase.getComponent(i).setz(feed[i]);
          phase.getComponent(i).setNumberOfmoles(work.getTotalNumberOfMoles() * feed[i]);
        }
      }
    }
    shifted.init(2);
    initializeProperties(shifted, names);
    return shifted;
  }

  /**
   * Takes and validates a current supported equilibrium snapshot.
   *
   * @return independent fluid
   * @throws IllegalArgumentException if the fluid is unsupported or nonconservative
   */
  private SystemInterface snapshot() {
    SystemInterface work = system.clone();
    if (work.isChemicalSystem() || work.getNumberOfPhases() < 1 || work.getNumberOfPhases() > 2
        || !(work.getTotalNumberOfMoles() > 0.0) || !Double.isFinite(work.getTotalNumberOfMoles())
        || !(work.getTemperature() > 0.0) || !Double.isFinite(work.getTemperature()) || !(work.getPressure() > 0.0)
        || !Double.isFinite(work.getPressure())) {
      throw new IllegalArgumentException("Sensitivities require a finite nonreactive single-phase or VLE state");
    }
    int nc = work.getNumberOfComponents();
    double[] recombined = new double[nc];
    double betaSum = 0.0;
    double zSum = 0.0;
    for (int i = 0; i < nc; i++) {
      double z = work.getComponent(i).getz();
      if (!(z >= 0.0) || !Double.isFinite(z) || work.getComponent(i).getIonicCharge() != 0) {
        throw new IllegalArgumentException("Sensitivities require finite neutral feed fractions");
      }
      zSum += z;
    }
    for (int p = 0; p < work.getNumberOfPhases(); p++) {
      PhaseInterface phase = work.getPhase(p);
      PhaseType type = phase.getType();
      if (type != PhaseType.GAS && type != PhaseType.OIL && type != PhaseType.LIQUID && type != PhaseType.AQUEOUS) {
        throw new IllegalArgumentException("Solid and hydrate phase sensitivities are unsupported");
      }
      double bp = phase.getBeta();
      if (!(bp > 0.0 && bp <= 1.0)) {
        throw new IllegalArgumentException("Active phase amounts must be positive and normalized");
      }
      betaSum += bp;
      double sum = 0.0;
      for (int i = 0; i < nc; i++) {
        double x = phase.getComponent(i).getx();
        if (!(x >= 0.0) || !Double.isFinite(x)) {
          throw new IllegalArgumentException("Active phase compositions must be finite and nonnegative");
        }
        sum += x;
        recombined[i] += bp * x;
      }
      if (Math.abs(sum - 1.0) > 1.0e-10) {
        throw new IllegalArgumentException("Active phase compositions must be normalized");
      }
    }
    if (Math.abs(zSum - 1.0) > 1.0e-10 || Math.abs(betaSum - 1.0) > 1.0e-10) {
      throw new IllegalArgumentException("Feed and phase fractions must be normalized");
    }
    for (int i = 0; i < nc; i++) {
      if (Math.abs(recombined[i] - work.getComponent(i).getz()) > 1.0e-8) {
        throw new IllegalArgumentException("Phase compositions must reconstruct the feed");
      }
    }
    return work;
  }

  /**
   * Reads the physical gas phase index.
   *
   * @param work fluid to inspect
   * @return index, or minus one when no gas exists
   */
  private int vaporIndex(SystemInterface work) {
    for (int p = 0; p < work.getNumberOfPhases(); p++) {
      if (work.getPhase(p).getType() == PhaseType.GAS) {
        return p;
      }
    }
    return -1;
  }

  /**
   * Initializes only property models required by requested values.
   *
   * @param work local state
   * @param names normalized property names
   */
  private void initializeProperties(SystemInterface work, String[] names) {
    for (String name : names) {
      if ("viscosity".equals(name) || "thermalconductivity".equals(name) || "soundspeed".equals(name)) {
        work.initPhysicalProperties();
        return;
      }
    }
    for (String name : names) {
      if ("density".equals(name)) {
        work.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
        return;
      }
    }
  }

  /**
   * Reads requested finite values.
   *
   * @param work initialized state
   * @param names normalized property names
   * @return property values
   * @throws IllegalStateException if a property is non-finite
   */
  private double[] values(SystemInterface work, String[] names) {
    double[] result = new double[names.length];
    for (int i = 0; i < names.length; i++) {
      result[i] = value(work, names[i]);
      requireFinite(result[i]);
    }
    return result;
  }

  /**
   * Reads an intensive or molar property with explicit SI units.
   *
   * @param work initialized state
   * @param name normalized property name
   * @return property value
   * @throws IllegalArgumentException if the name is unsupported
   */
  private double value(SystemInterface work, String name) {
    switch (name) {
    case "density":
      return work.getDensity("kg/m3");
    case "enthalpy":
      return work.getEnthalpy("J/mol");
    case "entropy":
      return work.getEntropy("J/molK");
    case "cp":
      return work.getCp("J/molK");
    case "cv":
      return work.getCv("J/molK");
    case "compressibility":
    case "z":
      return work.getZ();
    case "molarvolume":
      return work.getVolume("m3") / work.getTotalNumberOfMoles();
    case "molarmass":
      return work.getMolarMass();
    case "viscosity":
      return work.getViscosity("kg/msec");
    case "thermalconductivity":
      return work.getThermalConductivity("W/mK");
    case "soundspeed":
      return work.getSoundSpeed("m/s");
    case "joulethomson":
      return work.getJouleThomsonCoefficient("K/bar");
    case "kappa":
    case "cpcvratio":
      return work.getKappa();
    case "gamma":
      return work.getGamma();
    case "gibbsenergy":
      return work.getGibbsEnergy() / work.getTotalNumberOfMoles();
    case "internalenergy":
      return work.getInternalEnergy() / work.getTotalNumberOfMoles();
    case "beta":
    case "vaporfraction":
      int vapor = vaporIndex(work);
      return vapor < 0 ? 0.0 : work.getPhase(vapor).getBeta();
    default:
      throw new IllegalArgumentException("Unknown property: " + name);
    }
  }

  /**
   * Returns the documented property unit, also validating the name.
   *
   * @param name normalized property name
   * @return property unit
   * @throws IllegalArgumentException if unsupported
   */
  private String unit(String name) {
    switch (name) {
    case "density":
      return "kg/m3";
    case "enthalpy":
    case "gibbsenergy":
    case "internalenergy":
      return "J/mol";
    case "entropy":
    case "cp":
    case "cv":
      return "J/molK";
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
    case "compressibility":
    case "z":
    case "kappa":
    case "cpcvratio":
    case "gamma":
    case "beta":
    case "vaporfraction":
      return "-";
    default:
      throw new IllegalArgumentException("Unknown property: " + name);
    }
  }

  /**
   * Rejects a non-finite derivative or property.
   *
   * @param value checked scalar
   * @throws IllegalStateException if non-finite
   */
  private void requireFinite(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Non-finite property sensitivity");
    }
  }

  /**
   * Gets current VLE fugacity Jacobians without retaining stale state across calls.
   *
   * @return liquid then vapor Jacobian, or null for an unsupported state
   */
  public FugacityJacobian[] getFugacityJacobians() {
    try {
      SystemInterface work = snapshot();
      work.init(3);
      flashGradients(work);
      int vapor = vaporIndex(work);
      return new FugacityJacobian[] {extract(work, 1 - vapor), extract(work, vapor)};
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
