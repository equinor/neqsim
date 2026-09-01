package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Finite-rate Claus catalytic converter with COS and CS2 hydrolysis.
 *
 * <p>
 * Reaction progress is calculated from catalyst contact time, Arrhenius kinetics, equilibrium approach, catalyst
 * activity, and a first-order pellet-effectiveness factor. Alumina, titania, and mixed beds use separate hydrolysis
 * activity multipliers. The public calibration multiplier is intentionally explicit and must be fitted against
 * catalyst-vendor or plant data for rating use.
 * </p>
 */
public class ClausCatalyticConverter extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Supported catalyst formulations. */
  public enum CatalystType {
    /** Activated alumina Claus catalyst. */
    ALUMINA,
    /** Titania catalyst with enhanced COS/CS2 hydrolysis. */
    TITANIA,
    /** Layered or mixed alumina/titania catalyst system. */
    MIXED
  }

  private CatalystType catalystType = CatalystType.ALUMINA;
  private double gasHourlySpaceVelocity = 1000.0;
  private double catalystActivity = 1.0;
  private double particleEffectivenessFactor = 0.95;
  private double calibrationFactor = 1.0;
  private double pressureDropBar = 0.05;
  private boolean adiabatic = true;
  private double clausPreExponentialFactor = 2.0e6;
  private double clausActivationEnergyJPerMol = 65000.0;
  private double hydrolysisPreExponentialFactor = 5.0e5;
  private double hydrolysisActivationEnergyJPerMol = 70000.0;
  private double clausConversion;
  private double cosConversion;
  private double cs2Conversion;
  private double temperatureRiseK;

  /** Create an unconnected converter. */
  public ClausCatalyticConverter(String name) {
    super(name);
  }

  /** Create a converter connected to a reheated process stream. */
  public ClausCatalyticConverter(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    double inletTemperature = system.getTemperature();
    double inletCos = SulfurProcessUtil.moles(system, "COS");
    double inletCs2 = SulfurProcessUtil.moles(system, "CS2");
    double contactTimeSeconds = 3600.0 / Math.max(gasHourlySpaceVelocity, 1.0e-12);
    double rateMultiplier = catalystActivity * particleEffectivenessFactor * calibrationFactor;

    double clausRateConstant = clausPreExponentialFactor
        * Math.exp(-clausActivationEnergyJPerMol / (8.314462618 * inletTemperature));
    double kineticConversion = 1.0 - Math.exp(-clausRateConstant * rateMultiplier * contactTimeSeconds);
    double equilibriumLimit = SulfurProcessUtil.clamp(0.995 - 1.8e-4 * Math.max(0.0, inletTemperature - 473.15), 0.80,
        0.995);
    clausConversion = Math.min(kineticConversion, equilibriumLimit);
    double clausExtent = Math.min(SulfurProcessUtil.moles(system, "H2S") / 2.0, SulfurProcessUtil.moles(system, "SO2"))
        * clausConversion;
    SulfurProcessUtil.addMoles(system, "H2S", -2.0 * clausExtent);
    SulfurProcessUtil.addMoles(system, "SO2", -clausExtent);
    SulfurProcessUtil.addMoles(system, "S8", 3.0 * clausExtent / 8.0);
    SulfurProcessUtil.addMoles(system, "water", 2.0 * clausExtent);

    double hydrolysisMultiplier = catalystType == CatalystType.TITANIA ? 2.5
        : catalystType == CatalystType.MIXED ? 1.7 : 1.0;
    double hydrolysisRateConstant = hydrolysisPreExponentialFactor
        * Math.exp(-hydrolysisActivationEnergyJPerMol / (8.314462618 * inletTemperature));
    double hydrolysisConversion = 1.0
        - Math.exp(-hydrolysisRateConstant * rateMultiplier * hydrolysisMultiplier * contactTimeSeconds);

    double cosExtent = Math.min(inletCos, SulfurProcessUtil.moles(system, "water")) * hydrolysisConversion;
    SulfurProcessUtil.addMoles(system, "COS", -cosExtent);
    SulfurProcessUtil.addMoles(system, "water", -cosExtent);
    SulfurProcessUtil.addMoles(system, "CO2", cosExtent);
    SulfurProcessUtil.addMoles(system, "H2S", cosExtent);

    double cs2Extent = Math.min(inletCs2, SulfurProcessUtil.moles(system, "water") / 2.0) * hydrolysisConversion;
    SulfurProcessUtil.addMoles(system, "CS2", -cs2Extent);
    SulfurProcessUtil.addMoles(system, "water", -2.0 * cs2Extent);
    SulfurProcessUtil.addMoles(system, "CO2", cs2Extent);
    SulfurProcessUtil.addMoles(system, "H2S", 2.0 * cs2Extent);

    cosConversion = inletCos <= 1.0e-20 ? 0.0 : cosExtent / inletCos;
    cs2Conversion = inletCs2 <= 1.0e-20 ? 0.0 : cs2Extent / inletCs2;
    temperatureRiseK = adiabatic ? 145000.0 * clausExtent / Math.max(estimateHeatCapacityFlow(system), 1.0) : 0.0;
    system.setTemperature(inletTemperature + temperatureRiseK);
    system.setPressure(Math.max(0.1, system.getPressure() - pressureDropBar));
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);
    setCalculationIdentifier(id);
  }

  private double estimateHeatCapacityFlow(SystemInterface system) {
    try {
      return system.getCp("J/molK") * system.getTotalNumberOfMoles();
    } catch (Exception ex) {
      return 35.0 * system.getTotalNumberOfMoles();
    }
  }

  /** Set catalyst formulation. */
  public void setCatalystType(CatalystType catalystType) {
    if (catalystType == null) {
      throw new IllegalArgumentException("catalystType cannot be null");
    }
    this.catalystType = catalystType;
  }

  /** Return catalyst formulation. */
  public CatalystType getCatalystType() {
    return catalystType;
  }

  /** Set gas hourly space velocity [1/h]. */
  public void setGasHourlySpaceVelocity(double gasHourlySpaceVelocity) {
    this.gasHourlySpaceVelocity = Math.max(gasHourlySpaceVelocity, 1.0e-12);
  }

  /** Set catalyst activity fraction. */
  public void setCatalystActivity(double catalystActivity) {
    this.catalystActivity = SulfurProcessUtil.clamp(catalystActivity, 0.0, 1.0);
  }

  /** Set catalyst-particle effectiveness factor. */
  public void setParticleEffectivenessFactor(double factor) {
    particleEffectivenessFactor = SulfurProcessUtil.clamp(factor, 0.0, 1.0);
  }

  /** Set plant or vendor kinetic calibration multiplier. */
  public void setCalibrationFactor(double calibrationFactor) {
    this.calibrationFactor = Math.max(calibrationFactor, 0.0);
  }

  /** Set converter pressure drop. */
  public void setPressureDrop(double value, String unit) {
    pressureDropBar = "Pa".equalsIgnoreCase(unit) ? value / 1.0e5 : value;
  }

  /** Select adiabatic or isothermal outlet temperature treatment. */
  public void setAdiabatic(boolean adiabatic) {
    this.adiabatic = adiabatic;
  }

  /** Return latest limiting-reactant Claus conversion. */
  public double getClausConversion() {
    return clausConversion;
  }

  /** Return latest COS hydrolysis conversion. */
  public double getCosConversion() {
    return cosConversion;
  }

  /** Return latest CS2 hydrolysis conversion. */
  public double getCs2Conversion() {
    return cs2Conversion;
  }

  /** Return latest adiabatic temperature rise [K]. */
  public double getTemperatureRise() {
    return temperatureRiseK;
  }
}
