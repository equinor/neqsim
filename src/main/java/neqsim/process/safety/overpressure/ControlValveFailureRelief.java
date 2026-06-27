package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the relief load from control-valve failure (fail-open) per TR3001 section 4.7.7 (SR-26501) and API STD 521
 * section 4.4.9.
 *
 * <p>
 * TR3001 SR-26501 requires the fail-open case to be evaluated with the upstream pressure at the maximum operating
 * pressure and to account for the possibility that the effective valve capacity (Cv) increases above the nominal value
 * through trim erosion or vibration. This calculator accepts either an effective flow area directly or a valve Cv
 * (converted to an effective area with a documented screening relation) and computes the fail-open gas flow with the
 * compressible nozzle relations.
 * </p>
 *
 * <p>
 * The Cv-to-area conversion uses the relation {@code Cv = 24.6 * Cd * A_in2} (consistent with
 * {@code ReliefValveSizing}), where {@code A_in2} is the effective area in square inches. This is a screening
 * approximation; for final sizing the manufacturer valve gas-flow characteristic should be used.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ControlValveFailureRelief {
  /** Conversion constant from the liquid-Cv definition (Cv = 24.6 Cd A_in2). */
  private static final double CV_TO_AREA_CONSTANT = 24.6;
  /** Square inches per square metre. */
  private static final double IN2_PER_M2 = 1550.0031;

  private String name = "Control valve failure";
  private double effectiveAreaM2 = Double.NaN;
  private double cv = Double.NaN;
  private double cvErosionMargin = 1.0;
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
  public ControlValveFailureRelief setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the effective fail-open flow area directly.
   *
   * @param effectiveAreaM2 the effective flow area in m^2; must be positive
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setEffectiveAreaM2(double effectiveAreaM2) {
    this.effectiveAreaM2 = effectiveAreaM2;
    return this;
  }

  /**
   * Sets the valve flow coefficient Cv. The effective area is derived from the documented screening relation when no
   * explicit area is supplied.
   *
   * @param cv the valve flow coefficient (US gpm/psi^0.5)
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setCv(double cv) {
    this.cv = cv;
    return this;
  }

  /**
   * Sets a multiplier applied to Cv to account for trim erosion or vibration increasing the effective capacity per
   * TR3001 SR-26501. A value of 1.0 applies no margin.
   *
   * @param cvErosionMargin the Cv margin multiplier (dimensionless); must be at least 1
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setCvErosionMargin(double cvErosionMargin) {
    this.cvErosionMargin = cvErosionMargin;
    return this;
  }

  /**
   * Sets the upstream pressure, normally the maximum operating pressure of the source.
   *
   * @param upstreamPressureBara the upstream pressure in bara; must be positive
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setUpstreamPressureBara(double upstreamPressureBara) {
    this.upstreamPressureBara = upstreamPressureBara;
    return this;
  }

  /**
   * Sets the upstream temperature.
   *
   * @param upstreamTemperatureC the upstream temperature in degrees Celsius
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setUpstreamTemperatureC(double upstreamTemperatureC) {
    this.upstreamTemperatureK = upstreamTemperatureC + 273.15;
    return this;
  }

  /**
   * Sets the downstream (protected, low-pressure) pressure.
   *
   * @param downstreamPressureBara the downstream pressure in bara; must be non-negative
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setDownstreamPressureBara(double downstreamPressureBara) {
    this.downstreamPressureBara = downstreamPressureBara;
    return this;
  }

  /**
   * Sets the discharge coefficient applied to the effective area.
   *
   * @param dischargeCoefficient the discharge coefficient Cd in (0, 1]
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setDischargeCoefficient(double dischargeCoefficient) {
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
  public ControlValveFailureRelief setFluid(SystemInterface fluid) {
    this.fluid = fluid;
    return this;
  }

  /**
   * Sets the gas specific-heat ratio Cp/Cv to use when no fluid is supplied.
   *
   * @param specificHeatRatio the specific-heat ratio (dimensionless); must be greater than 1
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setSpecificHeatRatio(double specificHeatRatio) {
    this.specificHeatRatio = specificHeatRatio;
    return this;
  }

  /**
   * Sets the gas molar mass to use when no fluid is supplied.
   *
   * @param molarMassKgPerMol the molar mass in kg/mol; must be positive
   * @return this calculator for chaining
   */
  public ControlValveFailureRelief setMolarMassKgPerMol(double molarMassKgPerMol) {
    this.molarMassKgPerMol = molarMassKgPerMol;
    return this;
  }

  /**
   * Converts a valve Cv to an effective flow area in m^2 using the documented screening relation.
   *
   * @param cvValue the valve flow coefficient
   * @return the effective flow area in m^2
   */
  private double cvToAreaM2(double cvValue) {
    double areaIn2 = cvValue / (CV_TO_AREA_CONSTANT * dischargeCoefficient);
    return areaIn2 / IN2_PER_M2;
  }

  /**
   * Calculates the control-valve failure relief scenario.
   *
   * @return the {@link ReliefScenario} with the fail-open gas relief rate
   */
  public ReliefScenario calculate() {
    List<String> warnings = new ArrayList<String>();

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

    double area = effectiveAreaM2;
    if (Double.isNaN(area) || area <= 0.0) {
      if (!Double.isNaN(cv) && cv > 0.0) {
        double effectiveCv = cv * cvErosionMargin;
        area = cvToAreaM2(effectiveCv);
        if (cvErosionMargin > 1.0) {
          warnings
              .add(String.format("Cv increased by erosion/vibration margin %.2f per TR3001 SR-26501", cvErosionMargin));
        }
        warnings.add("Effective area derived from Cv with screening relation Cv = 24.6 Cd A_in2");
      } else {
        warnings.add("Neither effective area nor Cv set; relief rate is zero");
        area = 0.0;
      }
    }

    double rate = 0.0;
    if (area > 0.0 && !Double.isNaN(upstreamPressureBara) && upstreamPressureBara > 0.0) {
      double p1 = upstreamPressureBara * 1.0e5;
      double p2 = (!Double.isNaN(downstreamPressureBara) ? downstreamPressureBara : 1.01325) * 1.0e5;
      rate = NozzleFlow.gasMassRateKgPerS(dischargeCoefficient, area, p1, p2, tempK, mm, k);
    } else if (Double.isNaN(upstreamPressureBara) || upstreamPressureBara <= 0.0) {
      warnings.add("Upstream pressure not set; relief rate is zero");
    }

    ReliefScenario.Builder builder = new ReliefScenario.Builder(name, ReliefCause.CONTROL_VALVE_FAILURE)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(rate).reliefTemperatureK(tempK).molarMassKgPerMol(mm)
        .compressibility(z).specificHeatRatio(k)
        .addAssumption("Fail-open control valve, upstream at maximum operating pressure per TR3001 SR-26501")
        .addAssumption(String.format("Discharge coefficient Cd = %.2f", dischargeCoefficient));
    builder.assumptions(warnings);
    return builder.build();
  }
}
