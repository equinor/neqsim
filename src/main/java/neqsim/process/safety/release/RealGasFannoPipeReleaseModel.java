package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Quasi-steady, adiabatic, constant-area real-gas pipe release with specified Darcy friction.
 *
 * <p>
 * The solver retains constant mass flux and stagnation enthalpy while marching the steady momentum balance with EOS
 * density and caloric properties. It finds the largest subsonic solution for a choked release and otherwise matches the
 * receiving pressure. This is a one-sided pipe model: it does not represent decompression waves, line packing, the
 * opposite side of a rupture, heat transfer, finite-rate phase transfer, slip or solid-bearing transport.
 */
public final class RealGasFannoPipeReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final int AXIAL_STEPS = 10;
  private static final int ROOT_ITERATIONS = 16;
  private static final int PROPERTY_ITERATIONS = 20;
  private static final double SONIC_LIMIT = 0.9995;
  private static final double PROPERTY_TOLERANCE = 2.0e-8;

  /** @return model identifier */
  @Override
  public String getModelId() {
    return "real-gas-fanno-pipe";
  }

  /** @return numerical model version */
  @Override
  public String getModelVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseModelEvidence getEvidence() {
    return new ReleaseModelEvidence("real-gas-fanno-pipe:1.0.0",
        Arrays.asList("ONE_SIDED_FULL_BORE", "CONSTANT_AREA_PIPE", "SINGLE_EQUILIBRIUM_GAS", "EOS_PROPERTIES",
            "SPECIFIED_DARCY_FRICTION"),
        Arrays.asList("NO_TRANSIENT_DECOMPRESSION_WAVES", "NO_LINE_PACKING", "NO_HEAT_TRANSFER", "NO_PHASE_TRANSFER",
            "NO_MULTIPHASE_SLIP", "NO_SOLID_BEARING_FLOW", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("real-gas-dilute-fanno-limit", ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/RealGasFannoPipeReleaseModelTest.java",
                "EOS marching converges to the independent analytical ideal-gas Fanno solution at dilute conditions",
                false),
            new ReleaseModelEvidence.Record("real-gas-fanno-refinement", ReleaseModelEvidence.Type.NUMERICAL,
                "src/test/java/neqsim/process/safety/release/RealGasFannoPipeReleaseModelTest.java",
                "Mass, stagnation-energy, physical-trend and coupled-inventory refinement checks", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      if (!request.hasFlowPath()) {
        throw new UnsupportedOperationException("Real-gas Fanno model requires positive pipe length and friction");
      }
      SystemInterface upstream = request.getFluid();
      ReleaseFlowRequest.positive(upstream.getPressure(), "upstream pressure");
      ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      ReleaseFlowRequest.positive(upstream.getTotalNumberOfMoles(), "inventory");
      new ThermodynamicOperations(upstream).TPflash();
      upstream.init(3);
      requireGasOnly(upstream);

      double pressure0Pa = upstream.getPressure("Pa");
      double enthalpy0Jkg = finite(upstream.getEnthalpy("J/kg"), "upstream enthalpy");
      double entropy0JkgK = finite(upstream.getEntropy("J/kgK"), "upstream entropy");
      ReleaseState stagnation = ReleaseState.fromFluid(upstream, 0.0);
      Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
      stations.put(Station.UPSTREAM_STAGNATION, stagnation);
      List<Diagnostic> diagnostics = assumptions(request);
      assessStation(upstream, "UPSTREAM_STAGNATION", diagnostics);
      if (pressure0Pa <= request.getBackPressurePa()) {
        stations.put(Station.THROAT_CRITICAL, stagnation);
        stations.put(Station.ORIFICE_EXIT, stagnation);
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW", "Upstream pressure is at or below receiving pressure"));
        return ReleaseFlowResult.success(this, 0.0, false, stations, diagnostics, null, false);
      }

      double upperFlux = stagnation.getDensityKgM3() * soundSpeed(upstream) * 1.5;
      double lowFlux = upperFlux * 1.0e-4;
      double highFlux = upperFlux;
      Trace lowTrace = trace(upstream, enthalpy0Jkg, entropy0JkgK, lowFlux, request);
      if (!lowTrace.completed) {
        throw new IllegalStateException("Cannot establish the low-flux pipe solution: " + lowTrace.failure);
      }
      for (int i = 0; i < ROOT_ITERATIONS; i++) {
        double trialFlux = 0.5 * (lowFlux + highFlux);
        Trace trial = trace(upstream, enthalpy0Jkg, entropy0JkgK, trialFlux, request);
        if (trial.completed && trial.exit.mach < SONIC_LIMIT) {
          lowFlux = trialFlux;
          lowTrace = trial;
        } else {
          highFlux = trialFlux;
        }
      }
      Trace critical = lowTrace;
      boolean choked = request.getBackPressurePa() <= critical.exit.fluid.getPressure("Pa");
      Trace accepted = critical;
      double acceptedFlux = lowFlux;
      if (!choked) {
        double lower = upperFlux * 1.0e-4;
        double upper = lowFlux;
        for (int i = 0; i < ROOT_ITERATIONS; i++) {
          double trialFlux = 0.5 * (lower + upper);
          Trace trial = trace(upstream, enthalpy0Jkg, entropy0JkgK, trialFlux, request);
          if (!trial.completed || trial.exit.fluid.getPressure("Pa") < request.getBackPressurePa()) {
            upper = trialFlux;
          } else {
            lower = trialFlux;
            accepted = trial;
            acceptedFlux = trialFlux;
          }
        }
      }

      ReleaseState exit = ReleaseState.fromFluid(accepted.exit.fluid, accepted.exit.velocityMs);
      stations.put(Station.THROAT_CRITICAL, exit);
      stations.put(Station.ORIFICE_EXIT, exit);
      SystemInterface ambientFluid = choked
          ? isentropicExpansion(accepted.exit.fluid, enthalpy0Jkg, request.getBackPressurePa())
          : accepted.exit.fluid;
      double ambientVelocity = choked
          ? Math.sqrt(Math.max(0.0, 2.0 * (enthalpy0Jkg - ambientFluid.getEnthalpy("J/kg"))))
          : accepted.exit.velocityMs;
      stations.put(Station.AMBIENT_EXPANDED, ReleaseState.fromFluid(ambientFluid, ambientVelocity));
      assessStation(accepted.exit.fluid, "PIPE_EXIT", diagnostics);
      assessStation(ambientFluid, "AMBIENT_EXPANDED", diagnostics);
      double energyError = Math
          .abs(enthalpy0Jkg - exit.getEnthalpyJkg() - 0.5 * exit.getVelocityMs() * exit.getVelocityMs());
      if (energyError > Math.max(0.2, Math.abs(enthalpy0Jkg) * 1.0e-6)) {
        throw new IllegalStateException("Stagnation-energy closure failed: " + energyError + " J/kg");
      }
      double fluxError = Math.abs(acceptedFlux - exit.getMassFluxKgM2s()) / Math.max(1.0, acceptedFlux);
      if (fluxError > 2.0e-7) {
        throw new IllegalStateException("Mass-flux closure failed: " + fluxError);
      }
      diagnostics.add(new Diagnostic("REAL_GAS_FANNO_SOLUTION", "EOS=" + upstream.getModelName() + ", axial steps="
          + AXIAL_STEPS + ", inlet Mach=" + accepted.inlet.mach + ", exit Mach=" + accepted.exit.mach));
      return ReleaseFlowResult.success(this, request.getEffectiveAreaM2() * acceptedFlux, choked, stations, diagnostics,
          soundSpeed(accepted.exit.fluid), false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "UNSUPPORTED_REAL_GAS_FANNO_REGIME", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "REAL_GAS_FANNO_CALCULATION_FAILED", ex.getMessage());
    }
  }

  private static Trace trace(SystemInterface upstream, double enthalpy0Jkg, double entropy0JkgK, double massFlux,
      ReleaseFlowRequest request) {
    try {
      FlowState inlet = inletState(upstream, enthalpy0Jkg, entropy0JkgK, massFlux);
      FlowState state = inlet;
      double stepLength = request.getFlowPathLengthM() / AXIAL_STEPS;
      for (int i = 0; i < AXIAL_STEPS; i++) {
        if (state.mach >= SONIC_LIMIT) {
          return new Trace(false, inlet, state, "sonic limit reached before the pipe exit");
        }
        double gradient = pressureGradient(upstream, enthalpy0Jkg, massFlux, request, state);
        double predictedPressure = state.fluid.getPressure("Pa") + gradient * stepLength;
        if (!(predictedPressure > 0.0) || predictedPressure >= state.fluid.getPressure("Pa")) {
          return new Trace(false, inlet, state, "pressure step did not decrease pressure");
        }
        FlowState predicted = energyState(upstream, enthalpy0Jkg, massFlux, predictedPressure);
        if (predicted.mach >= 1.0) {
          return new Trace(false, inlet, predicted, "predictor crossed the sonic limit");
        }
        double correctedGradient = 0.5
            * (gradient + pressureGradient(upstream, enthalpy0Jkg, massFlux, request, predicted));
        double correctedPressure = state.fluid.getPressure("Pa") + correctedGradient * stepLength;
        if (!(correctedPressure > 0.0) || correctedPressure >= state.fluid.getPressure("Pa")) {
          return new Trace(false, inlet, state, "corrected pressure step did not decrease pressure");
        }
        state = energyState(upstream, enthalpy0Jkg, massFlux, correctedPressure);
      }
      return new Trace(state.mach < 1.0, inlet, state, state.mach < 1.0 ? null : "exit is sonic");
    } catch (RuntimeException ex) {
      return new Trace(false, null, null, ex.getMessage());
    }
  }

  private static FlowState inletState(SystemInterface upstream, double enthalpy0Jkg, double entropy0JkgK,
      double massFlux) {
    double highPressure = upstream.getPressure("Pa");
    double upstreamDensity = upstream.getMass("kg") / upstream.getVolume("m3");
    if (massFlux <= upstreamDensity * soundSpeed(upstream) * 1.0e-6) {
      double velocity = massFlux / upstreamDensity;
      return new FlowState(upstream.clone(), velocity, velocity / soundSpeed(upstream));
    }
    FlowState high = energyState(upstream, enthalpy0Jkg, massFlux, highPressure);
    double highError = high.fluid.getEntropy("J/kgK") - entropy0JkgK;
    double lowPressure = highPressure;
    FlowState low = high;
    double lowError = highError;
    for (int i = 1; i <= 36 && lowError * highError > 0.0; i++) {
      lowPressure = highPressure * Math.exp(-Math.log(200.0) * i / 36.0);
      low = energyState(upstream, enthalpy0Jkg, massFlux, lowPressure);
      lowError = low.fluid.getEntropy("J/kgK") - entropy0JkgK;
    }
    if (lowError * highError > 0.0) {
      throw new IllegalStateException("Cannot bracket the subsonic isentropic pipe inlet");
    }
    for (int i = 0; i < ROOT_ITERATIONS; i++) {
      double pressure = 0.5 * (lowPressure + highPressure);
      FlowState middle = energyState(upstream, enthalpy0Jkg, massFlux, pressure);
      double error = middle.fluid.getEntropy("J/kgK") - entropy0JkgK;
      if (error * lowError <= 0.0) {
        highPressure = pressure;
        high = middle;
        highError = error;
      } else {
        lowPressure = pressure;
        low = middle;
        lowError = error;
      }
    }
    FlowState result = Math.abs(lowError) < Math.abs(highError) ? low : high;
    if (result.mach >= 1.0) {
      throw new IllegalStateException("Pipe inlet is not subsonic");
    }
    return result;
  }

  private static double pressureGradient(SystemInterface upstream, double enthalpy0Jkg, double massFlux,
      ReleaseFlowRequest request, FlowState state) {
    double pressure = state.fluid.getPressure("Pa");
    double step = Math.max(20.0, pressure * 2.0e-5);
    double lowerPressure = pressure - step;
    double upperPressure = Math.min(pressure + step, upstream.getPressure("Pa") * (1.0 - 1.0e-12));
    FlowState lower = energyState(upstream, enthalpy0Jkg, massFlux, lowerPressure);
    FlowState upper = energyState(upstream, enthalpy0Jkg, massFlux, upperPressure);
    double velocityDerivative = (upper.velocityMs - lower.velocityMs) / (upperPressure - lowerPressure);
    double denominator = 1.0 + massFlux * velocityDerivative;
    if (!Double.isFinite(denominator) || denominator <= 2.0e-4) {
      throw new IllegalStateException("Steady momentum equation reached its sonic singularity");
    }
    double density = state.fluid.getMass("kg") / state.fluid.getVolume("m3");
    return -request.getDarcyFrictionFactor() * massFlux * massFlux
        / (2.0 * request.getDiameterM() * density * denominator);
  }

  private static FlowState energyState(SystemInterface upstream, double enthalpy0Jkg, double massFlux,
      double pressurePa) {
    if (!(pressurePa > 0.0) || pressurePa > upstream.getPressure("Pa") * 1.0001) {
      throw new IllegalStateException("Pressure is outside the one-sided expansion domain");
    }
    SystemInterface state = upstream.clone();
    state.setPressure(pressurePa, "Pa");
    double density = upstream.getMass("kg") / upstream.getVolume("m3");
    for (int i = 0; i < PROPERTY_ITERATIONS; i++) {
      double velocity = massFlux / density;
      double staticEnthalpy = enthalpy0Jkg - 0.5 * velocity * velocity;
      new ThermodynamicOperations(state).PHflash(staticEnthalpy, "J/kg");
      state.init(3);
      requireGasOnly(state);
      double updatedDensity = state.getMass("kg") / state.getVolume("m3");
      if (Math.abs(updatedDensity - density) <= PROPERTY_TOLERANCE * Math.max(1.0, updatedDensity)) {
        density = updatedDensity;
        double finalVelocity = massFlux / density;
        return new FlowState(state, finalVelocity, finalVelocity / soundSpeed(state));
      }
      density = 0.2 * density + 0.8 * updatedDensity;
    }
    throw new IllegalStateException("EOS stagnation-energy iteration did not converge");
  }

  private static SystemInterface isentropicExpansion(SystemInterface exit, double enthalpy0Jkg, double backPressurePa) {
    SystemInterface ambient = exit.clone();
    double entropy = exit.getEntropy("J/kgK");
    ambient.setPressure(backPressurePa, "Pa");
    new ThermodynamicOperations(ambient).PSflash(entropy, "J/kgK");
    ambient.init(3);
    requireGasOnly(ambient);
    if (ambient.getEnthalpy("J/kg") > enthalpy0Jkg + 0.05) {
      throw new IllegalStateException("Ambient expansion violates stagnation-energy availability");
    }
    return ambient;
  }

  private static List<Diagnostic> assumptions(ReleaseFlowRequest request) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("REAL_GAS_FANNO_ASSUMPTIONS",
        "Steady adiabatic constant-area single-equilibrium-gas flow with EOS properties"));
    diagnostics.add(new Diagnostic("QUASI_STEADY_ONE_SIDED_PIPE",
        "One pipe side from a stagnation boundary; decompression waves, line packing and the opposite rupture side are excluded"));
    diagnostics.add(new Diagnostic("SPECIFIED_DARCY_FRICTION", "Darcy friction factor="
        + request.getDarcyFrictionFactor() + "; no roughness or Reynolds correlation inferred"));
    diagnostics.add(new Diagnostic("NUMERICAL_REFINEMENT",
        "Axial steps=" + AXIAL_STEPS + ", bracket iterations=" + ROOT_ITERATIONS));
    return diagnostics;
  }

  private static void requireGasOnly(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()) {
      throw new UnsupportedOperationException("Reactions, forced phases, solids and hydrates are excluded");
    }
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new UnsupportedOperationException("The real-gas Fanno model requires one equilibrium gas phase");
    }
  }

  private static void assessStation(SystemInterface fluid, String station, List<Diagnostic> diagnostics) {
    ReleaseSolidRiskAssessment assessment = ReleaseSolidRiskAssessment.assess(fluid);
    if (assessment.getStatus() == ReleaseSolidRiskAssessment.Status.UNRESOLVED) {
      throw new IllegalStateException("SOLID_RISK_ASSESSMENT_FAILED at " + station + ": " + assessment.getMessage());
    }
    if (!assessment.isClear()) {
      throw new UnsupportedOperationException(
          assessment.getStatus().name() + " at " + station + ": " + assessment.getMessage());
    }
    diagnostics.add(new Diagnostic("SOLID_RISK_ASSESSED", station + ": " + assessment.getMessage()));
  }

  private static double soundSpeed(SystemInterface fluid) {
    return ReleaseFlowRequest.positive(fluid.getPhase(0).getSoundSpeed(), "EOS sound speed");
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " must be finite");
    }
    return value;
  }

  private static final class FlowState {
    private final SystemInterface fluid;
    private final double velocityMs;
    private final double mach;

    private FlowState(SystemInterface fluid, double velocityMs, double mach) {
      this.fluid = fluid;
      this.velocityMs = velocityMs;
      this.mach = mach;
    }
  }

  private static final class Trace {
    private final boolean completed;
    private final FlowState inlet;
    private final FlowState exit;
    private final String failure;

    private Trace(boolean completed, FlowState inlet, FlowState exit, String failure) {
      this.completed = completed;
      this.inlet = inlet;
      this.exit = exit;
      this.failure = failure;
    }
  }
}
