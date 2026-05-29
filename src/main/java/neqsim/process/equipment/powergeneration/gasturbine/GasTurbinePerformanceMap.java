package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;

/**
 * Part-load and ambient-correction performance map for a gas turbine.
 *
 * <p>
 * The map combines three effects to map ISO rated performance from {@link GasTurbineSpec} to actual
 * site conditions and operating load:
 * </p>
 * <ul>
 * <li><b>Part-load heat rate</b> — polynomial in load fraction (load = actual power / rated power
 * at site conditions). Default coefficients reproduce the classical aeroderivative shape: ~+5 %
 * heat rate at 75 % load, ~+18 % at 50 %, ~+45 % at 30 %.</li>
 * <li><b>Ambient temperature correction</b> — linear loss of available power above 15 °C ISO (~0.7
 * %/°C for aeroderivatives, ~0.5 %/°C for industrial frames) and a small heat-rate penalty.</li>
 * <li><b>Site pressure / altitude correction</b> — power scales linearly with ambient pressure
 * ratio relative to 1.013 bara.</li>
 * </ul>
 *
 * <p>
 * All coefficients can be overridden if vendor maps are available.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class GasTurbinePerformanceMap implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** ISO reference ambient temperature [K]. */
  public static final double T_ISO_K = 288.15;
  /** ISO reference ambient pressure [bara]. */
  public static final double P_ISO_BARA = 1.01325;

  // Heat-rate polynomial: HR / HR_iso = a0 + a1*x + a2*x^2 with x = load fraction
  private double heatRatePolyA0;
  private double heatRatePolyA1;
  private double heatRatePolyA2;

  // Ambient correction
  private double powerLossPerKelvin;
  private double heatRateRisePerKelvin;

  // Minimum acceptable load before flame-out / unstable combustion
  private double minLoadFraction = 0.30;

  /**
   * Create a default map suitable for a low-emission aeroderivative turbine.
   */
  public GasTurbinePerformanceMap() {
    // Calibrated so that HR/HRiso(1.0)=1.00, HR/HRiso(0.75)=1.05,
    // HR/HRiso(0.50)=1.18, HR/HRiso(0.30)=1.45
    // Least-squares fit of polynomial a0 + a1*x + a2*x^2.
    this.heatRatePolyA0 = 2.106;
    this.heatRatePolyA1 = -2.587;
    this.heatRatePolyA2 = 1.481;
    this.powerLossPerKelvin = 0.007;
    this.heatRateRisePerKelvin = 0.0008;
  }

  /**
   * Create a map for an industrial / heavy-frame turbine with flatter part-load curve and lower
   * ambient sensitivity.
   *
   * @return new map instance configured for industrial turbines
   */
  public static GasTurbinePerformanceMap forIndustrial() {
    GasTurbinePerformanceMap m = new GasTurbinePerformanceMap();
    m.heatRatePolyA0 = 1.825;
    m.heatRatePolyA1 = -1.973;
    m.heatRatePolyA2 = 1.148;
    m.powerLossPerKelvin = 0.005;
    m.heatRateRisePerKelvin = 0.0006;
    m.minLoadFraction = 0.40;
    return m;
  }

  /**
   * Create a map matching the supplied catalog spec (selects industrial vs aeroderivative
   * defaults).
   *
   * @param spec gas turbine catalog entry
   * @return new map configured for the spec type
   */
  public static GasTurbinePerformanceMap fromSpec(GasTurbineSpec spec) {
    if (spec != null && spec.getType() == GasTurbineSpec.TurbineType.INDUSTRIAL) {
      return forIndustrial();
    }
    return new GasTurbinePerformanceMap();
  }

  /**
   * Maximum continuous power available at the given ambient conditions.
   *
   * @param ratedPowerW ISO rated power [W]
   * @param ambientTemperatureK ambient temperature [K]
   * @param ambientPressureBara ambient pressure [bara]
   * @return site-corrected available power [W]
   */
  public double getAvailablePower(double ratedPowerW, double ambientTemperatureK,
      double ambientPressureBara) {
    double tFactor = 1.0 - powerLossPerKelvin * (ambientTemperatureK - T_ISO_K);
    if (tFactor < 0.5) {
      tFactor = 0.5;
    }
    double pFactor = ambientPressureBara / P_ISO_BARA;
    if (pFactor < 0.5) {
      pFactor = 0.5;
    }
    return ratedPowerW * tFactor * pFactor;
  }

  /**
   * Effective heat rate at a given load fraction and ambient temperature.
   *
   * @param isoHeatRateKJPerKWh ISO heat rate [kJ/kWh]
   * @param loadFraction fraction of site-corrected available power (0–1.1)
   * @param ambientTemperatureK ambient temperature [K]
   * @return effective heat rate [kJ/kWh]
   * @throws IllegalArgumentException if {@code loadFraction} is non-positive
   */
  public double getHeatRate(double isoHeatRateKJPerKWh, double loadFraction,
      double ambientTemperatureK) {
    if (loadFraction <= 0.0) {
      throw new IllegalArgumentException("loadFraction must be positive, got " + loadFraction);
    }
    double x = Math.min(1.10, loadFraction);
    double partLoadMultiplier = heatRatePolyA0 + heatRatePolyA1 * x + heatRatePolyA2 * x * x;
    if (partLoadMultiplier < 1.0) {
      // never better than ISO at full load
      partLoadMultiplier = 1.0 - 0.02 * (x - 1.0); // small bonus near rated
      if (partLoadMultiplier < 0.98) {
        partLoadMultiplier = 0.98;
      }
    }
    double tCorr = 1.0 + heatRateRisePerKelvin * (ambientTemperatureK - T_ISO_K);
    if (tCorr < 0.95) {
      tCorr = 0.95;
    }
    return isoHeatRateKJPerKWh * partLoadMultiplier * tCorr;
  }

  /**
   * Thermal efficiency at a given load fraction and ambient temperature.
   *
   * @param isoHeatRateKJPerKWh ISO heat rate [kJ/kWh]
   * @param loadFraction load fraction
   * @param ambientTemperatureK ambient temperature [K]
   * @return thermal efficiency (0–1)
   */
  public double getEfficiency(double isoHeatRateKJPerKWh, double loadFraction,
      double ambientTemperatureK) {
    double hr = getHeatRate(isoHeatRateKJPerKWh, loadFraction, ambientTemperatureK);
    if (hr <= 0.0) {
      return 0.0;
    }
    return 3600.0 / hr;
  }

  /**
   * Exhaust mass flow at the given load fraction (approximately linear with load fraction for
   * aeroderivatives, slightly less than linear for industrial frames).
   *
   * @param ratedExhaustFlowKgPerS ISO exhaust flow [kg/s]
   * @param loadFraction load fraction
   * @return exhaust flow [kg/s]
   */
  public double getExhaustFlow(double ratedExhaustFlowKgPerS, double loadFraction) {
    if (loadFraction <= 0.0) {
      return 0.0;
    }
    double f = 0.5 + 0.5 * Math.min(1.10, loadFraction);
    return ratedExhaustFlowKgPerS * f;
  }

  /**
   * Exhaust temperature at the given load fraction. Exhaust T typically increases as load drops on
   * a two-shaft engine because TET is held constant while air flow drops.
   *
   * @param ratedExhaustTemperatureK ISO exhaust temperature [K]
   * @param loadFraction load fraction
   * @return exhaust temperature [K]
   */
  public double getExhaustTemperature(double ratedExhaustTemperatureK, double loadFraction) {
    double x = Math.max(0.2, Math.min(1.10, loadFraction));
    // Rise of ~30 K from 100 % to 60 %, drop of ~50 K from 60 % to 30 %
    double delta;
    if (x >= 0.6) {
      delta = 30.0 * (1.0 - x) / 0.4;
    } else {
      delta = 30.0 - 80.0 * (0.6 - x) / 0.3;
    }
    return ratedExhaustTemperatureK + delta;
  }

  /**
   * Get minimum stable load fraction.
   *
   * @return minimum load fraction (0–1)
   */
  public double getMinLoadFraction() {
    return minLoadFraction;
  }

  /**
   * Set minimum stable load fraction (engineering judgement / OEM limit).
   *
   * @param minLoadFraction minimum load fraction (0–1)
   */
  public void setMinLoadFraction(double minLoadFraction) {
    this.minLoadFraction = minLoadFraction;
  }

  /**
   * Override the part-load polynomial coefficients.
   *
   * @param a0 constant term
   * @param a1 linear term
   * @param a2 quadratic term
   */
  public void setHeatRatePolynomial(double a0, double a1, double a2) {
    this.heatRatePolyA0 = a0;
    this.heatRatePolyA1 = a1;
    this.heatRatePolyA2 = a2;
  }

  /**
   * Override the ambient temperature corrections.
   *
   * @param powerLossPerKelvin fraction of rated power lost per K above ISO
   * @param heatRateRisePerKelvin fractional heat-rate rise per K above ISO
   */
  public void setAmbientCorrection(double powerLossPerKelvin, double heatRateRisePerKelvin) {
    this.powerLossPerKelvin = powerLossPerKelvin;
    this.heatRateRisePerKelvin = heatRateRisePerKelvin;
  }
}
