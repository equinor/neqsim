package neqsim.process.equipment.refrigeration;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of one compressor stage of a {@link RefrigerationSystem}.
 *
 * <p>
 * Stage 0 takes suction from the warmest level and discharges to the condenser; the last stage takes suction from the
 * coldest level.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class RefrigerationStage implements Serializable {
  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Name of the level the stage takes suction from. */
  private final String levelName;

  /** Suction state after the suction line pressure drop. */
  private final RefrigerantState suction;

  /** Discharge state. */
  private final RefrigerantState discharge;

  /** Mass flow in kg/s. */
  private final double massFlow;

  /** Isentropic efficiency. */
  private final double isentropicEfficiency;

  /** Shaft power in W. */
  private final double power;

  /**
   * Creates a stage result.
   *
   * @param levelName name of the level the stage takes suction from
   * @param suction suction state, or null when the stage has no flow
   * @param discharge discharge state, or null when the stage has no flow
   * @param massFlow mass flow in kg/s
   * @param isentropicEfficiency isentropic efficiency
   * @param power shaft power in W
   */
  RefrigerationStage(String levelName, RefrigerantState suction, RefrigerantState discharge, double massFlow,
      double isentropicEfficiency, double power) {
    this.levelName = levelName;
    this.suction = suction;
    this.discharge = discharge;
    this.massFlow = massFlow;
    this.isentropicEfficiency = isentropicEfficiency;
    this.power = power;
  }

  /**
   * Gets the name of the level the stage takes suction from.
   *
   * @return level name
   */
  public String getLevelName() {
    return levelName;
  }

  /**
   * Gets the suction state after the suction line pressure drop.
   *
   * @return suction state, or null when the stage has no flow
   */
  public RefrigerantState getSuction() {
    return suction;
  }

  /**
   * Gets the discharge state.
   *
   * @return discharge state, or null when the stage has no flow
   */
  public RefrigerantState getDischarge() {
    return discharge;
  }

  /**
   * Gets the mass flow through the stage.
   *
   * @return mass flow in kg/s
   */
  public double getMassFlow() {
    return massFlow;
  }

  /**
   * Gets the actual volume flow at suction, the basis for compressor displacement or inlet-flow sizing.
   *
   * @return suction volume flow in m3/hr
   */
  public double getSuctionVolumeFlow() {
    return suction == null ? 0.0 : massFlow / suction.getDensity() * 3600.0;
  }

  /**
   * Gets the pressure ratio of the stage.
   *
   * @return discharge pressure divided by suction pressure, or NaN when the stage has no flow
   */
  public double getPressureRatio() {
    return suction == null ? Double.NaN : discharge.getPressure() / suction.getPressure();
  }

  /**
   * Gets the isentropic efficiency used for the stage.
   *
   * @return isentropic efficiency
   */
  public double getIsentropicEfficiency() {
    return isentropicEfficiency;
  }

  /**
   * Gets the shaft power of the stage.
   *
   * @return power in W
   */
  public double getPower() {
    return power;
  }

  /**
   * Builds an ordered map of the stage for reporting.
   *
   * @return results map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("suctionLevel", levelName);
    map.put("massFlow_kg_per_hr", massFlow * 3600.0);
    map.put("power_kW", power / 1000.0);
    map.put("isentropicEfficiency", isentropicEfficiency);
    if (suction != null) {
      map.put("suctionVolumeFlow_m3_per_hr", getSuctionVolumeFlow());
      map.put("pressureRatio", getPressureRatio());
      map.put("suction", suction.toMap());
      map.put("discharge", discharge.toMap());
    }
    return map;
  }
}
