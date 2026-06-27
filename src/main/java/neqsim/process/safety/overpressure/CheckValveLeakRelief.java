package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the relief load from check-valve leakage per TR3001 section 4.7.3 (SR-26466) and API STD 521 section
 * 4.4.9.3.
 *
 * <p>
 * TR3001 SR-26466 requires the leakage area to be taken as 1% of the nominal check-valve flow area (with a recommended
 * minimum of 0.1%), with the upstream pressure at the maximum accumulated pressure (MAAP) of the high-pressure source.
 * The leaking gas flow through this small area is computed with the isentropic compressible nozzle relations and
 * selects the choked or sub-critical branch automatically.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class CheckValveLeakRelief {
  private String name = "Check valve leakage";
  private double nominalDiameterM = Double.NaN;
  private double leakAreaFraction = 0.01;
  private double upstreamPressureBara = Double.NaN;
  private double upstreamTemperatureK = Double.NaN;
  private double downstreamPressureBara = Double.NaN;
  private double dischargeCoefficient = 0.85;
  private SystemInterface fluid = null;
  private double specificHeatRatio = Double.NaN;
  private double molarMassKgPerMol = Double.NaN;

  /**
   * Sets the scenario name.
   *
   * @param name the scenario name
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the nominal check-valve flow diameter used to compute the nominal flow area.
   *
   * @param nominalDiameterM the nominal diameter in metres; must be positive
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setNominalDiameterM(double nominalDiameterM) {
    this.nominalDiameterM = nominalDiameterM;
    return this;
  }

  /**
   * Sets the nominal check-valve flow diameter in inches.
   *
   * @param nominalDiameterInch the nominal diameter in inches; must be positive
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setNominalDiameterInch(double nominalDiameterInch) {
    this.nominalDiameterM = nominalDiameterInch * 0.0254;
    return this;
  }

  /**
   * Sets the leakage area as a fraction of the nominal flow area. The TR3001 SR-26466 default is 0.01 (1%); values
   * below 0.001 (0.1%) are clamped to the recommended minimum.
   *
   * @param leakAreaFraction the leak area fraction (dimensionless)
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setLeakAreaFraction(double leakAreaFraction) {
    this.leakAreaFraction = leakAreaFraction;
    return this;
  }

  /**
   * Sets the upstream (high-pressure source) pressure, normally the maximum accumulated pressure (MAAP) of the source.
   *
   * @param upstreamPressureBara the upstream pressure in bara; must be positive
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setUpstreamPressureBara(double upstreamPressureBara) {
    this.upstreamPressureBara = upstreamPressureBara;
    return this;
  }

  /**
   * Sets the upstream temperature.
   *
   * @param upstreamTemperatureC the upstream temperature in degrees Celsius
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setUpstreamTemperatureC(double upstreamTemperatureC) {
    this.upstreamTemperatureK = upstreamTemperatureC + 273.15;
    return this;
  }

  /**
   * Sets the downstream (protected, low-pressure) pressure.
   *
   * @param downstreamPressureBara the downstream pressure in bara; must be non-negative
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setDownstreamPressureBara(double downstreamPressureBara) {
    this.downstreamPressureBara = downstreamPressureBara;
    return this;
  }

  /**
   * Sets the discharge coefficient applied to the leakage area.
   *
   * @param dischargeCoefficient the discharge coefficient Cd in (0, 1]
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setDischargeCoefficient(double dischargeCoefficient) {
    this.dischargeCoefficient = dischargeCoefficient;
    return this;
  }

  /**
   * Sets the upstream gas fluid used to evaluate the relieving-fluid properties. When supplied it overrides any
   * explicitly set specific-heat ratio and molar mass.
   *
   * @param fluid the NeqSim fluid; not null
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Sets the gas specific-heat ratio Cp/Cv to use when no fluid is supplied.
   *
   * @param specificHeatRatio the specific-heat ratio (dimensionless); must be greater than 1
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setSpecificHeatRatio(double specificHeatRatio) {
    this.specificHeatRatio = specificHeatRatio;
    return this;
  }

  /**
   * Sets the gas molar mass to use when no fluid is supplied.
   *
   * @param molarMassKgPerMol the molar mass in kg/mol; must be positive
   * @return this calculator for chaining
   */
  public CheckValveLeakRelief setMolarMassKgPerMol(double molarMassKgPerMol) {
    this.molarMassKgPerMol = molarMassKgPerMol;
    return this;
  }

  /**
   * Calculates the check-valve leakage relief scenario.
   *
   * @return the {@link ReliefScenario} with the leaking gas relief rate
   */
  public ReliefScenario calculate() {
    List<String> warnings = new ArrayList<String>();
    double fraction = leakAreaFraction;
    if (fraction < 0.001) {
      warnings.add("Leak area fraction below TR3001 minimum 0.1%; clamped to 0.001");
      fraction = 0.001;
    }

    double k = !Double.isNaN(specificHeatRatio) ? specificHeatRatio : 1.3;
    double mm = !Double.isNaN(molarMassKgPerMol) ? molarMassKgPerMol : 0.020;
    double z = 1.0;
    double tempK = !Double.isNaN(upstreamTemperatureK) ? upstreamTemperatureK : 288.15;
    if (fluid != null && !Double.isNaN(upstreamPressureBara)) {
      ReliefFluidState state = ReliefFluidState.evaluate(fluid, upstreamPressureBara, tempK, warnings);
      k = state.specificHeatRatio;
      mm = state.molarMassKgPerMol;
      z = state.compressibility;
      tempK = state.temperatureK;
    }

    double rate = 0.0;
    if (Double.isNaN(nominalDiameterM) || nominalDiameterM <= 0.0) {
      warnings.add("Nominal check-valve diameter not set; relief rate is zero");
    } else if (Double.isNaN(upstreamPressureBara) || upstreamPressureBara <= 0.0) {
      warnings.add("Upstream pressure not set; relief rate is zero");
    } else {
      double nominalArea = Math.PI / 4.0 * nominalDiameterM * nominalDiameterM;
      double leakArea = fraction * nominalArea;
      double p1 = upstreamPressureBara * 1.0e5;
      double p2 = (!Double.isNaN(downstreamPressureBara) ? downstreamPressureBara : 1.01325) * 1.0e5;
      rate = NozzleFlow.gasMassRateKgPerS(dischargeCoefficient, leakArea, p1, p2, tempK, mm, k);
    }

    ReliefScenario.Builder builder = new ReliefScenario.Builder(name, ReliefCause.CHECK_VALVE_LEAKAGE)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(rate).reliefTemperatureK(tempK).molarMassKgPerMol(mm)
        .compressibility(z).specificHeatRatio(k)
        .addAssumption(String.format("Leak area = %.2f%% of nominal flow area per TR3001 SR-26466", fraction * 100.0))
        .addAssumption("Upstream pressure taken at high-pressure source MAAP")
        .addAssumption(String.format("Discharge coefficient Cd = %.2f", dischargeCoefficient));
    builder.assumptions(warnings);
    return builder.build();
  }
}
