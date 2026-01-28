package neqsim.process.equipment.tank;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.tank.TankMechanicalDesign;
import neqsim.process.util.monitor.TankResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Tank class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Tank extends ProcessEquipmentBaseClass
    implements AutoSizeable, CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Tank.class);

  /** Mechanical design for the tank. */
  private TankMechanicalDesign mechanicalDesign;

  /** Whether tank has been auto-sized. */
  private boolean autoSized = false;

  /** Design liquid level (fraction). */
  private double designLiquidLevel = 0.5;

  /** Design liquid residence time (seconds). */
  private double designResidenceTime = 300.0;

  /** Design volume (m3). */
  private double designVolume = 0.0;

  /** Minimum residence time allowed (seconds). */
  private double minResidenceTime = 60.0;

  /** Maximum liquid level allowed (fraction). */
  private double maxLiquidLevel = 0.9;

  /** Minimum liquid level allowed (fraction). */
  private double minLiquidLevel = 0.1;

  /** Tank capacity constraints map. */
  private java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> tankCapacityConstraints =
      new java.util.LinkedHashMap<String, neqsim.process.equipment.capacity.CapacityConstraint>();

  /** Whether capacity analysis is enabled. */
  private boolean tankCapacityAnalysisEnabled = false;

  SystemInterface thermoSystem;
  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;
  Stream gasOutStream;
  Stream liquidOutStream;
  private int numberOfInputStreams = 0;
  Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
  private double efficiency = 1.0;
  private double liquidCarryoverFraction = 0.0;
  private double gasCarryunderFraction = 0.0;
  private double volume = 136000.0;
  double steelWallTemperature = 298.15;
  double steelWallMass = 1840.0 * 1000.0;
  double steelWallArea = 15613.0;
  double heatTransferNumber = 5.0;
  double steelCp = 450.0;

  double separatorLength = 40.0;
  double separatorDiameter = 60.0;
  double liquidVolume = 235.0;
  double gasVolume = 15.0;

  private double liquidLevel = liquidVolume / (liquidVolume + gasVolume);

  /**
   * Constructor for Tank.
   *
   * @param name name of tank
   */
  public Tank(String name) {
    super(name);
    setCalculateSteadyState(true);
    initMechanicalDesign();
  }

  /**
   * <p>
   * Constructor for Tank.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Tank(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /** {@inheritDoc} */
  @Override
  public TankMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new TankMechanicalDesign(this);
  }

  /**
   * <p>
   * setInletStream.
   * </p>
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface inletStream) {
    inletStreamMixer.addStream(inletStream);
    thermoSystem = inletStream.getThermoSystem().clone();
    gasSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[0]);
    gasOutStream = new Stream("gasOutStream", gasSystem);

    thermoSystem = inletStream.getThermoSystem().clone();
    liquidSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    liquidOutStream = new Stream("liquidOutStream", liquidSystem);
  }

  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream) {
    if (numberOfInputStreams == 0) {
      setInletStream(newStream);
    } else {
      inletStreamMixer.addStream(newStream);
    }
    numberOfInputStreams++;
  }

  /**
   * <p>
   * Getter for the field <code>liquidOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * getGas.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getGas() {
    return getGasOutStream();
  }

  /**
   * <p>
   * getLiquid.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getLiquid() {
    return getLiquidOutStream();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Calculates the following properties:
   * </p>
   * <ul>
   * <li>steelWallTemperature</li>
   * <li>gasOutStream</li>
   * <li>liquidOutStream</li>
   * <li><code>thermoSystem</code> including properties</li>
   * <li>liquidLevel</li>
   * <li>liquidVolume</li>
   * <li>gasVolume</li>
   * </ul>
   */
  @Override
  public void run(UUID id) {
    inletStreamMixer.run(id);
    SystemInterface thermoSystem2 = inletStreamMixer.getOutletStream().getThermoSystem().clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
    ops.VUflash(thermoSystem2.getVolume(), thermoSystem2.getInternalEnergy());
    logger.info("Volume " + thermoSystem2.getVolume() + " internalEnergy "
        + thermoSystem2.getInternalEnergy());
    steelWallTemperature = thermoSystem2.getTemperature();
    if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
    } else {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "gas");
    }
    if (thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "oil");
    } else {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2.getEmptySystemClone(), "oil");
    }

    thermoSystem = thermoSystem2.clone();
    thermoSystem.setTotalNumberOfMoles(1.0e-10);
    thermoSystem.init(1);
    logger.info("number of phases " + thermoSystem.getNumberOfPhases());
    for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
      double relFact = gasVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
      if (j == 1) {
        relFact = liquidVolume / (thermoSystem.getPhase(j).getVolume() * 1.0e-5);
      }
      for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
        thermoSystem.addComponent(thermoSystem.getPhase(j).getComponent(i).getComponentName(),
            relFact * thermoSystem.getPhase(j).getComponent(i).getNumberOfMolesInPhase(), j);
      }
    }
    if (thermoSystem2.getNumberOfPhases() == 2) {
      thermoSystem.setBeta(gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
          / (gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
              + liquidVolume / thermoSystem2.getPhase(1).getMolarVolume()));
    } else {
      thermoSystem.setBeta(1.0 - 1e-10);
    }
    thermoSystem.init(3);
    logger.info("moles in separator " + thermoSystem.getNumberOfMoles());
    double volume1 = thermoSystem.getVolume();
    logger.info("volume1 bef " + volume1);
    logger.info("beta " + thermoSystem.getBeta());

    if (thermoSystem2.getNumberOfPhases() == 2) {
      liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
    } else {
      liquidLevel = 1e-10;
    }
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;
    logger.info("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    thermoSystem.display();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      return;
    }

    inletStreamMixer.run(id);

    System.out.println("moles out" + liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    // double inMoles =
    // inletStreamMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
    // double gasoutMoles = gasOutStream.getThermoSystem().getNumberOfMoles();
    // double liqoutMoles = liquidOutStream.getThermoSystem().getNumberOfMoles();
    thermoSystem.init(3);
    gasOutStream.getThermoSystem().init(3);
    liquidOutStream.getThermoSystem().init(3);
    inletStreamMixer.getOutletStream().getThermoSystem().init(3);
    double volume1 = thermoSystem.getVolume();
    System.out.println("volume1 " + volume1);
    double deltaEnergy = inletStreamMixer.getOutletStream().getThermoSystem().getEnthalpy()
        - gasOutStream.getThermoSystem().getEnthalpy()
        - liquidOutStream.getThermoSystem().getEnthalpy();
    System.out.println("enthalph delta " + deltaEnergy);
    double wallHeatTransfer = heatTransferNumber * steelWallArea
        * (steelWallTemperature - thermoSystem.getTemperature()) * dt;
    System.out.println("delta temp " + (steelWallTemperature - thermoSystem.getTemperature()));
    steelWallTemperature -= wallHeatTransfer / (steelCp * steelWallMass);
    System.out.println("wall Temperature " + steelWallTemperature);

    double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy + wallHeatTransfer;

    System.out.println("energy cooling " + dt * deltaEnergy);
    System.out.println("energy heating " + wallHeatTransfer / dt + " kW");

    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      double dn = 0.0;
      for (int k = 0; k < inletStreamMixer.getOutletStream().getThermoSystem()
          .getNumberOfPhases(); k++) {
        dn += inletStreamMixer.getOutletStream().getThermoSystem().getPhase(k).getComponent(i)
            .getNumberOfMolesInPhase();
      }
      dn = dn - gasOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase()
          - liquidOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      System.out.println("dn " + dn);
      thermoSystem.addComponent(inletStreamMixer.getOutletStream().getThermoSystem().getPhase(0)
          .getComponent(i).getComponentName(), dn * dt);
    }
    System.out.println("liquid level " + liquidLevel);
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;

    System.out.println("total moles " + thermoSystem.getTotalNumberOfMoles());

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.VUflash(volume1, newEnergy);

    setOutComposition(thermoSystem);
    setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

    if (thermoSystem.hasPhaseType("oil")) {
      liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
    } else {
      liquidLevel = 1e-10;
    }
    System.out.println("liquid level " + liquidLevel);
    liquidVolume =
        getLiquidLevel() * 3.14 / 4.0 * separatorDiameter * separatorDiameter * separatorLength;
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * separatorDiameter * separatorDiameter
        * separatorLength;
    setCalculationIdentifier(id);
  }

  /**
   * <p>
   * setOutComposition.
   * </p>
   *
   * @param thermoSystem a {@link neqsim.thermo.system.SystemInterface} object
   */
  public void setOutComposition(SystemInterface thermoSystem) {
    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      if (thermoSystem.hasPhaseType("gas")) {
        getGasOutStream().getThermoSystem().getPhase(0).getComponent(i).setx(thermoSystem
            .getPhase(thermoSystem.getPhaseNumberOfPhase("gas")).getComponent(i).getx());
      }
      if (thermoSystem.hasPhaseType("oil")) {
        getLiquidOutStream().getThermoSystem().getPhase(0).getComponent(i).setx(thermoSystem
            .getPhase(thermoSystem.getPhaseNumberOfPhase("oil")).getComponent(i).getx());
      }
    }
  }

  /**
   * <p>
   * setTempPres.
   * </p>
   *
   * @param temp a double
   * @param pres a double
   */
  public void setTempPres(double temp, double pres) {
    gasOutStream.getThermoSystem().setTemperature(temp);
    liquidOutStream.getThermoSystem().setTemperature(temp);

    inletStreamMixer.setPressure(pres);
    gasOutStream.getThermoSystem().setPressure(pres);
    liquidOutStream.getThermoSystem().setPressure(pres);

    UUID id = UUID.randomUUID();
    inletStreamMixer.run(id);
    gasOutStream.run(id);
    liquidOutStream.run(id);
  }

  /**
   * <p>
   * Getter for the field <code>efficiency</code>.
   * </p>
   *
   * @return a double
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * <p>
   * Setter for the field <code>efficiency</code>.
   * </p>
   *
   * @param efficiency a double
   */
  public void setEfficiency(double efficiency) {
    this.efficiency = efficiency;
  }

  /**
   * <p>
   * Getter for the field <code>liquidCarryoverFraction</code>.
   * </p>
   *
   * @return a double
   */
  public double getLiquidCarryoverFraction() {
    return liquidCarryoverFraction;
  }

  /**
   * <p>
   * Setter for the field <code>liquidCarryoverFraction</code>.
   * </p>
   *
   * @param liquidCarryoverFraction a double
   */
  public void setLiquidCarryoverFraction(double liquidCarryoverFraction) {
    this.liquidCarryoverFraction = liquidCarryoverFraction;
  }

  /**
   * <p>
   * Getter for the field <code>gasCarryunderFraction</code>.
   * </p>
   *
   * @return a double
   */
  public double getGasCarryunderFraction() {
    return gasCarryunderFraction;
  }

  /**
   * <p>
   * Setter for the field <code>gasCarryunderFraction</code>.
   * </p>
   *
   * @param gasCarryunderFraction a double
   */
  public void setGasCarryunderFraction(double gasCarryunderFraction) {
    this.gasCarryunderFraction = gasCarryunderFraction;
  }

  /**
   * <p>
   * Getter for the field <code>liquidLevel</code>.
   * </p>
   *
   * @return a double
   */
  public double getLiquidLevel() {
    return liquidLevel;
  }

  /**
   * <p>
   * Getter for the field <code>volume</code>.
   * </p>
   *
   * @return a double
   */
  public double getVolume() {
    return volume;
  }

  /**
   * <p>
   * Setter for the field <code>volume</code>.
   * </p>
   *
   * @param volume a double
   */
  public void setVolume(double volume) {
    this.volume = volume;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getThermoSystem();
      inletFlow += inletStreamMixer.getStream(i).getThermoSystem().getFlowRate(unit);
    }
    double outletFlow = getGasOutStream().getThermoSystem().getFlowRate(unit)
        + getLiquidOutStream().getThermoSystem().getFlowRate(unit);
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new TankResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    TankResponse res = new TankResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the tank setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>At least one inlet stream is connected</li>
   * <li>Tank volume is positive</li>
   * <li>Liquid level is within valid range</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Tank has no name",
          "Set tank name in constructor: new Tank(\"MyTank\")");
    }

    // Check: At least one inlet stream is connected (via addStream or setInletStream)
    // thermoSystem is set when setInletStream() is called
    if (thermoSystem == null && numberOfInputStreams == 0) {
      result.addError("stream", "No inlet stream connected",
          "Connect inlet stream: tank.setInletStream(stream) or tank.addStream(stream)");
    }

    // Check: Tank volume is positive
    if (volume <= 0) {
      result.addError("dimensions", "Tank volume must be positive: " + volume + " m3",
          "Set positive volume: tank.setVolume(100.0)");
    }

    // Check: Liquid level is in valid range (0-1)
    if (liquidLevel < 0 || liquidLevel > 1) {
      result.addWarning("level", "Liquid level outside 0-1 range: " + liquidLevel,
          "Liquid level should be between 0 and 1 (fraction)");
    }

    // Check: Efficiency is in valid range
    if (efficiency < 0 || efficiency > 1) {
      result.addError("efficiency", "Efficiency must be between 0 and 1: " + efficiency,
          "Set valid efficiency value");
    }

    return result;
  }

  // ============================================================================
  // AutoSizeable Implementation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    if (thermoSystem == null) {
      throw new IllegalStateException("Inlet stream must be connected before auto-sizing tank");
    }

    // Calculate required volume based on desired residence time
    double liquidFlowRate = 0.0;
    if (liquidSystem != null) {
      // Volume flow of liquid phase in m3/s
      liquidFlowRate = liquidSystem.getFlowRate("m3/hr") / 3600.0;
    } else if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      // Estimate from inlet stream
      liquidFlowRate = thermoSystem.getFlowRate("m3/hr") / 3600.0 * 0.5; // rough estimate
    }

    if (liquidFlowRate > 0) {
      // Required volume = flow rate * residence time * safety factor
      double requiredLiquidVolume = liquidFlowRate * designResidenceTime * safetyFactor;
      // Total volume considering design liquid level
      this.designVolume = requiredLiquidVolume / designLiquidLevel;
      this.volume = designVolume;
      this.liquidVolume = requiredLiquidVolume;
      this.gasVolume = volume - liquidVolume;
    }

    // Initialize capacity constraints
    initializeTankCapacityConstraints();

    autoSized = true;
    logger.info("Tank '{}' auto-sized: volume={:.1f} m3, liquid volume={:.1f} m3", getName(),
        volume, liquidVolume);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String companyStandard, String trDocument) {
    // Load company-specific parameters from database if available
    if (mechanicalDesign != null) {
      mechanicalDesign.setCompanySpecificDesignStandards(companyStandard);
    }
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAutoSized() {
    return autoSized;
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Tank Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(isAutoSized()).append("\n");

    sb.append("\n--- Dimensions ---\n");
    sb.append("Total Volume: ").append(String.format("%.1f m3", volume)).append("\n");
    sb.append("Liquid Volume: ").append(String.format("%.1f m3", liquidVolume)).append("\n");
    sb.append("Gas Volume: ").append(String.format("%.1f m3", gasVolume)).append("\n");
    sb.append("Separator Length: ").append(String.format("%.2f m", separatorLength)).append("\n");
    sb.append("Separator Diameter: ").append(String.format("%.2f m", separatorDiameter))
        .append("\n");

    sb.append("\n--- Operating Conditions ---\n");
    sb.append("Liquid Level: ").append(String.format("%.1f%%", liquidLevel * 100)).append("\n");
    sb.append("Efficiency: ").append(String.format("%.1f%%", efficiency * 100)).append("\n");

    if (liquidSystem != null) {
      double liquidFlowRate = liquidSystem.getFlowRate("m3/hr") / 3600.0;
      double actualResidenceTime = liquidFlowRate > 0 ? liquidVolume / liquidFlowRate : 0;
      sb.append("Actual Residence Time: ")
          .append(String.format("%.0f s (%.1f min)", actualResidenceTime, actualResidenceTime / 60))
          .append("\n");
    }

    if (isAutoSized()) {
      sb.append("\n--- Design Values ---\n");
      sb.append("Design Volume: ").append(String.format("%.1f m3", designVolume)).append("\n");
      sb.append("Design Liquid Level: ").append(String.format("%.1f%%", designLiquidLevel * 100))
          .append("\n");
      sb.append("Design Residence Time: ")
          .append(String.format("%.0f s (%.1f min)", designResidenceTime, designResidenceTime / 60))
          .append("\n");
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    java.util.Map<String, Object> report = new java.util.LinkedHashMap<String, Object>();
    report.put("equipmentName", getName());
    report.put("equipmentType", "Tank");
    report.put("autoSized", autoSized);

    java.util.Map<String, Object> dimensions = new java.util.LinkedHashMap<String, Object>();
    dimensions.put("totalVolume_m3", volume);
    dimensions.put("liquidVolume_m3", liquidVolume);
    dimensions.put("gasVolume_m3", gasVolume);
    dimensions.put("separatorLength_m", separatorLength);
    dimensions.put("separatorDiameter_m", separatorDiameter);
    report.put("dimensions", dimensions);

    java.util.Map<String, Object> operating = new java.util.LinkedHashMap<String, Object>();
    operating.put("liquidLevel_fraction", liquidLevel);
    operating.put("efficiency", efficiency);
    if (liquidSystem != null) {
      double liquidFlowRate = liquidSystem.getFlowRate("m3/hr") / 3600.0;
      operating.put("liquidFlowRate_m3_s", liquidFlowRate);
      operating.put("actualResidenceTime_s",
          liquidFlowRate > 0 ? liquidVolume / liquidFlowRate : 0);
    }
    report.put("operatingConditions", operating);

    if (autoSized) {
      java.util.Map<String, Object> design = new java.util.LinkedHashMap<String, Object>();
      design.put("designVolume_m3", designVolume);
      design.put("designLiquidLevel_fraction", designLiquidLevel);
      design.put("designResidenceTime_s", designResidenceTime);
      report.put("designValues", design);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  // ============================================================================
  // CapacityConstrainedEquipment Implementation
  // ============================================================================

  /**
   * Initialize tank capacity constraints.
   */
  private void initializeTankCapacityConstraints() {
    tankCapacityConstraints.clear();

    // Liquid level constraint
    neqsim.process.equipment.capacity.CapacityConstraint levelConstraint =
        new neqsim.process.equipment.capacity.CapacityConstraint("liquidLevel", "-",
            neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD);
    levelConstraint.setDesignValue(designLiquidLevel);
    levelConstraint.setMinValue(minLiquidLevel);
    levelConstraint.setMaxValue(maxLiquidLevel);
    levelConstraint.setUnit("-");
    levelConstraint.setDescription("Liquid level fraction (0-1)");
    levelConstraint.setValueSupplier(this::getLiquidLevel);
    tankCapacityConstraints.put("liquidLevel", levelConstraint);

    // Residence time constraint
    neqsim.process.equipment.capacity.CapacityConstraint residenceConstraint =
        new neqsim.process.equipment.capacity.CapacityConstraint("residenceTime", "s",
            neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
    residenceConstraint.setDesignValue(designResidenceTime);
    residenceConstraint.setMinValue(minResidenceTime);
    residenceConstraint.setUnit("s");
    residenceConstraint.setDescription("Liquid residence time");
    residenceConstraint.setValueSupplier(() -> {
      if (liquidSystem != null) {
        double liquidFlowRate = liquidSystem.getFlowRate("m3/hr") / 3600.0;
        return liquidFlowRate > 0 ? liquidVolume / liquidFlowRate : Double.MAX_VALUE;
      }
      return Double.MAX_VALUE;
    });
    tankCapacityConstraints.put("residenceTime", residenceConstraint);

    // Volume utilization constraint
    if (designVolume > 0) {
      neqsim.process.equipment.capacity.CapacityConstraint volumeConstraint =
          new neqsim.process.equipment.capacity.CapacityConstraint("volumeUtilization", "m3",
              neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.SOFT);
      volumeConstraint.setDesignValue(designVolume);
      volumeConstraint.setMaxValue(designVolume);
      volumeConstraint.setUnit("m3");
      volumeConstraint.setDescription("Volume utilization vs design");
      volumeConstraint.setValueSupplier(() -> liquidVolume + gasVolume);
      tankCapacityConstraints.put("volumeUtilization", volumeConstraint);
    }
  }

  /**
   * Sets the design liquid level.
   *
   * @param level design liquid level (0-1 fraction)
   */
  public void setDesignLiquidLevel(double level) {
    this.designLiquidLevel = level;
    initializeTankCapacityConstraints();
  }

  /**
   * Gets the design liquid level.
   *
   * @return design liquid level (0-1 fraction)
   */
  public double getDesignLiquidLevel() {
    return designLiquidLevel;
  }

  /**
   * Sets the design residence time.
   *
   * @param time design residence time in seconds
   */
  public void setDesignResidenceTime(double time) {
    this.designResidenceTime = time;
    initializeTankCapacityConstraints();
  }

  /**
   * Gets the design residence time.
   *
   * @return design residence time in seconds
   */
  public double getDesignResidenceTime() {
    return designResidenceTime;
  }

  /**
   * Sets the minimum residence time.
   *
   * @param time minimum residence time in seconds
   */
  public void setMinResidenceTime(double time) {
    this.minResidenceTime = time;
    initializeTankCapacityConstraints();
  }

  /**
   * Gets the minimum residence time.
   *
   * @return minimum residence time in seconds
   */
  public double getMinResidenceTime() {
    return minResidenceTime;
  }

  /**
   * Sets the maximum liquid level.
   *
   * @param level maximum liquid level (0-1 fraction)
   */
  public void setMaxLiquidLevel(double level) {
    this.maxLiquidLevel = level;
    initializeTankCapacityConstraints();
  }

  /**
   * Gets the maximum liquid level.
   *
   * @return maximum liquid level (0-1 fraction)
   */
  public double getMaxLiquidLevel() {
    return maxLiquidLevel;
  }

  /**
   * Sets the minimum liquid level.
   *
   * @param level minimum liquid level (0-1 fraction)
   */
  public void setMinLiquidLevel(double level) {
    this.minLiquidLevel = level;
    initializeTankCapacityConstraints();
  }

  /**
   * Gets the minimum liquid level.
   *
   * @return minimum liquid level (0-1 fraction)
   */
  public double getMinLiquidLevel() {
    return minLiquidLevel;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityAnalysisEnabled() {
    return tankCapacityAnalysisEnabled;
  }

  /** {@inheritDoc} */
  @Override
  public void setCapacityAnalysisEnabled(boolean enabled) {
    this.tankCapacityAnalysisEnabled = enabled;
    if (enabled && tankCapacityConstraints.isEmpty()) {
      initializeTankCapacityConstraints();
    }
  }

  /** {@inheritDoc} */
  @Override
  public java.util.Map<String, neqsim.process.equipment.capacity.CapacityConstraint> getCapacityConstraints() {
    return java.util.Collections.unmodifiableMap(tankCapacityConstraints);
  }

  /** {@inheritDoc} */
  @Override
  public neqsim.process.equipment.capacity.CapacityConstraint getBottleneckConstraint() {
    neqsim.process.equipment.capacity.CapacityConstraint bottleneck = null;
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : tankCapacityConstraints
        .values()) {
      if (constraint.isEnabled()) {
        double util = constraint.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = constraint;
        }
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : tankCapacityConstraints
        .values()) {
      if (constraint.isEnabled() && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : tankCapacityConstraints
        .values()) {
      if (constraint.isEnabled()
          && constraint
              .getType() == neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType.HARD
          && constraint.getUtilization() > 1.0) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (neqsim.process.equipment.capacity.CapacityConstraint constraint : tankCapacityConstraints
        .values()) {
      if (constraint.isEnabled()) {
        maxUtil = Math.max(maxUtil, constraint.getUtilization());
      }
    }
    return maxUtil;
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(
      neqsim.process.equipment.capacity.CapacityConstraint constraint) {
    tankCapacityConstraints.put(constraint.getName(), constraint);
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    return tankCapacityConstraints.remove(constraintName) != null;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    tankCapacityConstraints.clear();
  }
}
