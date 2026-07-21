package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Claus reaction furnace with selectable equilibrium or reduced-kinetic calculation.
 *
 * <p>
 * The reduced mechanism resolves ammonia and methane destruction, H2S oxidation, the thermal
 * Claus reaction, H2S dissociation, and balanced COS/CS2 side reactions. Model coefficients are
 * public configuration inputs so a plant-specific calibration can be fitted without changing the
 * material-balance implementation.
 * </p>
 */
public class ClausReactionFurnace extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Furnace chemistry mode. */
  public enum ModelMode {
    /** Element-constrained Gibbs minimization using the NeqSim Gibbs reactor. */
    EQUILIBRIUM,
    /** Public reduced mechanism with finite residence-time effects. */
    REDUCED_KINETIC,
    /** Reduced mechanism with user-supplied calibration factors. */
    EMPIRICAL_PLANT_CALIBRATED
  }

  /** Furnace energy mode. */
  public enum EnergyMode {
    /** Calculate temperature rise from reaction heat and mixture heat capacity. */
    ADIABATIC,
    /** Hold the specified outlet temperature. */
    ISOTHERMAL
  }

  private ModelMode modelMode = ModelMode.REDUCED_KINETIC;
  private EnergyMode energyMode = EnergyMode.ADIABATIC;
  private double residenceTimeSeconds = 1.2;
  private double heatLossFraction = 0.05;
  private double specifiedOutletTemperatureK = 1473.15;
  private double thermalClausApproach = 0.35;
  private double h2sDissociationFraction = 0.015;
  private double cosFormationFraction = 0.02;
  private double cs2FormationFraction = 0.01;
  private double kineticCalibrationFactor = 1.0;
  private double furnaceTemperatureK;
  private double h2sOxidizedMoles;
  private double methaneConversion;
  private double ammoniaConversion;
  private double sulfurBalanceError;

  /** Create an unconnected furnace. */
  public ClausReactionFurnace(String name) {
    super(name);
  }

  /** Create a furnace connected to a premixed acid-gas/oxidant stream. */
  public ClausReactionFurnace(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    double inletSulfurAtoms = SulfurProcessUtil.sulfurAtomMoles(system);
    double inletMethane = SulfurProcessUtil.moles(system, "methane");
    double inletAmmonia = SulfurProcessUtil.moles(system, "ammonia");
    h2sOxidizedMoles = 0.0;
    methaneConversion = 0.0;
    ammoniaConversion = 0.0;
    sulfurBalanceError = 0.0;

    if (modelMode == ModelMode.EQUILIBRIUM) {
      runEquilibrium(system, inletSulfurAtoms, inletMethane, inletAmmonia, id);
      return;
    }

    double inletTemperatureK = system.getTemperature();
    double availableOxygen = SulfurProcessUtil.moles(system, "oxygen");
    double heatReleasedJ = 0.0;

    double ammoniaK = 8.0 * kineticCalibrationFactor
        * Math.exp(-25000.0 / (8.314462618 * Math.max(inletTemperatureK, 700.0)));
    double ammoniaExtent = inletAmmonia * (1.0 - Math.exp(-ammoniaK * residenceTimeSeconds));
    ammoniaExtent = Math.min(ammoniaExtent, availableOxygen / 0.75);
    SulfurProcessUtil.addMoles(system, "ammonia", -ammoniaExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -0.75 * ammoniaExtent);
    SulfurProcessUtil.addMoles(system, "nitrogen", 0.5 * ammoniaExtent);
    SulfurProcessUtil.addMoles(system, "water", 1.5 * ammoniaExtent);
    availableOxygen -= 0.75 * ammoniaExtent;
    heatReleasedJ += 316000.0 * ammoniaExtent;

    double methaneK = 5.0 * kineticCalibrationFactor
        * Math.exp(-30000.0 / (8.314462618 * Math.max(inletTemperatureK, 700.0)));
    double methaneExtent = inletMethane * (1.0 - Math.exp(-methaneK * residenceTimeSeconds));
    methaneExtent = Math.min(methaneExtent, availableOxygen / 2.0);
    SulfurProcessUtil.addMoles(system, "methane", -methaneExtent);
    SulfurProcessUtil.addMoles(system, "oxygen", -2.0 * methaneExtent);
    SulfurProcessUtil.addMoles(system, "CO2", methaneExtent);
    SulfurProcessUtil.addMoles(system, "water", 2.0 * methaneExtent);
    availableOxygen -= 2.0 * methaneExtent;
    heatReleasedJ += 802000.0 * methaneExtent;

    double h2s = SulfurProcessUtil.moles(system, "H2S");
    h2sOxidizedMoles = Math.min(h2s, availableOxygen / 1.5);
    SulfurProcessUtil.addMoles(system, "H2S", -h2sOxidizedMoles);
    SulfurProcessUtil.addMoles(system, "oxygen", -1.5 * h2sOxidizedMoles);
    SulfurProcessUtil.addMoles(system, "SO2", h2sOxidizedMoles);
    SulfurProcessUtil.addMoles(system, "water", h2sOxidizedMoles);
    heatReleasedJ += 518000.0 * h2sOxidizedMoles;

    double kineticApproach = thermalClausApproach * kineticCalibrationFactor
        * (1.0 - Math.exp(-residenceTimeSeconds / 0.5));
    double clausExtent = Math.min(SulfurProcessUtil.moles(system, "H2S") / 2.0,
        SulfurProcessUtil.moles(system, "SO2"));
    clausExtent *= SulfurProcessUtil.clamp(kineticApproach, 0.0, 1.0);
    SulfurProcessUtil.addMoles(system, "H2S", -2.0 * clausExtent);
    SulfurProcessUtil.addMoles(system, "SO2", -clausExtent);
    SulfurProcessUtil.addMoles(system, "S8", 3.0 * clausExtent / 8.0);
    SulfurProcessUtil.addMoles(system, "water", 2.0 * clausExtent);
    heatReleasedJ += 145000.0 * clausExtent;

    double dissociationExtent = SulfurProcessUtil.moles(system, "H2S")
        * SulfurProcessUtil.clamp(h2sDissociationFraction * kineticCalibrationFactor, 0.0, 0.2);
    SulfurProcessUtil.addMoles(system, "H2S", -dissociationExtent);
    SulfurProcessUtil.addMoles(system, "hydrogen", dissociationExtent);
    SulfurProcessUtil.addMoles(system, "S8", dissociationExtent / 8.0);

    double cosExtent = Math.min(SulfurProcessUtil.moles(system, "CO"),
        SulfurProcessUtil.moles(system, "H2S"));
    cosExtent *= SulfurProcessUtil.clamp(cosFormationFraction * kineticCalibrationFactor, 0.0, 0.2);
    SulfurProcessUtil.addMoles(system, "CO", -cosExtent);
    SulfurProcessUtil.addMoles(system, "H2S", -cosExtent);
    SulfurProcessUtil.addMoles(system, "COS", cosExtent);
    SulfurProcessUtil.addMoles(system, "hydrogen", cosExtent);

    double cs2Extent = Math.min(SulfurProcessUtil.moles(system, "methane"),
        SulfurProcessUtil.moles(system, "H2S") / 2.0);
    cs2Extent *= SulfurProcessUtil.clamp(cs2FormationFraction * kineticCalibrationFactor, 0.0, 0.1);
    SulfurProcessUtil.addMoles(system, "methane", -cs2Extent);
    SulfurProcessUtil.addMoles(system, "H2S", -2.0 * cs2Extent);
    SulfurProcessUtil.addMoles(system, "CS2", cs2Extent);
    SulfurProcessUtil.addMoles(system, "hydrogen", 4.0 * cs2Extent);

    if (energyMode == EnergyMode.ADIABATIC) {
      double heatCapacityFlow = estimateHeatCapacityFlow(system);
      furnaceTemperatureK = inletTemperatureK
          + heatReleasedJ * (1.0 - SulfurProcessUtil.clamp(heatLossFraction, 0.0, 0.95))
              / Math.max(heatCapacityFlow, 1.0);
      furnaceTemperatureK = SulfurProcessUtil.clamp(furnaceTemperatureK, 700.0, 2500.0);
    } else {
      furnaceTemperatureK = specifiedOutletTemperatureK;
    }

    system.setTemperature(furnaceTemperatureK);
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);

    methaneConversion = inletMethane <= 1.0e-20 ? 1.0 : methaneExtent / inletMethane;
    ammoniaConversion = inletAmmonia <= 1.0e-20 ? 1.0 : ammoniaExtent / inletAmmonia;
    sulfurBalanceError = inletSulfurAtoms <= 1.0e-20 ? 0.0
        : (SulfurProcessUtil.sulfurAtomMoles(system) - inletSulfurAtoms) / inletSulfurAtoms;
    setCalculationIdentifier(id);
  }

  /** Run the existing Gibbs minimizer as the furnace chemistry kernel. */
  private void runEquilibrium(SystemInterface system, double inletSulfurAtoms,
      double inletMethane, double inletAmmonia, UUID id) {
    Stream feed = new Stream(getName() + " equilibrium feed", system);
    feed.run(id);
    GibbsReactor reactor = new GibbsReactor(getName() + " Gibbs kernel", feed);
    reactor.setEnergyMode(energyMode == EnergyMode.ADIABATIC
        ? GibbsReactor.EnergyMode.ADIABATIC : GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setMaxIterations(5000);
    reactor.setConvergenceTolerance(1.0e-9);
    reactor.setComponentAsInert("nitrogen");
    reactor.run(id);
    if (!reactor.hasConverged()) {
      throw new IllegalStateException("Gibbs furnace calculation did not converge");
    }
    SystemInterface result = reactor.getOutletStream().getThermoSystem().clone();
    furnaceTemperatureK = result.getTemperature();
    SulfurProcessUtil.updateOutlet(outStream, result, id);
    h2sOxidizedMoles = Double.NaN;
    methaneConversion = inletMethane <= 1.0e-20 ? 1.0
        : 1.0 - SulfurProcessUtil.moles(result, "methane") / inletMethane;
    ammoniaConversion = inletAmmonia <= 1.0e-20 ? 1.0
        : 1.0 - SulfurProcessUtil.moles(result, "ammonia") / inletAmmonia;
    sulfurBalanceError = inletSulfurAtoms <= 1.0e-20 ? 0.0
        : (SulfurProcessUtil.sulfurAtomMoles(result) - inletSulfurAtoms)
            / inletSulfurAtoms;
    setCalculationIdentifier(id);
  }

  /** Estimate total heat-capacity flow [J/K on the stream mole basis]. */
  private double estimateHeatCapacityFlow(SystemInterface system) {
    try {
      return system.getCp("J/molK") * system.getTotalNumberOfMoles();
    } catch (Exception ex) {
      return 35.0 * system.getTotalNumberOfMoles();
    }
  }

  /** Set furnace chemistry mode. */
  public void setModelMode(ModelMode modelMode) {
    if (modelMode == null) {
      throw new IllegalArgumentException("modelMode cannot be null");
    }
    this.modelMode = modelMode;
  }

  /** Return furnace chemistry mode. */
  public ModelMode getModelMode() {
    return modelMode;
  }

  /** Set adiabatic or isothermal energy mode. */
  public void setEnergyMode(EnergyMode energyMode) {
    if (energyMode == null) {
      throw new IllegalArgumentException("energyMode cannot be null");
    }
    this.energyMode = energyMode;
  }

  /** Set furnace residence time [s]. */
  public void setResidenceTime(double seconds) {
    this.residenceTimeSeconds = Math.max(seconds, 0.0);
  }

  /** Set fraction of reaction heat lost to the surroundings. */
  public void setHeatLossFraction(double fraction) {
    this.heatLossFraction = SulfurProcessUtil.clamp(fraction, 0.0, 0.95);
  }

  /** Set isothermal outlet temperature. */
  public void setSpecifiedOutletTemperature(double value, String unit) {
    specifiedOutletTemperatureK = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set thermal Claus approach fraction. */
  public void setThermalClausApproach(double approach) {
    thermalClausApproach = SulfurProcessUtil.clamp(approach, 0.0, 1.0);
  }

  /** Set common finite-rate calibration multiplier. */
  public void setKineticCalibrationFactor(double factor) {
    kineticCalibrationFactor = Math.max(factor, 0.0);
  }

  /** Return latest furnace outlet temperature [K]. */
  public double getFurnaceTemperature() {
    return furnaceTemperatureK;
  }

  /** Return H2S oxidation extent, or NaN in elemental-equilibrium mode. */
  public double getH2SOxidizedMoles() {
    return h2sOxidizedMoles;
  }

  /** Return latest methane conversion. */
  public double getMethaneConversion() {
    return methaneConversion;
  }

  /** Return latest ammonia conversion. */
  public double getAmmoniaConversion() {
    return ammoniaConversion;
  }

  /** Return relative sulfur-atom closure error. */
  public double getSulfurBalanceError() {
    return sulfurBalanceError;
  }
}
