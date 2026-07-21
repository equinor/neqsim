package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Claus tail-gas hydrogenation, hydrolysis, and selective H2S-removal unit.
 *
 * <p>
 * The hydrogenation reactor is finite-rate. The absorber is represented by explicit H2S removal
 * and CO2 co-absorption efficiencies and exposes a separate acid-gas recycle stream. This keeps the
 * process topology rigorous while allowing a future rate-based electrolyte column to replace the
 * absorber calculation without changing the SRU flowsheet API.
 * </p>
 */
public class TailGasTreatmentUnit extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double reactorTemperatureK = 573.15;
  private double residenceTimeSeconds = 1.0;
  private double hydrogenStoichiometricFactor = 1.10;
  private double hydrogenationPreExponentialFactor = 5.0e5;
  private double hydrogenationActivationEnergyJPerMol = 60000.0;
  private double hydrolysisPreExponentialFactor = 3.0e5;
  private double hydrolysisActivationEnergyJPerMol = 65000.0;
  private double h2sRemovalEfficiency = 0.995;
  private double co2CoAbsorptionFraction = 0.02;
  private double pressureDropBar = 0.15;
  private StreamInterface acidGasRecycleStream;
  private double hydrogenationConversion;
  private double hydrolysisConversion;
  private double absorbedH2SMoles;
  private double hydrogenAddedMoles;

  /** Create an unconnected tail-gas unit. */
  public TailGasTreatmentUnit(String name) {
    super(name);
  }

  /** Create a tail-gas unit connected to a Claus tail-gas stream. */
  public TailGasTreatmentUnit(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    system.setTemperature(reactorTemperatureK);
    double reducibleSulfur = SulfurProcessUtil.moles(system, "SO2")
        + 8.0 * SulfurProcessUtil.moles(system, "S8");
    double requiredHydrogen = 3.0 * SulfurProcessUtil.moles(system, "SO2")
        + 8.0 * SulfurProcessUtil.moles(system, "S8");
    double targetHydrogen = hydrogenStoichiometricFactor * requiredHydrogen;
    hydrogenAddedMoles = Math.max(0.0,
        targetHydrogen - SulfurProcessUtil.moles(system, "hydrogen"));
    SulfurProcessUtil.addMoles(system, "hydrogen", hydrogenAddedMoles);

    double hydrogenationRate = hydrogenationPreExponentialFactor
        * Math.exp(-hydrogenationActivationEnergyJPerMol
            / (8.314462618 * reactorTemperatureK));
    hydrogenationConversion = SulfurProcessUtil.clamp(
        1.0 - Math.exp(-hydrogenationRate * residenceTimeSeconds), 0.0, 1.0);

    double so2Extent = Math.min(SulfurProcessUtil.moles(system, "SO2"),
        SulfurProcessUtil.moles(system, "hydrogen") / 3.0) * hydrogenationConversion;
    SulfurProcessUtil.addMoles(system, "SO2", -so2Extent);
    SulfurProcessUtil.addMoles(system, "hydrogen", -3.0 * so2Extent);
    SulfurProcessUtil.addMoles(system, "H2S", so2Extent);
    SulfurProcessUtil.addMoles(system, "water", 2.0 * so2Extent);

    double sulfurExtent = Math.min(SulfurProcessUtil.moles(system, "S8"),
        SulfurProcessUtil.moles(system, "hydrogen") / 8.0) * hydrogenationConversion;
    SulfurProcessUtil.addMoles(system, "S8", -sulfurExtent);
    SulfurProcessUtil.addMoles(system, "hydrogen", -8.0 * sulfurExtent);
    SulfurProcessUtil.addMoles(system, "H2S", 8.0 * sulfurExtent);

    double hydrolysisRate = hydrolysisPreExponentialFactor
        * Math.exp(-hydrolysisActivationEnergyJPerMol
            / (8.314462618 * reactorTemperatureK));
    hydrolysisConversion = SulfurProcessUtil.clamp(
        1.0 - Math.exp(-hydrolysisRate * residenceTimeSeconds), 0.0, 1.0);

    double cosExtent = Math.min(SulfurProcessUtil.moles(system, "COS"),
        SulfurProcessUtil.moles(system, "water")) * hydrolysisConversion;
    SulfurProcessUtil.addMoles(system, "COS", -cosExtent);
    SulfurProcessUtil.addMoles(system, "water", -cosExtent);
    SulfurProcessUtil.addMoles(system, "CO2", cosExtent);
    SulfurProcessUtil.addMoles(system, "H2S", cosExtent);

    double cs2Extent = Math.min(SulfurProcessUtil.moles(system, "CS2"),
        SulfurProcessUtil.moles(system, "water") / 2.0) * hydrolysisConversion;
    SulfurProcessUtil.addMoles(system, "CS2", -cs2Extent);
    SulfurProcessUtil.addMoles(system, "water", -2.0 * cs2Extent);
    SulfurProcessUtil.addMoles(system, "CO2", cs2Extent);
    SulfurProcessUtil.addMoles(system, "H2S", 2.0 * cs2Extent);

    absorbedH2SMoles = SulfurProcessUtil.moles(system, "H2S")
        * SulfurProcessUtil.clamp(h2sRemovalEfficiency, 0.0, 1.0);
    double absorbedCo2 = SulfurProcessUtil.moles(system, "CO2")
        * SulfurProcessUtil.clamp(co2CoAbsorptionFraction, 0.0, 1.0);
    SulfurProcessUtil.addMoles(system, "H2S", -absorbedH2SMoles);
    SulfurProcessUtil.addMoles(system, "CO2", -absorbedCo2);
    system.setPressure(Math.max(0.1, system.getPressure() - pressureDropBar));
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);

    SystemInterface recycle = SulfurProcessUtil.prepareSystem(system);
    for (int i = 0; i < recycle.getNumberOfComponents(); i++) {
      SulfurProcessUtil.setMoles(recycle, recycle.getComponent(i).getComponentName(), 1.0e-30);
    }
    SulfurProcessUtil.setMoles(recycle, "H2S", absorbedH2SMoles);
    SulfurProcessUtil.setMoles(recycle, "CO2", absorbedCo2);
    recycle.setTemperature(313.15);
    SulfurProcessUtil.flash(recycle, getName() + " recycle");
    acidGasRecycleStream = new Stream(getName() + " acid gas recycle", recycle);
    acidGasRecycleStream.run(id);

    if (reducibleSulfur <= 1.0e-20) {
      hydrogenationConversion = 0.0;
    }
    setCalculationIdentifier(id);
  }

  /** Set hydrogenation-reactor temperature. */
  public void setReactorTemperature(double value, String unit) {
    reactorTemperatureK = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set hydrogenation and hydrolysis residence time [s]. */
  public void setResidenceTime(double seconds) {
    residenceTimeSeconds = Math.max(seconds, 0.0);
  }

  /** Set hydrogen supply relative to stoichiometric demand. */
  public void setHydrogenStoichiometricFactor(double factor) {
    hydrogenStoichiometricFactor = Math.max(factor, 1.0);
  }

  /** Set selective H2S-removal efficiency. */
  public void setH2SRemovalEfficiency(double efficiency) {
    h2sRemovalEfficiency = SulfurProcessUtil.clamp(efficiency, 0.0, 1.0);
  }

  /** Set CO2 coabsorption fraction. */
  public void setCo2CoAbsorptionFraction(double fraction) {
    co2CoAbsorptionFraction = SulfurProcessUtil.clamp(fraction, 0.0, 1.0);
  }

  /** Return the H2S-rich acid-gas recycle stream. */
  public StreamInterface getAcidGasRecycleStream() {
    return acidGasRecycleStream;
  }

  /** Return latest SO2/S8 hydrogenation conversion. */
  public double getHydrogenationConversion() {
    return hydrogenationConversion;
  }

  /** Return latest common COS/CS2 hydrolysis conversion. */
  public double getHydrolysisConversion() {
    return hydrolysisConversion;
  }

  /** Return H2S captured on the stream molar-flow basis. */
  public double getAbsorbedH2SMoles() {
    return absorbedH2SMoles;
  }

  /** Return supplemental hydrogen added on the stream molar-flow basis. */
  public double getHydrogenAddedMoles() {
    return hydrogenAddedMoles;
  }
}
