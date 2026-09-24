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
 * Steady, adiabatic, constant-area ideal-gas pipe release with specified Darcy friction.
 *
 * <p>
 * The model solves the analytical Fanno relations between a reservoir stagnation state and a pipe exit. It supports
 * one-sided full-bore gas release while the inventory pressure changes slowly relative to pipe transit time. The caller
 * supplies a constant Darcy friction factor; roughness correlations, heat transfer, line packing waves, finite-rate
 * phase transfer, slip and solid-bearing transport are outside this model.
 */
public final class IdealGasFannoPipeReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final double GAS_CONSTANT_J_MOL_K = 8.31446261815324;
  private static final double MIN_MACH = 1.0e-10;
  private static final int BISECTION_ITERATIONS = 120;

  /** @return model identifier */
  @Override
  public String getModelId() {
    return "ideal-gas-fanno-pipe";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseModelEvidence getEvidence() {
    return new ReleaseModelEvidence("ideal-gas-fanno-pipe:1.0.0",
        Arrays.asList("ONE_SIDED_FULL_BORE", "CONSTANT_AREA_PIPE", "CALORICALLY_PERFECT_GAS",
            "SPECIFIED_DARCY_FRICTION"),
        Arrays.asList("NO_REAL_GAS_DEPARTURE", "NO_TRANSIENT_DECOMPRESSION_WAVES", "NO_HEAT_TRANSFER",
            "NO_MULTIPHASE_SLIP", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("fanno-analytical-solution", ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/IdealGasFannoPipeReleaseModelTest.java",
                "Independent Fanno-function, choking, backpressure and friction-length comparisons", false),
            new ReleaseModelEvidence.Record("fanno-inventory-refinement", ReleaseModelEvidence.Type.NUMERICAL,
                "src/test/java/neqsim/process/safety/release/IdealGasFannoPipeReleaseModelTest.java",
                "Coupled inventory conservation and timestep-refinement matrix", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      if (!request.hasFlowPath()) {
        throw new UnsupportedOperationException("Fanno model requires positive pipe length and Darcy friction");
      }
      SystemInterface upstream = request.getFluid();
      ReleaseFlowRequest.positive(upstream.getPressure(), "upstream pressure");
      ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      ReleaseFlowRequest.positive(upstream.getTotalNumberOfMoles(), "inventory");
      new ThermodynamicOperations(upstream).TPflash();
      upstream.init(3);
      requireGasOnly(upstream);

      double pressure0Pa = ReleaseFlowRequest.positive(upstream.getPressure() * 1.0e5, "upstream pressure");
      double temperature0K = ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      double molarMassKgMol = ReleaseFlowRequest.positive(upstream.getMolarMass(), "mixture molar mass");
      double gamma = finite(upstream.getGamma(), "heat-capacity ratio");
      if (gamma <= 1.0 || gamma > 2.0) {
        throw new IllegalStateException("Heat-capacity ratio must be in (1, 2]");
      }
      double enthalpy0Jkg = finite(upstream.getEnthalpy("J/kg"), "upstream enthalpy");
      double entropy0JkgK = finite(upstream.getEntropy("J/kgK"), "upstream entropy");
      double specificGasConstant = GAS_CONSTANT_J_MOL_K / molarMassKgMol;
      double density0KgM3 = pressure0Pa / (specificGasConstant * temperature0K);
      Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
      ReleaseState stagnation = ReleaseState.idealGas(upstream, pressure0Pa, temperature0K, density0KgM3, enthalpy0Jkg,
          entropy0JkgK, 0.0);
      stations.put(Station.UPSTREAM_STAGNATION, stagnation);
      List<Diagnostic> diagnostics = assumptions(request);
      assessStation(upstream, pressure0Pa, temperature0K, "UPSTREAM_STAGNATION", diagnostics);
      if (pressure0Pa <= request.getBackPressurePa()) {
        stations.put(Station.THROAT_CRITICAL, stagnation);
        stations.put(Station.ORIFICE_EXIT, stagnation);
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW", "Upstream pressure is at or below receiving pressure"));
        return ReleaseFlowResult.success(this, 0.0, false, stations, diagnostics, null, false);
      }

      double frictionLength = ReleaseFlowRequest.positive(
          request.getDarcyFrictionFactor() * request.getFlowPathLengthM() / request.getDiameterM(),
          "Darcy friction length fL/D");
      double inletMachAtChoke = inletMach(1.0, frictionLength, gamma);
      FlowState critical = state(upstream, pressure0Pa, temperature0K, molarMassKgMol, gamma, enthalpy0Jkg,
          entropy0JkgK, inletMachAtChoke, 1.0);
      boolean choked = request.getBackPressurePa() <= critical.pressurePa;
      double exitMach = choked ? 1.0
          : unchokedExitMach(request.getBackPressurePa(), pressure0Pa, temperature0K, molarMassKgMol, gamma,
              enthalpy0Jkg, entropy0JkgK, frictionLength, upstream);
      double inletMach = inletMach(exitMach, frictionLength, gamma);
      FlowState exit = state(upstream, pressure0Pa, temperature0K, molarMassKgMol, gamma, enthalpy0Jkg, entropy0JkgK,
          inletMach, exitMach);
      double inletFlux = massFlux(pressure0Pa, temperature0K, gamma, specificGasConstant, inletMach);
      double relativeClosure = Math.abs(exit.state.getMassFluxKgM2s() - inletFlux) / Math.max(1.0, inletFlux);
      if (relativeClosure > 1.0e-9) {
        throw new IllegalStateException("Fanno mass-flux closure failed: " + relativeClosure);
      }
      stations.put(Station.THROAT_CRITICAL, exit.state);
      stations.put(Station.ORIFICE_EXIT, exit.state);
      ReleaseState ambient = choked
          ? expandedState(upstream, exit.stagnationPressurePa, temperature0K, molarMassKgMol, gamma, enthalpy0Jkg,
              entropy0JkgK, request.getBackPressurePa())
          : exit.state;
      assessStation(upstream, exit.state.getPressurePa(), exit.state.getTemperatureK(), "PIPE_EXIT", diagnostics);
      assessStation(upstream, ambient.getPressurePa(), ambient.getTemperatureK(), "AMBIENT_EXPANDED", diagnostics);
      stations.put(Station.AMBIENT_EXPANDED, ambient);
      double massFlowRateKgS = request.getEffectiveAreaM2() * exit.state.getMassFluxKgM2s();
      double soundSpeedMs = Math.sqrt(gamma * specificGasConstant * exit.state.getTemperatureK());
      diagnostics.add(new Diagnostic("FANNO_SOLUTION",
          "Darcy fL/D=" + frictionLength + ", inlet Mach=" + inletMach + ", exit Mach=" + exitMach));
      return ReleaseFlowResult.success(this, massFlowRateKgS, choked, stations, diagnostics, soundSpeedMs, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "UNSUPPORTED_FANNO_REGIME", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "FANNO_CALCULATION_FAILED", ex.getMessage());
    }
  }

  private static double unchokedExitMach(double backPressurePa, double pressure0Pa, double temperature0K,
      double molarMassKgMol, double gamma, double enthalpy0Jkg, double entropy0JkgK, double frictionLength,
      SystemInterface composition) {
    double low = MIN_MACH;
    double high = 1.0;
    for (int iteration = 0; iteration < BISECTION_ITERATIONS; iteration++) {
      double middle = 0.5 * (low + high);
      double inlet = inletMach(middle, frictionLength, gamma);
      FlowState candidate = state(composition, pressure0Pa, temperature0K, molarMassKgMol, gamma, enthalpy0Jkg,
          entropy0JkgK, inlet, middle);
      if (candidate.pressurePa > backPressurePa) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (low + high);
  }

  private static double inletMach(double exitMach, double frictionLength, double gamma) {
    double target = fannoFunction(exitMach, gamma) + frictionLength;
    if (!Double.isFinite(target) || target >= fannoFunction(MIN_MACH, gamma)) {
      throw new IllegalStateException("Fanno friction length exceeds the bounded Mach solver range");
    }
    double low = MIN_MACH;
    double high = exitMach;
    for (int iteration = 0; iteration < BISECTION_ITERATIONS; iteration++) {
      double middle = 0.5 * (low + high);
      if (fannoFunction(middle, gamma) > target) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return 0.5 * (low + high);
  }

  private static double fannoFunction(double mach, double gamma) {
    double mach2 = mach * mach;
    return (1.0 - mach2) / (gamma * mach2)
        + (gamma + 1.0) / (2.0 * gamma) * Math.log((gamma + 1.0) * mach2 / (2.0 + (gamma - 1.0) * mach2));
  }

  private static double stagnationPressureRatio(double mach, double gamma) {
    return 1.0 / mach
        * Math.pow((2.0 + (gamma - 1.0) * mach * mach) / (gamma + 1.0), (gamma + 1.0) / (2.0 * (gamma - 1.0)));
  }

  private static FlowState state(SystemInterface composition, double pressure0Pa, double temperature0K,
      double molarMassKgMol, double gamma, double enthalpy0Jkg, double entropy0JkgK, double inletMach,
      double exitMach) {
    double pressure0ExitPa = pressure0Pa * stagnationPressureRatio(exitMach, gamma)
        / stagnationPressureRatio(inletMach, gamma);
    double stagnationFactor = 1.0 + 0.5 * (gamma - 1.0) * exitMach * exitMach;
    double pressurePa = pressure0ExitPa / Math.pow(stagnationFactor, gamma / (gamma - 1.0));
    double temperatureK = temperature0K / stagnationFactor;
    double specificGasConstant = GAS_CONSTANT_J_MOL_K / molarMassKgMol;
    double soundSpeedMs = Math.sqrt(gamma * specificGasConstant * temperatureK);
    double velocityMs = exitMach * soundSpeedMs;
    double densityKgM3 = pressurePa / (specificGasConstant * temperatureK);
    double heatCapacityJkgK = gamma * specificGasConstant / (gamma - 1.0);
    double entropyJkgK = entropy0JkgK + heatCapacityJkgK * Math.log(temperatureK / temperature0K)
        - specificGasConstant * Math.log(pressurePa / pressure0Pa);
    ReleaseState state = ReleaseState.idealGas(composition, pressurePa, temperatureK, densityKgM3,
        enthalpy0Jkg - 0.5 * velocityMs * velocityMs, entropyJkgK, velocityMs);
    return new FlowState(pressurePa, pressure0ExitPa, state);
  }

  private static double massFlux(double pressure0Pa, double temperature0K, double gamma, double gasConstant,
      double mach) {
    double factor = 1.0 + 0.5 * (gamma - 1.0) * mach * mach;
    double temperature = temperature0K / factor;
    double pressure = pressure0Pa / Math.pow(factor, gamma / (gamma - 1.0));
    return pressure / (gasConstant * temperature) * mach * Math.sqrt(gamma * gasConstant * temperature);
  }

  private static ReleaseState expandedState(SystemInterface composition, double pressure0Pa, double temperature0K,
      double molarMassKgMol, double gamma, double enthalpy0Jkg, double entropy0JkgK, double pressurePa) {
    double pressureRatio = pressurePa / pressure0Pa;
    double temperatureK = temperature0K * Math.pow(pressureRatio, (gamma - 1.0) / gamma);
    double specificGasConstant = GAS_CONSTANT_J_MOL_K / molarMassKgMol;
    double heatCapacityJkgK = gamma * specificGasConstant / (gamma - 1.0);
    double velocityMs = Math.sqrt(Math.max(0.0, 2.0 * heatCapacityJkgK * (temperature0K - temperatureK)));
    double densityKgM3 = pressurePa / (specificGasConstant * temperatureK);
    return ReleaseState.idealGas(composition, pressurePa, temperatureK, densityKgM3,
        enthalpy0Jkg - 0.5 * velocityMs * velocityMs, entropy0JkgK, velocityMs);
  }

  private static List<Diagnostic> assumptions(ReleaseFlowRequest request) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("FANNO_MODEL_ASSUMPTIONS",
        "Steady adiabatic constant-area calorically perfect gas with specified constant Darcy friction"));
    diagnostics.add(new Diagnostic("QUASI_STEADY_ONE_SIDED_PIPE",
        "One pipe segment from stagnation boundary to release plane; transient waves and the opposite rupture side are excluded"));
    diagnostics.add(new Diagnostic("SPECIFIED_DARCY_FRICTION", "Darcy friction factor="
        + request.getDarcyFrictionFactor() + "; no roughness or Reynolds correlation inferred"));
    return diagnostics;
  }

  private static void requireGasOnly(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()) {
      throw new UnsupportedOperationException("Reactions, forced phases, solids and hydrates are excluded");
    }
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new UnsupportedOperationException("The Fanno pipe model requires one gas phase");
    }
  }

  private static void assessStation(SystemInterface composition, double pressurePa, double temperatureK, String station,
      List<Diagnostic> diagnostics) {
    SystemInterface trial = composition.clone();
    trial.setPressure(pressurePa / 1.0e5);
    trial.setTemperature(temperatureK);
    new ThermodynamicOperations(trial).TPflash();
    trial.init(3);
    requireGasOnly(trial);
    ReleaseSolidRiskAssessment assessment = ReleaseSolidRiskAssessment.assess(trial);
    if (assessment.getStatus() == ReleaseSolidRiskAssessment.Status.UNRESOLVED) {
      throw new IllegalStateException("SOLID_RISK_ASSESSMENT_FAILED at " + station + ": " + assessment.getMessage());
    }
    if (!assessment.isClear()) {
      throw new UnsupportedOperationException(
          assessment.getStatus().name() + " at " + station + ": " + assessment.getMessage());
    }
    diagnostics.add(new Diagnostic("SOLID_RISK_ASSESSED", station + ": " + assessment.getMessage()));
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " must be finite");
    }
    return value;
  }

  private static final class FlowState {
    private final double pressurePa;
    private final double stagnationPressurePa;
    private final ReleaseState state;

    private FlowState(double pressurePa, double stagnationPressurePa, ReleaseState state) {
      this.pressurePa = pressurePa;
      this.stagnationPressurePa = stagnationPressurePa;
      this.state = state;
    }
  }
}
