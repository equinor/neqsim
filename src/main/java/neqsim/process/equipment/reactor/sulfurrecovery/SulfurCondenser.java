package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.sulfur.SulfurThermodynamics;

/**
 * Sulfur condenser with mixed-allotrope vapour equilibrium and a liquid-sulfur product stream.
 */
public class SulfurCondenser extends TwoPortEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private double outletTemperatureK = 423.15;
  private double pressureDropBar = 0.03;
  private double sulfurRemovalEfficiency = 0.999;
  private double liquidSulfurEntrainmentFraction = 0.0;
  private double latentHeatJPerMolS8 = 75000.0;
  private StreamInterface liquidSulfurStream;
  private double condensedS8Moles;
  private double heatDutyW;
  private double inletSulfurDewPointK;
  private double outletDewPointMarginK;
  private Map<String, Double> outletSulfurAllotropeFractions = Collections.emptyMap();

  /** Create an unconnected condenser. */
  public SulfurCondenser(String name) {
    super(name);
  }

  /** Create a condenser connected to a sulfur-bearing gas stream. */
  public SulfurCondenser(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = SulfurProcessUtil.prepareSystem(inStream.getThermoSystem());
    double inletTemperature = system.getTemperature();
    double inletS8 = SulfurProcessUtil.moles(system, "S8");
    double nonSulfurMoles = Math.max(1.0e-30, system.getTotalNumberOfMoles() - inletS8);
    double pressureBar = Math.max(0.1, system.getPressure() - pressureDropBar);

    inletSulfurDewPointK = SulfurProcessUtil.calculateSulfurDewPointK(pressureBar, nonSulfurMoles, inletS8);

    double saturationPressureBar = SulfurThermodynamics.calculateVapourPressureBar(outletTemperatureK);
    double sulfurVapourMoleFraction = Math.min(0.999999, saturationPressureBar / pressureBar);
    double meanAtoms = SulfurThermodynamics.calculateMeanSulfurAtomsPerMolecule(outletTemperatureK,
        Math.max(saturationPressureBar, 1.0e-12));
    double saturatedSulfurMolecules = sulfurVapourMoleFraction * nonSulfurMoles / (1.0 - sulfurVapourMoleFraction);
    double saturatedS8Equivalent = saturatedSulfurMolecules * meanAtoms / 8.0;

    double thermodynamicCondensate = Math.max(0.0, inletS8 - saturatedS8Equivalent);
    condensedS8Moles = thermodynamicCondensate * SulfurProcessUtil.clamp(sulfurRemovalEfficiency, 0.0, 1.0);
    double entrainedMoles = condensedS8Moles * SulfurProcessUtil.clamp(liquidSulfurEntrainmentFraction, 0.0, 1.0);
    condensedS8Moles -= entrainedMoles;
    SulfurProcessUtil.addMoles(system, "S8", -condensedS8Moles);

    double heatCapacityFlow = estimateHeatCapacityFlow(system);
    heatDutyW = heatCapacityFlow * (outletTemperatureK - inletTemperature) - condensedS8Moles * latentHeatJPerMolS8;
    system.setTemperature(outletTemperatureK);
    system.setPressure(pressureBar);
    SulfurProcessUtil.flash(system, getName());
    SulfurProcessUtil.updateOutlet(outStream, system, id);
    liquidSulfurStream = SulfurProcessUtil.createSingleComponentStream(getName() + " liquid sulfur", system, "S8",
        condensedS8Moles, outletTemperatureK, pressureBar, id);

    double outletS8 = SulfurProcessUtil.moles(system, "S8");
    double outletSulfurPressure = SulfurProcessUtil.calculateElementalSulfurVapourPressureBar(outletTemperatureK,
        pressureBar, nonSulfurMoles, outletS8);
    double outletDewPoint = SulfurProcessUtil.calculateSulfurDewPointK(pressureBar, nonSulfurMoles, outletS8);
    outletDewPointMarginK = outletTemperatureK - outletDewPoint;
    outletSulfurAllotropeFractions = SulfurThermodynamics.calculateAllotropeMoleFractions(outletTemperatureK,
        Math.max(outletSulfurPressure, 1.0e-12));
    setCalculationIdentifier(id);
  }

  private double estimateHeatCapacityFlow(SystemInterface system) {
    try {
      return system.getCp("J/molK") * system.getTotalNumberOfMoles();
    } catch (Exception ex) {
      return 35.0 * system.getTotalNumberOfMoles();
    }
  }

  /** Set condenser gas and liquid outlet temperature. */
  @Override
  public void setOutletTemperature(double value, String unit) {
    outletTemperatureK = "C".equalsIgnoreCase(unit) ? value + 273.15 : value;
  }

  /** Set process-side pressure drop. */
  public void setPressureDrop(double value, String unit) {
    pressureDropBar = "Pa".equalsIgnoreCase(unit) ? value / 1.0e5 : value;
  }

  /** Set fraction of thermodynamic condensate removed from the gas. */
  public void setSulfurRemovalEfficiency(double efficiency) {
    sulfurRemovalEfficiency = SulfurProcessUtil.clamp(efficiency, 0.0, 1.0);
  }

  /** Set fraction of separated liquid sulfur entrained back into the gas. */
  public void setLiquidSulfurEntrainmentFraction(double fraction) {
    liquidSulfurEntrainmentFraction = SulfurProcessUtil.clamp(fraction, 0.0, 1.0);
  }

  /** Return separated liquid-sulfur product stream. */
  public StreamInterface getLiquidSulfurStream() {
    return liquidSulfurStream;
  }

  /** Return net condensed sulfur mass rate. */
  public double getCondensedSulfur(String unit) {
    double massKg = condensedS8Moles * SulfurProcessUtil.S8_MOLAR_MASS_KG_PER_MOL;
    return "kg/hr".equalsIgnoreCase(unit) ? massKg * 3600.0 : massKg;
  }

  /** Return latest process-side heat duty. */
  public double getHeatDuty(String unit) {
    if ("kW".equalsIgnoreCase(unit)) {
      return heatDutyW / 1000.0;
    }
    if ("MW".equalsIgnoreCase(unit)) {
      return heatDutyW / 1.0e6;
    }
    return heatDutyW;
  }

  /** Return calculated inlet sulfur dew point. */
  public double getInletSulfurDewPoint(String unit) {
    return "C".equalsIgnoreCase(unit) ? inletSulfurDewPointK - 273.15 : inletSulfurDewPointK;
  }

  /** Return outlet temperature minus sulfur dew point [K]. */
  public double getOutletDewPointMargin() {
    return outletDewPointMarginK;
  }

  /** Return mixed sulfur-vapor allotrope fractions at the outlet. */
  public Map<String, Double> getOutletSulfurAllotropeFractions() {
    return outletSulfurAllotropeFractions;
  }
}
