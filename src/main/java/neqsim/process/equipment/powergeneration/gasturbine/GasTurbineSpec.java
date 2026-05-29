package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;

/**
 * Immutable catalog entry describing a gas turbine package at ISO conditions (15 °C, 1.013 bara, 60
 * % RH, sea level).
 *
 * <p>
 * All data in this class is sourced from publicly available OEM datasheets and trade press. Values
 * are typical and intended for screening / right-sizing studies, not for guaranteed performance
 * calculations.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public final class GasTurbineSpec implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Engine archetype. */
  public enum TurbineType {
    /** Aircraft-derivative gas turbine (fast start, high efficiency). */
    AERODERIVATIVE,
    /** Heavy industrial gas turbine (long overhaul interval, slower start). */
    INDUSTRIAL
  }

  private final String model;
  private final TurbineType type;
  private final double ratedPowerW;
  private final double heatRateKJPerKWh;
  private final double exhaustFlowKgPerS;
  private final double exhaustTemperatureK;
  private final double noxPpmDLE;
  private final double massTonnes;
  private final String description;

  /**
   * Full constructor.
   *
   * @param model engine model identifier
   * @param type aero-derivative or industrial
   * @param ratedPowerW ISO rated shaft power [W]
   * @param heatRateKJPerKWh ISO heat rate (LHV basis) [kJ/kWh]
   * @param exhaustFlowKgPerS ISO exhaust mass flow [kg/s]
   * @param exhaustTemperatureK ISO exhaust temperature [K]
   * @param noxPpmDLE NOx emission with dry low-emission combustor [ppmv @ 15 % O2]
   * @param massTonnes package dry mass [t]
   * @param description short text description
   */
  public GasTurbineSpec(String model, TurbineType type, double ratedPowerW, double heatRateKJPerKWh,
      double exhaustFlowKgPerS, double exhaustTemperatureK, double noxPpmDLE, double massTonnes,
      String description) {
    this.model = model;
    this.type = type;
    this.ratedPowerW = ratedPowerW;
    this.heatRateKJPerKWh = heatRateKJPerKWh;
    this.exhaustFlowKgPerS = exhaustFlowKgPerS;
    this.exhaustTemperatureK = exhaustTemperatureK;
    this.noxPpmDLE = noxPpmDLE;
    this.massTonnes = massTonnes;
    this.description = description;
  }

  /**
   * Get the OEM model name.
   *
   * @return model name
   */
  public String getModel() {
    return model;
  }

  /**
   * Get the engine archetype.
   *
   * @return turbine type
   */
  public TurbineType getType() {
    return type;
  }

  /**
   * Get the ISO rated power.
   *
   * @return rated power in Watts
   */
  public double getRatedPowerW() {
    return ratedPowerW;
  }

  /**
   * Get the ISO rated power in megawatts.
   *
   * @return rated power in MW
   */
  public double getRatedPowerMW() {
    return ratedPowerW / 1.0e6;
  }

  /**
   * Get the ISO heat rate (LHV basis).
   *
   * @return heat rate in kJ/kWh
   */
  public double getHeatRateKJPerKWh() {
    return heatRateKJPerKWh;
  }

  /**
   * Get the ISO thermal efficiency.
   *
   * @return thermal efficiency (dimensionless, 0–1)
   */
  public double getIsoEfficiency() {
    if (heatRateKJPerKWh <= 0.0) {
      return 0.0;
    }
    return 3600.0 / heatRateKJPerKWh;
  }

  /**
   * Get the ISO exhaust mass flow.
   *
   * @return exhaust flow in kg/s
   */
  public double getExhaustFlowKgPerS() {
    return exhaustFlowKgPerS;
  }

  /**
   * Get the ISO exhaust temperature.
   *
   * @return exhaust temperature in K
   */
  public double getExhaustTemperatureK() {
    return exhaustTemperatureK;
  }

  /**
   * Get the rated NOx emission with dry low-emission combustor.
   *
   * @return NOx in ppmv at 15 % O2
   */
  public double getNoxPpmDLE() {
    return noxPpmDLE;
  }

  /**
   * Get the package dry mass.
   *
   * @return mass in tonnes
   */
  public double getMassTonnes() {
    return massTonnes;
  }

  /**
   * Get a short description.
   *
   * @return description string
   */
  public String getDescription() {
    return description;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format("GasTurbineSpec[%s, %.1f MW, %.0f kJ/kWh, eta=%.1f%%]", model,
        getRatedPowerMW(), heatRateKJPerKWh, getIsoEfficiency() * 100.0);
  }
}
