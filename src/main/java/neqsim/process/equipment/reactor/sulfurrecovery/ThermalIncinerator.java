package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/** Finite-rate thermal incinerator for Claus tail-gas sulfur species. */
public class ThermalIncinerator extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double temperatureK = 1073.15;
  private double residenceTimeSeconds = 1.0;
  private double excessOxygenFraction = 0.20;
  private double oxidantOxygenMoleFraction = 0.21;
  private double mixingFactor = 0.95;
  private double preExponentialFactor = 1.0e7;
  private double activationEnergyJPerMol = 90000.0;
  private double pressureDropBar = 0.05;
  private double sulfurDestructionEfficiency;
  private double so2EmissionKgPerHour;

  /** Create an unconnected incinerator. */
  public ThermalIncinerator(String name) {
    super(name);
  }

  /** Create an incinerator connected to tail gas. */
  public ThermalIncinerator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    double inletReducedSulfur = SulfurProcessUtil.sulfurAtomMoles(system) - SulfurProcessUtil.moles(system, "SO2");
    double oxygenDemand = 1.5 * SulfurProcessUtil.moles(system, "H2S") + 1.5 * SulfurProcessUtil.moles(system, "COS")
        + 3.0 * SulfurProcessUtil.moles(system, "CS2") + 8.0 * SulfurProcessUtil.moles(system, "S8")
        + 0.5 * SulfurProcessUtil.moles(system, "hydrogen") + 0.5 * SulfurProcessUtil.moles(system, "CO")
        + 2.0 * SulfurProcessUtil.moles(system, "methane");
    double targetOxygen = oxygenDemand * (1.0 + Math.max(excessOxygenFraction, 0.0));
    double oxygenAdded = Math.max(0.0, targetOxygen - SulfurProcessUtil.moles(system, "oxygen"));
    SulfurProcessUtil.addMoles(system, "oxygen", oxygenAdded);
    double oxygenFraction = SulfurProcessUtil.clamp(oxidantOxygenMoleFraction, 0.01, 0.99);
    SulfurProcessUtil.addMoles(system, "nitrogen", oxygenAdded * (1.0 - oxygenFraction) / oxygenFraction);

    double rateConstant = preExponentialFactor * Math.exp(-activationEnergyJPerMol / (8.314462618 * temperatureK));
    double conversion = SulfurProcessUtil.clamp(mixingFactor * (1.0 - Math.exp(-rateConstant * residenceTimeSeconds)),
        0.0, 1.0);

    double h2sExtent = SulfurProcessUtil.moles(system, "H2S") * conversion;
    oxidize(system, "H2S", h2sExtent, 1.5, 1.0, 0.0, 1.0);
    double cosExtent = SulfurProcessUtil.moles(system, "COS") * conversion;
    oxidize(system, "COS", cosExtent, 1.5, 1.0, 1.0, 0.0);
    double cs2Extent = SulfurProcessUtil.moles(system, "CS2") * conversion;
    oxidize(system, "CS2", cs2Extent, 3.0, 2.0, 1.0, 0.0);
    double s8Extent = SulfurProcessUtil.moles(system, "S8") * conversion;
    oxidize(system, "S8", s8Extent, 8.0, 8.0, 0.0, 0.0);

    double hydrogenExtent = SulfurProcessUtil.moles(system, "hydrogen") * conversion;
    SulfurProcessUtil.addMoles(system, "hydrogen", -hydrogenExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -0.5 * hydrogenExtent);
    SulfurProcessUtil.addMoles(system, "water", hydrogenExtent);
    double coExtent = SulfurProcessUtil.moles(system, "CO") * conversion;
    SulfurProcessUtil.addMoles(system, "CO", -coExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -0.5 * coExtent);
    SulfurProcessUtil.addMoles(system, "CO2", coExtent);
    double methaneExtent = SulfurProcessUtil.moles(system, "methane") * conversion;
    SulfurProcessUtil.addMoles(system, "methane", -methaneExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -2.0 * methaneExtent);
    SulfurProcessUtil.addMoles(system, "CO2", methaneExtent);
    SulfurProcessUtil.addMoles(system, "water", 2.0 * methaneExtent);

    system.setTemperature(temperatureK);
    system.setPressure(Math.max(0.1, system.getPressure() - pressureDropBar));
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);
    double outletReducedSulfur = SulfurProcessUtil.sulfurAtomMoles(system) - SulfurProcessUtil.moles(system, "SO2");
    sulfurDestructionEfficiency = inletReducedSulfur <= 1.0e-20 ? 1.0 : 1.0 - outletReducedSulfur / inletReducedSulfur;
    so2EmissionKgPerHour = SulfurProcessUtil.moles(system, "SO2") * 0.0640638 * 3600.0;
    setCalculationIdentifier(id);
  }

  /** Apply a balanced sulfur-species oxidation extent. */
  private void oxidize(SystemInterface system, String reactant, double extent, double oxygenCoefficient,
      double so2Coefficient, double co2Coefficient, double waterCoefficient) {
    double oxygenLimitedExtent = Math.min(extent, SulfurProcessUtil.moles(system, "oxygen") / oxygenCoefficient);
    SulfurProcessUtil.addMoles(system, reactant, -oxygenLimitedExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -oxygenCoefficient * oxygenLimitedExtent);
    SulfurProcessUtil.addMoles(system, "SO2", so2Coefficient * oxygenLimitedExtent);
    SulfurProcessUtil.addMoles(system, "CO2", co2Coefficient * oxygenLimitedExtent);
    SulfurProcessUtil.addMoles(system, "water", waterCoefficient * oxygenLimitedExtent);
  }

  /** Set incinerator temperature. */
  public void setTemperature(double value, String unit) {
    temperatureK = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set incinerator residence time [s]. */
  public void setResidenceTime(double seconds) {
    residenceTimeSeconds = Math.max(seconds, 0.0);
  }

  /** Set excess oxygen fraction above stoichiometric demand. */
  public void setExcessOxygenFraction(double fraction) {
    excessOxygenFraction = Math.max(fraction, 0.0);
  }

  /** Set oxygen mole fraction of incinerator oxidant. */
  public void setOxidantOxygenMoleFraction(double fraction) {
    oxidantOxygenMoleFraction = SulfurProcessUtil.clamp(fraction, 0.01, 0.99);
  }

  /** Set finite mixing-effectiveness factor. */
  public void setMixingFactor(double factor) {
    mixingFactor = SulfurProcessUtil.clamp(factor, 0.0, 1.0);
  }

  /** Return conversion of reduced sulfur species to SO2. */
  public double getSulfurDestructionEfficiency() {
    return sulfurDestructionEfficiency;
  }

  /** Return stack SO2 mass rate. */
  public double getSo2Emission(String unit) {
    return "kg/s".equalsIgnoreCase(unit) ? so2EmissionKgPerHour / 3600.0 : so2EmissionKgPerHour;
  }
}
