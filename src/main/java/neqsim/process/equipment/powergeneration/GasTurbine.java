package neqsim.process.equipment.powergeneration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Gas turbine model with integrated air compression, combustion, and expansion.
 *
 * <p>
 * Implements {@link CapacityConstrainedEquipment} and {@link AutoSizeable} for integration with the production
 * optimization framework. Capacity constraints track power output vs rated capacity and thermal efficiency.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class GasTurbine extends TwoPortEquipment implements CapacityConstrainedEquipment, AutoSizeable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Compressor.class);

  public SystemInterface thermoSystem;
  public StreamInterface airStream;
  public Compressor airCompressor;
  public double combustionpressure = 2.5;
  double airGasRatio = 2.8;
  /**
   * Excess-air factor applied on top of the stoichiometric air demand. Gas turbines run with large excess air
   * (typically 2-3x stoichiometric) to limit the turbine inlet temperature.
   */
  private double excessAirFactor = 2.5;
  double expanderPower = 0.0;
  double compressorPower = 0.0;
  private double heat = 0.0;

  public double power = 0.0;

  /** Rated (maximum) power output in Watts. Used for capacity constraint calculations. */
  private double ratedPowerW = 0.0;

  /** Whether this equipment has been auto-sized. */
  private boolean autoSized = false;

  /** Storage for capacity constraints. */
  private final Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();

  /**
   * Constructor for GasTurbine.
   */
  public GasTurbine() {
    this("GasTurbine");
  }

  /**
   * Constructor for GasTurbine.
   *
   * @param name a {@link java.lang.String} object
   */
  public GasTurbine(String name) {
    super(name);
    // needs to be changed to gas tubing mechanical design
    SystemInterface airThermoSystem = new neqsim.thermo.Fluid().create("combustion air");
    airThermoSystem.createDatabase(true);
    // airThermoSystem.display();
    airStream = new Stream("airStream", airThermoSystem);
    airStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    airStream.setTemperature(288.15, "K");
    airCompressor = new Compressor("airCompressor", airStream);
  }

  /**
   * Constructor for GasTurbine.
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public GasTurbine(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public CompressorMechanicalDesign getMechanicalDesign() {
    return new CompressorMechanicalDesign(this);
  }

  /**
   * Getter for the field <code>heat</code>.
   *
   * <p>
   * Returns the exhaust heat rejected by the turbine cooler (the energy a HRSG or bottoming cycle could recover).
   * </p>
   *
   * @return exhaust heat duty in watts (W)
   */
  public double getHeat() {
    return heat;
  }

  /**
   * Getter for the field <code>power</code>.
   *
   * <p>
   * Returns the net shaft power available to the load, computed as the expander work minus the internal air-compressor
   * work. A positive value means the turbine delivers net power.
   * </p>
   *
   * @return net shaft power in watts (W)
   */
  public double getPower() {
    return power;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Setter for the field <code>inletStream</code>.
   * </p>
   */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    this.inStream = inletStream;
    try {
      this.outStream = inletStream.clone(this.getName() + " out stream");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    thermoSystem = inStream.getThermoSystem().clone();

    // Size the combustion-air flow from the fuel's stoichiometric oxygen demand plus excess air so
    // there is always enough oxygen for complete combustion. A fixed air/gas ratio (the legacy
    // default of 2.8) is sub-stoichiometric for methane and drives the oxygen inventory negative.
    double o2FractionInAir = getOxygenMoleFractionInAir();
    double o2PerMolFuel = calcStoichiometricOxygenDemand();
    if (o2FractionInAir > 0.0 && o2PerMolFuel > 0.0) {
      double requiredAirGasRatio = o2PerMolFuel / o2FractionInAir * excessAirFactor;
      this.airGasRatio = Math.max(this.airGasRatio, requiredAirGasRatio);
    }

    airStream.setFlowRate(thermoSystem.getFlowRate("mole/sec") * airGasRatio, "mole/sec");
    airStream.setPressure(ThermodynamicConstantsInterface.referencePressure);
    airStream.run(id);

    airCompressor.setInletStream(airStream);
    airCompressor.setOutletPressure(combustionpressure);
    airCompressor.run(id);
    compressorPower = airCompressor.getPower();
    StreamInterface outStreamAir = airCompressor.getOutletStream().clone();
    outStreamAir.getFluid().addFluid(thermoSystem);
    outStreamAir.run(id);

    double heatOfCombustion = inStream.LCV() * inStream.getFlowRate("mole/sec");
    Heater locHeater = new Heater("locHeater", outStreamAir);
    locHeater.setEnergyInput(heatOfCombustion);
    locHeater.run(id);

    // Apply combustion stoichiometry for all hydrocarbon components, limited by available oxygen.
    combustFuel(locHeater.getOutletStream().getFluid());

    locHeater.getOutletStream().getFluid().init(3);

    Expander expander = new Expander("expander", locHeater.getOutletStream());
    expander.setOutletPressure(ThermodynamicConstantsInterface.referencePressure);
    expander.run(id);

    Cooler cooler1 = new Cooler("cooler1", expander.getOutletStream());
    cooler1.setOutTemperature(288.15);
    cooler1.run(id);

    expanderPower = expander.getPower();

    // Expander.getPower() follows the compressor work convention (dH = hout - hinn), so it is
    // negative for the work the hot gas delivers to the shaft. The net shaft power available to
    // the load is therefore the magnitude of the expander work minus the air-compressor work:
    // (-expanderPower) - compressorPower.
    power = -expanderPower - compressorPower;
    this.heat = cooler1.getDuty();
    setCalculationIdentifier(id);
  }

  /**
   * Get the oxygen mole fraction of the combustion-air fluid.
   *
   * @return oxygen mole fraction (0-1)
   */
  private double getOxygenMoleFractionInAir() {
    SystemInterface air = airStream.getThermoSystem();
    for (int i = 0; i < air.getNumberOfComponents(); i++) {
      if ("oxygen".equals(air.getComponent(i).getName())) {
        return air.getComponent(i).getz();
      }
    }
    return 0.0;
  }

  /**
   * Calculate the stoichiometric oxygen demand of the fuel for complete combustion.
   *
   * <p>
   * For each hydrocarbon with C carbon and H hydrogen atoms the demand is (C + H/4) mol O2 per mol of that component,
   * weighted by its mole fraction.
   * </p>
   *
   * @return stoichiometric oxygen demand in mol O2 per mol fuel
   */
  private double calcStoichiometricOxygenDemand() {
    double o2 = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      if (thermoSystem.getComponent(i).isHydrocarbon()) {
        double c = thermoSystem.getComponent(i).getElements().getNumberOfElements("C");
        double h = thermoSystem.getComponent(i).getElements().getNumberOfElements("H");
        o2 += thermoSystem.getComponent(i).getz() * (c + h / 4.0);
      }
    }
    return o2;
  }

  /**
   * Apply combustion stoichiometry to a combined air + fuel fluid.
   *
   * <p>
   * Every hydrocarbon component is oxidised to CO2 and water, consuming oxygen. The burned fraction is limited by the
   * oxygen available in the fluid so the oxygen inventory never goes negative.
   * </p>
   *
   * @param fluid the combined combustion fluid to modify in place
   */
  private void combustFuel(SystemInterface fluid) {
    double requiredO2 = 0.0;
    double availableO2 = 0.0;
    int n = fluid.getNumberOfComponents();
    for (int i = 0; i < n; i++) {
      if ("oxygen".equals(fluid.getComponent(i).getName())) {
        availableO2 = fluid.getComponent(i).getNumberOfmoles();
      }
      if (fluid.getComponent(i).isHydrocarbon()) {
        double moles = fluid.getComponent(i).getNumberOfmoles();
        double c = fluid.getComponent(i).getElements().getNumberOfElements("C");
        double h = fluid.getComponent(i).getElements().getNumberOfElements("H");
        requiredO2 += moles * (c + h / 4.0);
      }
    }
    if (requiredO2 <= 0.0) {
      return;
    }
    double burnFraction = Math.min(1.0, availableO2 / requiredO2);

    double totalCO2 = 0.0;
    double totalH2O = 0.0;
    double totalO2Consumed = 0.0;
    List<String> hcNames = new ArrayList<String>();
    List<Double> hcBurn = new ArrayList<Double>();
    for (int i = 0; i < n; i++) {
      if (fluid.getComponent(i).isHydrocarbon()) {
        double moles = fluid.getComponent(i).getNumberOfmoles();
        double c = fluid.getComponent(i).getElements().getNumberOfElements("C");
        double h = fluid.getComponent(i).getElements().getNumberOfElements("H");
        double burned = moles * burnFraction;
        totalCO2 += burned * c;
        totalH2O += burned * h / 2.0;
        totalO2Consumed += burned * (c + h / 4.0);
        hcNames.add(fluid.getComponent(i).getName());
        hcBurn.add(burned);
      }
    }
    for (int k = 0; k < hcNames.size(); k++) {
      fluid.addComponent(hcNames.get(k), -hcBurn.get(k));
    }
    fluid.addComponent("CO2", totalCO2);
    fluid.addComponent("water", totalH2O);
    fluid.addComponent("oxygen", -totalO2Consumed);
  }

  /**
   * Get the excess-air factor applied on top of the stoichiometric air demand.
   *
   * @return excess-air factor (1.0 = stoichiometric)
   */
  public double getExcessAirFactor() {
    return excessAirFactor;
  }

  /**
   * Set the excess-air factor applied on top of the stoichiometric air demand.
   *
   * @param excessAirFactor excess-air factor (must be &ge; 1.0 for complete combustion)
   */
  public void setExcessAirFactor(double excessAirFactor) {
    this.excessAirFactor = excessAirFactor;
  }

  /**
   * Calculates ideal air fuel ratio [kg air/kg fuel].
   *
   * @return ideal air fuel ratio [kg air/kg fuel]
   */
  public double calcIdealAirFuelRatio() {
    thermoSystem = inStream.getThermoSystem().clone();
    double elementsH = 0.0;
    double elementsC = 0.0;
    double sumHC = 0.0;
    double molMassHC = 0.0;
    double wtFracHC = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfComponents(); i++) {
      if (thermoSystem.getComponent(i).isHydrocarbon()) {
        sumHC += thermoSystem.getComponent(i).getz();
        molMassHC += thermoSystem.getComponent(i).getz() * thermoSystem.getComponent(i).getMolarMass();
        elementsC += thermoSystem.getComponent(i).getz()
            * thermoSystem.getComponent(i).getElements().getNumberOfElements("C");
        elementsH += thermoSystem.getComponent(i).getz()
            * thermoSystem.getComponent(i).getElements().getNumberOfElements("H");
      }
    }

    if (sumHC < 1e-100) {
      return 0.0;
    } else {
      wtFracHC = molMassHC / thermoSystem.getMolarMass();
      molMassHC /= sumHC;
      elementsC /= sumHC;
      elementsH /= sumHC;
    }
    double A = elementsC + elementsH / 4;

    double AFR = A * (32.0 + 3.76 * 28.0) / 1000.0 / molMassHC * wtFracHC;
    return AFR;
  }

  /**
   * Get power output in specified unit.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return power output in specified unit
   */
  public double getPower(String unit) {
    switch (unit) {
    case "kW":
      return power / 1000.0;
    case "MW":
      return power / 1.0e6;
    case "hp":
      return power / 745.7;
    default:
      return power;
    }
  }

  /**
   * Get the rated (maximum) power output.
   *
   * @return rated power in Watts
   */
  public double getRatedPower() {
    return ratedPowerW;
  }

  /**
   * Get the rated (maximum) power output in specified unit.
   *
   * @param unit power unit ("W", "kW", "MW", "hp")
   * @return rated power in specified unit
   */
  public double getRatedPower(String unit) {
    switch (unit) {
    case "kW":
      return ratedPowerW / 1000.0;
    case "MW":
      return ratedPowerW / 1.0e6;
    case "hp":
      return ratedPowerW / 745.7;
    default:
      return ratedPowerW;
    }
  }

  /**
   * Set the rated (maximum) power output.
   *
   * @param ratedPower rated power in Watts
   */
  public void setRatedPower(double ratedPower) {
    this.ratedPowerW = ratedPower;
    initializeCapacityConstraints();
  }

  /**
   * Set the rated (maximum) power output with unit.
   *
   * @param ratedPower rated power value
   * @param unit power unit ("W", "kW", "MW", "hp")
   */
  public void setRatedPower(double ratedPower, String unit) {
    switch (unit) {
    case "kW":
      this.ratedPowerW = ratedPower * 1000.0;
      break;
    case "MW":
      this.ratedPowerW = ratedPower * 1.0e6;
      break;
    case "hp":
      this.ratedPowerW = ratedPower * 745.7;
      break;
    default:
      this.ratedPowerW = ratedPower;
    }
    initializeCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return Math.abs(power);
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return ratedPowerW > 0 ? ratedPowerW : Math.abs(power) * 1.2;
  }

  /**
   * Initialize capacity constraints for the gas turbine.
   */
  private void initializeCapacityConstraints() {
    capacityConstraints.clear();
    if (ratedPowerW > 0) {
      addCapacityConstraint(new CapacityConstraint("power", "kW", CapacityConstraint.ConstraintType.HARD)
          .setDesignValue(ratedPowerW / 1000.0).setMaxValue(ratedPowerW / 1000.0 * 1.1).setWarningThreshold(0.9)
          .setDescription("Gas turbine power output vs rated capacity")
          .setValueSupplier(() -> Math.abs(this.power) / 1000.0));
    }
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    return Collections.unmodifiableMap(capacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = c;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (CapacityConstraint c : capacityConstraints.values()) {
      if (c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (CapacityConstraint c : capacityConstraints.values()) {
      double util = c.getUtilization();
      if (!Double.isNaN(util)) {
        maxUtil = Math.max(maxUtil, util);
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    if (constraint != null) {
      capacityConstraints.put(constraint.getName(), constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return capacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    capacityConstraints.clear();
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (power > 0) {
      this.ratedPowerW = Math.abs(power) * safetyFactor;
      initializeCapacityConstraints();
      autoSized = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Gas Turbine Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");
    sb.append("\n--- Operating Conditions ---\n");
    sb.append("Power Output: ").append(String.format("%.2f kW", Math.abs(power) / 1000.0)).append("\n");
    if (ratedPowerW > 0) {
      sb.append("Rated Power: ").append(String.format("%.2f kW", ratedPowerW / 1000.0)).append("\n");
      sb.append("Utilization: ").append(String.format("%.1f%%", Math.abs(power) / ratedPowerW * 100)).append("\n");
    }
    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }
}
