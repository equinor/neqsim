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
 * Strict calorically perfect ideal-gas short-orifice model.
 *
 * <p>
 * The model obtains mixture molar mass and heat-capacity ratio from the initialized NeqSim gas without substituting
 * defaults. It then applies analytical isentropic nozzle equations with constant gamma. Multiphase, reacting,
 * forced-phase, solid-check and hydrate-check states are unsupported. It does not represent real-gas departure,
 * flashing, slip, pipe friction, heat transfer or inventory depletion.
 */
public final class IdealGasReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final double GAS_CONSTANT_J_MOL_K = 8.31446261815324;

  /** @return model identifier */
  @Override
  public String getModelId() {
    return "ideal-gas-isentropic-orifice";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseModelEvidence getEvidence() {
    return new ReleaseModelEvidence("ideal-gas-isentropic-orifice:1.0.0",
        Arrays.asList("SHORT_ORIFICE", "SINGLE_GAS_PHASE", "CALORICALLY_PERFECT_GAS"),
        Arrays.asList("NO_REAL_GAS_DEPARTURE", "NO_PHASE_CHANGE", "NO_PIPE_FRICTION", "NO_EXPERIMENTAL_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("ideal-gas-choked-limit", ReleaseModelEvidence.Type.ANALYTICAL,
                "src/test/java/neqsim/process/safety/release/ReleaseFlowBenchmarkTest.java",
                "Closed-form choked and high-backpressure ideal-gas limits", false),
            new ReleaseModelEvidence.Record("ideal-gas-state-closure", ReleaseModelEvidence.Type.CONSERVATION,
                "src/test/java/neqsim/process/safety/release/ReleaseFlowAdapterTest.java",
                "Station mass-flux and stagnation-energy invariants", false)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      request.requireShortOpening();
      SystemInterface upstream = request.getFluid();
      ReleaseFlowRequest.positive(upstream.getPressure(), "upstream pressure");
      ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      ReleaseFlowRequest.positive(upstream.getTotalNumberOfMoles(), "inventory");
      new ThermodynamicOperations(upstream).TPflash();
      upstream.init(3);
      requireGasOnly(upstream);

      double pressurePa = ReleaseFlowRequest.positive(upstream.getPressure() * 1.0e5, "upstream pressure");
      double temperatureK = ReleaseFlowRequest.positive(upstream.getTemperature(), "upstream temperature");
      double molarMassKgMol = ReleaseFlowRequest.positive(upstream.getMolarMass(), "mixture molar mass");
      double enthalpyJkg = finite(upstream.getEnthalpy("J/kg"), "upstream enthalpy");
      double entropyJkgK = finite(upstream.getEntropy("J/kgK"), "upstream entropy");
      double upstreamDensityKgM3 = pressurePa * molarMassKgMol / (GAS_CONSTANT_J_MOL_K * temperatureK);

      Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
      ReleaseState stagnation = ReleaseState.idealGas(upstream, pressurePa, temperatureK, upstreamDensityKgM3,
          enthalpyJkg, entropyJkgK, 0.0);
      stations.put(Station.UPSTREAM_STAGNATION, stagnation);
      List<Diagnostic> diagnostics = assumptions();
      if (pressurePa <= request.getBackPressurePa()) {
        stations.put(Station.THROAT_CRITICAL, stagnation);
        stations.put(Station.ORIFICE_EXIT, stagnation);
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW", "Upstream pressure is at or below receiving pressure"));
        return ReleaseFlowResult.success(this, 0.0, false, stations, diagnostics, null, false);
      }

      double gamma = finite(upstream.getGamma(), "heat-capacity ratio");
      if (gamma <= 1.0 || gamma > 2.0) {
        throw new IllegalStateException("Heat-capacity ratio must be in (1, 2]");
      }
      double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
      double backPressureRatio = request.getBackPressurePa() / pressurePa;
      boolean choked = backPressureRatio <= criticalRatio;
      double throatPressurePa = choked ? pressurePa * criticalRatio : request.getBackPressurePa();
      ReleaseState throat = expandedState(upstream, pressurePa, temperatureK, molarMassKgMol, gamma, enthalpyJkg,
          entropyJkgK, throatPressurePa);
      stations.put(Station.THROAT_CRITICAL, throat);
      stations.put(Station.ORIFICE_EXIT, throat);
      ReleaseState ambient = choked
          ? expandedState(upstream, pressurePa, temperatureK, molarMassKgMol, gamma, enthalpyJkg, entropyJkgK,
              request.getBackPressurePa())
          : throat;
      stations.put(Station.AMBIENT_EXPANDED, ambient);
      double massFlowRateKgS = request.getEffectiveAreaM2() * throat.getMassFluxKgM2s();
      double soundSpeedMs = Math.sqrt(gamma * GAS_CONSTANT_J_MOL_K * throat.getTemperatureK() / molarMassKgMol);
      return ReleaseFlowResult.success(this, massFlowRateKgS, choked, stations, diagnostics, soundSpeedMs, false);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "UNSUPPORTED_IDEAL_GAS_REGIME", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "IDEAL_GAS_CALCULATION_FAILED", ex.getMessage());
    }
  }

  private static ReleaseState expandedState(SystemInterface composition, double pressure0Pa, double temperature0K,
      double molarMassKgMol, double gamma, double enthalpy0Jkg, double entropy0JkgK, double pressurePa) {
    double pressureRatio = pressurePa / pressure0Pa;
    double temperatureK = temperature0K * Math.pow(pressureRatio, (gamma - 1.0) / gamma);
    double specificGasConstant = GAS_CONSTANT_J_MOL_K / molarMassKgMol;
    double heatCapacityJkgK = gamma * specificGasConstant / (gamma - 1.0);
    double velocityMs = Math.sqrt(Math.max(0.0, 2.0 * heatCapacityJkgK * (temperature0K - temperatureK)));
    double densityKgM3 = pressurePa * molarMassKgMol / (GAS_CONSTANT_J_MOL_K * temperatureK);
    double enthalpyJkg = enthalpy0Jkg - 0.5 * velocityMs * velocityMs;
    return ReleaseState.idealGas(composition, pressurePa, temperatureK, densityKgM3, enthalpyJkg, entropy0JkgK,
        velocityMs);
  }

  private static List<Diagnostic> assumptions() {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    diagnostics.add(new Diagnostic("IDEAL_GAS_ASSUMPTIONS",
        "Calorically perfect gas, constant gamma, isentropic acceleration; no real-gas departure or phase change"));
    diagnostics.add(new Diagnostic("NO_PROPERTY_FALLBACK",
        "Mixture molar mass and gamma are required NeqSim properties; no default is substituted"));
    diagnostics.add(new Diagnostic("EXIT_ALIASES_THROAT", "Zero-length opening: exit equals accepted throat"));
    return diagnostics;
  }

  private static void requireGasOnly(SystemInterface fluid) {
    if (fluid.isChemicalSystem() || fluid.isForcePhaseTypes() || fluid.doSolidPhaseCheck() || fluid.getHydrateCheck()) {
      throw new UnsupportedOperationException("Reactions, forced phases, solids and hydrates are excluded");
    }
    if (fluid.getNumberOfPhases() != 1 || fluid.getPhase(0).getType() != PhaseType.GAS) {
      throw new UnsupportedOperationException("The ideal-gas adapter requires one gas phase");
    }
  }

  private static double finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalStateException(name + " must be finite");
    }
    return value;
  }
}
