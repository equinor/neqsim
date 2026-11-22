package neqsim.process.mechanicaldesign.heatexchanger;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Single-phase chevron plate exchanger rating using Martin's 1999 correlations.
 *
 * <p>
 * Geometry uses metres and developed (one-face) plate area in m2. Installed plates include two end plates: active area
 * is (N - 2) times the plate area. The N - 1 channels alternate; an odd extra channel belongs to the cold side.
 * Hydraulic diameter is twice the clear gap divided by the surface enlargement factor. Fluid properties are held
 * constant at the caller's representative bulk temperature. This is a thermal-hydraulic screening model, not a
 * pressure-code or vendor design.
 * </p>
 *
 * <p>
 * Thermal rating supports single-pass counterflow. Multipass channel hydraulics are available separately; a pass count
 * alone does not specify the thermal routing. No maldistribution, phase change, static head, gasket or bolt stress is
 * calculated. Fouling models are read at their current operating time and are never advanced here.
 * </p>
 *
 * <p>
 * Reference: H. Martin, Economic optimization of compact heat exchangers (1999), Appendix,
 * https://doi.org/10.5445/IR/1000034866. Darcy friction is four times the paper's Fanning factor. The documented
 * experimental range is Re 200-10000; results flag extrapolation. The original transition at Re = 2000 is retained.
 * </p>
 */
public class PlateHeatExchangerDesignCalculator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private double plateArea = 1.0;
  private double effectiveFlowWidth = 0.5;
  private double plateSpacing = 0.003;
  private double surfaceEnlargementFactor = 1.18;
  private double chevronAngle = 45.0;
  private double plateThickness = 0.0006;
  private double plateConductivity = 16.0;
  private int numberOfPlates = 101;
  private int hotPasses = 1;
  private int coldPasses = 1;
  private double hotPortDiameter = 0.1;
  private double coldPortDiameter = 0.1;
  private double portLossCoefficient = 1.5;
  private int frameCapacityPlates = 0;
  private int boltedCapacityPlates = 0;
  private Side hot;
  private Side cold;
  private FoulingModel hotFoulingModel;
  private FoulingModel coldFoulingModel;

  /** Creates a calculator with illustrative geometry; both fluid sides must be supplied. */
  public PlateHeatExchangerDesignCalculator() {
  }

  /**
   * Evaluates single-pass counterflow without changing any input or fouling state.
   *
   * @return immutable thermal and hydraulic rating
   * @throws IllegalArgumentException for invalid geometry, properties or capacity limits
   * @throws IllegalStateException if a fluid side is missing or derived values are nonfinite
   * @throws UnsupportedOperationException for multipass thermal routing
   */
  public Rating calculate() {
    validate();
    if (hotPasses != 1 || coldPasses != 1) {
      throw new UnsupportedOperationException(
          "Thermal rating requires single-pass counterflow; use calculateHydraulics for multipass channels");
    }
    if (hot.temperature < cold.temperature) {
      throw new IllegalArgumentException("Hot inlet must be at or above cold inlet temperature");
    }
    double rfHot = resistance(hotFoulingModel);
    double rfCold = resistance(coldFoulingModel);
    ChannelResult hotResult = channel(hot, true);
    ChannelResult coldResult = channel(cold, false);
    double area = getHeatTransferArea();
    double cleanU = 0.0;
    double overallU = 0.0;
    if (hot.massFlow > 0.0 && cold.massFlow > 0.0) {
      double cleanResistance = 1.0 / hotResult.heatTransferCoefficient + plateThickness / plateConductivity
          + 1.0 / coldResult.heatTransferCoefficient;
      cleanU = 1.0 / cleanResistance;
      overallU = 1.0 / (cleanResistance + rfHot + rfCold);
    }
    double ch = hot.massFlow * hot.cp;
    double cc = cold.massFlow * cold.cp;
    nonnegative(ch, "hot capacity rate");
    nonnegative(cc, "cold capacity rate");
    double cmin = Math.min(ch, cc);
    double ntu = cmin == 0.0 ? 0.0 : overallU * area / cmin;
    double effectiveness = cmin == 0.0 ? 0.0 : counterflowEffectiveness(ntu, cmin / Math.max(ch, cc));
    double duty = effectiveness * cmin * (hot.temperature - cold.temperature);
    nonnegative(duty, "duty");
    return new Rating(hotResult, coldResult, area, cleanU, overallU, ntu, effectiveness, duty,
        hot.temperature - (ch == 0.0 ? 0.0 : duty / ch), cold.temperature + (cc == 0.0 ? 0.0 : duty / cc));
  }

  /**
   * Calculates one side's hydraulics, including equal channels per pass and port losses.
   *
   * @param hotSide true for hot, false for cold
   * @return channel and port results; multipass turning losses beyond the supplied K are excluded
   * @throws IllegalArgumentException for invalid inputs or nonintegral channels per pass
   * @throws IllegalStateException if a fluid side is missing
   */
  public ChannelResult calculateHydraulics(boolean hotSide) {
    validate();
    return channel(hotSide ? hot : cold, hotSide);
  }

  private ChannelResult channel(Side side, boolean hotSide) {
    int passes = hotSide ? hotPasses : coldPasses;
    int channels = hotSide ? getHotChannelCount() : getColdChannelCount();
    double diameter = hotSide ? hotPortDiameter : coldPortDiameter;
    double flowArea = channels / passes * plateSpacing * effectiveFlowWidth;
    double dh = getHydraulicDiameter();
    double velocity = side.massFlow / (side.density * flowArea);
    double re = side.density * velocity * dh / side.viscosity;
    double pr = side.cp * side.viscosity / side.conductivity;
    double fd = re == 0.0 ? 0.0 : martinDarcyFrictionFactor(re, chevronAngle);
    double nu = re == 0.0 ? 0.0 : martinNusseltNumber(re, pr, chevronAngle);
    double h = nu * side.conductivity / dh;
    double channelDrop = fd * passes * getEffectiveFlowLength() / dh * side.density * velocity * velocity / 2.0;
    double portVelocity = 4.0 * side.massFlow / (side.density * Math.PI * diameter * diameter);
    double portDrop = portLossCoefficient * passes * side.density * portVelocity * portVelocity / 2.0;
    double[] values = {velocity, re, pr, fd, nu, h, channelDrop, portVelocity, portDrop};
    for (double value : values) {
      nonnegative(value, "channel result");
    }
    return new ChannelResult(channels / passes, velocity, re, pr, fd, nu, h, channelDrop, portVelocity, portDrop);
  }

  /**
   * Martin (1999) Darcy friction factor, including the original Re = 2000 transition.
   *
   * @param reynolds positive channel Reynolds number
   * @param angleDegrees chevron angle to main flow in degrees, from 10 through 80
   * @return Darcy friction factor (four times Fanning)
   * @throws IllegalArgumentException if inputs are outside the mathematical domain
   */
  public static double martinDarcyFrictionFactor(double reynolds, double angleDegrees) {
    positive(reynolds, "Reynolds number");
    angle(angleDegrees);
    double phi = Math.toRadians(angleDegrees);
    double cos = Math.cos(phi);
    double f0 = reynolds < 2000.0 ? 16.0 / reynolds : Math.pow(1.56 * Math.log(reynolds) - 3.0, -2.0);
    double f1 = reynolds < 2000.0 ? 149.0 / reynolds + 0.9625 : 9.75 / Math.pow(reynolds, 0.289);
    double inverseRoot = cos / Math.sqrt(0.045 * Math.tan(phi) + 0.09 * Math.sin(phi) + f0 / cos)
        + (1.0 - cos) / Math.sqrt(3.8 * f1);
    return 4.0 / (inverseRoot * inverseRoot);
  }

  /**
   * Martin Nusselt correlation using the matching Darcy friction factor.
   *
   * @param reynolds positive channel Reynolds number
   * @param prandtl positive bulk Prandtl number
   * @param angleDegrees chevron angle to main flow in degrees, from 10 through 80
   * @return Nusselt number; wall/bulk viscosity correction is assumed unity
   * @throws IllegalArgumentException for invalid inputs
   */
  public static double martinNusseltNumber(double reynolds, double prandtl, double angleDegrees) {
    positive(prandtl, "Prandtl number");
    double fd = martinDarcyFrictionFactor(reynolds, angleDegrees);
    return 0.122 * Math.cbrt(prandtl)
        * Math.pow(fd * reynolds * reynolds * Math.sin(2.0 * Math.toRadians(angleDegrees)), 0.374);
  }

  /**
   * Numerically stable counterflow effectiveness, including zero NTU and equal capacity rates.
   *
   * @param ntu finite nonnegative number of transfer units
   * @param capacityRatio smaller divided by larger heat capacity rate, from zero to one
   * @return effectiveness between zero and one
   * @throws IllegalArgumentException for invalid arguments
   */
  public static double counterflowEffectiveness(double ntu, double capacityRatio) {
    nonnegative(ntu, "NTU");
    nonnegative(capacityRatio, "capacity ratio");
    if (capacityRatio > 1.0) {
      throw new IllegalArgumentException("Capacity ratio must not exceed one");
    }
    if (capacityRatio == 1.0) {
      return ntu / (1.0 + ntu);
    }
    double delta = 1.0 - capacityRatio;
    double numerator = -Math.expm1(-ntu * delta);
    return numerator / (delta + capacityRatio * numerator);
  }

  /**
   * Sizes a circular port from total side flow and a specified velocity limit.
   *
   * @param massFlow flow in kg/s, nonnegative
   * @param density density in kg/m3, positive
   * @param maximumVelocity allowable velocity in m/s, positive
   * @return minimum inside diameter in m (zero for no flow)
   * @throws IllegalArgumentException for invalid inputs
   */
  public static double sizePortDiameter(double massFlow, double density, double maximumVelocity) {
    nonnegative(massFlow, "mass flow");
    positive(density, "density");
    positive(maximumVelocity, "maximum port velocity");
    return Math.sqrt(4.0 * massFlow / (Math.PI * density * maximumVelocity));
  }

  private void validate() {
    positive(plateArea, "plate area");
    positive(effectiveFlowWidth, "effective flow width");
    positive(plateSpacing, "plate spacing");
    positive(surfaceEnlargementFactor, "surface enlargement factor");
    positive(plateThickness, "plate thickness");
    positive(plateConductivity, "plate conductivity");
    positive(hotPortDiameter, "hot port diameter");
    positive(coldPortDiameter, "cold port diameter");
    nonnegative(portLossCoefficient, "port loss coefficient");
    angle(chevronAngle);
    if (surfaceEnlargementFactor < 1.0 || numberOfPlates < 3 || hotPasses < 1 || coldPasses < 1
        || getHotChannelCount() % hotPasses != 0 || getColdChannelCount() % coldPasses != 0) {
      throw new IllegalArgumentException(
          "Require enlargement >= 1, N >= 3 and positive passes dividing each side's channels");
    }
    validateCapacities();
    if (hot == null || cold == null) {
      throw new IllegalStateException("Configure both hot and cold fluid sides before rating");
    }
    positive(getHeatTransferArea(), "heat transfer area");
    positive(getHydraulicDiameter(), "hydraulic diameter");
    positive(getEffectiveFlowLength(), "flow length");
  }

  private void validateCapacities() {
    if (frameCapacityPlates < 0 || boltedCapacityPlates < 0
        || (frameCapacityPlates != 0 && frameCapacityPlates < numberOfPlates)
        || (boltedCapacityPlates != 0 && boltedCapacityPlates < numberOfPlates)) {
      throw new IllegalArgumentException("Known frame and bolt capacities must cover the installed plate count");
    }
  }

  private static double resistance(FoulingModel model) {
    double rf = model == null ? 0.0 : model.getFoulingResistance();
    nonnegative(rf, "fouling resistance");
    return rf;
  }

  private static void angle(double value) {
    positive(value, "chevron angle");
    if (value < 10.0 || value > 80.0) {
      throw new IllegalArgumentException("Martin chevron angle must be between 10 and 80 degrees");
    }
  }

  private static void positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
  }

  private static void nonnegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
  }

  /** @return hot channels, excluding the two exterior plate faces */
  public int getHotChannelCount() {
    return (numberOfPlates - 1) / 2;
  }

  /** @return cold channels, including the extra channel for an even plate count */
  public int getColdChannelCount() {
    return numberOfPlates - 1 - getHotChannelCount();
  }

  /** @return developed active heat-transfer area in m2; both end plates are excluded */
  public double getHeatTransferArea() {
    return (numberOfPlates - 2.0) * plateArea;
  }

  /** @return hydraulic channel diameter in m, including surface enlargement */
  public double getHydraulicDiameter() {
    return 2.0 * plateSpacing / surfaceEnlargementFactor;
  }

  /** @return effective straight flow length in m, derived from area, width and enlargement */
  public double getEffectiveFlowLength() {
    return plateArea / (effectiveFlowWidth * surfaceEnlargementFactor);
  }

  /**
   * Returns frame headroom independently of the installed bolt length.
   *
   * @return number of additional plates allowed by the frame
   * @throws IllegalStateException if the frame capacity is unknown
   * @throws IllegalArgumentException if known capacities are below the installed count
   */
  public int getFrameSparePlates() {
    validateCapacities();
    if (frameCapacityPlates == 0) {
      throw new IllegalStateException("Frame capacity is unknown");
    }
    return frameCapacityPlates - numberOfPlates;
  }

  /**
   * Returns headroom jointly limited by frame and current bolts, not a retrofit certification.
   *
   * @return additional plates without replacing the current bolts
   * @throws IllegalStateException if either mechanical capacity is unknown
   * @throws IllegalArgumentException if known capacities are below the installed count
   */
  public int getAdditionalPlatesWithoutBoltReplacement() {
    validateCapacities();
    if (frameCapacityPlates == 0 || boltedCapacityPlates == 0) {
      throw new IllegalStateException("Both frame and bolt capacities are required");
    }
    return Math.min(frameCapacityPlates, boltedCapacityPlates) - numberOfPlates;
  }

  /**
   * @return additional developed surface in m2 allowed by the frame alone
   * @throws IllegalStateException if the frame capacity is unknown
   * @throws IllegalArgumentException if known capacities are invalid
   */
  public double getFrameAdditionalArea() {
    return getFrameSparePlates() * plateArea;
  }

  /**
   * Sets the hot side using constant representative bulk properties.
   *
   * @param massFlow mass flow in kg/s, nonnegative
   * @param temperature inlet temperature in degrees C, above absolute zero
   * @param density density in kg/m3
   * @param viscosity dynamic viscosity in Pa s
   * @param cp specific heat capacity in J/(kg K)
   * @param conductivity thermal conductivity in W/(m K)
   * @throws IllegalArgumentException for nonfinite or nonphysical input
   */
  public void setHotSide(double massFlow, double temperature, double density, double viscosity, double cp,
      double conductivity) {
    hot = new Side(massFlow, temperature, density, viscosity, cp, conductivity);
  }

  /**
   * Snapshots an already flashed single-phase hot inlet stream without modifying it.
   *
   * @param stream stream whose inlet properties will be used as constant bulk properties
   * @throws IllegalArgumentException for null, multiphase or nonphysical input
   */
  public void setHotStream(StreamInterface stream) {
    hot = snapshot(stream);
  }

  /**
   * Sets the hot side fouling model, read at its current operating time on every calculation.
   *
   * @param model fouling model, or null for a clean side; caller owns time advancement
   */
  public void setHotFoulingModel(FoulingModel model) {
    hotFoulingModel = model;
  }

  /**
   * Sets the cold side using constant representative bulk properties.
   *
   * @param massFlow mass flow in kg/s, nonnegative
   * @param temperature inlet temperature in degrees C, above absolute zero
   * @param density density in kg/m3
   * @param viscosity dynamic viscosity in Pa s
   * @param cp specific heat capacity in J/(kg K)
   * @param conductivity thermal conductivity in W/(m K)
   * @throws IllegalArgumentException for nonfinite or nonphysical input
   */
  public void setColdSide(double massFlow, double temperature, double density, double viscosity, double cp,
      double conductivity) {
    cold = new Side(massFlow, temperature, density, viscosity, cp, conductivity);
  }

  /**
   * Snapshots an already flashed single-phase cold inlet stream without modifying it.
   *
   * @param stream stream whose inlet properties will be used as constant bulk properties
   * @throws IllegalArgumentException for null, multiphase or nonphysical input
   */
  public void setColdStream(StreamInterface stream) {
    cold = snapshot(stream);
  }

  /**
   * Sets the cold side fouling model, read at its current operating time on every calculation.
   *
   * @param model fouling model, or null for a clean side; caller owns time advancement
   */
  public void setColdFoulingModel(FoulingModel model) {
    coldFoulingModel = model;
  }

  /**
   * Sets the developed area of one active plate in m2. Inputs are checked when calculating.
   *
   * @param value developed area of one active plate in m2
   */
  public void setPlateArea(double value) {
    plateArea = value;
  }

  /** @return developed area of one active plate in m2 */
  public double getPlateArea() {
    return plateArea;
  }

  /**
   * Sets the effective width between gaskets in m. Inputs are checked when calculating.
   *
   * @param value effective width between gaskets in m
   */
  public void setEffectiveFlowWidth(double value) {
    effectiveFlowWidth = value;
  }

  /** @return effective width between gaskets in m */
  public double getEffectiveFlowWidth() {
    return effectiveFlowWidth;
  }

  /**
   * Sets the clear channel gap in m. Inputs are checked when calculating.
   *
   * @param value clear channel gap in m
   */
  public void setPlateSpacing(double value) {
    plateSpacing = value;
  }

  /** @return clear channel gap in m */
  public double getPlateSpacing() {
    return plateSpacing;
  }

  /**
   * Sets the developed to projected area ratio, at least one. Inputs are checked when calculating.
   *
   * @param value developed to projected area ratio, at least one
   */
  public void setSurfaceEnlargementFactor(double value) {
    surfaceEnlargementFactor = value;
  }

  /** @return developed to projected area ratio, at least one */
  public double getSurfaceEnlargementFactor() {
    return surfaceEnlargementFactor;
  }

  /**
   * Sets the corrugation angle to the main flow direction in degrees, from 10 through 80. Inputs are checked when
   * calculating.
   *
   * @param value corrugation angle to the main flow direction in degrees, from 10 through 80
   */
  public void setChevronAngle(double value) {
    chevronAngle = value;
  }

  /** @return corrugation angle to the main flow direction in degrees, from 10 through 80 */
  public double getChevronAngle() {
    return chevronAngle;
  }

  /**
   * Sets the metal plate thickness in m. Inputs are checked when calculating.
   *
   * @param value metal plate thickness in m
   */
  public void setPlateThickness(double value) {
    plateThickness = value;
  }

  /** @return metal plate thickness in m */
  public double getPlateThickness() {
    return plateThickness;
  }

  /**
   * Sets the metal thermal conductivity in W/(m K). Inputs are checked when calculating.
   *
   * @param value metal thermal conductivity in W/(m K)
   */
  public void setPlateConductivity(double value) {
    plateConductivity = value;
  }

  /** @return metal thermal conductivity in W/(m K) */
  public double getPlateConductivity() {
    return plateConductivity;
  }

  /**
   * Sets the installed count including both end plates, at least three. Inputs are checked when calculating.
   *
   * @param value installed count including both end plates, at least three
   */
  public void setNumberOfPlates(int value) {
    numberOfPlates = value;
  }

  /** @return installed count including both end plates, at least three */
  public int getNumberOfPlates() {
    return numberOfPlates;
  }

  /**
   * Sets the hot-side passes, dividing its channel count exactly. Inputs are checked when calculating.
   *
   * @param value hot-side passes, dividing its channel count exactly
   */
  public void setHotPasses(int value) {
    hotPasses = value;
  }

  /** @return hot-side passes, dividing its channel count exactly */
  public int getHotPasses() {
    return hotPasses;
  }

  /**
   * Sets the cold-side passes, dividing its channel count exactly. Inputs are checked when calculating.
   *
   * @param value cold-side passes, dividing its channel count exactly
   */
  public void setColdPasses(int value) {
    coldPasses = value;
  }

  /** @return cold-side passes, dividing its channel count exactly */
  public int getColdPasses() {
    return coldPasses;
  }

  /**
   * Sets the hot-side port inside diameter in m. Inputs are checked when calculating.
   *
   * @param value hot-side port inside diameter in m
   */
  public void setHotPortDiameter(double value) {
    hotPortDiameter = value;
  }

  /** @return hot-side port inside diameter in m */
  public double getHotPortDiameter() {
    return hotPortDiameter;
  }

  /**
   * Sets the cold-side port inside diameter in m. Inputs are checked when calculating.
   *
   * @param value cold-side port inside diameter in m
   */
  public void setColdPortDiameter(double value) {
    coldPortDiameter = value;
  }

  /** @return cold-side port inside diameter in m */
  public double getColdPortDiameter() {
    return coldPortDiameter;
  }

  /**
   * Sets the combined inlet/outlet port loss coefficient per pass, nonnegative. Inputs are checked when calculating.
   *
   * @param value combined inlet/outlet port loss coefficient per pass, nonnegative
   */
  public void setPortLossCoefficient(double value) {
    portLossCoefficient = value;
  }

  /** @return combined inlet/outlet port loss coefficient per pass, nonnegative */
  public double getPortLossCoefficient() {
    return portLossCoefficient;
  }

  /**
   * Sets the frame plate limit including end plates; zero means unknown. Inputs are checked when calculating.
   *
   * @param value frame plate limit including end plates; zero means unknown
   */
  public void setFrameCapacityPlates(int value) {
    frameCapacityPlates = value;
  }

  /** @return frame plate limit including end plates; zero means unknown */
  public int getFrameCapacityPlates() {
    return frameCapacityPlates;
  }

  /**
   * Sets the current bolt plate limit including end plates; zero means unknown. Inputs are checked when calculating.
   *
   * @param value current bolt plate limit including end plates; zero means unknown
   */
  public void setBoltedCapacityPlates(int value) {
    boltedCapacityPlates = value;
  }

  /** @return current bolt plate limit including end plates; zero means unknown */
  public int getBoltedCapacityPlates() {
    return boltedCapacityPlates;
  }

  private static Side snapshot(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      throw new IllegalArgumentException("A flashed stream is required");
    }
    SystemInterface fluid = stream.getThermoSystem().clone();
    PhaseType type = fluid.getPhase(0).getType();
    if (fluid.getNumberOfPhases() != 1
        || (type != PhaseType.GAS && type != PhaseType.LIQUID && type != PhaseType.OIL && type != PhaseType.AQUEOUS)) {
      throw new IllegalArgumentException("Plate rating requires a single fluid phase");
    }
    fluid.initProperties();
    return new Side(stream.getFlowRate("kg/sec"), stream.getTemperature("C"), fluid.getDensity("kg/m3"),
        fluid.getViscosity("kg/msec"), fluid.getCp("J/kgK"), fluid.getThermalConductivity("W/mK"));
  }

  private static final class Side implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double massFlow;
    private final double temperature;
    private final double density;
    private final double viscosity;
    private final double cp;
    private final double conductivity;

    private Side(double massFlow, double temperature, double density, double viscosity, double cp,
        double conductivity) {
      nonnegative(massFlow, "mass flow");
      positive(temperature + 273.15, "absolute inlet temperature");
      positive(density, "density");
      positive(viscosity, "viscosity");
      positive(cp, "heat capacity");
      positive(conductivity, "fluid conductivity");
      this.massFlow = massFlow;
      this.temperature = temperature;
      this.density = density;
      this.viscosity = viscosity;
      this.cp = cp;
      this.conductivity = conductivity;
    }
  }

  /** Immutable ChannelResult snapshot; earlier results remain valid after calculator edits. */
  public static final class ChannelResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final int channelsPerPass;
    private final double velocity;
    private final double reynoldsNumber;
    private final double prandtlNumber;
    private final double darcyFrictionFactor;
    private final double nusseltNumber;
    private final double heatTransferCoefficient;
    private final double channelPressureDrop;
    private final double portVelocity;
    private final double portPressureDrop;

    private ChannelResult(int channelsPerPass, double velocity, double reynoldsNumber, double prandtlNumber,
        double darcyFrictionFactor, double nusseltNumber, double heatTransferCoefficient, double channelPressureDrop,
        double portVelocity, double portPressureDrop) {
      this.channelsPerPass = channelsPerPass;
      this.velocity = velocity;
      this.reynoldsNumber = reynoldsNumber;
      this.prandtlNumber = prandtlNumber;
      this.darcyFrictionFactor = darcyFrictionFactor;
      this.nusseltNumber = nusseltNumber;
      this.heatTransferCoefficient = heatTransferCoefficient;
      this.channelPressureDrop = channelPressureDrop;
      this.portVelocity = portVelocity;
      this.portPressureDrop = portPressureDrop;
    }

    /** @return parallel channels per pass */
    public int getChannelsPerPass() {
      return channelsPerPass;
    }

    /** @return channel velocity in m/s */
    public double getVelocity() {
      return velocity;
    }

    /** @return channel Reynolds number */
    public double getReynoldsNumber() {
      return reynoldsNumber;
    }

    /** @return bulk Prandtl number */
    public double getPrandtlNumber() {
      return prandtlNumber;
    }

    /** @return Darcy friction factor */
    public double getDarcyFrictionFactor() {
      return darcyFrictionFactor;
    }

    /** @return channel Nusselt number */
    public double getNusseltNumber() {
      return nusseltNumber;
    }

    /** @return film coefficient in W/(m2 K) */
    public double getHeatTransferCoefficient() {
      return heatTransferCoefficient;
    }

    /** @return frictional channel pressure drop in Pa */
    public double getChannelPressureDrop() {
      return channelPressureDrop;
    }

    /** @return port velocity in m/s */
    public double getPortVelocity() {
      return portVelocity;
    }

    /** @return combined port pressure drop in Pa */
    public double getPortPressureDrop() {
      return portPressureDrop;
    }

    /** @return total pressure drop in Pa, excluding static head */
    public double getTotalPressureDrop() {
      return channelPressureDrop + portPressureDrop;
    }

    /** @return true for the documented Re 200-10000 experimental range */
    public boolean isWithinCorrelationRange() {
      return reynoldsNumber >= 200.0 && reynoldsNumber <= 10000.0;
    }
  }

  /** Immutable Rating snapshot; earlier results remain valid after calculator edits. */
  public static final class Rating implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ChannelResult hotSide;
    private final ChannelResult coldSide;
    private final double area;
    private final double cleanU;
    private final double overallU;
    private final double ntu;
    private final double effectiveness;
    private final double duty;
    private final double hotOutletTemperature;
    private final double coldOutletTemperature;

    private Rating(ChannelResult hotSide, ChannelResult coldSide, double area, double cleanU, double overallU,
        double ntu, double effectiveness, double duty, double hotOutletTemperature, double coldOutletTemperature) {
      this.hotSide = hotSide;
      this.coldSide = coldSide;
      this.area = area;
      this.cleanU = cleanU;
      this.overallU = overallU;
      this.ntu = ntu;
      this.effectiveness = effectiveness;
      this.duty = duty;
      this.hotOutletTemperature = hotOutletTemperature;
      this.coldOutletTemperature = coldOutletTemperature;
    }

    /** @return hot-side hydraulic and film results */
    public ChannelResult getHotSide() {
      return hotSide;
    }

    /** @return cold-side hydraulic and film results */
    public ChannelResult getColdSide() {
      return coldSide;
    }

    /** @return active developed surface in m2 */
    public double getArea() {
      return area;
    }

    /** @return clean overall coefficient in W/(m2 K) */
    public double getCleanU() {
      return cleanU;
    }

    /** @return fouled overall coefficient in W/(m2 K) */
    public double getOverallU() {
      return overallU;
    }

    /** @return number of transfer units */
    public double getNtu() {
      return ntu;
    }

    /** @return counterflow effectiveness */
    public double getEffectiveness() {
      return effectiveness;
    }

    /** @return heat transferred from hot to cold in W */
    public double getDuty() {
      return duty;
    }

    /** @return hot outlet temperature in degrees C */
    public double getHotOutletTemperature() {
      return hotOutletTemperature;
    }

    /** @return cold outlet temperature in degrees C */
    public double getColdOutletTemperature() {
      return coldOutletTemperature;
    }

    /** @return overall conductance in W/K */
    public double getUA() {
      return overallU * area;
    }

    /** @return true only if both side Reynolds numbers are within the correlation range */
    public boolean isWithinCorrelationRange() {
      return hotSide.isWithinCorrelationRange() && coldSide.isWithinCorrelationRange();
    }
  }

  /**
   * Calculates a fresh, unit-labelled report including explicit mechanical headroom. Unknown capacities are represented
   * as null headroom, never as unlimited capacity.
   *
   * @return report map
   * @throws IllegalArgumentException for invalid inputs
   * @throws IllegalStateException for missing sides
   * @throws UnsupportedOperationException for multipass thermal routing
   */
  public Map<String, Object> toMap() {
    Rating r = calculate();
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("model", "Martin 1999; single-phase constant-property counterflow");
    result.put("installedPlates", numberOfPlates);
    result.put("frameCapacityPlates", frameCapacityPlates);
    result.put("boltedCapacityPlates", boltedCapacityPlates);
    result.put("frameSparePlates", frameCapacityPlates == 0 ? null : getFrameSparePlates());
    result.put("frameAdditionalArea_m2", frameCapacityPlates == 0 ? null : getFrameAdditionalArea());
    result.put("additionalPlatesWithoutBoltReplacement",
        frameCapacityPlates == 0 || boltedCapacityPlates == 0 ? null : getAdditionalPlatesWithoutBoltReplacement());
    result.put("area_m2", r.area);
    result.put("cleanU_W_m2K", r.cleanU);
    result.put("overallU_W_m2K", r.overallU);
    result.put("UA_W_K", r.getUA());
    result.put("duty_W", r.duty);
    result.put("effectiveness", r.effectiveness);
    result.put("NTU", r.ntu);
    result.put("hotOutletTemperature_C", r.hotOutletTemperature);
    result.put("coldOutletTemperature_C", r.coldOutletTemperature);
    result.put("withinCorrelationRange", r.isWithinCorrelationRange());
    result.put("hotSide", channelMap(r.hotSide));
    result.put("coldSide", channelMap(r.coldSide));
    return result;
  }

  private static Map<String, Object> channelMap(ChannelResult r) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("channelsPerPass", r.channelsPerPass);
    map.put("velocity_m_s", r.velocity);
    map.put("Re", r.reynoldsNumber);
    map.put("Pr", r.prandtlNumber);
    map.put("Nu", r.nusseltNumber);
    map.put("darcyFrictionFactor", r.darcyFrictionFactor);
    map.put("filmCoefficient_W_m2K", r.heatTransferCoefficient);
    map.put("portVelocity_m_s", r.portVelocity);
    map.put("channelPressureDrop_Pa", r.channelPressureDrop);
    map.put("portPressureDrop_Pa", r.portPressureDrop);
    map.put("totalPressureDrop_Pa", r.getTotalPressureDrop());
    return map;
  }

  /**
   * Returns a fresh JSON report with units in field names.
   *
   * @return JSON report
   * @throws IllegalArgumentException for invalid inputs
   * @throws IllegalStateException for missing sides
   * @throws UnsupportedOperationException for multipass thermal routing
   */
  public String toJson() {
    return new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(toMap());
  }
}
