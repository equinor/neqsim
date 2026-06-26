package neqsim.process.safety.blowby;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Screening calculator for HP-to-LP gas blowby through a failed-open restriction (for example a
 * level-control valve passing gas when its liquid seal is lost, or a high-pressure source bleeding
 * into a low-pressure system).
 *
 * <p>
 * The calculator estimates the gas mass rate through the fully open restriction using the
 * isentropic nozzle relations (API 520 critical-flow form). If the downstream/upstream pressure
 * ratio is below the critical ratio the flow is choked; otherwise a subcritical relation is used.
 * The estimated blowby rate is compared against an optional downstream relief capacity to produce a
 * screening verdict that supports the "More Pressure" HAZOP cause.
 * </p>
 *
 * <p>
 * This is a screening tool: real valve trim, two-phase carry-over and transient inventory effects
 * are not modelled. The specific-heat ratio and molar mass may be supplied directly or derived from
 * a NeqSim fluid.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class GasBlowbyAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(GasBlowbyAnalyzer.class);

  /** Universal gas constant in J/(mol K). */
  private static final double R_GAS = 8.314462618;

  /** Standard temperature in K (15 degC). */
  private static final double STD_TEMPERATURE_K = 288.15;

  /** Standard pressure in Pa (1 atm). */
  private static final double STD_PRESSURE_PA = 101325.0;

  /** Verdict of a gas blowby screening. */
  public enum BlowbyVerdict {
    /** No downstream relief capacity was supplied. */
    NO_RELIEF_DATA,
    /** Downstream relief capacity covers the estimated blowby rate. */
    RELIEF_ADEQUATE,
    /** Downstream relief capacity is below the estimated blowby rate. */
    RELIEF_INADEQUATE
  }

  private double upstreamPressureBara = Double.NaN;
  private double upstreamTemperatureK = Double.NaN;
  private double downstreamPressureBara = Double.NaN;
  private double restrictionAreaM2 = Double.NaN;
  private double dischargeCoefficient = 0.85;
  private double specificHeatRatio = Double.NaN;
  private double molarMassKgPerMol = Double.NaN;
  private SystemInterface gas = null;
  private double reliefCapacityKgPerHr = Double.NaN;
  private boolean reliefDataProvided = false;

  /**
   * Creates an empty gas blowby analyzer with default discharge coefficient 0.85.
   */
  public GasBlowbyAnalyzer() {}

  /**
   * Sets the upstream (high-pressure source) pressure.
   *
   * @param pressure upstream pressure value; must be positive
   * @param unit pressure unit accepted by {@link neqsim.util.unit.PressureUnit}
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setUpstreamPressure(double pressure, String unit) {
    this.upstreamPressureBara = new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
    return this;
  }

  /**
   * Sets the upstream (high-pressure source) temperature.
   *
   * @param temperature upstream temperature value; must be above absolute zero
   * @param unit temperature unit accepted by {@link neqsim.util.unit.TemperatureUnit}
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setUpstreamTemperature(double temperature, String unit) {
    this.upstreamTemperatureK =
        new neqsim.util.unit.TemperatureUnit(temperature, unit).getValue("K");
    return this;
  }

  /**
   * Sets the downstream (low-pressure protected system) pressure, typically the design or relief
   * set pressure. Used to determine whether the flow is choked.
   *
   * @param pressure downstream pressure value; must be non-negative
   * @param unit pressure unit accepted by {@link neqsim.util.unit.PressureUnit}
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setDownstreamPressure(double pressure, String unit) {
    this.downstreamPressureBara =
        new neqsim.util.unit.PressureUnit(pressure, unit).getValue("bara");
    return this;
  }

  /**
   * Sets the effective flow area of the fully open restriction directly.
   *
   * @param area area value; must be positive
   * @param unit area unit, one of "m2", "cm2" or "mm2"
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setRestrictionArea(double area, String unit) {
    this.restrictionAreaM2 = toSquareMetre(area, unit);
    return this;
  }

  /**
   * Sets the effective flow diameter of the fully open restriction. The area is computed as a
   * circle.
   *
   * @param diameter diameter value; must be positive
   * @param unit length unit, one of "m", "cm", "mm" or "in"/"inch"
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setRestrictionDiameter(double diameter, String unit) {
    double d = toMetre(diameter, unit);
    this.restrictionAreaM2 = Math.PI / 4.0 * d * d;
    return this;
  }

  /**
   * Sets the discharge coefficient of the restriction.
   *
   * @param cd discharge coefficient; must be in (0, 1]
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setDischargeCoefficient(double cd) {
    if (cd <= 0.0 || cd > 1.0) {
      throw new IllegalArgumentException("dischargeCoefficient must be in (0, 1]");
    }
    this.dischargeCoefficient = cd;
    return this;
  }

  /**
   * Sets the specific-heat ratio (Cp/Cv) of the gas directly.
   *
   * @param k specific-heat ratio; must be greater than 1
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setSpecificHeatRatio(double k) {
    if (k <= 1.0) {
      throw new IllegalArgumentException("specificHeatRatio must be greater than 1");
    }
    this.specificHeatRatio = k;
    return this;
  }

  /**
   * Sets the molar mass of the gas directly.
   *
   * @param molarMass molar mass value; must be positive
   * @param unit molar mass unit, one of "kg/mol" or "g/mol"
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setMolarMass(double molarMass, String unit) {
    if (molarMass <= 0.0) {
      throw new IllegalArgumentException("molarMass must be positive");
    }
    if ("kg/mol".equalsIgnoreCase(unit)) {
      this.molarMassKgPerMol = molarMass;
    } else if ("g/mol".equalsIgnoreCase(unit)) {
      this.molarMassKgPerMol = molarMass / 1000.0;
    } else {
      throw new IllegalArgumentException("Unsupported molar mass unit: " + unit);
    }
    return this;
  }

  /**
   * Sets a NeqSim fluid from which the specific-heat ratio and molar mass are derived during
   * {@link #analyze()}.
   *
   * @param gas the gas fluid; not null
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setGas(SystemInterface gas) {
    this.gas = gas;
    return this;
  }

  /**
   * Sets the downstream relief capacity that must absorb the blowby gas.
   *
   * @param capacity relief capacity value; must be non-negative
   * @param unit mass-rate unit, one of "kg/hr"/"kg/h" or "kg/s"
   * @return this analyzer for chaining
   */
  public GasBlowbyAnalyzer setDownstreamReliefCapacity(double capacity, String unit) {
    if (capacity < 0.0) {
      throw new IllegalArgumentException("relief capacity must be non-negative");
    }
    if ("kg/hr".equalsIgnoreCase(unit) || "kg/h".equalsIgnoreCase(unit)) {
      this.reliefCapacityKgPerHr = capacity;
    } else if ("kg/s".equalsIgnoreCase(unit) || "kg/sec".equalsIgnoreCase(unit)) {
      this.reliefCapacityKgPerHr = capacity * 3600.0;
    } else {
      throw new IllegalArgumentException("Unsupported relief capacity unit: " + unit);
    }
    this.reliefDataProvided = true;
    return this;
  }

  /**
   * Runs the gas blowby screening.
   *
   * @return an immutable {@link GasBlowbyResult}
   * @throws IllegalStateException if mandatory inputs are missing or non-physical
   */
  public GasBlowbyResult analyze() {
    List<String> warnings = new ArrayList<String>();
    validateInputs();

    double k = resolveSpecificHeatRatio(warnings);
    double m = resolveMolarMass(warnings);

    double p1Pa = upstreamPressureBara * 1.0e5;
    double p2Pa = downstreamPressureBara * 1.0e5;
    double t1 = upstreamTemperatureK;

    double criticalRatio = Math.pow(2.0 / (k + 1.0), k / (k - 1.0));
    double actualRatio = p2Pa / p1Pa;
    boolean choked = actualRatio <= criticalRatio;

    double massRateKgPerS;
    if (choked) {
      massRateKgPerS = dischargeCoefficient * restrictionAreaM2 * p1Pa
          * Math.sqrt(k * m / (R_GAS * t1) * Math.pow(2.0 / (k + 1.0), (k + 1.0) / (k - 1.0)));
    } else {
      double term = Math.pow(actualRatio, 2.0 / k) - Math.pow(actualRatio, (k + 1.0) / k);
      massRateKgPerS = dischargeCoefficient * restrictionAreaM2 * p1Pa
          * Math.sqrt(2.0 * m / (R_GAS * t1) * (k / (k - 1.0)) * term);
      warnings.add("Subcritical (non-choked) flow: downstream pressure ratio "
          + String.format("%.3f", actualRatio) + " exceeds critical ratio "
          + String.format("%.3f", criticalRatio));
    }

    double massRateKgPerHr = massRateKgPerS * 3600.0;
    double molesPerS = massRateKgPerS / m;
    double stdVolM3PerS = molesPerS * R_GAS * STD_TEMPERATURE_K / STD_PRESSURE_PA;
    double stdVolSm3PerHr = stdVolM3PerS * 3600.0;

    double margin = Double.NaN;
    boolean adequate = false;
    BlowbyVerdict verdict;
    if (!reliefDataProvided) {
      verdict = BlowbyVerdict.NO_RELIEF_DATA;
      warnings
          .add("No downstream relief capacity supplied; verdict limited to blowby rate estimate.");
    } else {
      margin = reliefCapacityKgPerHr - massRateKgPerHr;
      adequate = margin >= 0.0;
      verdict = adequate ? BlowbyVerdict.RELIEF_ADEQUATE : BlowbyVerdict.RELIEF_INADEQUATE;
    }

    logger.info("Gas blowby screening: rate {} kg/hr, choked {}, verdict {}", massRateKgPerHr,
        choked, verdict);

    return new GasBlowbyResult(choked, criticalRatio, actualRatio, massRateKgPerHr, stdVolSm3PerHr,
        restrictionAreaM2, dischargeCoefficient, k, m, reliefCapacityKgPerHr, reliefDataProvided,
        margin, adequate, verdict, warnings);
  }

  /**
   * Validates that the mandatory inputs are present and physically consistent.
   *
   * @throws IllegalStateException if a mandatory input is missing or non-physical
   */
  private void validateInputs() {
    if (Double.isNaN(upstreamPressureBara) || upstreamPressureBara <= 0.0) {
      throw new IllegalStateException("upstream pressure must be set and positive");
    }
    if (Double.isNaN(upstreamTemperatureK) || upstreamTemperatureK <= 0.0) {
      throw new IllegalStateException("upstream temperature must be set and above absolute zero");
    }
    if (Double.isNaN(downstreamPressureBara) || downstreamPressureBara < 0.0) {
      throw new IllegalStateException("downstream pressure must be set and non-negative");
    }
    if (downstreamPressureBara >= upstreamPressureBara) {
      throw new IllegalStateException(
          "downstream pressure must be below upstream pressure for blowby");
    }
    if (Double.isNaN(restrictionAreaM2) || restrictionAreaM2 <= 0.0) {
      throw new IllegalStateException("restriction area or diameter must be set and positive");
    }
    if (gas == null && (Double.isNaN(specificHeatRatio) || Double.isNaN(molarMassKgPerMol))) {
      throw new IllegalStateException(
          "either set a gas fluid or supply both specific-heat ratio and molar mass directly");
    }
  }

  /**
   * Resolves the specific-heat ratio, preferring an explicit value and falling back to a NeqSim
   * fluid.
   *
   * @param warnings list to append warnings to
   * @return the specific-heat ratio to use
   */
  private double resolveSpecificHeatRatio(List<String> warnings) {
    if (!Double.isNaN(specificHeatRatio)) {
      return specificHeatRatio;
    }
    try {
      SystemInterface state = gas.clone();
      state.setPressure(upstreamPressureBara, "bara");
      state.setTemperature(upstreamTemperatureK);
      new neqsim.thermodynamicoperations.ThermodynamicOperations(state).TPflash();
      state.initProperties();
      double k = state.getKappa();
      if (Double.isNaN(k) || k <= 1.0) {
        warnings.add("Fluid Cp/Cv non-physical; using default specific-heat ratio 1.3");
        return 1.3;
      }
      return k;
    } catch (Exception ex) {
      warnings
          .add("Failed to evaluate Cp/Cv from fluid (" + ex.getMessage() + "); using default 1.3");
      return 1.3;
    }
  }

  /**
   * Resolves the molar mass, preferring an explicit value and falling back to a NeqSim fluid.
   *
   * @param warnings list to append warnings to
   * @return the molar mass in kg/mol to use
   */
  private double resolveMolarMass(List<String> warnings) {
    if (!Double.isNaN(molarMassKgPerMol)) {
      return molarMassKgPerMol;
    }
    try {
      SystemInterface state = gas.clone();
      state.setPressure(upstreamPressureBara, "bara");
      state.setTemperature(upstreamTemperatureK);
      new neqsim.thermodynamicoperations.ThermodynamicOperations(state).TPflash();
      double mm = state.getMolarMass("kg/mol");
      if (Double.isNaN(mm) || mm <= 0.0) {
        warnings.add("Fluid molar mass non-physical; using default 0.018 kg/mol");
        return 0.018;
      }
      return mm;
    } catch (Exception ex) {
      warnings.add("Failed to evaluate molar mass from fluid (" + ex.getMessage()
          + "); using default 0.018 kg/mol");
      return 0.018;
    }
  }

  /**
   * Converts a length to metres.
   *
   * @param value length value; must be positive
   * @param unit length unit, one of "m", "cm", "mm", "in"/"inch"
   * @return length in metres
   * @throws IllegalArgumentException if the value is non-positive or the unit is unsupported
   */
  private static double toMetre(double value, String unit) {
    if (value <= 0.0) {
      throw new IllegalArgumentException("length must be positive");
    }
    if ("m".equalsIgnoreCase(unit)) {
      return value;
    } else if ("cm".equalsIgnoreCase(unit)) {
      return value / 100.0;
    } else if ("mm".equalsIgnoreCase(unit)) {
      return value / 1000.0;
    } else if ("in".equalsIgnoreCase(unit) || "inch".equalsIgnoreCase(unit)) {
      return value * 0.0254;
    }
    throw new IllegalArgumentException("Unsupported length unit: " + unit);
  }

  /**
   * Converts an area to square metres.
   *
   * @param value area value; must be positive
   * @param unit area unit, one of "m2", "cm2", "mm2"
   * @return area in square metres
   * @throws IllegalArgumentException if the value is non-positive or the unit is unsupported
   */
  private static double toSquareMetre(double value, String unit) {
    if (value <= 0.0) {
      throw new IllegalArgumentException("area must be positive");
    }
    if ("m2".equalsIgnoreCase(unit)) {
      return value;
    } else if ("cm2".equalsIgnoreCase(unit)) {
      return value / 10000.0;
    } else if ("mm2".equalsIgnoreCase(unit)) {
      return value / 1.0e6;
    }
    throw new IllegalArgumentException("Unsupported area unit: " + unit);
  }
}
