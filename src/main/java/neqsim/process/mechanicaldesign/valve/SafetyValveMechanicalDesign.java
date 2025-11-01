package neqsim.process.mechanicaldesign.valve;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.SafetyValve;
import neqsim.process.equipment.valve.SafetyValve.FluidService;
import neqsim.process.equipment.valve.SafetyValve.RelievingScenario;
import neqsim.process.equipment.valve.SafetyValve.SizingStandard;
import neqsim.thermo.system.SystemInterface;

/**
 * Mechanical design for safety valves based on API 520 gas sizing.
 *
 * @author esol
 */
public class SafetyValveMechanicalDesign extends ValveMechanicalDesign {
  private static final long serialVersionUID = 1L;
  private double orificeArea = 0.0; // m^2
  private double controllingOrificeArea = 0.0;
  private String controllingScenarioName;
  private final Map<FluidService, SafetyValveSizingStrategy> strategies;
  private Map<String, SafetyValveScenarioResult> scenarioResults = new LinkedHashMap<>();

  /**
   * <p>
   * Constructor for SafetyValveMechanicalDesign.
   * </p>
   *
   * @param equipment a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public SafetyValveMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    strategies = new EnumMap<>(FluidService.class);
    strategies.put(FluidService.GAS, new GasSizingStrategy());
    strategies.put(FluidService.LIQUID, new LiquidSizingStrategy());
    strategies.put(FluidService.MULTIPHASE, new MultiphaseSizingStrategy());
    strategies.put(FluidService.FIRE, new FireCaseSizingStrategy());
  }

  /**
   * Calculates the required orifice area for gas/vapor service according to API 520.
   *
   * @param massFlow mass flow rate at relieving conditions [kg/s]
   * @param relievingPressure absolute relieving pressure [Pa]
   * @param relievingTemperature relieving temperature [K]
   * @param z gas compressibility factor [-]
   * @param molecularWeight molecular weight [kg/mol]
   * @param k heat capacity ratio (Cp/Cv) [-]
   * @param kd discharge coefficient [-]
   * @param kb back pressure correction factor [-]
   * @param kw installation correction factor [-]
   * @return required flow area [m^2]
   */
  public double calcGasOrificeAreaAPI520(double massFlow, double relievingPressure,
      double relievingTemperature, double z, double molecularWeight, double k, double kd, double kb,
      double kw) {
    double R = 8.314; // J/(mol K)
    double C = Math.sqrt(k) * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (2.0 * (k - 1.0)));
    double numerator = massFlow * Math.sqrt(z * R * relievingTemperature / molecularWeight);
    double denominator = kd * kb * kw * relievingPressure * C;
    return numerator / denominator;
  }

  private double calcGasOrificeAreaISO4126(double massFlow, double relievingPressure,
      double relievingTemperature, double z, double molecularWeight, double k, double kd, double kb,
      double kw) {
    // ISO 4126 uses the same base expression as API 520 but typically with a lower discharge
    // coefficient.
    return calcGasOrificeAreaAPI520(massFlow, relievingPressure, relievingTemperature, z,
        molecularWeight, k, kd, kb, kw);
  }

  private double calcLiquidOrificeArea(double massFlow, double relievingPressure, double backPressure,
      double density, double kd, double kb, double kw) {
    double deltaP = Math.max(relievingPressure - backPressure, 1.0);
    double denominator = kd * kb * kw * Math.sqrt(2.0 * density * deltaP);
    return massFlow / denominator;
  }

  private double calcHemMultiphaseOrificeArea(double massFlow, double relievingPressure,
      double backPressure, double density, double kd, double kb, double kw) {
    double deltaP = Math.max(relievingPressure - backPressure, 1.0);
    double denominator = kd * kb * kw * Math.sqrt(density * deltaP);
    return massFlow / denominator;
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    SafetyValve valve = (SafetyValve) getProcessEquipment();
    valve.ensureDefaultScenario();

    Map<String, SafetyValveScenarioResult> newResults = new LinkedHashMap<>();
    double maxArea = 0.0;
    String maxScenario = null;
    double activeArea = 0.0;
    String activeScenarioName = valve.getActiveScenarioName().orElse(null);

    for (RelievingScenario scenario : valve.getRelievingScenarios()) {
      SizingContext context = buildContext(valve, scenario);
      SafetyValveSizingStrategy strategy = strategies
          .getOrDefault(scenario.getFluidService(), strategies.get(FluidService.GAS));
      double area = strategy.calculateOrificeArea(context);
      boolean isActive = scenario.getName().equals(activeScenarioName)
          || (activeScenarioName == null && newResults.isEmpty());
      if (isActive) {
        activeArea = area;
        activeScenarioName = scenario.getName();
      }

      if (area > maxArea) {
        maxArea = area;
        maxScenario = scenario.getName();
      }

      SafetyValveScenarioResult result = new SafetyValveScenarioResult(scenario.getName(),
          scenario.getFluidService(), scenario.getSizingStandard(), area, context.setPressurePa,
          context.relievingPressurePa, context.overpressureMarginPa, context.backPressurePa,
          isActive, false);
      newResults.put(scenario.getName(), result);
    }

    // Update controlling flags now that maximum is known
    if (maxScenario != null) {
      SafetyValveScenarioResult controlling = newResults.get(maxScenario);
      if (controlling != null) {
        newResults.put(maxScenario,
            controlling.markControlling(true));
      }
    }

    this.scenarioResults = newResults;
    this.orificeArea = activeArea;
    this.controllingOrificeArea = maxArea;
    this.controllingScenarioName = maxScenario;
  }

  /**
   * Returns the calculated orifice area.
   *
   * @return area [m^2]
   */
  public double getOrificeArea() {
    return orificeArea;
  }

  /**
   * @return the largest required orifice area across all configured scenarios
   */
  public double getControllingOrificeArea() {
    return controllingOrificeArea;
  }

  /**
   * @return the name of the scenario requiring the maximum area, or {@code null} if none
   */
  public String getControllingScenarioName() {
    return controllingScenarioName;
  }

  /**
   * Immutable view of scenario sizing results keyed by scenario name.
   *
   * @return map of results
   */
  public Map<String, SafetyValveScenarioResult> getScenarioResults() {
    return Collections.unmodifiableMap(scenarioResults);
  }

  /**
   * Convenience accessor returning a structured report suitable for higher-level analyzers.
   *
   * @return list of report entries preserving scenario insertion order
   */
  public Map<String, SafetyValveScenarioReport> getScenarioReports() {
    Map<String, SafetyValveScenarioReport> report = new LinkedHashMap<>();
    for (Map.Entry<String, SafetyValveScenarioResult> entry : scenarioResults.entrySet()) {
      report.put(entry.getKey(), new SafetyValveScenarioReport(entry.getValue()));
    }
    return Collections.unmodifiableMap(report);
  }

  private SizingContext buildContext(SafetyValve valve, RelievingScenario scenario) {
    StreamInterface stream = Optional.ofNullable(scenario.getRelievingStream())
        .orElse(valve.getInletStream());
    if (stream == null) {
      throw new IllegalStateException("Safety valve requires a stream for sizing calculations");
    }

    SystemInterface system = stream.getThermoSystem();
    double massFlow = system.getFlowRate("kg/sec");
    double relievingTemperature = system.getTemperature();
    double z = system.getZ();
    double mw = system.getMolarMass();
    double k = system.getGamma();
    double density = system.getDensity("kg/m3");

    double setPressureBar = scenario.getSetPressure().orElse(valve.getPressureSpec());
    double setPressurePa = setPressureBar * 1.0e5;
    double relievingPressurePa = setPressurePa * (1.0 + scenario.getOverpressureFraction());
    double overpressureMarginPa = relievingPressurePa - setPressurePa;
    double backPressurePa = scenario.getBackPressure() * 1.0e5;

    double kd = scenario.getDischargeCoefficient()
        .orElseGet(() -> defaultDischargeCoefficient(scenario));
    double kb = scenario.getBackPressureCorrection().orElse(1.0);
    double kw = scenario.getInstallationCorrection().orElse(1.0);

    return new SizingContext(scenario, stream, massFlow, relievingTemperature, z, mw, k, density,
        setPressurePa, relievingPressurePa, overpressureMarginPa, backPressurePa, kd, kb, kw);
  }

  private double defaultDischargeCoefficient(RelievingScenario scenario) {
    if (scenario.getFluidService() == FluidService.GAS
        || scenario.getFluidService() == FluidService.FIRE) {
      return scenario.getSizingStandard() == SizingStandard.ISO_4126 ? 0.9 : 0.975;
    }
    if (scenario.getFluidService() == FluidService.MULTIPHASE) {
      return 0.85;
    }
    // Liquid service
    return 0.62;
  }

  /** Container holding data shared with the sizing strategies. */
  static final class SizingContext {
    final RelievingScenario scenario;
    final StreamInterface stream;
    final double massFlow;
    final double relievingTemperature;
    final double z;
    final double molecularWeight;
    final double k;
    final double density;
    final double setPressurePa;
    final double relievingPressurePa;
    final double overpressureMarginPa;
    final double backPressurePa;
    final double dischargeCoefficient;
    final double backPressureCorrection;
    final double installationCorrection;

    SizingContext(RelievingScenario scenario, StreamInterface stream, double massFlow,
        double relievingTemperature, double z, double molecularWeight, double k, double density,
        double setPressurePa, double relievingPressurePa, double overpressureMarginPa,
        double backPressurePa, double dischargeCoefficient, double backPressureCorrection,
        double installationCorrection) {
      this.scenario = scenario;
      this.stream = stream;
      this.massFlow = massFlow;
      this.relievingTemperature = relievingTemperature;
      this.z = z;
      this.molecularWeight = molecularWeight;
      this.k = k;
      this.density = density;
      this.setPressurePa = setPressurePa;
      this.relievingPressurePa = relievingPressurePa;
      this.overpressureMarginPa = overpressureMarginPa;
      this.backPressurePa = backPressurePa;
      this.dischargeCoefficient = dischargeCoefficient;
      this.backPressureCorrection = backPressureCorrection;
      this.installationCorrection = installationCorrection;
    }
  }

  private interface SafetyValveSizingStrategy {
    double calculateOrificeArea(SizingContext context);
  }

  private class GasSizingStrategy implements SafetyValveSizingStrategy {
    @Override
    public double calculateOrificeArea(SizingContext context) {
      if (context.scenario.getSizingStandard() == SizingStandard.ISO_4126) {
        return calcGasOrificeAreaISO4126(context.massFlow, context.relievingPressurePa,
            context.relievingTemperature, context.z, context.molecularWeight, context.k,
            context.dischargeCoefficient, context.backPressureCorrection,
            context.installationCorrection);
      }
      return calcGasOrificeAreaAPI520(context.massFlow, context.relievingPressurePa,
          context.relievingTemperature, context.z, context.molecularWeight, context.k,
          context.dischargeCoefficient, context.backPressureCorrection,
          context.installationCorrection);
    }
  }

  private class LiquidSizingStrategy implements SafetyValveSizingStrategy {
    @Override
    public double calculateOrificeArea(SizingContext context) {
      double kd = context.dischargeCoefficient;
      double kb = context.backPressureCorrection;
      double kw = context.installationCorrection;
      return calcLiquidOrificeArea(context.massFlow, context.relievingPressurePa,
          context.backPressurePa, context.density, kd, kb, kw);
    }
  }

  private class MultiphaseSizingStrategy implements SafetyValveSizingStrategy {
    @Override
    public double calculateOrificeArea(SizingContext context) {
      double kd = context.dischargeCoefficient;
      double kb = context.backPressureCorrection;
      double kw = context.installationCorrection;
      return calcHemMultiphaseOrificeArea(context.massFlow, context.relievingPressurePa,
          context.backPressurePa, context.density, kd, kb, kw);
    }
  }

  private class FireCaseSizingStrategy extends GasSizingStrategy {
    private static final double FIRE_MARGIN_FACTOR = 1.1;

    @Override
    public double calculateOrificeArea(SizingContext context) {
      double baseArea = super.calculateOrificeArea(context);
      return baseArea * FIRE_MARGIN_FACTOR;
    }
  }

  /**
   * Detailed sizing outcome for a single scenario.
   */
  public static final class SafetyValveScenarioResult {
    private final String scenarioName;
    private final FluidService fluidService;
    private final SizingStandard sizingStandard;
    private final double requiredOrificeArea;
    private final double setPressurePa;
    private final double relievingPressurePa;
    private final double overpressureMarginPa;
    private final double backPressurePa;
    private final boolean activeScenario;
    private final boolean controllingScenario;

    SafetyValveScenarioResult(String scenarioName, FluidService fluidService,
        SizingStandard sizingStandard, double requiredOrificeArea, double setPressurePa,
        double relievingPressurePa, double overpressureMarginPa, double backPressurePa,
        boolean activeScenario, boolean controllingScenario) {
      this.scenarioName = scenarioName;
      this.fluidService = fluidService;
      this.sizingStandard = sizingStandard;
      this.requiredOrificeArea = requiredOrificeArea;
      this.setPressurePa = setPressurePa;
      this.relievingPressurePa = relievingPressurePa;
      this.overpressureMarginPa = overpressureMarginPa;
      this.backPressurePa = backPressurePa;
      this.activeScenario = activeScenario;
      this.controllingScenario = controllingScenario;
    }

    SafetyValveScenarioResult markControlling(boolean controlling) {
      return new SafetyValveScenarioResult(scenarioName, fluidService, sizingStandard,
          requiredOrificeArea, setPressurePa, relievingPressurePa, overpressureMarginPa,
          backPressurePa, activeScenario, controlling);
    }

    public String getScenarioName() {
      return scenarioName;
    }

    public FluidService getFluidService() {
      return fluidService;
    }

    public SizingStandard getSizingStandard() {
      return sizingStandard;
    }

    public double getRequiredOrificeArea() {
      return requiredOrificeArea;
    }

    public double getSetPressurePa() {
      return setPressurePa;
    }

    public double getSetPressureBar() {
      return setPressurePa / 1.0e5;
    }

    public double getRelievingPressurePa() {
      return relievingPressurePa;
    }

    public double getRelievingPressureBar() {
      return relievingPressurePa / 1.0e5;
    }

    public double getOverpressureMarginPa() {
      return overpressureMarginPa;
    }

    public double getOverpressureMarginBar() {
      return overpressureMarginPa / 1.0e5;
    }

    public double getBackPressurePa() {
      return backPressurePa;
    }

    public double getBackPressureBar() {
      return backPressurePa / 1.0e5;
    }

    public boolean isActiveScenario() {
      return activeScenario;
    }

    public boolean isControllingScenario() {
      return controllingScenario;
    }
  }

  /**
   * Lightweight reporting view for consumption by analysis tools.
   */
  public static final class SafetyValveScenarioReport {
    private final String scenarioName;
    private final FluidService fluidService;
    private final SizingStandard sizingStandard;
    private final double requiredOrificeArea;
    private final double setPressureBar;
    private final double relievingPressureBar;
    private final double overpressureMarginBar;
    private final double backPressureBar;
    private final boolean activeScenario;
    private final boolean controllingScenario;

    SafetyValveScenarioReport(SafetyValveScenarioResult result) {
      this.scenarioName = result.getScenarioName();
      this.fluidService = result.getFluidService();
      this.sizingStandard = result.getSizingStandard();
      this.requiredOrificeArea = result.getRequiredOrificeArea();
      this.setPressureBar = result.getSetPressureBar();
      this.relievingPressureBar = result.getRelievingPressureBar();
      this.overpressureMarginBar = result.getOverpressureMarginBar();
      this.backPressureBar = result.getBackPressureBar();
      this.activeScenario = result.isActiveScenario();
      this.controllingScenario = result.isControllingScenario();
    }

    public String getScenarioName() {
      return scenarioName;
    }

    public FluidService getFluidService() {
      return fluidService;
    }

    public SizingStandard getSizingStandard() {
      return sizingStandard;
    }

    public double getRequiredOrificeArea() {
      return requiredOrificeArea;
    }

    public double getSetPressureBar() {
      return setPressureBar;
    }

    public double getRelievingPressureBar() {
      return relievingPressureBar;
    }

    public double getOverpressureMarginBar() {
      return overpressureMarginBar;
    }

    public double getBackPressureBar() {
      return backPressureBar;
    }

    public boolean isActiveScenario() {
      return activeScenario;
    }

    public boolean isControllingScenario() {
      return controllingScenario;
    }
  }
}
