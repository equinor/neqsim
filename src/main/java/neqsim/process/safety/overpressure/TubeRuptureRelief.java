package neqsim.process.safety.overpressure;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.system.SystemInterface;

/**
 * Calculates the relief load from heat-exchanger tube rupture per TR3001 section 4.8.6.2 (SR-26616) and API STD 521
 * section 4.4.14.
 *
 * <p>
 * TR3001 SR-26616 requires the full-bore rupture of a single tube to be evaluated, which presents two open ends to the
 * low-pressure side (the flow area is therefore twice the tube cross-section). The high-pressure fluid is taken at its
 * maximum operating pressure and the relief inlet pressure must not exceed the lowest MAWP on the low-pressure side.
 * Vaporization/flashing of the high-pressure fluid as it enters the low-pressure side should be accounted for; this
 * screening calculator records that assumption and uses the high-pressure fluid properties for the compressible nozzle
 * flow.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class TubeRuptureRelief {
  private String name = "Tube rupture";
  private double tubeInnerDiameterM = Double.NaN;
  private int numberOfOpenEnds = 2;
  private double highPressureBara = Double.NaN;
  private double highTemperatureK = Double.NaN;
  private double lowPressureBara = Double.NaN;
  private double dischargeCoefficient = 0.85;
  private SystemInterface highPressureFluid = null;
  private double specificHeatRatio = Double.NaN;
  private double molarMassKgPerMol = Double.NaN;

  /**
   * Sets the scenario name.
   *
   * @param name the scenario name
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setName(String name) {
    this.name = name;
    return this;
  }

  /**
   * Sets the tube inner diameter.
   *
   * @param tubeInnerDiameterM the tube inner diameter in metres; must be positive
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setTubeInnerDiameterM(double tubeInnerDiameterM) {
    this.tubeInnerDiameterM = tubeInnerDiameterM;
    return this;
  }

  /**
   * Sets the tube inner diameter in millimetres.
   *
   * @param tubeInnerDiameterMm the tube inner diameter in millimetres; must be positive
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setTubeInnerDiameterMm(double tubeInnerDiameterMm) {
    this.tubeInnerDiameterM = tubeInnerDiameterMm / 1000.0;
    return this;
  }

  /**
   * Sets the number of open ends presented by the rupture. A full-bore single-tube rupture presents two open ends (the
   * default).
   *
   * @param numberOfOpenEnds the number of open ends; must be at least 1
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setNumberOfOpenEnds(int numberOfOpenEnds) {
    this.numberOfOpenEnds = numberOfOpenEnds;
    return this;
  }

  /**
   * Sets the high-pressure side pressure, normally the maximum operating pressure.
   *
   * @param highPressureBara the high-pressure side pressure in bara; must be positive
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setHighPressureBara(double highPressureBara) {
    this.highPressureBara = highPressureBara;
    return this;
  }

  /**
   * Sets the high-pressure side temperature.
   *
   * @param highTemperatureC the high-pressure side temperature in degrees Celsius
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setHighTemperatureC(double highTemperatureC) {
    this.highTemperatureK = highTemperatureC + 273.15;
    return this;
  }

  /**
   * Sets the low-pressure side pressure into which the rupture discharges.
   *
   * @param lowPressureBara the low-pressure side pressure in bara; must be non-negative
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setLowPressureBara(double lowPressureBara) {
    this.lowPressureBara = lowPressureBara;
    return this;
  }

  /**
   * Sets the discharge coefficient applied to the rupture flow area.
   *
   * @param dischargeCoefficient the discharge coefficient Cd in (0, 1]
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setDischargeCoefficient(double dischargeCoefficient) {
    this.dischargeCoefficient = dischargeCoefficient;
    return this;
  }

  /**
   * Sets the high-pressure side fluid used to evaluate the relieving-fluid properties. When supplied it overrides any
   * explicitly set specific-heat ratio and molar mass.
   *
   * @param highPressureFluid the NeqSim fluid; not null
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setHighPressureFluid(SystemInterface highPressureFluid) {
    this.highPressureFluid = highPressureFluid;
    return this;
  }

  /**
   * Sets the gas specific-heat ratio Cp/Cv to use when no fluid is supplied.
   *
   * @param specificHeatRatio the specific-heat ratio (dimensionless); must be greater than 1
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setSpecificHeatRatio(double specificHeatRatio) {
    this.specificHeatRatio = specificHeatRatio;
    return this;
  }

  /**
   * Sets the gas molar mass to use when no fluid is supplied.
   *
   * @param molarMassKgPerMol the molar mass in kg/mol; must be positive
   * @return this calculator for chaining
   */
  public TubeRuptureRelief setMolarMassKgPerMol(double molarMassKgPerMol) {
    this.molarMassKgPerMol = molarMassKgPerMol;
    return this;
  }

  /**
   * Calculates the tube-rupture relief scenario.
   *
   * @return the {@link ReliefScenario} with the tube-rupture relief rate
   */
  public ReliefScenario calculate() {
    List<String> warnings = new ArrayList<String>();
    if (numberOfOpenEnds < 1) {
      warnings.add("Number of open ends below 1; using 2 (full-bore single-tube rupture)");
      numberOfOpenEnds = 2;
    }

    double k = !Double.isNaN(specificHeatRatio) ? specificHeatRatio : 1.3;
    double mm = !Double.isNaN(molarMassKgPerMol) ? molarMassKgPerMol : 0.020;
    double z = 1.0;
    double tempK = !Double.isNaN(highTemperatureK) ? highTemperatureK : 288.15;
    if (highPressureFluid != null && !Double.isNaN(highPressureBara)) {
      ReliefFluidState state = ReliefFluidState.evaluate(highPressureFluid, highPressureBara, tempK, warnings);
      k = state.specificHeatRatio;
      mm = state.molarMassKgPerMol;
      z = state.compressibility;
      tempK = state.temperatureK;
    }

    double rate = 0.0;
    if (Double.isNaN(tubeInnerDiameterM) || tubeInnerDiameterM <= 0.0) {
      warnings.add("Tube inner diameter not set; relief rate is zero");
    } else if (Double.isNaN(highPressureBara) || highPressureBara <= 0.0) {
      warnings.add("High-pressure side pressure not set; relief rate is zero");
    } else {
      double tubeArea = Math.PI / 4.0 * tubeInnerDiameterM * tubeInnerDiameterM;
      double area = numberOfOpenEnds * tubeArea;
      double p1 = highPressureBara * 1.0e5;
      double p2 = (!Double.isNaN(lowPressureBara) ? lowPressureBara : 1.01325) * 1.0e5;
      rate = NozzleFlow.gasMassRateKgPerS(dischargeCoefficient, area, p1, p2, tempK, mm, k);
    }

    ReliefScenario.Builder builder = new ReliefScenario.Builder(name, ReliefCause.TUBE_RUPTURE)
        .phase(ReliefPhase.VAPOUR).reliefRateKgPerS(rate).reliefTemperatureK(tempK).molarMassKgPerMol(mm)
        .compressibility(z).specificHeatRatio(k)
        .addAssumption(
            String.format("%d open ends (full-bore single-tube rupture) per TR3001 SR-26616", numberOfOpenEnds))
        .addAssumption("High-pressure fluid at maximum operating pressure")
        .addAssumption("Vaporization/flashing on the low-pressure side not modelled (screening estimate)")
        .addAssumption(String.format("Discharge coefficient Cd = %.2f", dischargeCoefficient));
    builder.assumptions(warnings);
    return builder.build();
  }
}
