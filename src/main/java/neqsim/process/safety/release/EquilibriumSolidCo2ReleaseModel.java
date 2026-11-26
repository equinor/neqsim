package neqsim.process.safety.release;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.release.ReleaseFlowResult.Diagnostic;
import neqsim.process.safety.release.ReleaseFlowResult.Station;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Pure-CO2 choked short-opening model with an equilibrium gas/dry-ice ambient expansion.
 *
 * <p>
 * The solid-free restriction rate and throat are delegated to {@link HomogeneousEquilibriumReleaseModel} at a pressure
 * two percent above the CO2 triple-point pressure. The delegate must remain choked, so this auxiliary boundary cannot
 * determine the rate. Below the triple-point pressure, the ambient sublimation temperature is solved from the NeqSim
 * Clausius-Clapeyron solid-vapour-pressure correlation. Gas and solid mass fractions then follow from the isentropic
 * lever rule, and velocity follows from stagnation-enthalpy closure.
 * </p>
 *
 * <p>
 * This model is restricted to pure CO2 with a solid-free throat. It assumes equilibrium, zero slip and one common
 * temperature at the ambient-expanded station. It does not predict particle size, particle slip, nucleation,
 * deposition, dry ice inside the restriction, shocks or atmospheric mixing.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0.0
 */
public final class EquilibriumSolidCo2ReleaseModel implements ReleaseFlowModel {
  private static final long serialVersionUID = 1L;
  private static final double TRIPLE_PRESSURE_MARGIN = 1.02;
  private static final int SUBLIMATION_ITERATIONS = 100;

  /** @return stable model identifier */
  @Override
  public String getModelId() {
    return "equilibrium-solid-co2-orifice";
  }

  /** @return numerical model version */
  @Override
  public String getModelVersion() {
    return "1.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseModelEvidence getEvidence() {
    return new ReleaseModelEvidence("equilibrium-solid-co2-orifice:1.0.0",
        Arrays.asList("PURE_CO2", "CHOKED_SHORT_ORIFICE", "EQUILIBRIUM_DRY_ICE_AMBIENT_EXPANSION"),
        Arrays.asList("SOLID_FREE_THROAT_REQUIRED", "ZERO_SOLID_SLIP", "NO_PARTICLE_SIZE_DISTRIBUTION",
            "NO_NUCLEATION_OR_DEPOSITION", "NO_SOLID_RESTRICTION_FLOW", "NO_ENGINEERING_QUALIFICATION"),
        Arrays.asList(
            new ReleaseModelEvidence.Record("co2-sublimation-closure", ReleaseModelEvidence.Type.ANALYTICAL,
                "neqsim.thermo.component.ComponentInterface#getCCsolidVaporPressure; "
                    + "src/test/java/neqsim/process/safety/release/HomogeneousEquilibriumDenseCo2BenchmarkTest.java",
                "Clausius-Clapeyron sublimation boundary with entropy lever rule and stagnation-energy closure", false),
            new ReleaseModelEvidence.Record("hammer-dense-co2-restrictions-2022",
                ReleaseModelEvidence.Type.EXPERIMENTAL,
                "DOI 10.1016/j.ijmultiphaseflow.2022.104201; "
                    + "src/test/resources/neqsim/process/safety/release/hammer-dense-co2-2022.csv",
                "Six dense pure-CO2 restriction rates with reported uncertainty; the retained ambient dry-ice "
                    + "fractions are model predictions and were not measured in the source",
                true)));
  }

  /** {@inheritDoc} */
  @Override
  public ReleaseFlowResult calculate(ReleaseFlowRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request required");
      }
      request.requireShortOpening();
      SystemInterface source = request.getFluid();
      requirePureCo2(source);
      ComponentInterface component = source.getComponent(0);
      double triplePressurePa = ReleaseFlowRequest.positive(component.getTriplePointPressure() * 1.0e5,
          "CO2 triple-point pressure");
      if (request.getBackPressurePa() >= triplePressurePa) {
        throw new UnsupportedOperationException(
            "Equilibrium dry-ice ambient model requires receiving pressure below the CO2 triple-point pressure");
      }
      double restrictionPressurePa = TRIPLE_PRESSURE_MARGIN * triplePressurePa;
      if (source.getPressure() * 1.0e5 <= restrictionPressurePa) {
        throw new UnsupportedOperationException("Upstream pressure must exceed the solid-free restriction boundary");
      }
      ReleaseFlowResult restriction = new HomogeneousEquilibriumReleaseModel()
          .calculate(copyAtBackPressure(request, restrictionPressurePa));
      if (!restriction.isUsable()) {
        Diagnostic failure = restriction.getDiagnostics().get(0);
        boolean unsupported = restriction.getStatus() == ReleaseFlowResult.Status.UNSUPPORTED;
        return ReleaseFlowResult.failure(this, unsupported, "RESTRICTION_PLANE_" + failure.getCode(),
            failure.getMessage());
      }
      if (!restriction.isChoked()) {
        throw new UnsupportedOperationException(
            "Solid ambient adapter requires a choked restriction rate independent of receiving pressure");
      }
      ReleaseState upstream = restriction.getStations().get(Station.UPSTREAM_STAGNATION);
      ReleaseState ambient = ambientState(source, upstream, request.getBackPressurePa());
      Map<Station, ReleaseState> states = new EnumMap<Station, ReleaseState>(Station.class);
      states.putAll(restriction.getStations());
      states.put(Station.AMBIENT_EXPANDED, ambient);
      List<Diagnostic> diagnostics = restrictionDiagnostics(restriction);
      diagnostics.add(new Diagnostic("SOLID_MODEL_ASSUMPTIONS",
          "Solid-free HEM restriction; isentropic equilibrium gas/dry-ice ambient state with zero slip"));
      diagnostics.add(new Diagnostic("SOLID_FREE_RESTRICTION_BOUNDARY",
          "HEM restriction boundary=" + restrictionPressurePa + " Pa; CO2 triple pressure=" + triplePressurePa
              + " Pa; accepted only because the restriction remained choked"));
      diagnostics.add(new Diagnostic("AMBIENT_EQUILIBRIUM_DRY_ICE",
          "Clausius-Clapeyron gas/solid equilibrium at " + ambient.getPressurePa() + " Pa and "
              + ambient.getTemperatureK() + " K; solid mass fraction="
              + ambient.getPhaseMassFractions().get(PhaseType.SOLID.name())));
      diagnostics.add(new Diagnostic("SOLID_FLOW_LIMITATIONS",
          "Zero slip and one equilibrium temperature; no particle size, nucleation, deposition or solid throat flow"));
      return ReleaseFlowResult.success(this, restriction.getMassFlowRateKgS(), true, states, diagnostics,
          restriction.getThroatSoundSpeedMs(), restriction.getStatus() == ReleaseFlowResult.Status.VALID_WITH_WARNINGS);
    } catch (UnsupportedOperationException ex) {
      return ReleaseFlowResult.failure(this, true, "UNSUPPORTED_PHYSICS", ex.getMessage());
    } catch (RuntimeException ex) {
      return ReleaseFlowResult.failure(this, false, "CALCULATION_FAILED", ex.getMessage());
    }
  }

  /**
   * Retains restriction diagnostics without mislabelling the replacement solid ambient station.
   *
   * @param restriction accepted solid-free HEM restriction calculation
   * @return mutable diagnostics for this model result
   */
  private static List<Diagnostic> restrictionDiagnostics(ReleaseFlowResult restriction) {
    List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
    for (Diagnostic diagnostic : restriction.getDiagnostics()) {
      boolean delegatedAssumption = "MODEL_ASSUMPTIONS".equals(diagnostic.getCode());
      boolean replacedAmbientAssessment = "SOLID_RISK_ASSESSED".equals(diagnostic.getCode())
          && diagnostic.getMessage().startsWith(Station.AMBIENT_EXPANDED.name() + ":");
      if (!delegatedAssumption && !replacedAmbientAssessment) {
        diagnostics.add(diagnostic);
      }
    }
    return diagnostics;
  }

  /**
   * Copies a request while replacing only its receiving pressure.
   *
   * @param request original immutable request
   * @param backPressurePa replacement absolute pressure in Pa
   * @return copied short-opening request
   */
  private static ReleaseFlowRequest copyAtBackPressure(ReleaseFlowRequest request, double backPressurePa) {
    if (request.hasSelectedUpstreamPhase()) {
      return ReleaseFlowRequest.forSelectedPhase(request.getFluid(), request.getSelectedUpstreamPhase(),
          request.getDiameterM(), request.getDischargeCoefficient(), backPressurePa);
    }
    return new ReleaseFlowRequest(request.getFluid(), request.getDiameterM(), request.getDischargeCoefficient(),
        backPressurePa);
  }

  /**
   * Requires a pure-CO2 composition without changing the caller's state.
   *
   * @param fluid defensive request copy
   */
  private static void requirePureCo2(SystemInterface fluid) {
    if (fluid.getNumberOfComponents() != 1 || !"CO2".equalsIgnoreCase(fluid.getComponent(0).getComponentName())) {
      throw new UnsupportedOperationException("Equilibrium solid ambient model currently supports pure CO2 only");
    }
  }

  /**
   * Resolves the isentropic equilibrium gas/dry-ice ambient station.
   *
   * @param compositionSource pure-CO2 composition and EOS source
   * @param upstream accepted upstream stagnation snapshot
   * @param pressurePa ambient absolute pressure in Pa
   * @return immutable gas/solid ambient station
   */
  private static ReleaseState ambientState(SystemInterface compositionSource, ReleaseState upstream,
      double pressurePa) {
    SystemInterface gas = compositionSource.clone();
    ComponentInterface component = gas.getComponent(0);
    double pressureBar = pressurePa / 1.0e5;
    double temperatureK = sublimationTemperature(component, pressureBar);
    double vaporPressureBar = component.getCCsolidVaporPressure(temperatureK);
    double vaporPressureDerivativeBarK = component.getCCsolidVaporPressuredT(temperatureK);
    double sublimationEnthalpyJkg = ThermodynamicConstantsInterface.R * temperatureK * temperatureK
        * vaporPressureDerivativeBarK / vaporPressureBar / component.getMolarMass();
    if (!Double.isFinite(sublimationEnthalpyJkg) || sublimationEnthalpyJkg <= 0.0) {
      throw new IllegalStateException("CO2 sublimation enthalpy could not be derived from the vapour-pressure slope");
    }
    gas.setPressure(pressureBar);
    gas.setTemperature(temperatureK);
    gas.setSolidPhaseCheck(false);
    gas.setHydrateCheck(false);
    new ThermodynamicOperations(gas).TPflash();
    gas.init(3);
    if (gas.getNumberOfPhases() != 1 || gas.getPhase(0).getType() != PhaseType.GAS) {
      throw new IllegalStateException("Solid-free CO2 reference at the sublimation boundary is not one gas phase");
    }
    double gasEntropyJkgK = gas.getEntropy("J/kgK");
    double solidEntropyJkgK = gasEntropyJkgK - sublimationEnthalpyJkg / temperatureK;
    double gasMassFraction = (upstream.getEntropyJkgK() - solidEntropyJkgK) / (gasEntropyJkgK - solidEntropyJkgK);
    if (!Double.isFinite(gasMassFraction) || gasMassFraction <= 0.0 || gasMassFraction >= 1.0) {
      throw new UnsupportedOperationException(
          "Upstream entropy does not resolve a two-phase gas/dry-ice ambient state");
    }
    double solidMassFraction = 1.0 - gasMassFraction;
    double enthalpyJkg = gas.getEnthalpy("J/kg") - solidMassFraction * sublimationEnthalpyJkg;
    double kineticEnergyJkg = upstream.getEnthalpyJkg() - enthalpyJkg;
    if (!Double.isFinite(kineticEnergyJkg) || kineticEnergyJkg < 0.0) {
      throw new IllegalStateException("Gas/dry-ice ambient state does not close stagnation enthalpy");
    }
    double velocityMs = Math.sqrt(2.0 * kineticEnergyJkg);
    double gasDensityKgM3 = gas.getPhase(0).getMass() / gas.getPhase(0).getVolume("m3");
    double solidDensityKgM3 = component.getPureComponentSolidDensity(temperatureK);
    ReleaseFlowRequest.positive(gasDensityKgM3, "CO2 gas density");
    ReleaseFlowRequest.positive(solidDensityKgM3, "CO2 solid density");
    String componentName = component.getComponentName();
    Map<String, Double> phaseFractions = new TreeMap<String, Double>();
    phaseFractions.put(PhaseType.GAS.name(), gasMassFraction);
    phaseFractions.put(PhaseType.SOLID.name(), solidMassFraction);
    Map<String, Double> phaseDensities = new TreeMap<String, Double>();
    phaseDensities.put(PhaseType.GAS.name(), gasDensityKgM3);
    phaseDensities.put(PhaseType.SOLID.name(), solidDensityKgM3);
    Map<String, Map<String, Double>> phaseComponents = new TreeMap<String, Map<String, Double>>();
    Map<String, Double> pureGas = new TreeMap<String, Double>();
    pureGas.put(componentName, 1.0);
    Map<String, Double> pureSolid = new TreeMap<String, Double>();
    pureSolid.put(componentName, 1.0);
    phaseComponents.put(PhaseType.GAS.name(), pureGas);
    phaseComponents.put(PhaseType.SOLID.name(), pureSolid);
    return ReleaseState.analyticalPhaseBasis(gas, pressurePa, temperatureK, enthalpyJkg, upstream.getEntropyJkgK(),
        velocityMs, phaseFractions, phaseDensities, phaseComponents);
  }

  /**
   * Solves the CO2 sublimation temperature at a specified pressure.
   *
   * @param component pure-CO2 component data
   * @param pressureBar absolute pressure in bar
   * @return sublimation temperature in K
   */
  private static double sublimationTemperature(ComponentInterface component, double pressureBar) {
    ReleaseFlowRequest.positive(pressureBar, "ambient pressure");
    double high = ReleaseFlowRequest.positive(component.getTriplePointTemperature(), "CO2 triple temperature");
    double low = Math.max(1.0, 0.25 * high);
    double lowPressure = component.getCCsolidVaporPressure(low);
    double highPressure = component.getCCsolidVaporPressure(high);
    if (!Double.isFinite(lowPressure) || !Double.isFinite(highPressure) || lowPressure >= pressureBar
        || highPressure <= pressureBar) {
      throw new UnsupportedOperationException("Ambient pressure is outside the CO2 sublimation correlation bracket");
    }
    for (int iteration = 0; iteration < SUBLIMATION_ITERATIONS; iteration++) {
      double mid = 0.5 * (low + high);
      if (component.getCCsolidVaporPressure(mid) > pressureBar) {
        high = mid;
      } else {
        low = mid;
      }
    }
    return 0.5 * (low + high);
  }
}
