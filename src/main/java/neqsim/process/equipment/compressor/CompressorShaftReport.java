package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Operating-point report of a {@link CompressorShaft}: the common speed, the driver power balance and, for every
 * member, its speed, power, surge and stonewall margins, the discharge-pressure margin against the process need and the
 * recycle fraction that anti-surge would have to carry. It is the Java counterpart of the member/state report of a
 * Python shaft group and is meant for automation and MCP use ({@link #toJson()}).
 *
 * <p>
 * Flags per member use fixed wording so callers can test for them: {@code "idle"}, {@code "surge"},
 * {@code "stonewall"}, {@code "recycle required"}, {@code "speed too low for the discharge need"},
 * {@code "excess head (throttle or recycle)"} and {@code "outside member speed range"}.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftReport implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  private final String shaftName;
  private final boolean turning;
  private final double speedRpm;
  private final double ambientTemperatureK;
  private final double totalPowerW;
  private final double requiredDriverPowerW;
  private final double availablePowerW;
  private final double powerUtilisation;
  private final boolean overPower;
  private final String solveStatus;
  private final boolean feasible;
  private final double driverFuelMassFlowKgPerHr;
  private final double driverExhaustTemperatureK;
  private final List<MemberReport> members;

  /**
   * Constructor.
   *
   * @param shaftName name of the shaft
   * @param turning true when at least one member is active
   * @param speedRpm common shaft speed [rpm]
   * @param ambientTemperatureK ambient temperature used for the driver rating [K]
   * @param totalPowerW sum of the active member powers [W]
   * @param requiredDriverPowerW driver power including gearbox losses [W]
   * @param availablePowerW driver power available at the ambient temperature [W], 0 if unlimited
   * @param powerUtilisation required over available driver power, 0 if unlimited
   * @param overPower true when the required power exceeds the available power
   * @param solveStatus status of the last solve, or "NONE"
   * @param feasible feasibility of the last solve
   * @param driverFuelMassFlowKgPerHr driver fuel flow [kg/h], NaN without a gas turbine unit driver
   * @param driverExhaustTemperatureK driver exhaust temperature [K], NaN without a gas turbine unit driver
   * @param members member reports in flow order
   */
  CompressorShaftReport(String shaftName, boolean turning, double speedRpm, double ambientTemperatureK,
      double totalPowerW, double requiredDriverPowerW, double availablePowerW, double powerUtilisation,
      boolean overPower, String solveStatus, boolean feasible, double driverFuelMassFlowKgPerHr,
      double driverExhaustTemperatureK, List<MemberReport> members) {
    this.shaftName = shaftName;
    this.turning = turning;
    this.speedRpm = speedRpm;
    this.ambientTemperatureK = ambientTemperatureK;
    this.totalPowerW = totalPowerW;
    this.requiredDriverPowerW = requiredDriverPowerW;
    this.availablePowerW = availablePowerW;
    this.powerUtilisation = powerUtilisation;
    this.overPower = overPower;
    this.solveStatus = solveStatus;
    this.feasible = feasible;
    this.driverFuelMassFlowKgPerHr = driverFuelMassFlowKgPerHr;
    this.driverExhaustTemperatureK = driverExhaustTemperatureK;
    this.members = new ArrayList<MemberReport>(members);
  }

  /**
   * Get the shaft name.
   *
   * @return shaft name
   */
  public String getShaftName() {
    return shaftName;
  }

  /**
   * Whether the shaft turns (at least one active member).
   *
   * @return true if turning
   */
  public boolean isTurning() {
    return turning;
  }

  /**
   * Get the common shaft speed.
   *
   * @return speed [rpm]
   */
  public double getSpeedRpm() {
    return speedRpm;
  }

  /**
   * Get the ambient temperature used for the driver rating.
   *
   * @return ambient temperature [K]
   */
  public double getAmbientTemperatureK() {
    return ambientTemperatureK;
  }

  /**
   * Get the sum of the member powers.
   *
   * @return power [W]
   */
  public double getTotalPowerW() {
    return totalPowerW;
  }

  /**
   * Get the power the driver must deliver.
   *
   * @return driver power including gearbox losses [W]
   */
  public double getRequiredDriverPowerW() {
    return requiredDriverPowerW;
  }

  /**
   * Get the power the driver can deliver at the ambient temperature.
   *
   * @return available power [W], 0 when no limit is configured
   */
  public double getAvailablePowerW() {
    return availablePowerW;
  }

  /**
   * Get the driver power utilisation.
   *
   * @return required over available power, 0 when no limit is configured
   */
  public double getPowerUtilisation() {
    return powerUtilisation;
  }

  /**
   * Whether the driver is overloaded.
   *
   * @return true if the required power exceeds the available power
   */
  public boolean isOverPower() {
    return overPower;
  }

  /**
   * Get the status of the last solve.
   *
   * @return name of the {@link CompressorShaft.SolveStatus} or "NONE"
   */
  public String getSolveStatus() {
    return solveStatus;
  }

  /**
   * Whether the last solve was feasible.
   *
   * @return true if feasible (or no solve was done)
   */
  public boolean isFeasible() {
    return feasible;
  }

  /**
   * Get the fuel flow of a gas turbine unit driver.
   *
   * @return fuel flow [kg/h], NaN without such a driver
   */
  public double getDriverFuelMassFlowKgPerHr() {
    return driverFuelMassFlowKgPerHr;
  }

  /**
   * Get the exhaust temperature of a gas turbine unit driver.
   *
   * @return exhaust temperature [K], NaN without such a driver
   */
  public double getDriverExhaustTemperatureK() {
    return driverExhaustTemperatureK;
  }

  /**
   * Get the member reports.
   *
   * @return unmodifiable list in flow order
   */
  public List<MemberReport> getMembers() {
    return Collections.unmodifiableList(members);
  }

  /**
   * Get the report of one member by compressor name.
   *
   * @param name compressor name
   * @return the member report, or {@code null} if no member has that name
   */
  public MemberReport getMember(String name) {
    for (MemberReport m : members) {
      if (m.getName().equals(name)) {
        return m;
      }
    }
    return null;
  }

  /**
   * Whether any active member needs anti-surge recycle to stay on its control line.
   *
   * @return true if a recycle fraction above zero is required
   */
  public boolean isRecycleRequired() {
    for (MemberReport m : members) {
      if (m.isActive() && m.getRequiredRecycleFraction() > 0.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Serialize the report to compact JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"shaft\":\"").append(esc(shaftName)).append("\",\"turning\":").append(turning);
    sb.append(",\"speedRpm\":").append(num(speedRpm));
    sb.append(",\"ambientTemperatureK\":").append(num(ambientTemperatureK));
    sb.append(",\"totalPowerW\":").append(num(totalPowerW));
    sb.append(",\"requiredDriverPowerW\":").append(num(requiredDriverPowerW));
    sb.append(",\"availablePowerW\":").append(num(availablePowerW));
    sb.append(",\"powerUtilisation\":").append(num(powerUtilisation));
    sb.append(",\"overPower\":").append(overPower);
    sb.append(",\"solveStatus\":\"").append(solveStatus).append("\",\"feasible\":").append(feasible);
    sb.append(",\"driverFuelMassFlowKgPerHr\":").append(num(driverFuelMassFlowKgPerHr));
    sb.append(",\"driverExhaustTemperatureK\":").append(num(driverExhaustTemperatureK));
    sb.append(",\"recycleRequired\":").append(isRecycleRequired()).append(",\"members\":[");
    for (int i = 0; i < members.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(members.get(i).toJson());
    }
    return sb.append("]}").toString();
  }

  /**
   * JSON-safe number: NaN and infinity become null.
   *
   * @param v value
   * @return JSON token
   */
  static String num(double v) {
    return Double.isNaN(v) || Double.isInfinite(v) ? "null" : Double.toString(v);
  }

  /**
   * Escape a string for JSON.
   *
   * @param s text
   * @return escaped text
   */
  static String esc(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "'");
  }

  /**
   * State of one member at the common shaft speed.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class MemberReport implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final String name;
    private final boolean active;
    private final double gearRatio;
    private final double speedRpm;
    private final double powerW;
    private final double inletFlowM3PerHr;
    private final double inletPressureBara;
    private final double outletPressureBara;
    private final double requiredDischargeBara;
    private final double dischargeMarginBar;
    private final double distanceToSurge;
    private final double distanceToStoneWall;
    private final double requiredRecycleFraction;
    private final double recyclePowerW;
    private final boolean surge;
    private final boolean stoneWall;
    private final boolean speedWithinRange;
    private final List<String> flags;

    /**
     * Constructor.
     *
     * @param name compressor name
     * @param active true when in service
     * @param gearRatio member rpm per shaft rpm
     * @param speedRpm member speed [rpm]
     * @param powerW member shaft power [W]
     * @param inletFlowM3PerHr inlet actual flow [m3/h]
     * @param inletPressureBara suction pressure [bara]
     * @param outletPressureBara discharge pressure [bara]
     * @param requiredDischargeBara process discharge need [bara], NaN if not set
     * @param distanceToSurge fractional distance to surge, NaN if unavailable
     * @param distanceToStoneWall fractional distance to stonewall, NaN if unavailable
     * @param requiredRecycleFraction recycle fraction needed to reach the control line
     * @param recyclePowerW power spent on that recycle [W]
     * @param surge true if at or beyond surge
     * @param stoneWall true if at or beyond stonewall
     * @param speedWithinRange true if the member speed is inside its minimum/maximum speed
     * @param flags textual flags
     */
    MemberReport(String name, boolean active, double gearRatio, double speedRpm, double powerW, double inletFlowM3PerHr,
        double inletPressureBara, double outletPressureBara, double requiredDischargeBara, double distanceToSurge,
        double distanceToStoneWall, double requiredRecycleFraction, double recyclePowerW, boolean surge,
        boolean stoneWall, boolean speedWithinRange, List<String> flags) {
      this.name = name;
      this.active = active;
      this.gearRatio = gearRatio;
      this.speedRpm = speedRpm;
      this.powerW = powerW;
      this.inletFlowM3PerHr = inletFlowM3PerHr;
      this.inletPressureBara = inletPressureBara;
      this.outletPressureBara = outletPressureBara;
      this.requiredDischargeBara = requiredDischargeBara;
      this.dischargeMarginBar = Double.isNaN(requiredDischargeBara) ? Double.NaN
          : outletPressureBara - requiredDischargeBara;
      this.distanceToSurge = distanceToSurge;
      this.distanceToStoneWall = distanceToStoneWall;
      this.requiredRecycleFraction = requiredRecycleFraction;
      this.recyclePowerW = recyclePowerW;
      this.surge = surge;
      this.stoneWall = stoneWall;
      this.speedWithinRange = speedWithinRange;
      this.flags = new ArrayList<String>(flags);
    }

    /**
     * Get the compressor name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Whether the member is in service.
     *
     * @return true if active
     */
    public boolean isActive() {
      return active;
    }

    /**
     * Get the gear ratio.
     *
     * @return member rpm per shaft rpm
     */
    public double getGearRatio() {
      return gearRatio;
    }

    /**
     * Get the member speed.
     *
     * @return speed [rpm]
     */
    public double getSpeedRpm() {
      return speedRpm;
    }

    /**
     * Get the member shaft power.
     *
     * @return power [W], 0 when idle
     */
    public double getPowerW() {
      return powerW;
    }

    /**
     * Get the inlet actual flow.
     *
     * @return flow [m3/h]
     */
    public double getInletFlowM3PerHr() {
      return inletFlowM3PerHr;
    }

    /**
     * Get the suction pressure.
     *
     * @return pressure [bara]
     */
    public double getInletPressureBara() {
      return inletPressureBara;
    }

    /**
     * Get the discharge pressure.
     *
     * @return pressure [bara]
     */
    public double getOutletPressureBara() {
      return outletPressureBara;
    }

    /**
     * Get the process discharge need.
     *
     * @return required discharge pressure [bara], NaN if not set
     */
    public double getRequiredDischargeBara() {
      return requiredDischargeBara;
    }

    /**
     * Get the discharge-pressure margin: delivered minus required.
     *
     * @return margin [bar], positive when the chart gives more than the process needs, NaN if no need was set
     */
    public double getDischargeMarginBar() {
      return dischargeMarginBar;
    }

    /**
     * Get the fractional distance to surge.
     *
     * @return distance, NaN when no chart is available
     */
    public double getDistanceToSurge() {
      return distanceToSurge;
    }

    /**
     * Get the fractional distance to stonewall.
     *
     * @return distance, NaN when no chart is available
     */
    public double getDistanceToStoneWall() {
      return distanceToStoneWall;
    }

    /**
     * Get the recycle fraction needed to hold the member on its anti-surge control line.
     *
     * @return fraction of the suction flow (0 to 1)
     */
    public double getRequiredRecycleFraction() {
      return requiredRecycleFraction;
    }

    /**
     * Get the shaft power wasted by that recycle.
     *
     * @return power [W]
     */
    public double getRecyclePowerW() {
      return recyclePowerW;
    }

    /**
     * Whether the member is at or beyond surge.
     *
     * @return true if in surge
     */
    public boolean isSurge() {
      return surge;
    }

    /**
     * Whether the member is at or beyond stonewall.
     *
     * @return true if in stonewall
     */
    public boolean isStoneWall() {
      return stoneWall;
    }

    /**
     * Whether the member speed is inside its speed range.
     *
     * @return true if inside the range
     */
    public boolean isSpeedWithinRange() {
      return speedWithinRange;
    }

    /**
     * Get the flags.
     *
     * @return unmodifiable list of flag texts
     */
    public List<String> getFlags() {
      return Collections.unmodifiableList(flags);
    }

    /**
     * Serialize to compact JSON.
     *
     * @return JSON string
     */
    String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"name\":\"").append(esc(name)).append("\",\"active\":").append(active);
      sb.append(",\"gearRatio\":").append(num(gearRatio));
      sb.append(",\"speedRpm\":").append(num(speedRpm));
      sb.append(",\"powerW\":").append(num(powerW));
      sb.append(",\"inletFlowM3PerHr\":").append(num(inletFlowM3PerHr));
      sb.append(",\"inletPressureBara\":").append(num(inletPressureBara));
      sb.append(",\"outletPressureBara\":").append(num(outletPressureBara));
      sb.append(",\"requiredDischargeBara\":").append(num(requiredDischargeBara));
      sb.append(",\"dischargeMarginBar\":").append(num(dischargeMarginBar));
      sb.append(",\"distanceToSurge\":").append(num(distanceToSurge));
      sb.append(",\"distanceToStoneWall\":").append(num(distanceToStoneWall));
      sb.append(",\"requiredRecycleFraction\":").append(num(requiredRecycleFraction));
      sb.append(",\"recyclePowerW\":").append(num(recyclePowerW));
      sb.append(",\"surge\":").append(surge).append(",\"stoneWall\":").append(stoneWall);
      sb.append(",\"speedWithinRange\":").append(speedWithinRange).append(",\"flags\":[");
      for (int i = 0; i < flags.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append('"').append(esc(flags.get(i))).append('"');
      }
      return sb.append("]}").toString();
    }
  }
}
