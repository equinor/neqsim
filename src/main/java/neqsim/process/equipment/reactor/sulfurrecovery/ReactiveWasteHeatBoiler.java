package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.sulfur.SulfurThermodynamics;

/**
 * Reactive one-dimensional waste-heat-boiler surrogate for a Claus thermal stage.
 *
 * <p>
 * The model couples specified cooling duty with finite-rate Claus recombination and COS/CS2 hydrolysis during rapid
 * quench. It exposes steam production and the equilibrium sulfur vapour distribution at the outlet. Detailed
 * tube-by-tube geometry can be represented by increasing the number of cells; each cell updates composition and
 * temperature before the next kinetic step.
 * </p>
 */
public class ReactiveWasteHeatBoiler extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double outletTemperatureK = 623.15;
  private double residenceTimeSeconds = 0.25;
  private int numberOfCells = 20;
  private double pressureDropBar = 0.05;
  private double steamLatentHeatJPerKg = 2.10e6;
  private double clausPreExponentialFactor = 30.0;
  private double clausActivationEnergyJPerMol = 35000.0;
  private double cosHydrolysisPreExponentialFactor = 2.0;
  private double cosHydrolysisActivationEnergyJPerMol = 45000.0;
  private double cs2HydrolysisPreExponentialFactor = 1.0;
  private double cs2HydrolysisActivationEnergyJPerMol = 50000.0;
  private double heatDutyW;
  private double steamProductionKgPerSecond;
  private double cosConversion;
  private double cs2Conversion;
  private Map<String, Double> outletSulfurAllotropeFractions = Collections.emptyMap();

  /** Create an unconnected boiler. */
  public ReactiveWasteHeatBoiler(String name) {
    super(name);
  }

  /** Create a boiler connected to a furnace outlet. */
  public ReactiveWasteHeatBoiler(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    double inletTemperature = system.getTemperature();
    double inletCos = SulfurProcessUtil.moles(system, "COS");
    double inletCs2 = SulfurProcessUtil.moles(system, "CS2");
    double cellTime = residenceTimeSeconds / Math.max(numberOfCells, 1);

    for (int cell = 0; cell < Math.max(numberOfCells, 1); cell++) {
      double fraction = (cell + 0.5) / Math.max(numberOfCells, 1);
      double temperature = inletTemperature + fraction * (outletTemperatureK - inletTemperature);

      double clausRateConstant = clausPreExponentialFactor
          * Math.exp(-clausActivationEnergyJPerMol / (8.314462618 * temperature));
      double clausExtent = Math.min(SulfurProcessUtil.moles(system, "H2S") / 2.0,
          SulfurProcessUtil.moles(system, "SO2"));
      clausExtent *= 1.0 - Math.exp(-clausRateConstant * cellTime);
      SulfurProcessUtil.addMoles(system, "H2S", -2.0 * clausExtent);
      SulfurProcessUtil.addMoles(system, "SO2", -clausExtent);
      SulfurProcessUtil.addMoles(system, "S8", 3.0 * clausExtent / 8.0);
      SulfurProcessUtil.addMoles(system, "water", 2.0 * clausExtent);

      double cosRateConstant = cosHydrolysisPreExponentialFactor
          * Math.exp(-cosHydrolysisActivationEnergyJPerMol / (8.314462618 * temperature));
      double cosExtent = Math.min(SulfurProcessUtil.moles(system, "COS"), SulfurProcessUtil.moles(system, "water"));
      cosExtent *= 1.0 - Math.exp(-cosRateConstant * cellTime);
      SulfurProcessUtil.addMoles(system, "COS", -cosExtent);
      SulfurProcessUtil.addMoles(system, "water", -cosExtent);
      SulfurProcessUtil.addMoles(system, "CO2", cosExtent);
      SulfurProcessUtil.addMoles(system, "H2S", cosExtent);

      double cs2RateConstant = cs2HydrolysisPreExponentialFactor
          * Math.exp(-cs2HydrolysisActivationEnergyJPerMol / (8.314462618 * temperature));
      double cs2Extent = Math.min(SulfurProcessUtil.moles(system, "CS2"),
          SulfurProcessUtil.moles(system, "water") / 2.0);
      cs2Extent *= 1.0 - Math.exp(-cs2RateConstant * cellTime);
      SulfurProcessUtil.addMoles(system, "CS2", -cs2Extent);
      SulfurProcessUtil.addMoles(system, "water", -2.0 * cs2Extent);
      SulfurProcessUtil.addMoles(system, "CO2", cs2Extent);
      SulfurProcessUtil.addMoles(system, "H2S", 2.0 * cs2Extent);
    }

    double heatCapacityFlow = estimateHeatCapacityFlow(system);
    heatDutyW = heatCapacityFlow * (outletTemperatureK - inletTemperature);
    steamProductionKgPerSecond = Math.max(0.0, -heatDutyW / steamLatentHeatJPerKg);
    system.setTemperature(outletTemperatureK);
    system.setPressure(Math.max(0.1, system.getPressure() - pressureDropBar));
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);

    cosConversion = inletCos <= 1.0e-20 ? 0.0 : 1.0 - SulfurProcessUtil.moles(system, "COS") / inletCos;
    cs2Conversion = inletCs2 <= 1.0e-20 ? 0.0 : 1.0 - SulfurProcessUtil.moles(system, "CS2") / inletCs2;
    double sulfurPressure = calculateElementalSulfurPartialPressure(system);
    outletSulfurAllotropeFractions = SulfurThermodynamics.calculateAllotropeMoleFractions(outletTemperatureK,
        Math.max(sulfurPressure, 1.0e-12));
    setCalculationIdentifier(id);
  }

  /** Estimate sulfur molecular partial pressure from S8-equivalent atom inventory. */
  private double calculateElementalSulfurPartialPressure(SystemInterface system) {
    double s8Equivalent = SulfurProcessUtil.moles(system, "S8");
    double nonSulfurMoles = Math.max(1.0e-30, system.getTotalNumberOfMoles() - s8Equivalent);
    return SulfurProcessUtil.calculateElementalSulfurVapourPressureBar(system.getTemperature(), system.getPressure(),
        nonSulfurMoles, s8Equivalent);
  }

  private double estimateHeatCapacityFlow(SystemInterface system) {
    try {
      return system.getCp("J/molK") * system.getTotalNumberOfMoles();
    } catch (Exception ex) {
      return 35.0 * system.getTotalNumberOfMoles();
    }
  }

  /** Set process-gas outlet temperature. */
  public void setOutletTemperature(double value, String unit) {
    outletTemperatureK = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set gas residence time [s]. */
  public void setResidenceTime(double seconds) {
    residenceTimeSeconds = Math.max(seconds, 0.0);
  }

  /** Set axial finite-cell count. */
  public void setNumberOfCells(int cells) {
    numberOfCells = Math.max(cells, 1);
  }

  /** Set process-side pressure drop. */
  public void setPressureDrop(double value, String unit) {
    pressureDropBar = "Pa".equalsIgnoreCase(unit) ? value / 1.0e5 : value;
  }

  /** Return latest process-side heat duty in the requested unit. */
  public double getHeatDuty(String unit) {
    if ("kW".equalsIgnoreCase(unit)) {
      return heatDutyW / 1000.0;
    }
    if ("MW".equalsIgnoreCase(unit)) {
      return heatDutyW / 1.0e6;
    }
    return heatDutyW;
  }

  /** Return equivalent steam production in the requested unit. */
  public double getSteamProduction(String unit) {
    return "kg/hr".equalsIgnoreCase(unit) ? steamProductionKgPerSecond * 3600.0 : steamProductionKgPerSecond;
  }

  /** Return latest COS hydrolysis conversion. */
  public double getCosConversion() {
    return cosConversion;
  }

  /** Return latest CS2 hydrolysis conversion. */
  public double getCs2Conversion() {
    return cs2Conversion;
  }

  /** Return mixed sulfur-vapor allotrope mole fractions at the outlet. */
  public Map<String, Double> getOutletSulfurAllotropeFractions() {
    return outletSulfurAllotropeFractions;
  }
}
