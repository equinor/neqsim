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
              : "Cold TP flashes in bracketed mixture entropy root"));
      if (upper <= lower) {
        states.put(Station.THROAT_CRITICAL, initial);
        states.put(Station.ORIFICE_EXIT, initial);
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW", "Upstream pressure is at or below receiving pressure"));
        return ReleaseFlowResult.success(this, 0.0, false, states, diagnostics, null, false);
      }
      Sample[] samples = new Sample[SAMPLES + 1];
      for (int i = 0; i <= SAMPLES; i++) {
        double pressure = lower * Math.exp(Math.log(upper / lower) * i / SAMPLES);
        samples[i] = i == SAMPLES ? new Sample(upstream, initial) : sample(upstream, initial, pressure);
      }
      Sample best = samples[0];
      // Refine every resolved local maximum; do not assume a single smooth maximum across phase boundaries.
      for (int i = 1; i < SAMPLES; i++) {
        if (samples[i].flux() >= samples[i - 1].flux() && samples[i].flux() >= samples[i + 1].flux()) {
          Sample candidate = refine(upstream, initial, samples[i - 1].state.getPressurePa(),
              samples[i + 1].state.getPressurePa());
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
      boolean choked = best.state.getPressurePa() > lower * (1.0 + 1e-5);
      states.put(Station.THROAT_CRITICAL, best.state);
      states.put(Station.ORIFICE_EXIT, best.state);
      states.put(Station.AMBIENT_EXPANDED, samples[0].state);
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

  private static Sample sample(SystemInterface upstream, ReleaseState initial, double pressure) {
    SystemInterface trial = isentropic(upstream, initial.getEntropyJkgK(), pressure);
    supported(trial);
    checkEquilibrium(trial);
    double entropyError = Math.abs(trial.getEntropy("J/kgK") - initial.getEntropyJkgK());
    if (entropyError > 1e-5) {
      throw new IllegalStateException("Specific entropy closure failed: " + entropyError + " J/(kg K)");
    }
    for (int i = 0; i < upstream.getNumberOfComponents(); i++) {
      double expected = upstream.getComponent(i).getNumberOfmoles();
      double recovered = 0.0;
      for (int k = 0; k < trial.getNumberOfPhases(); k++) {
        recovered += trial.getPhase(k).getNumberOfMolesInPhase() * trial.getPhase(k).getComponent(i).getx();
      }
      if (Math.abs(recovered - expected) > 1e-8 * upstream.getTotalNumberOfMoles()) {
        throw new IllegalStateException("Component inventory closure failed");
      }
    }
    double drop = initial.getEnthalpyJkg() - trial.getEnthalpy("J/kg");
    if (!Double.isFinite(drop) || drop < -1e-5) {
      throw new IllegalStateException("Expansion increased enthalpy or returned a nonfinite value");
    }
    return new Sample(trial, ReleaseState.fromFluid(trial, Math.sqrt(2.0 * Math.max(0.0, drop))));
  }

  private static SystemInterface isentropic(SystemInterface upstream, double entropy, double pressure) {
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
    for (int i = 0; i < 100; i++) {
      double temperature = 0.5 * (low + high);
      SystemInterface trial = tp(upstream, pressure, temperature);
      double residual = trial.getEntropy("J/kgK") - entropy;
      if (!Double.isFinite(residual)) {
        throw new IllegalStateException("Nonfinite entropy root");
      }
      if (Math.abs(residual) <= 1e-7) {
        return trial;
      }
      if (residual > 0.0) {
        high = temperature;
      } else {
        low = temperature;
      }
    }
    throw new IllegalStateException("Mixture entropy root did not converge at " + pressure + " Pa");
  }

  private static SystemInterface tp(SystemInterface upstream, double pressure, double temperature) {
    SystemInterface trial = upstream.clone();
    trial.setPressure(pressure / 1e5);
    trial.setTemperature(temperature);
    new ThermodynamicOperations(trial).TPflash();
    trial.init(3);
    return trial;
  }

  private static Sample refine(SystemInterface upstream, ReleaseState initial, double low, double high) {
    double ratio = (Math.sqrt(5.0) - 1.0) / 2.0;
    Sample left = sample(upstream, initial, high - ratio * (high - low));
    Sample right = sample(upstream, initial, low + ratio * (high - low));
    for (int iteration = 0; iteration < 80; iteration++) {
      if (high - low <= PRESSURE_TOLERANCE * initial.getPressurePa()) {
        return left.flux() > right.flux() ? left : right;
      }
      if (left.flux() < right.flux()) {
        low = left.state.getPressurePa();
        left = right;
        right = sample(upstream, initial, low + ratio * (high - low));
      } else {
        high = right.state.getPressurePa();
        right = left;
        left = sample(upstream, initial, high - ratio * (high - low));
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
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      if ("CO2".equals(fluid.getComponent(i).getComponentName()) && fluid.getComponent(i).getNumberOfmoles() > 0.0
          && fluid.getTemperature() < 216.592) {
        throw new UnsupportedOperationException(
            "CO2 below 216.592 K requires a separately assessed solid-capable model");
      }
    }
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
              throw new IllegalStateException("Interphase fugacity closure failed");
            }
          }
        }
      }
      if (Math.abs(sum - 1.0) > 1e-8) {
        throw new IllegalStateException("Phase composition does not close");
      }
    }
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
