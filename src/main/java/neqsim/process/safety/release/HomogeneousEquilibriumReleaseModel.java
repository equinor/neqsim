package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.EquilibriumSoundSpeed;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.TPmultiflash;

/**
 * Equilibrium isentropic short-opening model, maximizing rho*sqrt(2*(h0-h)) over pressure. Zero slip, no friction, heat
 * transfer, solids or delayed flashing. It does not model pipe rupture. All flashes act on clones. Invalid physics or
 * failed required flashes produce unusable results.
 */
public final class HomogeneousEquilibriumReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final int SAMPLES = 48;
  private static final double PRESSURE_TOLERANCE = 1e-7;

  /** @return model identifier */
  @Override
  public String getModelId() {
    return "homogeneous-equilibrium-orifice";
  }

  /** @return numerical model version, including guarded phase and solid-risk checks */
  @Override
  public String getModelVersion() {
    return "1.2.0";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    boolean previousWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      SystemInterface upstream = request.getFluid();
      ReleaseFlowRequest.positive(upstream.getPressure(), "upstream pressure");
      ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      ReleaseFlowRequest.positive(upstream.getTotalNumberOfMoles(), "inventory");
      supported(upstream);
      new ThermodynamicOperations(upstream).TPflash();
      upstream.init(3);
      supported(upstream);
      ReleaseState initial = ReleaseState.fromFluid(upstream, 0.0);
      checkEquilibrium(upstream);
      double upper = initial.getPressurePa();
      double lower = request.getBackPressurePa();
      Map<Station, ReleaseState> states = new EnumMap<Station, ReleaseState>(Station.class);
      states.put(Station.UPSTREAM_STAGNATION, initial);
      List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
      diagnostics.add(new Diagnostic("MODEL_ASSUMPTIONS",
          "Equilibrium, zero slip, isentropic acceleration; no pipe friction, heat transfer or solids"));
      diagnostics.add(new Diagnostic("EXIT_ALIASES_THROAT", "Zero-length opening: exit equals accepted throat"));
      diagnostics.add(new Diagnostic("ENTROPY_SOLVER",
          upstream.getNumberOfComponents() == 1 ? "Pure-fluid PS flash retains saturation quality"
              : "Cold TP entropy bracket with guarded same-EOS incipient-phase continuation when required"));
      if (upper <= lower) {
        states.put(Station.THROAT_CRITICAL, initial);
        states.put(Station.ORIFICE_EXIT, initial);
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW", "Upstream pressure is at or below receiving pressure"));
        ReleaseSolidRiskAssessment applicability = ReleaseSolidRiskAssessment.assess(upstream);
        if (!applicability.isClear()) {
          return solidRiskFailure(applicability, "UPSTREAM_STAGNATION");
        }
        diagnostics.add(new Diagnostic("SOLID_RISK_ASSESSED", "UPSTREAM_STAGNATION: " + applicability.getMessage()));
        return ReleaseFlowResult.success(this, 0.0, false, states, diagnostics, null, false);
      }
      SolverDiagnostics solver = new SolverDiagnostics();
      Sample[] samples = new Sample[SAMPLES + 1];
      for (int i = 0; i <= SAMPLES; i++) {
        double pressure = lower * Math.exp(Math.log(upper / lower) * i / SAMPLES);
        samples[i] = i == SAMPLES ? new Sample(upstream, initial) : sample(upstream, initial, pressure, solver);
      }
      Sample best = samples[0];
      // Refine every resolved local maximum; do not assume a single smooth maximum across phase boundaries.
      for (int i = 1; i < SAMPLES; i++) {
        if (samples[i].flux() >= samples[i - 1].flux() && samples[i].flux() >= samples[i + 1].flux()) {
          Sample candidate = refine(upstream, initial, samples[i - 1].state.getPressurePa(),
              samples[i + 1].state.getPressurePa(), solver);
          if (candidate.flux() > best.flux()) {
            best = candidate;
          }
        }
      }
      for (Sample sample : samples) {
        if (sample.flux() > best.flux()) {
          best = sample;
        }
      }
      if (solver.continuations > 0 || solver.acceptedCandidates > 0) {
        diagnostics.add(new Diagnostic("INCIPIENT_PHASE_CONTINUATION",
            "Same-EOS phase-split continuation resolved " + solver.continuations
                + " entropy roots; accepted candidates=" + solver.acceptedCandidates + ", rejected candidates="
                + solver.rejectedCandidates + "; last rejection=" + solver.lastRejection));
      }
      boolean choked = best.state.getPressurePa() > lower * (1.0 + 1e-5);
      states.put(Station.THROAT_CRITICAL, best.state);
      states.put(Station.ORIFICE_EXIT, best.state);
      states.put(Station.AMBIENT_EXPANDED, samples[0].state);
      ReleaseFlowResult applicabilityFailure = assessSolidRisk(diagnostics, upstream, best.fluid, samples[0].fluid);
      if (applicabilityFailure != null) {
        return applicabilityFailure;
      }
      Double soundSpeed = null;
      boolean warning = false;
      if (choked) {
        EquilibriumSoundSpeed.Result acoustic = best.fluid.calculateEquilibriumSoundSpeed();
        if (acoustic.isConverged()) {
          soundSpeed = acoustic.getSoundSpeed();
          double mach = best.state.getVelocityMs() / soundSpeed;
          if (Math.abs(mach - 1.0) > 0.05) {
            warning = true;
            diagnostics.add(new Diagnostic("THROAT_MACH_MISMATCH",
                "Equilibrium Mach=" + mach + "; phase-boundary maximum may be nonsmooth"));
          }
        } else {
          warning = true;
          diagnostics.add(new Diagnostic("ACOUSTIC_UNAVAILABLE", acoustic.getStatus() + ": " + acoustic.getMessage()));
        }
      }
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * best.flux(), choked, states, diagnostics,
          soundSpeed, warning);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "UNSUPPORTED_PHYSICS", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "CALCULATION_FAILED", ex.getMessage());
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
  }

  private static Sample sample(SystemInterface upstream, ReleaseState initial, double pressure,
      SolverDiagnostics solver) {
    SystemInterface trial = isentropic(upstream, initial.getEntropyJkgK(), pressure, solver);
    supported(trial);
    checkEquilibrium(trial);
    double entropyError = Math.abs(trial.getEntropy("J/kgK") - initial.getEntropyJkgK());
    if (entropyError > 1e-5) {
      throw new IllegalStateException("Specific entropy closure failed: " + entropyError + " J/(kg K)");
    }
    checkInventory(upstream, trial);
    double drop = initial.getEnthalpyJkg() - trial.getEnthalpy("J/kg");
    if (!Double.isFinite(drop) || drop < -1e-5) {
      throw new IllegalStateException("Expansion increased enthalpy or returned a nonfinite value");
    }
    return new Sample(trial, ReleaseState.fromFluid(trial, Math.sqrt(2.0 * Math.max(0.0, drop))));
  }

  private static SystemInterface isentropic(SystemInterface upstream, double entropy, double pressure,
      SolverDiagnostics solver) {
    if (upstream.getNumberOfComponents() == 1) {
      SystemInterface trial = upstream.clone();
      trial.setPressure(pressure / 1e5);
      new ThermodynamicOperations(trial).PSflash(entropy, "J/kgK");
      trial.init(3);
      return trial;
    }
    double low = upstream.getTemperature();
    double high = low;
    double lowError = tp(upstream, pressure, low).getEntropy("J/kgK") - entropy;
    double highError = lowError;
    double span = Math.max(1.0, low * 0.005);
    for (int i = 0; i < 30 && (lowError > 0.0 || highError < 0.0); i++, span *= 1.6) {
      if (lowError > 0.0) {
        low = Math.max(1.0, upstream.getTemperature() - span);
        lowError = tp(upstream, pressure, low).getEntropy("J/kgK") - entropy;
      }
      if (highError < 0.0) {
        high = upstream.getTemperature() + span;
        highError = tp(upstream, pressure, high).getEntropy("J/kgK") - entropy;
      }
    }
    if (!(lowError <= 0.0 && highError >= 0.0)) {
      throw new IllegalStateException("Cannot bracket mixture entropy at " + pressure + " Pa");
    }
    double bracketLow = low;
    double bracketHigh = high;
    SystemInterface phaseSeed = null;
    for (int i = 0; i < 100; i++) {
      double temperature = 0.5 * (low + high);
      SystemInterface trial = tp(upstream, pressure, temperature);
      if (isVapourLiquidSeed(trial)) {
        phaseSeed = trial;
      }
      double residual = trial.getEntropy("J/kgK") - entropy;
      if (!Double.isFinite(residual)) {
        throw new IllegalStateException("Nonfinite entropy root");
      }
      if (Math.abs(residual) <= 1e-7) {
        try {
          checkEquilibrium(trial);
          return trial;
        } catch (IllegalStateException ex) {
          if (phaseSeed == null) {
            throw ex;
          }
          return phaseBoundaryEntropy(upstream, phaseSeed, entropy, pressure, bracketLow, bracketHigh, solver);
        }
      }
      if (high - low <= 1e-9 && phaseSeed != null) {
        return phaseBoundaryEntropy(upstream, phaseSeed, entropy, pressure, bracketLow, bracketHigh, solver);
      }
      if (residual > 0.0) {
        high = temperature;
      } else {
        low = temperature;
      }
    }
    throw new IllegalStateException("Mixture entropy root did not converge at " + pressure + " Pa");
  }

  /**
   * Resolves a cold-TP entropy discontinuity using an already observed vapour/liquid split. Candidate continuation
   * never changes the EOS, components or equilibrium equations. The cold result remains the reference; a continued
   * candidate must preserve its inventory, pass strict fugacity and phase checks, and have no higher Gibbs energy
   * beyond floating-point noise.
   */
  private static SystemInterface phaseBoundaryEntropy(SystemInterface upstream, SystemInterface seed, double entropy,
      double pressure, double low, double high, SolverDiagnostics solver) {
    for (int iteration = 0; iteration < 100; iteration++) {
      double temperature = 0.5 * (low + high);
      SystemInterface cold = tp(upstream, pressure, temperature);
      SystemInterface trial = continuedTp(seed, cold, solver);
      double residual = trial.getEntropy("J/kgK") - entropy;
      if (!Double.isFinite(residual)) {
        throw new IllegalStateException("Nonfinite continued entropy root");
      }
      if (Math.abs(residual) <= 1e-7) {
        solver.continuations++;
        return trial;
      }
      if (temperature == low || temperature == high) {
        break;
      }
      if (residual > 0.0) {
        high = temperature;
      } else {
        low = temperature;
      }
    }
    throw new IllegalStateException("Continued mixture entropy root did not converge at " + pressure + " Pa");
  }

  private static boolean isVapourLiquidSeed(SystemInterface fluid) {
    if (fluid.getNumberOfPhases() != 2) {
      return false;
    }
    boolean gas = false;
    boolean liquid = false;
    for (int phase = 0; phase < 2; phase++) {
      PhaseType type = fluid.getPhase(phase).getType();
      gas |= type == PhaseType.GAS;
      liquid |= type == PhaseType.OIL || type == PhaseType.LIQUID;
      if (fluid.getBeta(phase) <= 1e-10 || fluid.getBeta(phase) >= 1.0 - 1e-10) {
        return false;
      }
    }
    return gas && liquid;
  }

  private static SystemInterface continuedTp(SystemInterface seed, SystemInterface cold, SolverDiagnostics solver) {
    SystemInterface candidate = seed.clone();
    try {
      candidate.setTemperature(cold.getTemperature());
      candidate.init(1);
      TPmultiflash phaseSplit = new TPmultiflash(candidate, false);
      phaseSplit.setDoubleArrays();
      for (int update = 0; update < 20; update++) {
        phaseSplit.solveBeta();
      }
      candidate.init(3);
      if (!isVapourLiquidSeed(candidate)) {
        throw new IllegalStateException("Continuation lost the vapour/liquid active set");
      }
      supported(candidate);
      checkEquilibrium(candidate);
      checkInventory(cold, candidate);
      ReleaseState.fromFluid(candidate, 0.0);
      double referenceGibbs = cold.getGibbsEnergy();
      double candidateGibbs = candidate.getGibbsEnergy();
      double noise = 1e-9 * cold.getTotalNumberOfMoles() + 16.0 * Math.ulp(Math.abs(referenceGibbs));
      if (!Double.isFinite(referenceGibbs) || !Double.isFinite(candidateGibbs)
          || candidateGibbs > referenceGibbs + noise) {
        throw new IllegalStateException("Continuation did not retain the lower-Gibbs equilibrium");
      }
      solver.acceptedCandidates++;
      return candidate;
    } catch (RuntimeException ex) {
      // A rejected numerical candidate never replaces the cold reference or bypasses root closure.
      solver.rejectedCandidates++;
      solver.lastRejection = ex.getMessage();
      return cold;
    }
  }

  private static void checkInventory(SystemInterface reference, SystemInterface trial) {
    for (int component = 0; component < reference.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < trial.getNumberOfPhases(); phase++) {
        recovered += trial.getPhase(phase).getNumberOfMolesInPhase()
            * trial.getPhase(phase).getComponent(component).getx();
      }
      if (!Double.isFinite(recovered)
          || Math.abs(recovered - reference.getComponent(component).getNumberOfmoles()) > 1e-8
              * reference.getTotalNumberOfMoles()) {
        throw new IllegalStateException("Component inventory closure failed");
      }
    }
  }

  private static SystemInterface tp(SystemInterface upstream, double pressure, double temperature) {
    SystemInterface trial = upstream.clone();
    trial.setPressure(pressure / 1e5);
    trial.setTemperature(temperature);
    new ThermodynamicOperations(trial).TPflash();
    trial.init(3);
    return trial;
  }

  private static Sample refine(SystemInterface upstream, ReleaseState initial, double low, double high,
      SolverDiagnostics solver) {
    double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
    Sample left = sample(upstream, initial, high - ratio * (high - low), solver);
    Sample right = sample(upstream, initial, low + ratio * (high - low), solver);
    for (int iteration = 0; iteration < 80; iteration++) {
      if (high - low <= PRESSURE_TOLERANCE * initial.getPressurePa()) {
        return left.flux() > right.flux() ? left : right;
      }
      if (left.flux() < right.flux()) {
        low = left.state.getPressurePa();
        left = right;
        right = sample(upstream, initial, low + ratio * (high - low), solver);
      } else {
        high = right.state.getPressurePa();
        right = left;
        left = sample(upstream, initial, high - ratio * (high - low), solver);
      }
    }
    throw new IllegalStateException("Critical pressure search did not converge");
  }

  private static void supported(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()) {
      throw new UnsupportedOperationException("Reactions, forced phases, solids and hydrates are excluded");
    }
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseType type = fluid.getPhase(i).getType();
      if (type != PhaseType.GAS && type != PhaseType.OIL && type != PhaseType.LIQUID && type != PhaseType.AQUEOUS) {
        throw new UnsupportedOperationException("Unsupported phase: " + type);
      }
    }
  }

  private ReleaseFlowResult assessSolidRisk(List<Diagnostic> diagnostics, SystemInterface upstream,
      SystemInterface throat, SystemInterface ambient) {
    SystemInterface[] fluids = {upstream, throat, ambient};
    String[] stations = {"UPSTREAM_STAGNATION", "THROAT_CRITICAL", "AMBIENT_EXPANDED"};
    for (int index = 0; index < fluids.length; index++) {
      ReleaseSolidRiskAssessment assessment = ReleaseSolidRiskAssessment.assess(fluids[index]);
      if (!assessment.isClear()) {
        return solidRiskFailure(assessment, stations[index]);
      }
      diagnostics.add(new Diagnostic("SOLID_RISK_ASSESSED", stations[index] + ": " + assessment.getMessage()));
    }
    return null;
  }

  private ReleaseFlowResult solidRiskFailure(ReleaseSolidRiskAssessment assessment, String station) {
    boolean unresolved = assessment.getStatus() == ReleaseSolidRiskAssessment.Status.UNRESOLVED;
    String code = unresolved ? "SOLID_RISK_ASSESSMENT_FAILED" : assessment.getStatus().name();
    return ReleaseFlowResult.failure(this, !unresolved, code, station + ": " + assessment.getMessage());
  }

  private static void checkEquilibrium(SystemInterface fluid) {
    for (int k = 0; k < fluid.getNumberOfPhases(); k++) {
      double sum = 0.0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        double x = fluid.getPhase(k).getComponent(i).getx();
        if (!Double.isFinite(x) || x < 0.0) {
          throw new IllegalStateException("Invalid phase composition");
        }
        sum += x;
        for (int j = k + 1; j < fluid.getNumberOfPhases(); j++) {
          double y = fluid.getPhase(j).getComponent(i).getx();
          if (x > 1e-10 && y > 1e-10) {
            double f1 = x * fluid.getPhase(k).getComponent(i).getFugacityCoefficient();
            double f2 = y * fluid.getPhase(j).getComponent(i).getFugacityCoefficient();
            double residual = Math.abs(Math.log(f1 / f2));
            if (!Double.isFinite(residual) || residual > 1e-5) {
              throw new IllegalStateException(
                  "Interphase fugacity closure failed at " + fluid.getPressure() + " bara: residual=" + residual);
            }
          }
        }
      }
      if (Math.abs(sum - 1.0) > 1e-8) {
        throw new IllegalStateException("Phase composition does not close");
      }
    }
  }

  private static final class SolverDiagnostics {
    private int continuations;
    private int acceptedCandidates;
    private int rejectedCandidates;
    private String lastRejection = "none";
  }

  private static final class Sample {
    private final SystemInterface fluid;
    private final ReleaseState state;

    private Sample(SystemInterface fluid, ReleaseState state) {
      this.fluid = fluid;
      this.state = state;
    }

    private double flux() {
      return state.getMassFluxKgM2s();
    }
  }
}
