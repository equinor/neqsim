package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Explicit adapter for the historical {@link LeakModel#calculateMassFlowRate(SystemInterface)} screening equation.
 *
 * <p>
 * The returned rate delegates to the legacy method for numerical compatibility. The legacy method may replace
 * unavailable density, molar mass or heat-capacity ratio with screening defaults. Results are therefore always marked
 * {@code VALID_WITH_WARNINGS} and carry stable {@code SCREENING_ONLY} and unresolved-station diagnostics. New
 * engineering calculations should select a physical model with declared applicability instead.
 */
public final class LegacyScreeningReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;

  /** @return model identifier */
  @Override
  public String getModelId() {
    return "legacy-orifice-screening";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      SystemInterface input = request.getFluid();
      SystemInterface stationFluid = input.clone();
      List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
      diagnostics.add(new Diagnostic("SCREENING_ONLY",
          "Compatibility result from the historical LeakModel equation; not a qualified physical release model"));
      diagnostics.add(new Diagnostic("UNRESOLVED_STATIONS",
          "Legacy screening does not solve throat or exit thermodynamics; reported opening states alias upstream"));
      diagnostics.add(new Diagnostic("LEGACY_FALLBACK_POLICY",
          "The delegated method retains historical density, molar-mass and gamma defaults when properties fail"));
      try {
        new ThermodynamicOperations(stationFluid).TPflash();
      } catch (RuntimeException ex) {
        stationFluid.init(3);
        diagnostics.add(new Diagnostic("LEGACY_TPFLASH_FALLBACK",
            "TP flash failed and the historical initialized-state fallback was used"));
      }
      stationFluid.init(3);
      ReleaseState upstream = ReleaseState.fromFluid(stationFluid, 0.0);
      LeakModel legacy = LeakModel.builder().fluid(input).holeDiameter(request.getDiameterM())
          .dischargeCoefficient(request.getDischargeCoefficient()).backPressure(request.getBackPressurePa()).build();
      double massFlowRateKgS = legacy.calculateMassFlowRate(input);
      if (!Double.isFinite(massFlowRateKgS) || massFlowRateKgS < 0.0) {
        throw new IllegalStateException("Legacy method returned an invalid release rate");
      }
      double velocityMs = massFlowRateKgS == 0.0 ? 0.0
          : massFlowRateKgS / (upstream.getDensityKgM3() * request.getEffectiveAreaM2());
      ReleaseState opening = ReleaseState.fromFluid(stationFluid, velocityMs);
      Map<Station, ReleaseState> stations = new EnumMap<Station, ReleaseState>(Station.class);
      stations.put(Station.UPSTREAM_STAGNATION, upstream);
      stations.put(Station.THROAT_CRITICAL, opening);
      stations.put(Station.ORIFICE_EXIT, opening);
      boolean choked = isLegacyChoked(stationFluid, request.getBackPressurePa(), diagnostics);
      if (massFlowRateKgS == 0.0) {
        choked = false;
        diagnostics.add(new Diagnostic("NO_FORWARD_FLOW_OR_SCREENING_ZERO",
            "Legacy screening returned zero; inspect upstream pressure and retained fallback behavior"));
      }
      return ReleaseFlowResult.success(this, massFlowRateKgS, choked, stations, diagnostics, null, true);
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "LEGACY_SCREENING_ADAPTER_FAILED", ex.getMessage());
    }
  }

  private static boolean isLegacyChoked(SystemInterface fluid, double backPressurePa, List<Diagnostic> diagnostics) {
    double gamma;
    try {
      gamma = fluid.getGamma();
      if (!Double.isFinite(gamma) || gamma <= 1.0 || gamma > 2.0) {
        gamma = 1.3;
        diagnostics.add(new Diagnostic("LEGACY_GAMMA_DEFAULT", "Historical gamma default 1.3 was used"));
      }
    } catch (RuntimeException ex) {
      gamma = 1.3;
      diagnostics.add(new Diagnostic("LEGACY_GAMMA_DEFAULT", "Historical gamma default 1.3 was used"));
    }
    double criticalRatio = Math.pow(2.0 / (gamma + 1.0), gamma / (gamma - 1.0));
    return backPressurePa / (fluid.getPressure() * 1.0e5) <= criticalRatio;
  }
}
