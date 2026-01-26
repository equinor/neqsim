/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.process.equipment.separator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.StandardConstraintType;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.sectiontype.ManwaySection;
import neqsim.process.equipment.separator.sectiontype.MeshSection;
import neqsim.process.equipment.separator.sectiontype.NozzleSection;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.equipment.separator.sectiontype.ValveSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.ml.StateVector;
import neqsim.process.ml.StateVectorProvider;
import neqsim.process.util.fire.SeparatorFireExposure;
import neqsim.process.util.monitor.SeparatorResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSoreideWhitson;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Separator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Separator extends ProcessEquipmentBaseClass
    implements SeparatorInterface, StateVectorProvider, CapacityConstrainedEquipment, AutoSizeable {
  /**
   * Initializes separator for transient calculations.
   */
  public void initializeTransientCalculation() {
    try {
      liquidVolume = calcLiquidVolume();
      enforceHeadspace();

      thermoSystem = thermoSystem2.clone();
      thermoSystem.init(1);
      thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      thermoSystem2.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
        double relFact = 1.0;
        if (thermoSystem.getPhase(j).getPhaseTypeName().equals("gas")) {
          relFact = gasVolume / (thermoSystem2.getPhase(j).getVolume("m3"));
        } else {
          relFact = liquidVolume / (thermoSystem2.getPhase(j).getVolume("m3"));
        }
        for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
          thermoSystem.addComponent(i,
              (relFact - 1.0) * thermoSystem2.getPhase(j).getComponent(i).getNumberOfMolesInPhase(),
              j);
        }
      }
      ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
      if (thermoSystem.getNumberOfComponents() > 1) {
        ops.TPflash();
      } else {
        thermoSystem.setBeta(getFeedStream().getFluid().getBeta());
      }
      thermoSystem.init(3);
      thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
        liquidLevel = levelFromVolume(thermoSystem.getPhase(1).getVolume("m3"));
        liquidVolume = calcLiquidVolume();
      } else {
        liquidLevel = 0.0;
        liquidVolume = 0.0;
      }
      enforceHeadspace();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    isInitTransient = true;
  }

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Separator.class);

  SystemInterface thermoSystem;
  SystemInterface gasSystem;
  SystemInterface waterSystem;
  SystemInterface liquidSystem;
  SystemInterface thermoSystemCloned;
  SystemInterface thermoSystem2;
  boolean isInitTransient = false;

  /** Orientation of separator. "horizontal" or "vertical" */
  private String orientation = "horizontal";
  StreamInterface gasOutStream;
  StreamInterface liquidOutStream;

  private double pressureDrop = 0.0;
  public int numberOfInputStreams = 0;
  Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
  private double efficiency = 1.0;
  private double liquidCarryoverFraction = 0.0;
  private double gasCarryunderFraction = 0.0;

  private String specifiedStream = "feed";

  private double oilInGas = 0.0;
  private String oilInGasSpec = "mole";

  private double waterInGas = 0.0;
  private String waterInGasSpec = "mole";

  private double gasInLiquid = 0.0;
  private String gasInLiquidSpec = "mole";

  /** Length of separator volume. */
  private double separatorLength = 5.0;
  /** Inner diameter/height of separator volume. */
  private double internalDiameter = 1.0;
  private double internalRadius = internalDiameter / 2;

  /** Liquid level height in meters (default set to 50% of internal diameter). */
  protected double liquidLevel = 0.5 * internalDiameter;

  private static final double MIN_HEADSPACE_FRACTION = 0.05;
  private static final double MIN_HEADSPACE_VOLUME = 1.0e-6;

  /**
   * Default liquid density for sizing calculations when no liquid phase is
   * present [kg/m³]. Assumes
   * water density for conservative sizing.
   */
  public static final double DEFAULT_LIQUID_DENSITY_FOR_SIZING = 1000.0;

  /** Separator cross sectional area. */
  private double sepCrossArea = Math.PI * internalDiameter * internalDiameter / 4.0;

  /** Separator volume. */
  private double separatorVolume = sepCrossArea * separatorLength;

  double liquidVolume;
  double gasVolume;

  ArrayList<SeparatorSection> separatorSection = new ArrayList<SeparatorSection>();

  SeparatorMechanicalDesign separatorMechanicalDesign;
  private double lastEnthalpy;
  private double lastFlowRate;
  private double lastPressure;

  // Heat input capabilities
  private boolean setHeatInput = false;
  private double heatInput = 0.0; // W (watts)
  private String heatInputUnit = "W";

  // Design capacity parameters
  /**
   * Default design gas load factor (K-factor) [m/s]. Used to detect user
   * overrides.
   */
  private static final double DEFAULT_DESIGN_GAS_LOAD_FACTOR = 0.11;
  /** Design gas load factor (K-factor) from mechanical design [m/s]. */
  private double designGasLoadFactor = DEFAULT_DESIGN_GAS_LOAD_FACTOR;
  /** Liquid level fraction (Fg) from mechanical design. */
  private double designLiquidLevelFraction = 0.8;
  /** Maximum gas volumetric flow rate from design [m3/s]. */
  private double maxDesignGasFlowRate = Double.MAX_VALUE;
  /** Whether to enforce capacity limits during simulation. */
  private boolean enforceCapacityLimits = false;

  /** Capacity constraints map for this separator. */
  private Map<String, CapacityConstraint> capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();

  /**
   * Constructor for Separator.
   *
   * @param name Name of separator
   */
  public Separator(String name) {
    super(name);
    liquidVolume = calcLiquidVolume();
    enforceHeadspace();
    setCalculateSteadyState(true);
    initMechanicalDesign();
    initializeCapacityConstraints();
  }

  /**
   * Constructor for Separator.
   *
   * @param name        a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *                    object
   */
  public Separator(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
    numberOfInputStreams++;
  }

  /** {@inheritDoc} */
  @Override
  public SeparatorMechanicalDesign getMechanicalDesign() {
    return separatorMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    separatorMechanicalDesign = new SeparatorMechanicalDesign(this);
  }

  /**
   * <p>
   * setInletStream.
   * </p>
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *                    object
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
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface}
   *                  object
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
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquidOutStream() {
    if (liquidOutStream.getFluid().getClass().getName()
        .equals("neqsim.thermo.system.SystemSoreideWhitson")) {
      if (!liquidOutStream.getFluid().hasPhaseType("aqueous")) {
        ((SystemSoreideWhitson) liquidOutStream.getFluid()).setSalinity(0.0, "mole/sec");
      }
    }
    return liquidOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getGasOutStream() {
    if (gasOutStream.getFluid().getClass().getName()
        .equals("neqsim.thermo.system.SystemSoreideWhitson")) {
      // if the fluid is a soreide whitson system, we need to clone it to avoid
      // problems with the thermo system
      ((SystemSoreideWhitson) gasOutStream.getFluid()).setSalinity(0.0, "mole/sec");
    }
    return gasOutStream;
  }

  /**
   * <p>
   * getGas.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getGas() {
    return getGasOutStream();
  }

  /**
   * <p>
   * getLiquid.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquid() {
    return getLiquidOutStream();
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return thermoSystem;
  }

  /**
   * <p>
   * setEntrainment.
   * </p>
   *
   * @param val             a double specifying the entrainment amount
   * @param specType        a {@link java.lang.String} object describing the
   *                        specification unit
   * @param specifiedStream a {@link java.lang.String} object describing the
   *                        reference stream
   * @param phaseFrom       a {@link java.lang.String} object describing the phase
   *                        entrained from
   * @param phaseTo         a {@link java.lang.String} object describing the phase
   *                        entrained to
   */
  public void setEntrainment(double val, String specType, String specifiedStream, String phaseFrom,
      String phaseTo) {
    this.specifiedStream = specifiedStream;
    if (phaseFrom.equals("oil") && phaseTo.equals("gas")) {
      oilInGas = val;
      oilInGasSpec = specType;
    }
    if (phaseFrom.equals("aqueous") && phaseTo.equals("gas")) {
      waterInGas = val;
      waterInGasSpec = specType;
    }
    if (phaseFrom.equals("gas") && phaseTo.equals("liquid")) {
      gasInLiquid = val;
      gasInLiquidSpec = specType;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    inletStreamMixer.run(id);
    double enthalpy = inletStreamMixer.getOutletStream().getFluid().getEnthalpy();
    double flow = inletStreamMixer.getOutletStream().getFlowRate("kg/hr");
    double pres = inletStreamMixer.getOutletStream().getPressure();
    if (Math.abs((lastEnthalpy - enthalpy) / enthalpy) < 1e-6
        && Math.abs((lastFlowRate - flow) / flow) < 1e-6
        && Math.abs((lastPressure - pres) / pres) < 1e-6) {
      return;
    }
    lastEnthalpy = inletStreamMixer.getOutletStream().getFluid().getEnthalpy();
    lastFlowRate = inletStreamMixer.getOutletStream().getFlowRate("kg/hr");
    lastPressure = inletStreamMixer.getOutletStream().getPressure();
    thermoSystem2 = inletStreamMixer.getOutletStream().getThermoSystem().clone();
    thermoSystem2.setPressure(thermoSystem2.getPressure() - pressureDrop);

    // Handle heat input for steady-state operations
    if (setHeatInput && heatInput != 0.0) {
      // Add heat input to the system enthalpy
      double currentEnthalpy = thermoSystem2.getEnthalpy(); // Default unit is J
      double newEnthalpy = currentEnthalpy + heatInput; // heatInput is in watts (J/s) - for steady
                                                        // state we add directly

      // Perform HP flash (enthalpy-pressure flash) with heat input
      ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
      ops.PHflash(newEnthalpy, 0); // Second parameter is typically 0
      thermoSystem2.initProperties();
    } else if (Math.abs(pressureDrop) > 1e-6) {
      ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
      ops.TPflash();
      thermoSystem2.initProperties();
    }

    thermoSystem2.addPhaseFractionToPhase(oilInGas, oilInGasSpec, specifiedStream, "oil", "gas");
    thermoSystem2.addPhaseFractionToPhase(waterInGas, waterInGasSpec, specifiedStream, "aqueous",
        "gas");
    if (thermoSystem2.hasPhaseType("liquid")) {
      thermoSystem2.addPhaseFractionToPhase(gasInLiquid, gasInLiquidSpec, specifiedStream, "gas",
          "liquid");
    } else if (thermoSystem2.hasPhaseType("oil") && thermoSystem2.hasPhaseType("aqueous")) {
      double oilMoles = thermoSystem2.getPhase("oil").getNumberOfMolesInPhase();
      double waterMoles = thermoSystem2.getPhase("aqueous").getNumberOfMolesInPhase();
      double totalMoles = oilMoles + waterMoles;
      if (totalMoles > 0.0) {
        double oilShare = oilMoles / totalMoles;
        double waterShare = waterMoles / totalMoles;
        thermoSystem2.addPhaseFractionToPhase(gasInLiquid * oilShare, gasInLiquidSpec,
            specifiedStream, "gas", "oil");
        thermoSystem2.addPhaseFractionToPhase(gasInLiquid * waterShare, gasInLiquidSpec,
            specifiedStream, "gas", "aqueous");
      }
    } else if (thermoSystem2.hasPhaseType("oil")) {
      thermoSystem2.addPhaseFractionToPhase(gasInLiquid, gasInLiquidSpec, specifiedStream, "gas",
          "oil");
    } else if (thermoSystem2.hasPhaseType("aqueous")) {
      thermoSystem2.addPhaseFractionToPhase(gasInLiquid, gasInLiquidSpec, specifiedStream, "gas",
          "aqueous");
    }

    if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
      gasOutStream.getFluid().init(2);
    } else {
      // No gas phase - set empty system with very low flow
      SystemInterface emptyGasSystem = thermoSystem2.getEmptySystemClone();
      emptyGasSystem.setTotalFlowRate(1e-20, "kg/hr");
      emptyGasSystem.init(0);
      gasOutStream.setThermoSystem(emptyGasSystem);
    }
    if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "liquid");
      liquidOutStream.getFluid().init(2);
    } else {
      // No liquid phase - set empty system with very low flow
      SystemInterface emptyLiquidSystem = thermoSystem2.getEmptySystemClone();
      emptyLiquidSystem.setTotalFlowRate(1e-20, "kg/hr");
      emptyLiquidSystem.init(0);
      liquidOutStream.setThermoSystem(emptyLiquidSystem);
    }
    if (thermoSystem2.hasPhaseType("gas") && thermoSystem2.getNumberOfComponents() > 1) {
      gasOutStream.run(id);
    } else if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.getFluid().init(3);
    }
    if (thermoSystem2.hasPhaseType("aqueous")
        || thermoSystem2.hasPhaseType("oil") && thermoSystem2.getNumberOfComponents() > 1) {
      liquidOutStream.run(id);
    } else if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
      try {
        liquidOutStream.getFluid().init(3);
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    if (getCalculateSteadyState()) {
      thermoSystem = thermoSystem2;
    } else {
      initializeTransientCalculation();
    }
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
  public String[][] getResultTable() {
    return thermoSystem.getResultTable();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      run(id);
      increaseTime(dt);
      setCalculationIdentifier(id);
    } else {
      if (!isInitTransient) {
        initializeTransientCalculation();
      }
      inletStreamMixer.run(id);

      // Check if separator has enough moles for calculation
      if (thermoSystem.getTotalNumberOfMoles() < 1e-10) {
        // Separator is essentially empty - just update time and return
        increaseTime(dt);
        setCalculationIdentifier(id);
        return;
      }

      try {
        thermoSystem.init(2);
      } catch (Exception e) {
        // If init fails due to low moles, skip this step
        logger.debug("Separator init(2) failed, likely due to low moles: " + e.getMessage());
        increaseTime(dt);
        setCalculationIdentifier(id);
        return;
      }
      thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      try {
        gasOutStream.getThermoSystem().init(2);
        if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
          liquidOutStream.getThermoSystem().init(2);
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
      boolean hasliq = false;
      double deliq = 0.0;
      if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
        hasliq = true;
        deliq = -liquidOutStream.getThermoSystem().getEnthalpy();
      }
      double deltaEnergy = inletStreamMixer.getOutletStream().getThermoSystem().getEnthalpy()
          - gasOutStream.getThermoSystem().getEnthalpy() + deliq;

      // Add external heat input (e.g., from flare radiation)
      if (setHeatInput && heatInput != 0.0) {
        deltaEnergy += heatInput; // Heat input in watts (J/s)
      }

      double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy;
      thermoSystem.init(0);
      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        double dncomp = 0.0;
        dncomp += inletStreamMixer.getOutletStream().getThermoSystem().getComponent(i).getNumberOfmoles();
        double dniliq = 0.0;
        if (hasliq) {
          dniliq = -liquidOutStream.getThermoSystem().getComponent(i).getNumberOfmoles();
        }
        dncomp += -gasOutStream.getThermoSystem().getComponent(i).getNumberOfmoles() + dniliq;

        double molesChange = dncomp * dt;
        double currentMoles = thermoSystem.getComponent(i).getNumberOfmoles();
        // Prevent negative moles - limit removal to what's available
        if (currentMoles + molesChange < 1e-20) {
          molesChange = -currentMoles + 1e-20;
        }
        thermoSystem.addComponent(i, molesChange);
      }

      // Skip VU flash if system has too few moles (essentially empty separator)
      if (thermoSystem.getTotalNumberOfMoles() < 1e-10) {
        setCalculationIdentifier(id);
        return;
      }

      ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
      thermoOps.VUflash(gasVolume + liquidVolume, newEnergy, "m3", "J");
      thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

      if (thermoSystem.getNumberOfComponents() > 1) {
        if (thermoSystem.hasPhaseType("gas")) {
          gasOutStream.getFluid()
              .setMolarComposition(thermoSystem.getPhase("gas").getMolarComposition());
        }
        if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
          if (thermoSystem.getNumberOfPhases() > 1) {
            liquidOutStream.getFluid()
                .setMolarComposition(thermoSystem.getPhase(1).getMolarComposition());
          }
        }
      }
      setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

      liquidLevel = 0.0;
      if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
        double volumeLoc = 0.0;
        if (thermoSystem.hasPhaseType("oil")) {
          volumeLoc += thermoSystem.getPhase("oil").getVolume("m3");
        }
        if (thermoSystem.hasPhaseType("aqueous")) {
          volumeLoc += thermoSystem.getPhase("aqueous").getVolume("m3");
        }
        liquidLevel = levelFromVolume(volumeLoc);
      }
      liquidVolume = calcLiquidVolume();
      enforceHeadspace();

      // System.out.printf("vol original: %.2f mine %f \n", liquidVolume, liqVol);
      setCalculationIdentifier(id);
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
    gasOutStream.getThermoSystem().setPressure(pres);

    if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
      liquidOutStream.getThermoSystem().setTemperature(temp);
      liquidOutStream.getThermoSystem().setPressure(pres);
    }

    inletStreamMixer.setPressure(pres);

    UUID id = UUID.randomUUID();

    inletStreamMixer.run(id);
    if (getThermoSystem().getNumberOfComponents() > 1) {
      gasOutStream.run(id);
      if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
        liquidOutStream.run(id);
      }
    } else {
      gasOutStream.getFluid().init(2);
      gasOutStream.getFluid().initPhysicalProperties("density");
      if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
        liquidOutStream.getFluid().init(2);
        liquidOutStream.getFluid().initPhysicalProperties("density");
      }
    }
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

  /** {@inheritDoc} */
  @Override
  public void setLiquidLevel(double liquidlev) {
    double maxHeight = getMaxLiquidHeight();
    if (maxHeight <= 0.0) {
      liquidLevel = 0.0;
    } else {
      liquidLevel = clampLiquidHeight(liquidlev * maxHeight);
    }
    updateHoldupVolumes();
  }

  /**
   * <p>
   * Getter for the field <code>liquidLevel</code> in percentage.
   * </p>
   *
   * @return a double
   */
  public double getLiquidLevel() {
    double maxHeight = getMaxLiquidHeight();
    if (maxHeight <= 0.0) {
      return 0.0;
    }
    return liquidLevel / maxHeight;
  }

  /**
   * <p>
   * Getter for the field <code>pressureDrop</code>.
   * </p>
   *
   * @return the pressureDrop
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * <p>
   * Setter for the field <code>pressureDrop</code>.
   * </p>
   *
   * @param pressureDrop the pressureDrop to set
   */
  public void setPressureDrop(double pressureDrop) {
    this.pressureDrop = pressureDrop;
  }

  /**
   * <p>
   * Getter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @return the diameter
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  /** {@inheritDoc} */
  @Override
  public void setInternalDiameter(double diameter) {
    double levelFraction = getLiquidLevel();
    this.internalDiameter = diameter;
    this.internalRadius = diameter / 2;
    this.sepCrossArea = Math.PI * internalDiameter * internalDiameter / 4.0;
    this.separatorVolume = sepCrossArea * separatorLength;
    this.liquidLevel = clampLiquidHeight(levelFraction * getMaxLiquidHeight());
    updateHoldupVolumes();
  }

  /**
   * <p>
   * getGasSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getGasSuperficialVelocity() {
    if (orientation.equals("horizontal")) {
      return thermoSystem.getPhase(0).getFlowRate("m3/sec")
          / (sepCrossArea - liquidArea(liquidLevel));
    } else if (orientation.equals("vertical")) {
      return thermoSystem.getPhase(0).getFlowRate("m3/sec") / sepCrossArea;
    } else {
      return 0;
    }
  }

  /**
   * <p>
   * getGasLoadFactor.
   * </p>
   *
   * @return a double
   */
  public double getGasLoadFactor() {
    thermoSystem.initPhysicalProperties();
    double gasDensity = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
    double liquidDensity;
    // For dry gas (single phase), use default liquid density of 1000 kg/m3
    if (thermoSystem.getNumberOfPhases() < 2
        || !thermoSystem.hasPhaseType("oil") && !thermoSystem.hasPhaseType("aqueous")) {
      liquidDensity = 1000.0; // Default liquid density for dry separators/scrubbers
    } else {
      liquidDensity = thermoSystem.getPhase(1).getPhysicalProperties().getDensity();
    }
    double term1 = (liquidDensity - gasDensity) / gasDensity;
    return getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
  }

  /**
   * <p>
   * getGasLoadFactor.
   * </p>
   *
   * @param phaseNumber a int
   * @return a double
   */
  public double getGasLoadFactor(int phaseNumber) {
    double gasAreaFraction = 1.0;
    if (orientation.equals("horizontal")) {
      gasAreaFraction = 1.0 - (liquidVolume / separatorVolume);
    }
    thermoSystem.initPhysicalProperties();
    double gasDensity = thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
    double liquidDensity;
    // For dry gas (single phase), use default liquid density of 1000 kg/m3
    if (thermoSystem.getNumberOfPhases() < 2 || phaseNumber >= thermoSystem.getNumberOfPhases()) {
      liquidDensity = 1000.0; // Default liquid density for dry separators/scrubbers
    } else {
      liquidDensity = thermoSystem.getPhase(phaseNumber).getPhysicalProperties().getDensity();
    }
    double term1 = 1.0 / gasAreaFraction * (liquidDensity - gasDensity) / gasDensity;
    return getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
  }

  /**
   * <p>
   * getDeRatedGasLoadFactor.
   * </p>
   *
   * @return a double
   */
  public double getDeRatedGasLoadFactor() {
    thermoSystem.initPhysicalProperties();
    double derating = 1.0;
    double surfaceTension = thermoSystem.getInterphaseProperties().getSurfaceTension(0, 1);
    if (surfaceTension < 10.0e-3) {
      derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
    }
    // System.out.println("derating " + derating);
    double term1 = (thermoSystem.getPhase(1).getPhysicalProperties().getDensity()
        - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
        / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
    return derating * getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
  }

  /**
   * <p>
   * getDeRatedGasLoadFactor.
   * </p>
   *
   * @param phaseNum a int
   * @return a double
   */
  public double getDeRatedGasLoadFactor(int phaseNum) {
    thermoSystem.initPhysicalProperties();
    double derating = 1.0;
    double surfaceTension = thermoSystem.getInterphaseProperties().getSurfaceTension(phaseNum - 1, phaseNum);
    if (surfaceTension < 10.0e-3) {
      derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
    }
    // System.out.println("derating " + derating);
    double term1 = (thermoSystem.getPhase(phaseNum).getPhysicalProperties().getDensity()
        - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
        / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
    return derating * getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
  }

  // ============================================================================
  // Design Capacity Methods
  // ============================================================================

  /**
   * Gets the design gas load factor (K-factor) that the separator is designed
   * for.
   *
   * @return design gas load factor [m/s]
   */
  public double getDesignGasLoadFactor() {
    return designGasLoadFactor;
  }

  /**
   * Sets the design gas load factor (K-factor) for the separator.
   * Also updates the associated capacity constraint if one exists.
   *
   * @param kFactor design gas load factor [m/s], typically 0.07-0.15 for
   *                horizontal separators
   */
  public void setDesignGasLoadFactor(double kFactor) {
    this.designGasLoadFactor = kFactor;
    // Update the capacity constraint if it exists
    // Note: This does NOT enable the constraint - users must explicitly call
    // enableConstraint("gasLoadFactor") or useXxxConstraints() to enable capacity
    // tracking
    CapacityConstraint constraint = capacityConstraints.get("gasLoadFactor");
    if (constraint != null) {
      constraint.setDesignValue(kFactor);
    }
  }

  /**
   * Calculates the maximum allowable gas velocity based on the design K-factor.
   * Uses the
   * Souders-Brown equation: V_max = K * sqrt((rho_liq - rho_gas) / rho_gas)
   *
   * <p>
   * If no liquid phase is present, a default liquid density of 1000 kg/m³ is
   * assumed for sizing
   * purposes.
   * </p>
   *
   * @return maximum allowable gas velocity [m/s]
   */
  public double getMaxAllowableGasVelocity() {
    if (thermoSystem == null) {
      return Double.MAX_VALUE;
    }
    thermoSystem.initPhysicalProperties();
    double gasDensity = thermoSystem.hasPhaseType("gas")
        ? thermoSystem.getPhase("gas").getPhysicalProperties().getDensity()
        : 50.0; // Default gas density if no gas phase

    // Use actual liquid density if available, otherwise default to 1000 kg/m³
    double liqDensity = DEFAULT_LIQUID_DENSITY_FOR_SIZING;
    if (thermoSystem.hasPhaseType("oil")) {
      liqDensity = thermoSystem.getPhase("oil").getPhysicalProperties().getDensity();
    } else if (thermoSystem.hasPhaseType("aqueous")) {
      liqDensity = thermoSystem.getPhase("aqueous").getPhysicalProperties().getDensity();
    }

    return designGasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
  }

  /**
   * Calculates the maximum allowable gas volumetric flow rate based on separator
   * design.
   *
   * @return maximum allowable gas flow rate [m3/s]
   */
  public double getMaxAllowableGasFlowRate() {
    double maxVelocity = getMaxAllowableGasVelocity();
    double gasArea;
    if (orientation.equals("horizontal")) {
      // For horizontal, gas flows through upper section (above liquid level)
      gasArea = sepCrossArea - liquidArea(liquidLevel);
    } else {
      // For vertical separator
      gasArea = sepCrossArea * (1.0 - designLiquidLevelFraction);
    }
    return maxVelocity * gasArea;
  }

  /**
   * Gets the capacity utilization as a fraction (current flow / max design flow).
   * A value greater
   * than 1.0 indicates the separator is overloaded.
   *
   * @return capacity utilization fraction (0.0 to 1.0+ if overloaded), or 0.0 if
   *         single phase (no
   *         separation needed), or Double.NaN if calculation error
   */
  public double getCapacityUtilization() {
    if (thermoSystem == null) {
      return Double.NaN; // No thermo system
    }

    // Single phase - no separation needed, return 0% utilization
    if (thermoSystem.getNumberOfPhases() < 2) {
      return 0.0;
    }

    // No gas phase - liquid-only, no gas separation needed
    if (!thermoSystem.hasPhaseType("gas")) {
      return 0.0;
    }

    double currentGasFlow = thermoSystem.getPhase(0).getFlowRate("m3/sec");
    double maxFlow = getMaxAllowableGasFlowRate();

    // Check for invalid max flow calculations
    if (maxFlow <= 0 || Double.isInfinite(maxFlow) || maxFlow == Double.MAX_VALUE
        || Double.isNaN(maxFlow)) {
      return Double.NaN; // Cannot calculate utilization
    }

    return currentGasFlow / maxFlow;
  }

  /**
   * Checks if the separator feed is single-phase (no separation required).
   *
   * @return true if the thermodynamic system has only one phase
   */
  public boolean isSinglePhase() {
    if (thermoSystem == null) {
      return true; // No system - treat as single phase
    }
    return thermoSystem.getNumberOfPhases() < 2;
  }

  /**
   * Checks if the separator is overloaded (operating above design capacity).
   *
   * @return true if capacity utilization is greater than 1.0
   */
  public boolean isOverloaded() {
    double util = getCapacityUtilization();
    if (Double.isNaN(util)) {
      return false; // Cannot determine
    }
    return util > 1.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates that the separator simulation produced physically reasonable
   * results.
   * </p>
   */
  @Override
  public boolean isSimulationValid() {
    if (thermoSystem == null) {
      return false;
    }

    // Check for NaN in basic properties
    if (Double.isNaN(thermoSystem.getTemperature()) || Double.isNaN(thermoSystem.getPressure())) {
      return false;
    }

    // Check phase fractions sum to approximately 1.0
    double totalFraction = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      double phaseFraction = thermoSystem.getPhase(i).getBeta();
      if (Double.isNaN(phaseFraction) || phaseFraction < 0) {
        return false;
      }
      totalFraction += phaseFraction;
    }
    if (Math.abs(totalFraction - 1.0) > 0.01) {
      return false; // Phase fractions don't sum to 1
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<String> getSimulationValidationErrors() {
    List<String> errors = new ArrayList<String>();

    if (thermoSystem == null) {
      errors.add(getName() + ": No thermodynamic system - simulation not run");
      return errors;
    }

    if (Double.isNaN(thermoSystem.getTemperature())) {
      errors.add(getName() + ": Temperature is NaN");
    }
    if (Double.isNaN(thermoSystem.getPressure())) {
      errors.add(getName() + ": Pressure is NaN");
    }

    // Check phase fractions
    double totalFraction = 0.0;
    for (int i = 0; i < thermoSystem.getNumberOfPhases(); i++) {
      double phaseFraction = thermoSystem.getPhase(i).getBeta();
      if (Double.isNaN(phaseFraction)) {
        errors.add(getName() + ": Phase " + i + " fraction is NaN");
      } else if (phaseFraction < 0) {
        errors.add(getName() + ": Phase " + i + " has negative fraction: " + phaseFraction);
      }
      totalFraction += phaseFraction;
    }
    if (Math.abs(totalFraction - 1.0) > 0.01) {
      errors.add(
          String.format("%s: Phase fractions sum to %.3f, expected 1.0", getName(), totalFraction));
    }

    return errors;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Checks if the separator is operating within its valid envelope. For
   * separators, this means not
   * overloaded (gas velocity within design limits).
   * </p>
   */
  @Override
  public boolean isWithinOperatingEnvelope() {
    if (!isSimulationValid()) {
      return false;
    }

    // Single phase is valid (no separation needed)
    if (isSinglePhase()) {
      return true;
    }

    // Check if severely overloaded (e.g., > 200% utilization is likely invalid)
    double util = getCapacityUtilization();
    if (!Double.isNaN(util) && util > 2.0) {
      return false; // Severely overloaded - likely simulation issue
    }

    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getOperatingEnvelopeViolation() {
    if (!isSimulationValid()) {
      List<String> errors = getSimulationValidationErrors();
      if (!errors.isEmpty()) {
        return errors.get(0);
      }
      return "Invalid simulation result";
    }

    double util = getCapacityUtilization();
    if (!Double.isNaN(util) && util > 2.0) {
      return String.format("Severely overloaded: %.0f%% utilization (max design velocity exceeded)",
          util * 100);
    }

    return null;
  }

  /**
   * Gets whether capacity limits are enforced during simulation.
   *
   * @return true if capacity limits are enforced
   */
  public boolean isEnforceCapacityLimits() {
    return enforceCapacityLimits;
  }

  /**
   * Sets whether to enforce capacity limits during simulation. When enabled, the
   * simulation will
   * check if flow exceeds separator design capacity.
   *
   * @param enforce true to enforce capacity limits
   */
  public void setEnforceCapacityLimits(boolean enforce) {
    this.enforceCapacityLimits = enforce;
  }

  /**
   * Sizes the separator diameter based on the current flow conditions and design
   * K-factor. Uses the
   * Souders-Brown equation to calculate the required diameter.
   *
   * <p>
   * If no liquid phase is present, a default liquid density of 1000 kg/m³ is
   * assumed for sizing
   * purposes.
   * </p>
   *
   * @param safetyFactor design safety factor (typically 1.1-1.5)
   */
  public void sizeFromFlow(double safetyFactor) {
    if (thermoSystem == null) {
      return;
    }
    thermoSystem.initPhysicalProperties();

    // Get gas density and flow
    double gasDensity;
    double gasVolumeFlow;
    if (thermoSystem.hasPhaseType("gas")) {
      gasDensity = thermoSystem.getPhase("gas").getPhysicalProperties().getDensity();
      gasVolumeFlow = thermoSystem.getPhase("gas").getFlowRate("m3/sec") * safetyFactor;
    } else {
      // No gas phase - cannot size based on gas flow
      return;
    }

    // Use actual liquid density if available, otherwise default to 1000 kg/m³
    double liqDensity = DEFAULT_LIQUID_DENSITY_FOR_SIZING;
    if (thermoSystem.hasPhaseType("oil")) {
      liqDensity = thermoSystem.getPhase("oil").getPhysicalProperties().getDensity();
    } else if (thermoSystem.hasPhaseType("aqueous")) {
      liqDensity = thermoSystem.getPhase("aqueous").getPhysicalProperties().getDensity();
    }

    // Max gas velocity from Souders-Brown
    double maxVelocity = designGasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);

    // Required gas area
    double requiredGasArea = gasVolumeFlow / maxVelocity;

    // Calculate diameter (assuming gas area fraction based on orientation)
    double gasAreaFraction = orientation.equals("horizontal") ? (1.0 - designLiquidLevelFraction)
        : (1.0 - designLiquidLevelFraction);
    double requiredTotalArea = requiredGasArea / gasAreaFraction;
    double requiredDiameter = Math.sqrt(4.0 * requiredTotalArea / Math.PI);

    setInternalDiameter(requiredDiameter);
    logger.info("Separator " + getName() + " sized to diameter: "
        + String.format("%.3f", requiredDiameter) + " m");
  }

  // ==================== AutoSizeable Implementation ====================

  /** Flag indicating if separator has been auto-sized. */
  private boolean autoSized = false;

  /** Minimum default design volume flow in m3/hr for zero flow cases. */
  private static final double MIN_DEFAULT_VOLUME_FLOW = 100.0; // 100 m3/hr

  /** {@inheritDoc} */
  @Override
  public void autoSize(double safetyFactor) {
    // Initialize mechanical design if not already done
    if (separatorMechanicalDesign == null) {
      initMechanicalDesign();
    }

    // First read design specifications to get standard K-factors, Fg, etc.
    separatorMechanicalDesign.readDesignSpecifications();

    // Then override with safety factor
    separatorMechanicalDesign.setVolumeSafetyFactor(safetyFactor);

    // If user has set a specific K-factor on separator, use it
    // Otherwise use the value from design standards
    if (designGasLoadFactor != DEFAULT_DESIGN_GAS_LOAD_FACTOR) {
      separatorMechanicalDesign.setGasLoadFactor(designGasLoadFactor);
    } else {
      // Use K-factor from design standards, apply to separator for consistency
      designGasLoadFactor = separatorMechanicalDesign.getGasLoadFactor();
    }

    // Check for zero/very low flow and set minimum design values
    double gasFlow = 0.0;
    double liquidFlow = 0.0;
    if (thermoSystem != null) {
      if (thermoSystem.hasPhaseType("gas")) {
        gasFlow = thermoSystem.getPhase("gas").getFlowRate("m3/hr");
      }
      if (thermoSystem.hasPhaseType("oil")) {
        liquidFlow += thermoSystem.getPhase("oil").getFlowRate("m3/hr");
      }
      if (thermoSystem.hasPhaseType("aqueous")) {
        liquidFlow += thermoSystem.getPhase("aqueous").getFlowRate("m3/hr");
      }
    }

    // If both flows are zero or very small, set minimum design values
    boolean hasFlow = gasFlow > 1e-6 || liquidFlow > 1e-6;
    if (!hasFlow) {
      // Set minimum design values for zero-flow separator
      separatorMechanicalDesign.setMaxDesignGassVolumeFlow(MIN_DEFAULT_VOLUME_FLOW);
      separatorMechanicalDesign.setMaxDesignVolumeFlow(MIN_DEFAULT_VOLUME_FLOW);
      logger.info("Separator '{}' has zero flow, using minimum design values", getName());
    }

    // Use mechanical design for complete sizing (diameter, length, wall thickness,
    // etc.)
    // Note: we skip super.calcDesign() to avoid re-reading design specs
    separatorMechanicalDesign.performSizingCalculations();

    // Apply calculated dimensions back to separator
    separatorMechanicalDesign.setDesign();

    // Clear capacity constraints to force re-initialization with new design values
    capacityConstraints.clear();
    initializeCapacityConstraints();

    // Enable the gasLoadFactor constraint since autoSize uses K-factor sizing
    CapacityConstraint gasLoadConstraint = capacityConstraints.get("gasLoadFactor");
    if (gasLoadConstraint != null) {
      gasLoadConstraint.setEnabled(true);
    }

    autoSized = true;
    logger.info("Separator " + getName() + " auto-sized: diameter="
        + String.format("%.3f", internalDiameter) + " m, length="
        + String.format("%.3f", separatorLength) + " m");
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize() {
    autoSize(1.2);
  }

  /** {@inheritDoc} */
  @Override
  public void autoSize(String company, String trDocument) {
    // Set company standard on mechanical design to load correct design parameters
    if (separatorMechanicalDesign == null) {
      initMechanicalDesign();
    }

    // Set company-specific design standards which triggers database lookup
    separatorMechanicalDesign.setCompanySpecificDesignStandards(company);

    // Read design specifications from database (loads GasLoadFactor, Fg, etc.)
    separatorMechanicalDesign.readDesignSpecifications();

    // Get company-specific K-factor from design standards if available
    if (separatorMechanicalDesign.getDesignStandard().containsKey("separator process design")) {
      double companyKFactor = ((neqsim.process.mechanicaldesign.designstandards.SeparatorDesignStandard) separatorMechanicalDesign
          .getDesignStandard().get("separator process design")).getGasLoadFactor();
      if (companyKFactor > 0) {
        setDesignGasLoadFactor(companyKFactor);
      }
    }

    // Use default safety factor (could also be loaded from TR document)
    double safetyFactor = 1.2;

    // Size using company parameters
    sizeFromFlow(safetyFactor);
    autoSized = true;
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
    sb.append("=== Separator Auto-Sizing Report ===\n");
    sb.append("Equipment: ").append(getName()).append("\n");
    sb.append("Auto-sized: ").append(autoSized).append("\n");
    sb.append("Internal Diameter: ").append(String.format("%.3f m", internalDiameter)).append("\n");
    sb.append("Length: ").append(String.format("%.3f m", separatorLength)).append("\n");
    sb.append("Design K-factor: ").append(String.format("%.4f m/s", designGasLoadFactor))
        .append("\n");
    sb.append("Orientation: ").append(orientation).append("\n");

    if (thermoSystem != null && thermoSystem.hasPhaseType("gas")) {
      thermoSystem.initPhysicalProperties();
      double gasDensity = thermoSystem.getPhase("gas").getPhysicalProperties().getDensity();

      // Use actual liquid density if available, otherwise default to 1000 kg/m³
      double liqDensity = DEFAULT_LIQUID_DENSITY_FOR_SIZING;
      if (thermoSystem.hasPhaseType("oil")) {
        liqDensity = thermoSystem.getPhase("oil").getPhysicalProperties().getDensity();
      } else if (thermoSystem.hasPhaseType("aqueous")) {
        liqDensity = thermoSystem.getPhase("aqueous").getPhysicalProperties().getDensity();
      }

      double gasVolumeFlow = thermoSystem.getPhase("gas").getFlowRate("m3/hr");
      double maxVelocity = designGasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
      double actualVelocity = gasVolumeFlow / 3600.0
          / (Math.PI * Math.pow(internalDiameter / 2, 2) * (1.0 - designLiquidLevelFraction));

      sb.append("\n--- Operating Conditions ---\n");
      sb.append("Gas Volume Flow: ").append(String.format("%.1f m3/hr", gasVolumeFlow))
          .append("\n");
      sb.append("Gas Density: ").append(String.format("%.2f kg/m3", gasDensity)).append("\n");
      sb.append("Liquid Density: ").append(String.format("%.2f kg/m3", liqDensity));
      if (!thermoSystem.hasPhaseType("oil") && !thermoSystem.hasPhaseType("aqueous")) {
        sb.append(" (assumed - no liquid phase)");
      }
      sb.append("\n");
      sb.append("Max Gas Velocity: ").append(String.format("%.3f m/s", maxVelocity)).append("\n");
      sb.append("Actual Gas Velocity: ").append(String.format("%.3f m/s", actualVelocity))
          .append("\n");
      sb.append("K-factor Utilization: ")
          .append(String.format("%.1f%%", actualVelocity / maxVelocity * 100)).append("\n");
    }

    return sb.toString();
  }

  /** {@inheritDoc} */
  @Override
  public String getSizingReportJson() {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("equipmentName", getName());
    report.put("autoSized", autoSized);
    report.put("internalDiameter_m", internalDiameter);
    report.put("length_m", separatorLength);
    report.put("designKFactor_mps", designGasLoadFactor);
    report.put("orientation", orientation);

    if (thermoSystem != null && thermoSystem.hasPhaseType("gas")) {
      thermoSystem.initPhysicalProperties();
      double gasDensity = thermoSystem.getPhase("gas").getPhysicalProperties().getDensity();

      // Use actual liquid density if available, otherwise default to 1000 kg/m³
      double liqDensity = DEFAULT_LIQUID_DENSITY_FOR_SIZING;
      boolean liquidDensityAssumed = true;
      if (thermoSystem.hasPhaseType("oil")) {
        liqDensity = thermoSystem.getPhase("oil").getPhysicalProperties().getDensity();
        liquidDensityAssumed = false;
      } else if (thermoSystem.hasPhaseType("aqueous")) {
        liqDensity = thermoSystem.getPhase("aqueous").getPhysicalProperties().getDensity();
        liquidDensityAssumed = false;
      }

      double gasVolumeFlow = thermoSystem.getPhase("gas").getFlowRate("m3/hr");
      double maxVelocity = designGasLoadFactor * Math.sqrt((liqDensity - gasDensity) / gasDensity);
      double actualVelocity = gasVolumeFlow / 3600.0
          / (Math.PI * Math.pow(internalDiameter / 2, 2) * (1.0 - designLiquidLevelFraction));

      report.put("gasVolumeFlow_m3hr", gasVolumeFlow);
      report.put("gasDensity_kgm3", gasDensity);
      report.put("liquidDensity_kgm3", liqDensity);
      report.put("liquidDensityAssumed", liquidDensityAssumed);
      report.put("maxGasVelocity_mps", maxVelocity);
      report.put("actualGasVelocity_mps", actualVelocity);
      report.put("kFactorUtilization", actualVelocity / maxVelocity);
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(report);
  }

  /**
   * Initializes separator dimensions from mechanical design calculations. This
   * uses the mechanical
   * design module to size the separator based on flow.
   */
  public void initDesignFromFlow() {
    if (separatorMechanicalDesign == null) {
      initMechanicalDesign();
    }
    separatorMechanicalDesign.calcDesign();
    separatorMechanicalDesign.setDesign();

    // Copy design parameters back to separator
    this.designGasLoadFactor = separatorMechanicalDesign.getGasLoadFactor();
  }

  /**
   * <p>
   * Getter for the field <code>orientation</code>.
   * </p>
   *
   * @return the orientation
   */
  public String getOrientation() {
    return orientation;
  }

  /**
   * <p>
   * Setter for the field <code>orientation</code>.
   * </p>
   *
   * @param orientation the orientation to set
   */
  public void setOrientation(String orientation) {
    double levelFraction = getLiquidLevel();
    if (orientation != null) {
      this.orientation = orientation;
    }
    this.liquidLevel = clampLiquidHeight(levelFraction * getMaxLiquidHeight());
    updateHoldupVolumes();
  }

  /**
   * <p>
   * Calculates both gas and liquid fluid section areas for horizontal separators.
   * Results can be
   * used for volume calculation, gas superficial velocity, and settling time.
   * </p>
   *
   * @param level current liquid level inside the separator [m]
   * @return separator liquid area.
   */
  public double liquidArea(double level) {
    double lArea = 0;

    if (level <= 0) {
      return 0;
    } else if (level >= internalDiameter) {
      return sepCrossArea;
    }

    if (orientation.equals("horizontal")) {
      if (level < internalRadius) {
        double d = internalRadius - level;
        double theta = Math.acos(d / internalRadius);
        double a = internalRadius * Math.sin(theta);
        double triArea = a * d;
        double circArea = theta * Math.pow(internalRadius, 2);
        lArea = circArea - triArea;
        // System.out.printf("Area func: radius %f d %f theta %f a %f area %f\n",
        // internalRadius, d,
        // theta, a, lArea);
      } else if (level > internalRadius) {
        double d = level - internalRadius;
        double theta = Math.acos(d / internalRadius);
        double a = internalRadius * Math.sin(theta);
        double triArea = a * d;
        double circArea = (Math.PI - theta) * Math.pow(internalRadius, 2);
        lArea = circArea + triArea;
        // System.out.printf("Area func: radius %f d %f theta %f a %f area %f\n",
        // internalRadius, d,
        // theta, a, lArea);
      } else {
        lArea = 0.5 * Math.PI * Math.pow(internalRadius, 2);
      }
    } else if (orientation.equals("vertical")) {
      lArea = sepCrossArea;
    } else {
      lArea = 0;
    }

    return lArea;
  }

  /**
   * <p>
   * calculates liquid volume based on separator type.
   * </p>
   *
   * @return liquid level in the separator
   */
  public double calcLiquidVolume() {
    double lVolume = 0.0;

    if (orientation.equals("horizontal")) {
      lVolume = liquidArea(liquidLevel) * separatorLength;
      // System.out.printf("from function: LVL %f Area %f\n", liquidLevel,
      // liquidArea(liquidLevel));
    } else if (orientation.equals("vertical")) {
      lVolume = sepCrossArea * liquidLevel;
    } else {
      lVolume = 0;
    }

    return lVolume;
  }

  /**
   * Keeps cached gas/liquid holdup volumes aligned with current geometry and
   * level.
   */
  private void updateHoldupVolumes() {
    liquidVolume = calcLiquidVolume();
    enforceHeadspace();
  }

  protected void enforceHeadspace() {
    double rawGasVolume = separatorVolume - liquidVolume;
    double minGasVolume = getMinGasVolume();
    if (rawGasVolume < minGasVolume) {
      gasVolume = Math.max(minGasVolume, 0.0);
      if (separatorVolume > 0.0) {
        double adjustedLiquidVolume = Math.max(separatorVolume - gasVolume, 0.0);
        if (Math.abs(adjustedLiquidVolume - liquidVolume) > 1.0e-12) {
          liquidLevel = levelFromVolume(adjustedLiquidVolume);
          liquidVolume = calcLiquidVolume();
        } else {
          liquidVolume = adjustedLiquidVolume;
        }
      }
    } else {
      gasVolume = rawGasVolume;
    }
  }

  private double getMaxLiquidHeight() {
    if ("vertical".equalsIgnoreCase(orientation)) {
      return separatorLength > 0.0 ? separatorLength : internalDiameter;
    }
    return internalDiameter;
  }

  private double getMinGasVolume() {
    if (separatorVolume <= 0.0) {
      return 0.0;
    }
    double candidate = Math.max(separatorVolume * MIN_HEADSPACE_FRACTION, MIN_HEADSPACE_VOLUME);
    if (candidate >= separatorVolume) {
      return 0.5 * separatorVolume;
    }
    return candidate;
  }

  private double clampLiquidHeight(double height) {
    double maxHeight = getMaxLiquidHeight();
    if (maxHeight <= 0.0) {
      return 0.0;
    }
    if (Double.isNaN(height)) {
      return 0.0;
    }
    double clamped = Math.max(0.0, Math.min(height, maxHeight));
    if (clamped >= maxHeight) {
      double epsilon = Math.max(maxHeight * 1.0e-6, 1.0e-6);
      clamped = Math.max(0.0, maxHeight - epsilon);
    }
    return clamped;
  }

  /**
   * Calculates the total inner surface area of the separator, including shell and
   * heads.
   *
   * @return inner surface area in square meters
   */
  public double getInnerSurfaceArea() {
    if (internalRadius <= 0.0 || separatorLength <= 0.0) {
      return 0.0;
    }
    double shellArea = 2.0 * Math.PI * internalRadius * separatorLength;
    double headArea = 2.0 * sepCrossArea;
    return shellArea + headArea;
  }

  /**
   * Estimates the wetted inner surface area based on current liquid level and
   * orientation.
   *
   * <p>
   * For horizontal separators, the wetted area uses the circular segment defined
   * by the liquid
   * level to apportion the cylindrical shell and head areas. For vertical
   * separators, the wetted
   * area is the side area up to the current level plus the bottom head.
   *
   * @return wetted area in square meters
   */
  public double getWettedArea() {
    if (internalRadius <= 0.0 || separatorLength <= 0.0) {
      return 0.0;
    }

    if (orientation.equalsIgnoreCase("horizontal")) {
      double level = clampLiquidHeight(liquidLevel);
      if (level <= 0.0) {
        return 0.0;
      }

      double r = internalRadius;
      double cappedLevel = Math.min(level, 2.0 * r);
      double theta = 2.0 * Math.acos((r - cappedLevel) / r); // central angle of liquid segment

      double wettedShellArea = r * theta * separatorLength; // arc length * length
      double wettedHeadArea = 2.0 * liquidArea(cappedLevel);
      return wettedShellArea + wettedHeadArea;
    }

    if (orientation.equalsIgnoreCase("vertical")) {
      double level = clampLiquidHeight(liquidLevel);
      if (level <= 0.0) {
        return 0.0;
      }

      double wettedShellArea = 2.0 * Math.PI * internalRadius * level;
      double wettedHeadArea = sepCrossArea; // bottom head is always wetted when level > 0
      if (level >= separatorLength) {
        wettedHeadArea += sepCrossArea; // top head becomes wetted when full
      }
      return wettedShellArea + wettedHeadArea;
    }

    return 0.0;
  }

  /**
   * Estimates the unwetted (dry) area as the remaining inner area not in contact
   * with liquid.
   *
   * @return unwetted area in square meters
   */
  public double getUnwettedArea() {
    double wetted = getWettedArea();
    double total = getInnerSurfaceArea();
    if (total <= 0.0) {
      return 0.0;
    }
    return Math.max(total - wetted, 0.0);
  }

  /**
   * Evaluates fire exposure using the separator geometry and process conditions.
   *
   * @param config fire scenario configuration
   * @return aggregated fire exposure result
   */
  public SeparatorFireExposure.FireExposureResult evaluateFireExposure(
      SeparatorFireExposure.FireScenarioConfig config) {
    return SeparatorFireExposure.evaluate(this, config);
  }

  /**
   * Evaluates fire exposure using separator geometry and process conditions while
   * accounting for
   * flare radiation based on the real flaring heat duty.
   *
   * @param config               fire scenario configuration
   * @param flare                flare supplying heat duty and radiation
   *                             parameters
   * @param flareGroundDistanceM horizontal distance from flare base to separator
   *                             [m]
   * @return aggregated fire exposure result
   */
  public SeparatorFireExposure.FireExposureResult evaluateFireExposure(
      SeparatorFireExposure.FireScenarioConfig config, neqsim.process.equipment.flare.Flare flare,
      double flareGroundDistanceM) {
    return SeparatorFireExposure.evaluate(this, config, flare, flareGroundDistanceM);
  }

  /**
   * <p>
   * Estimates liquid level based on volume for horizontal separators using
   * bisection method.
   * Vertical separators too. tol and maxIter are bisection loop parameters.
   * </p>
   *
   * @param volumeTarget desired liquid volume to be held in the separator [m3]
   * @return liquid level in the separator
   */
  public double levelFromVolume(double volumeTarget) {
    double tol = 1e-4;
    int maxIter = 100;

    double headspace = getMinGasVolume();
    double maxLiquidVolume = separatorVolume > 0.0 ? Math.max(separatorVolume - headspace, 0.0) : 0.0;
    double limitedVolume = Math.max(0.0, Math.min(volumeTarget, maxLiquidVolume));

    double a = 0.0;
    double b = internalDiameter;

    if (orientation.equalsIgnoreCase("horizontal")) {
      if (internalDiameter <= 0.0) {
        return 0.0;
      }

      if (separatorLength <= 0.0) {
        return 0.0;
      }

      double areaTarget = limitedVolume / separatorLength;

      double fa = liquidArea(a) - areaTarget;
      double fb = liquidArea(b) - areaTarget;

      if (Math.abs(fa) < tol) {
        return a;
      }

      if (Math.abs(fb) < tol) {
        return b;
      }

      if (fa * fb > 0) {
        throw new IllegalArgumentException("No root in interval — check volumeTarget");
      }

      double h = 0.0;

      for (int i = 0; i < maxIter; i++) {
        h = 0.5 * (a + b);
        double fh = liquidArea(h) - areaTarget;

        if (Math.abs(fh) < tol) {
          return h;
        }

        if (fa * fh < 0) {
          b = h;
          fb = fh;
        } else {
          a = h;
          fa = fh;
        }
      }

      return 0.5 * (a + b);
    } else if (orientation.equalsIgnoreCase("vertical")) {
      if (sepCrossArea <= 0.0) {
        return 0.0;
      }

      return clampLiquidHeight(limitedVolume / sepCrossArea);
    } else {
      return 0.0;
    }
  }

  /**
   * <p>
   * Getter for the field <code>separatorLength</code>.
   * </p>
   *
   * @return the separatorLength
   */

  public double getSeparatorLength() {
    return separatorLength;
  }

  /**
   * <p>
   * Setter for the field <code>separatorLength</code>.
   * </p>
   *
   * @param separatorLength the separatorLength to set
   */
  public void setSeparatorLength(double separatorLength) {
    double levelFraction = getLiquidLevel();
    this.separatorLength = separatorLength;
    this.separatorVolume = sepCrossArea * separatorLength;
    this.liquidLevel = clampLiquidHeight(levelFraction * getMaxLiquidHeight());
    updateHoldupVolumes();
  }

  /**
   * <p>
   * Getter for the field <code>separatorSection</code>.
   * </p>
   *
   * @param i a int
   * @return a
   *         {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection}
   *         object
   */
  public SeparatorSection getSeparatorSection(int i) {
    return separatorSection.get(i);
  }

  /**
   * <p>
   * Getter for the field <code>separatorSection</code>.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @return a
   *         {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection}
   *         object
   */
  public SeparatorSection getSeparatorSection(String name) {
    for (SeparatorSection sec : separatorSection) {
      if (sec.getName().equals(name)) {
        return sec;
      }
    }
    // System.out.println("no section with name: " + name + " found.....");
    return null;
  }

  /**
   * <p>
   * getSeparatorSections.
   * </p>
   *
   * @return a {@link java.util.ArrayList} object
   */
  public ArrayList<SeparatorSection> getSeparatorSections() {
    return separatorSection;
  }

  /**
   * <p>
   * addSeparatorSection.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param type a {@link java.lang.String} object
   */
  public void addSeparatorSection(String name, String type) {
    if (type.equalsIgnoreCase("vane")) {
      separatorSection.add(new SeparatorSection(name, type, this));
    } else if (type.equalsIgnoreCase("meshpad")) {
      separatorSection.add(new MeshSection(name, type, this));
    } else if (type.equalsIgnoreCase("manway")) {
      separatorSection.add(new ManwaySection(name, type, this));
    } else if (type.equalsIgnoreCase("valve")) {
      separatorSection.add(new ValveSection(name, type, this));
    } else if (type.equalsIgnoreCase("nozzle")) {
      separatorSection.add(new NozzleSection(name, type, this));
    } else {
      separatorSection.add(new SeparatorSection(name, type, this));
    }
  }

  /**
   * <p>
   * Getter for the field <code>designLiquidLevelFraction</code>.
   * </p>
   *
   * @return the designGasLevelFraction
   */
  public double getDesignLiquidLevelFraction() {
    return designLiquidLevelFraction;
  }

  /**
   * <p>
   * Setter for the field <code>designLiquidLevelFraction</code>.
   * </p>
   *
   * @param designLiquidLevelFraction a double
   */
  public void setDesignLiquidLevelFraction(double designLiquidLevelFraction) {
    this.designLiquidLevelFraction = designLiquidLevelFraction;
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    return getThermoSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entrop = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      if (inletStreamMixer.getStream(i).getFlowRate(unit) > 1e-10) {
        inletStreamMixer.getStream(i).getFluid().init(3);
        entrop += inletStreamMixer.getStream(i).getFluid().getEntropy();
      }
    }

    double liquidEntropy = 0.0;
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      try {
        getLiquidOutStream().getThermoSystem().init(3);
        liquidEntropy = getLiquidOutStream().getThermoSystem().getEntropy();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }

    double gasEntropy = 0.0;
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasEntropy = getGasOutStream().getThermoSystem().getEntropy();
    }

    return liquidEntropy + gasEntropy - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double flow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      if (inletStreamMixer.getStream(i).getFlowRate(unit) > 1e-10) {
        inletStreamMixer.getStream(i).getFluid().init(3);
        flow += inletStreamMixer.getStream(i).getFluid().getFlowRate(unit);
      }
    }

    double liquidFlow = 0.0;
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      getLiquidOutStream().getThermoSystem().init(3);
      liquidFlow = getLiquidOutStream().getThermoSystem().getFlowRate(unit);
      if (liquidFlow < 1e-10) {
        liquidFlow = 0.0;
      }
    }

    double gasFlow = 0.0;
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasFlow = getGasOutStream().getThermoSystem().getFlowRate(unit);
      if (gasFlow < 1e-10) {
        gasFlow = 0.0;
      }
    }

    return liquidFlow + gasFlow - flow;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    double exergy = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      if (inletStreamMixer.getStream(i).getFlowRate(unit) > 1e-10) {
        inletStreamMixer.getStream(i).getFluid().init(3);
        exergy += inletStreamMixer.getStream(i).getFluid().getExergy(surroundingTemperature, unit);
      }
    }

    double liquidExergy = 0.0;
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      getLiquidOutStream().getThermoSystem().init(3);
      liquidExergy = getLiquidOutStream().getThermoSystem().getExergy(surroundingTemperature, unit);
    }

    double gasExergy = 0.0;
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasExergy = getGasOutStream().getThermoSystem().getExergy(surroundingTemperature, unit);
    }

    return liquidExergy + gasExergy - exergy;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(designLiquidLevelFraction, efficiency,
        gasCarryunderFraction, gasInLiquid, gasInLiquidSpec, gasOutStream, gasSystem, gasVolume,
        inletStreamMixer, internalDiameter, liquidCarryoverFraction, liquidLevel, liquidOutStream,
        liquidSystem, liquidVolume, numberOfInputStreams, oilInGas, oilInGasSpec, orientation,
        pressureDrop, separatorLength, separatorSection, specifiedStream, thermoSystem,
        thermoSystem2, thermoSystemCloned, waterInGas, waterInGasSpec, waterSystem);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Separator other = (Separator) obj;
    return Double.doubleToLongBits(designLiquidLevelFraction) == Double
        .doubleToLongBits(other.designLiquidLevelFraction)
        && Double.doubleToLongBits(efficiency) == Double.doubleToLongBits(other.efficiency)
        && Double.doubleToLongBits(gasCarryunderFraction) == Double
            .doubleToLongBits(other.gasCarryunderFraction)
        && Double.doubleToLongBits(gasInLiquid) == Double.doubleToLongBits(other.gasInLiquid)
        && Objects.equals(gasInLiquidSpec, other.gasInLiquidSpec)
        && Objects.equals(gasOutStream, other.gasOutStream)
        && Objects.equals(gasSystem, other.gasSystem)
        && Double.doubleToLongBits(gasVolume) == Double.doubleToLongBits(other.gasVolume)
        && Objects.equals(inletStreamMixer, other.inletStreamMixer)
        && Double.doubleToLongBits(internalDiameter) == Double
            .doubleToLongBits(other.internalDiameter)
        && Double.doubleToLongBits(liquidCarryoverFraction) == Double
            .doubleToLongBits(other.liquidCarryoverFraction)
        && Double.doubleToLongBits(liquidLevel) == Double.doubleToLongBits(other.liquidLevel)
        && Objects.equals(liquidOutStream, other.liquidOutStream)
        && Objects.equals(liquidSystem, other.liquidSystem)
        && Double.doubleToLongBits(liquidVolume) == Double.doubleToLongBits(other.liquidVolume)
        && numberOfInputStreams == other.numberOfInputStreams
        && Double.doubleToLongBits(oilInGas) == Double.doubleToLongBits(other.oilInGas)
        && Objects.equals(oilInGasSpec, other.oilInGasSpec)
        && Objects.equals(orientation, other.orientation)
        && Double.doubleToLongBits(pressureDrop) == Double.doubleToLongBits(other.pressureDrop)
        && Double.doubleToLongBits(separatorLength) == Double
            .doubleToLongBits(other.separatorLength)
        && Objects.equals(separatorSection, other.separatorSection)
        && Objects.equals(specifiedStream, other.specifiedStream)
        && Objects.equals(thermoSystem, other.thermoSystem)
        && Objects.equals(thermoSystem2, other.thermoSystem2)
        && Objects.equals(thermoSystemCloned, other.thermoSystemCloned)
        && Double.doubleToLongBits(waterInGas) == Double.doubleToLongBits(other.waterInGas)
        && Objects.equals(waterInGasSpec, other.waterInGasSpec)
        && Objects.equals(waterSystem, other.waterSystem);
  }

  /**
   * <p>
   * getFeedStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getFeedStream() {
    return inletStreamMixer.getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new SeparatorResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    SeparatorResponse res = new SeparatorResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * Set heat input to the separator (e.g., from flare radiation, external
   * heating).
   *
   * @param heatInput heat duty in watts
   */
  public void setHeatInput(double heatInput) {
    this.heatInput = heatInput;
    this.heatInputUnit = "W";
    this.setHeatInput = true;
  }

  /**
   * Set heat input to the separator with specified unit.
   *
   * @param heatInput heat duty value
   * @param unit      heat duty unit (W, kW, MW, J/s, etc.)
   */
  public void setHeatInput(double heatInput, String unit) {
    this.heatInputUnit = unit;
    // Convert to watts for internal calculations
    switch (unit.toLowerCase()) {
      case "kw":
        this.heatInput = heatInput * 1000.0;
        break;
      case "mw":
        this.heatInput = heatInput * 1.0e6;
        break;
      case "j/s":
      case "w":
      default:
        this.heatInput = heatInput;
        break;
    }
    this.setHeatInput = true;
  }

  /**
   * Set heat duty (alias for setHeatInput).
   *
   * @param heatDuty heat duty in watts
   */
  public void setHeatDuty(double heatDuty) {
    setHeatInput(heatDuty);
  }

  /**
   * Set heat duty with unit (alias for setHeatInput).
   *
   * @param heatDuty heat duty value
   * @param unit     heat duty unit
   */
  public void setHeatDuty(double heatDuty, String unit) {
    setHeatInput(heatDuty, unit);
  }

  /**
   * Set heat duty (alias preserved for compatibility with energy-stream style
   * naming).
   *
   * @param heatDuty heat duty in watts
   */
  public void setDuty(double heatDuty) {
    setHeatInput(heatDuty);
  }

  /**
   * Set heat duty with unit (alias preserved for compatibility with energy-stream
   * style naming).
   *
   * @param heatDuty heat duty value
   * @param unit     heat duty unit
   */
  public void setDuty(double heatDuty, String unit) {
    setHeatInput(heatDuty, unit);
  }

  /**
   * Get heat input in watts.
   *
   * @return heat input in watts
   */
  public double getHeatInput() {
    return heatInput;
  }

  /**
   * Get heat input in specified unit.
   *
   * @param unit desired unit (W, kW, MW)
   * @return heat input in specified unit
   */
  public double getHeatInput(String unit) {
    switch (unit.toLowerCase()) {
      case "kw":
        return heatInput / 1000.0;
      case "mw":
        return heatInput / 1.0e6;
      case "j/s":
      case "w":
      default:
        return heatInput;
    }
  }

  /**
   * Get heat duty (alias for getHeatInput).
   *
   * @return heat duty in watts
   */
  public double getHeatDuty() {
    return getHeatInput();
  }

  /**
   * Get heat duty in specified unit.
   *
   * @param unit desired unit
   * @return heat duty in specified unit
   */
  public double getHeatDuty(String unit) {
    return getHeatInput(unit);
  }

  /**
   * Check if heat input is set.
   *
   * @return true if heat input is explicitly set
   */
  public boolean isSetHeatInput() {
    return setHeatInput;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit) {
    return getExergyChange(unit, 288.15);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For separators, capacity duty is defined as the gas outlet volumetric flow
   * rate in m³/hr. This
   * is used in conjunction with {@link #getCapacityMax()} for bottleneck analysis
   * via
   * {@link neqsim.process.processmodel.ProcessSystem#getBottleneck()}.
   * </p>
   *
   * @return gas outlet flow rate in m³/hr
   */
  @Override
  public double getCapacityDuty() {
    return getGasOutStream().getFlowRate("m3/hr");
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * For separators, maximum capacity is defined by the mechanical design's
   * maximum gas volume flow
   * in m³/hr. If not set, the value is derived from the gas load factor design:
   * {@code designGasLoadFactor * crossSectionalArea * 3600}.
   * </p>
   *
   * @return maximum design gas volume flow in m³/hr
   * @see neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign#getMaxDesignGassVolumeFlow()
   */
  @Override
  public double getCapacityMax() {
    double mechMax = getMechanicalDesign().getMaxDesignGassVolumeFlow();
    if (mechMax > 1e-12) {
      return mechMax;
    }
    // Fall back to gas load factor based capacity if mechanical design not set
    if (designGasLoadFactor > 0 && internalDiameter > 0) {
      double area = Math.PI * Math.pow(internalDiameter / 2.0, 2);
      return designGasLoadFactor * area * 3600.0; // Convert m/s * m² to m³/hr
    }
    return 0.0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns state vector containing:
   * <ul>
   * <li>pressure - Separator pressure [bar]</li>
   * <li>temperature - Separator temperature [K]</li>
   * <li>liquid_level - Liquid level fraction [0-1]</li>
   * <li>gas_density - Gas phase density [kg/m³]</li>
   * <li>liquid_density - Liquid phase density [kg/m³]</li>
   * <li>gas_flow - Gas outlet flow [kg/s]</li>
   * <li>liquid_flow - Liquid outlet flow [kg/s]</li>
   * <li>gas_load_factor - Gas load factor [-]</li>
   * </ul>
   */
  @Override
  public StateVector getStateVector() {
    StateVector state = new StateVector();

    // Basic thermodynamic state
    state.add("pressure", getPressure(), 0.0, 200.0, "bar");
    state.add("temperature", getTemperature(), 200.0, 500.0, "K");

    // Level
    state.add("liquid_level", getLiquidLevel(), 0.0, 1.0, "fraction");

    // Phase properties
    if (thermoSystem != null) {
      if (thermoSystem.hasPhaseType("gas")) {
        state.add("gas_density", thermoSystem.getPhase("gas").getDensity("kg/m3"), 0.0, 300.0,
            "kg/m3");
      }
      if (thermoSystem.hasPhaseType("oil")) {
        state.add("liquid_density", thermoSystem.getPhase("oil").getDensity("kg/m3"), 400.0, 1000.0,
            "kg/m3");
      } else if (thermoSystem.hasPhaseType("aqueous")) {
        state.add("liquid_density", thermoSystem.getPhase("aqueous").getDensity("kg/m3"), 900.0,
            1100.0, "kg/m3");
      }
    }

    // Flow rates
    if (gasOutStream != null) {
      state.add("gas_flow", gasOutStream.getFlowRate("kg/sec"), 0.0, 100.0, "kg/s");
    }
    if (liquidOutStream != null) {
      state.add("liquid_flow", liquidOutStream.getFlowRate("kg/sec"), 0.0, 100.0, "kg/s");
    }

    // Performance indicator
    state.add("gas_load_factor", getGasLoadFactor(), 0.0, 2.0, "factor");

    return state;
  }

  /*
   * private class SeparatorReport extends Object{ public Double gasLoadFactor;
   * SeparatorReport(){
   * gasLoadFactor = getGasLoadFactor(); } }
   *
   * public SeparatorReport getReport(){ return this.new SeparatorReport(); }
   */

  /**
   * {@inheritDoc}
   *
   * <p>
   * Validates the separator setup before execution. Checks that:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>At least one inlet stream is connected</li>
   * <li>Separator dimensions are positive</li>
   * <li>Liquid level is within valid range</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  @Override
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result = new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name (from interface default)
    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Separator has no name",
          "Set separator name in constructor: new Separator(\"MySeparator\")");
    }

    // Check: At least one inlet stream is connected
    if (numberOfInputStreams == 0) {
      result.addError("stream", "No inlet stream connected",
          "Connect inlet stream: separator.setInletStream(stream) or separator.addStream(stream)");
    }

    // Check: Inlet stream has valid fluid
    if (numberOfInputStreams > 0 && thermoSystem == null) {
      result.addError("stream", "Inlet stream has no fluid system",
          "Ensure inlet stream has a valid thermodynamic system");
    }

    // Check: Separator dimensions are positive
    if (separatorLength <= 0) {
      result.addError("dimensions", "Separator length must be positive: " + separatorLength + " m",
          "Set positive length: separator.setSeparatorLength(5.0)");
    }

    if (internalDiameter <= 0) {
      result.addError("dimensions",
          "Separator diameter must be positive: " + internalDiameter + " m",
          "Set positive diameter: separator.setInternalDiameter(1.0)");
    }

    // Check: Liquid level is within valid range (0-1)
    if (liquidLevel < 0 || liquidLevel > internalDiameter) {
      result.addWarning("level", "Liquid level may be outside valid range: " + liquidLevel
          + " m (diameter: " + internalDiameter + " m)",
          "Set liquid level between 0 and separator diameter");
    }

    // Check: Pressure drop is non-negative
    if (pressureDrop < 0) {
      result.addWarning("pressure", "Negative pressure drop: " + pressureDrop + " bar",
          "Pressure drop should typically be >= 0");
    }

    // Check: Efficiency is in valid range
    if (efficiency < 0 || efficiency > 1) {
      result.addError("efficiency", "Efficiency must be between 0 and 1: " + efficiency,
          "Set valid efficiency: separator.setEfficiency(0.95)");
    }

    return result;
  }

  // ==================== CapacityConstrainedEquipment Interface Implementation
  // ====================

  /**
   * Initializes default capacity constraints for this separator.
   *
   * <p>
   * Creates constraints for gas load factor based on the separator's design
   * parameters. Additional
   * constraints like liquid residence time can be added after construction.
   * </p>
   * 
   * <p>
   * Note: All separator constraints (gas load factor, K-value, droplet cut size,
   * inlet momentum, retention times) are disabled by default for backwards
   * compatibility with the optimizer. Use {@link #useEquinorConstraints()},
   * {@link #useAPIConstraints()}, or {@link #useAllConstraints()} to enable them
   * explicitly for capacity analysis.
   * </p>
   */
  protected void initializeCapacityConstraints() {
    // Gas load factor constraint (legacy) - disabled by default for backwards
    // compatibility with optimizer which uses getLiquidLevel() for separators.
    // Enable via useGasCapacityConstraints(), useEquinorConstraints(), or
    // useAllConstraints().
    addCapacityConstraint(StandardConstraintType.SEPARATOR_GAS_LOAD_FACTOR.createConstraint()
        .setDesignValue(designGasLoadFactor).setWarningThreshold(0.9).setEnabled(false)
        .setValueSupplier(() -> {
          try {
            return getGasLoadFactor();
          } catch (Exception e) {
            return 0.0;
          }
        }));

    // K-value constraint per TR3500 (limit 0.15 m/s)
    // Disabled by default - enable via useEquinorConstraints() or
    // useAllConstraints()
    addCapacityConstraint(StandardConstraintType.SEPARATOR_K_VALUE.createConstraint()
        .setDesignValue(DEFAULT_K_VALUE_LIMIT).setMaxValue(DEFAULT_K_VALUE_LIMIT)
        .setWarningThreshold(0.9).setEnabled(false).setValueSupplier(() -> {
          try {
            return calcKValueAtHLL();
          } catch (Exception e) {
            return 0.0;
          }
        }));

    // Droplet cut size constraint per TR3500 (limit 150 µm)
    // Disabled by default - enable via useEquinorConstraints() or
    // useAllConstraints()
    addCapacityConstraint(StandardConstraintType.SEPARATOR_DROPLET_CUTSIZE.createConstraint()
        .setDesignValue(DEFAULT_DROPLET_CUTSIZE_LIMIT * 1e6)
        .setMaxValue(DEFAULT_DROPLET_CUTSIZE_LIMIT * 1e6).setWarningThreshold(0.9)
        .setEnabled(false).setValueSupplier(() -> {
          try {
            return calcDropletCutSizeAtHLL() * 1e6; // Convert to µm
          } catch (Exception e) {
            return 0.0;
          }
        }));

    // Inlet momentum constraint (limit 16000 Pa)
    // Disabled by default - enable via useEquinorConstraints() or
    // useAllConstraints()
    addCapacityConstraint(StandardConstraintType.SEPARATOR_INLET_MOMENTUM.createConstraint()
        .setDesignValue(DEFAULT_INLET_MOMENTUM_LIMIT).setMaxValue(DEFAULT_INLET_MOMENTUM_LIMIT)
        .setWarningThreshold(0.9).setEnabled(false).setValueSupplier(() -> {
          try {
            return calcInletMomentumFlux();
          } catch (Exception e) {
            return 0.0;
          }
        }));

    // Oil retention time constraint per API 12J (minimum 3 minutes)
    // Note: For retention time, we want ABOVE the minimum, so utilization is
    // inverted
    // Disabled by default - enable via useEquinorConstraints() or
    // useAllConstraints()
    addCapacityConstraint(StandardConstraintType.SEPARATOR_OIL_RETENTION_TIME.createConstraint()
        .setDesignValue(DEFAULT_MIN_OIL_RETENTION_TIME).setMinValue(DEFAULT_MIN_OIL_RETENTION_TIME)
        .setWarningThreshold(1.2).setEnabled(false) // Warn if close to minimum
        .setValueSupplier(() -> {
          try {
            double retention = calcOilRetentionTime();
            return Double.isInfinite(retention) ? 999.0 : retention;
          } catch (Exception e) {
            return 999.0; // Return high value if no oil
          }
        }));

    // Water retention time constraint per API 12J (minimum 3 minutes)
    // Disabled by default - enable via useEquinorConstraints() or
    // useAllConstraints()
    addCapacityConstraint(StandardConstraintType.SEPARATOR_WATER_RETENTION_TIME.createConstraint()
        .setDesignValue(DEFAULT_MIN_WATER_RETENTION_TIME)
        .setMinValue(DEFAULT_MIN_WATER_RETENTION_TIME).setWarningThreshold(1.2).setEnabled(false)
        .setValueSupplier(() -> {
          try {
            double retention = calcWaterRetentionTime();
            return Double.isInfinite(retention) ? 999.0 : retention;
          } catch (Exception e) {
            return 999.0; // Return high value if no water
          }
        }));
  }

  /**
   * Set custom K-value limit for the separator constraint.
   *
   * @param limit K-value limit in m/s (default 0.15 per TR3500)
   */
  public void setKValueLimit(double limit) {
    CapacityConstraint constraint = capacityConstraints.get("kValue");
    if (constraint != null) {
      constraint.setDesignValue(limit).setMaxValue(limit);
    }
  }

  /**
   * Set custom droplet cut size limit for the separator constraint.
   *
   * @param limitMicrons droplet cut size limit in micrometers (default 150 µm per
   *                     TR3500)
   */
  public void setDropletCutSizeLimit(double limitMicrons) {
    CapacityConstraint constraint = capacityConstraints.get("dropletCutSize");
    if (constraint != null) {
      constraint.setDesignValue(limitMicrons).setMaxValue(limitMicrons);
    }
  }

  /**
   * Set custom inlet momentum limit for the separator constraint.
   *
   * @param limitPa momentum flux limit in Pa (default 16000 Pa per Equinor
   *                Revamp)
   */
  public void setInletMomentumLimit(double limitPa) {
    CapacityConstraint constraint = capacityConstraints.get("inletMomentum");
    if (constraint != null) {
      constraint.setDesignValue(limitPa).setMaxValue(limitPa);
    }
  }

  /**
   * Set custom minimum oil retention time for the separator constraint.
   *
   * @param minMinutes minimum oil retention time in minutes (default 3 min per
   *                   API 12J)
   */
  public void setMinOilRetentionTime(double minMinutes) {
    CapacityConstraint constraint = capacityConstraints.get("oilRetentionTime");
    if (constraint != null) {
      constraint.setDesignValue(minMinutes).setMinValue(minMinutes);
    }
  }

  /**
   * Set custom minimum water retention time for the separator constraint.
   *
   * @param minMinutes minimum water retention time in minutes (default 3 min per
   *                   API 12J)
   */
  public void setMinWaterRetentionTime(double minMinutes) {
    CapacityConstraint constraint = capacityConstraints.get("waterRetentionTime");
    if (constraint != null) {
      constraint.setDesignValue(minMinutes).setMinValue(minMinutes);
    }
  }

  /**
   * Get a summary of all constraint utilizations.
   *
   * @return constraint summary as formatted string
   */
  public String getConstraintSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Separator Constraint Summary: ").append(getName()).append(" ===\n");
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (!constraint.isEnabled()) {
        continue;
      }
      double util = constraint.getUtilization();
      String status = constraint.isViolated() ? "VIOLATED"
          : (constraint.isNearLimit() ? "WARNING" : "OK");
      sb.append(String.format("%s: %.2f %s (%.1f%% utilization) - %s%n", constraint.getName(),
          constraint.getCurrentValue(), constraint.getUnit(), util * 100, status));
    }
    return sb.toString();
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
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (!constraint.isEnabled()) {
        continue;
      }
      double util = constraint.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
        bottleneck = constraint;
      }
    }
    return bottleneck;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (constraint.isEnabled() && constraint.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (constraint.isEnabled() && constraint.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    double maxUtil = 0.0;
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      if (!constraint.isEnabled()) {
        continue;
      }
      double util = constraint.getUtilization();
      if (!Double.isNaN(util) && util > maxUtil) {
        maxUtil = util;
      }
      // Log constraint details if utilization is unusually high
      if (constraint.isEnabled() && util > 5.0) {
        logger.warn("Separator {} constraint {} has high utilization: {}%, "
            + "currentValue={}, designValue={}",
            getName(), constraint.getName(), util * 100,
            constraint.getCurrentValue(), constraint.getDesignValue());
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

  // ==================== Constraint Selection Methods ====================

  /**
   * Enable only the specified constraints by name. All other constraints are
   * removed.
   * 
   * <p>
   * Available constraint names:
   * <ul>
   * <li>"gasLoadFactor" - Gas load factor (K-factor)</li>
   * <li>"kValue" - K-value (Souders-Brown) at HLL per TR3500</li>
   * <li>"dropletCutSize" - Droplet cut size per TR3500</li>
   * <li>"inletMomentum" - Inlet nozzle momentum flux</li>
   * <li>"oilRetentionTime" - Oil retention time per API 12J</li>
   * <li>"waterRetentionTime" - Water retention time per API 12J</li>
   * </ul>
   * </p>
   *
   * @param constraintNames names of constraints to enable (all others will be
   *                        disabled)
   */
  public void useConstraints(String... constraintNames) {
    // Convert to set for efficient lookup
    java.util.Set<String> enabled = new java.util.HashSet<String>(
        java.util.Arrays.asList(constraintNames));
    // Enable only the requested constraints, disable others
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      entry.getValue().setEnabled(enabled.contains(entry.getKey()));
    }
  }

  /**
   * Enable specific constraints by name (additive - doesn't disable others).
   * Use this to add constraints to the existing set without clearing others.
   *
   * @param constraintNames names of constraints to enable
   */
  public void enableConstraints(String... constraintNames) {
    // Ensure constraints are initialized
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    for (String name : constraintNames) {
      CapacityConstraint constraint = capacityConstraints.get(name);
      if (constraint != null) {
        constraint.setEnabled(true);
      }
    }
  }

  /**
   * Disable specific constraints by name.
   *
   * @param constraintNames names of constraints to disable
   */
  public void disableConstraints(String... constraintNames) {
    // Ensure constraints are initialized
    if (capacityConstraints.isEmpty()) {
      initializeCapacityConstraints();
    }
    for (String name : constraintNames) {
      CapacityConstraint constraint = capacityConstraints.get(name);
      if (constraint != null) {
        constraint.setEnabled(false);
      }
    }
  }

  /**
   * Use Equinor TR3500 constraints (K-value, droplet cut size, inlet momentum,
   * retention times).
   * Enables these constraints in addition to the default gas load factor.
   */
  public void useEquinorConstraints() {
    enableConstraints("gasLoadFactor", "kValue", "dropletCutSize", "inletMomentum",
        "oilRetentionTime", "waterRetentionTime");
  }

  /**
   * Use API 12J constraints (K-value, retention times).
   * Enables these constraints in addition to the default gas load factor.
   */
  public void useAPIConstraints() {
    enableConstraints("gasLoadFactor", "kValue", "oilRetentionTime", "waterRetentionTime");
  }

  /**
   * Use gas scrubber constraints only (gas load factor, K-value).
   * For gas scrubbers, K-value is the primary performance metric as liquid
   * retention is not relevant - the focus is on removing liquid droplets from
   * gas.
   * This enables K-value in addition to the default gas load factor.
   */
  public void useGasScrubberConstraints() {
    enableConstraints("gasLoadFactor", "kValue");
  }

  /**
   * Use gas capacity constraints (gas load factor, K-value, droplet cut size,
   * inlet momentum).
   */
  public void useGasCapacityConstraints() {
    enableConstraints("gasLoadFactor", "kValue", "dropletCutSize", "inletMomentum");
  }

  /**
   * Use liquid capacity constraints (retention times).
   */
  public void useLiquidCapacityConstraints() {
    enableConstraints("oilRetentionTime", "waterRetentionTime");
  }

  /**
   * Use all available constraints.
   * Enables all constraints including the new TR3500/API 12J constraints.
   */
  public void useAllConstraints() {
    for (CapacityConstraint constraint : capacityConstraints.values()) {
      constraint.setEnabled(true);
    }
  }

  /**
   * Disable a specific constraint by name.
   *
   * @param constraintName name of constraint to disable
   * @return true if constraint was found and disabled
   */
  public boolean disableConstraint(String constraintName) {
    CapacityConstraint constraint = capacityConstraints.get(constraintName);
    if (constraint != null) {
      constraint.setEnabled(false);
      return true;
    }
    return false;
  }

  /**
   * Check if a specific constraint is enabled.
   *
   * @param constraintName name of constraint to check
   * @return true if constraint exists and is enabled
   */
  public boolean isConstraintEnabled(String constraintName) {
    CapacityConstraint constraint = capacityConstraints.get(constraintName);
    return constraint != null && constraint.isEnabled();
  }

  /**
   * Get list of all enabled constraint names.
   *
   * @return list of constraint names that are enabled
   */
  public java.util.List<String> getEnabledConstraintNames() {
    java.util.List<String> enabled = new java.util.ArrayList<String>();
    for (Map.Entry<String, CapacityConstraint> entry : capacityConstraints.entrySet()) {
      if (entry.getValue().isEnabled()) {
        enabled.add(entry.getKey());
      }
    }
    return enabled;
  }

  // ==================== Separator Performance Calculations ====================
  // These methods calculate operating performance parameters as used in
  // separator rating studies per Equinor TR3500 and API 12J standards.

  /**
   * Default K-value limit per Equinor TR3500 SR-50535 [m/s].
   * K-value must be less than this limit related to HLL.
   */
  public static final double DEFAULT_K_VALUE_LIMIT = 0.15;

  /**
   * Default droplet cut size limit per Equinor TR3500 SR-50535 [m].
   * Droplet cut size must be less than 150 µm (150e-6 m).
   */
  public static final double DEFAULT_DROPLET_CUTSIZE_LIMIT = 150e-6;

  /**
   * Default inlet momentum flux limit per Equinor Revamp Limit [Pa].
   */
  public static final double DEFAULT_INLET_MOMENTUM_LIMIT = 16000.0;

  /**
   * Default minimum oil retention time per API 12J [minutes].
   * Typically 3-5 minutes for oil gravities above 35° API.
   */
  public static final double DEFAULT_MIN_OIL_RETENTION_TIME = 3.0;

  /**
   * Default minimum water retention time per API 12J [minutes].
   */
  public static final double DEFAULT_MIN_WATER_RETENTION_TIME = 3.0;

  /**
   * Calculate the gas area above the specified liquid level for horizontal
   * separator.
   *
   * @param liquidLevelHeight liquid level height in meters
   * @return gas area in m²
   */
  public double calcGasAreaAboveLevel(double liquidLevelHeight) {
    if (!orientation.equalsIgnoreCase("horizontal")) {
      // For vertical separator, gas area is above the liquid
      return sepCrossArea;
    }
    double r = internalDiameter / 2.0;
    double h = Math.min(liquidLevelHeight, internalDiameter);
    if (h <= 0) {
      return sepCrossArea; // Full cross-section is gas
    }
    if (h >= internalDiameter) {
      return 0.0; // No gas area
    }
    // Gas area = total area - liquid area
    return sepCrossArea - liquidArea(h);
  }

  /**
   * Calculate gas velocity above the specified liquid level.
   *
   * @param liquidLevelHeight liquid level height in meters (e.g., HLL)
   * @return gas velocity in m/s
   */
  public double calcGasVelocityAboveLevel(double liquidLevelHeight) {
    if (thermoSystem == null || !thermoSystem.hasPhaseType("gas")) {
      return 0.0;
    }
    double gasArea = calcGasAreaAboveLevel(liquidLevelHeight);
    if (gasArea <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    double gasVolumeFlow = thermoSystem.getPhase(0).getFlowRate("m3/sec");
    return gasVolumeFlow / gasArea;
  }

  /**
   * Calculate the K-value (Souders-Brown factor) above the specified liquid
   * level.
   * K = v_gas * sqrt(ρ_gas / (ρ_liquid - ρ_gas))
   *
   * <p>
   * Per Equinor TR3500 SR-50535: K-value must be less than 0.15 m/s related to
   * HLL.
   * </p>
   *
   * @param liquidLevelHeight liquid level height in meters (typically HLL)
   * @return K-value in m/s
   */
  public double calcKValue(double liquidLevelHeight) {
    if (thermoSystem == null || thermoSystem.getNumberOfPhases() < 2) {
      return 0.0;
    }
    if (!thermoSystem.hasPhaseType("gas")) {
      return 0.0;
    }

    double gasDensity = thermoSystem.getPhase(0).getDensity("kg/m3");
    double liquidDensity = thermoSystem.getPhase(1).getDensity("kg/m3");

    if (liquidDensity <= gasDensity) {
      return Double.POSITIVE_INFINITY;
    }

    double gasVelocity = calcGasVelocityAboveLevel(liquidLevelHeight);
    return gasVelocity * Math.sqrt(gasDensity / (liquidDensity - gasDensity));
  }

  /**
   * Calculate the K-value at the design HLL from mechanical design.
   * Uses HLL fraction from SeparatorMechanicalDesign if available.
   *
   * @return K-value in m/s at HLL
   */
  public double calcKValueAtHLL() {
    double hll = getMechanicalDesign().getHLL();
    if (hll <= 0) {
      // Default to 70% of internal diameter if not set
      hll = internalDiameter * 0.70;
    }
    return calcKValue(hll);
  }

  /**
   * Check if K-value is within the specified limit.
   *
   * @param limit K-value limit in m/s (default 0.15 per TR3500)
   * @return true if K-value is below the limit
   */
  public boolean isKValueWithinLimit(double limit) {
    return calcKValueAtHLL() <= limit;
  }

  /**
   * Check if K-value is within the default limit (0.15 m/s per TR3500).
   *
   * @return true if K-value is below the default limit
   */
  public boolean isKValueWithinLimit() {
    return isKValueWithinLimit(DEFAULT_K_VALUE_LIMIT);
  }

  /**
   * Calculate the oil droplet cut size in the gas section above liquid level.
   * Based on Stokes law for terminal settling velocity.
   *
   * <p>
   * Per Equinor TR3500 SR-50535: Droplet cut size must be less than 150 µm.
   * </p>
   *
   * @param effectiveGasLength    effective gas separation length in meters
   * @param freeHeightAboveLiquid free height above liquid level in meters
   * @return droplet cut size in meters (multiply by 1e6 for µm)
   */
  public double calcDropletCutSize(double effectiveGasLength, double freeHeightAboveLiquid) {
    if (thermoSystem == null || thermoSystem.getNumberOfPhases() < 2) {
      return 0.0;
    }
    if (!thermoSystem.hasPhaseType("gas")) {
      return 0.0;
    }

    double gasDensity = thermoSystem.getPhase(0).getDensity("kg/m3");
    double oilDensity = thermoSystem.getPhase(1).getDensity("kg/m3");
    double temperature = thermoSystem.getTemperature() - 273.15; // Celsius

    // Gas velocity above liquid
    double gasVelocity = calcGasVelocityAboveLevel(internalDiameter - freeHeightAboveLiquid);
    if (gasVelocity <= 0 || effectiveGasLength <= 0) {
      return 0.0;
    }

    // Terminal velocity required for droplet to settle in available time
    double residenceTime = effectiveGasLength / gasVelocity;
    double terminalVelocity = freeHeightAboveLiquid / residenceTime;

    // Gas viscosity using Sutherland equation for methane (conservative)
    double gasViscosity = 11e-6 * (293.0 + 169.0) / (273.0 + temperature + 169.0)
        * Math.pow((273.0 + temperature) / 293.0, 1.5);

    // Stokes law: D = 2 * sqrt(9/2 * v_terminal * µ / ((ρ_oil - ρ_gas) * g))
    double dropletDiameter = 2.0 * Math.sqrt(
        4.5 * terminalVelocity * gasViscosity / ((oilDensity - gasDensity) * 9.81));

    return dropletDiameter;
  }

  /**
   * Calculate the oil droplet cut size at HLL using mechanical design parameters.
   *
   * @return droplet cut size in meters
   */
  public double calcDropletCutSizeAtHLL() {
    double hll = getMechanicalDesign().getHLL();
    if (hll <= 0) {
      hll = internalDiameter * 0.70;
    }
    double freeHeight = internalDiameter - hll;
    double effGasLength = getMechanicalDesign().getEffectiveLengthGas();
    if (effGasLength <= 0) {
      effGasLength = separatorLength * 0.64; // Default 64% of length
    }
    return calcDropletCutSize(effGasLength, freeHeight);
  }

  /**
   * Check if droplet cut size is within the specified limit.
   *
   * @param limitMicrons droplet cut size limit in micrometers (default 150 µm per
   *                     TR3500)
   * @return true if droplet cut size is below the limit
   */
  public boolean isDropletCutSizeWithinLimit(double limitMicrons) {
    return calcDropletCutSizeAtHLL() <= limitMicrons * 1e-6;
  }

  /**
   * Check if droplet cut size is within the default limit (150 µm per TR3500).
   *
   * @return true if droplet cut size is below the default limit
   */
  public boolean isDropletCutSizeWithinLimit() {
    return isDropletCutSizeWithinLimit(150.0);
  }

  /**
   * Calculate the inlet nozzle momentum flux.
   * M = ρ_mix * v_mix²
   *
   * <p>
   * Per Equinor Revamp Limit: Momentum flux should be less than 16000 Pa.
   * </p>
   *
   * @param nozzleDiameter inlet nozzle internal diameter in meters
   * @return momentum flux in Pa
   */
  public double calcInletMomentumFlux(double nozzleDiameter) {
    if (thermoSystem == null || nozzleDiameter <= 0) {
      return 0.0;
    }

    double nozzleArea = Math.PI * Math.pow(nozzleDiameter / 2.0, 2);

    // Get actual volumetric flows
    double gasFlow = 0.0;
    double oilFlow = 0.0;
    double waterFlow = 0.0;
    double gasDensity = 1.0;
    double oilDensity = 800.0;
    double waterDensity = 1000.0;

    if (thermoSystem.hasPhaseType("gas")) {
      int gasIndex = thermoSystem.getPhaseNumberOfPhase("gas");
      gasFlow = thermoSystem.getPhase(gasIndex).getFlowRate("m3/hr");
      gasDensity = thermoSystem.getPhase(gasIndex).getDensity("kg/m3");
    }
    if (thermoSystem.hasPhaseType("oil")) {
      int oilIndex = thermoSystem.getPhaseNumberOfPhase("oil");
      oilFlow = thermoSystem.getPhase(oilIndex).getFlowRate("m3/hr");
      oilDensity = thermoSystem.getPhase(oilIndex).getDensity("kg/m3");
    }
    if (thermoSystem.hasPhaseType("aqueous")) {
      int waterIndex = thermoSystem.getPhaseNumberOfPhase("aqueous");
      waterFlow = thermoSystem.getPhase(waterIndex).getFlowRate("m3/hr");
      waterDensity = thermoSystem.getPhase(waterIndex).getDensity("kg/m3");
    }

    double totalVolumeFlow = gasFlow + oilFlow + waterFlow; // m³/hr
    if (totalVolumeFlow <= 0) {
      return 0.0;
    }

    double mixVelocity = (totalVolumeFlow / 3600.0) / nozzleArea; // m/s
    double mixDensity = (gasDensity * gasFlow + oilDensity * oilFlow + waterDensity * waterFlow)
        / totalVolumeFlow;

    return mixDensity * mixVelocity * mixVelocity;
  }

  /**
   * Calculate the inlet momentum flux using mechanical design nozzle size.
   *
   * @return momentum flux in Pa
   */
  public double calcInletMomentumFlux() {
    double nozzleID = getMechanicalDesign().getInletNozzleID();
    if (nozzleID <= 0) {
      // Estimate nozzle size if not set
      nozzleID = internalDiameter * 0.15; // Rough estimate: 15% of vessel ID
    }
    return calcInletMomentumFlux(nozzleID);
  }

  /**
   * Check if inlet momentum flux is within the specified limit.
   *
   * @param limitPa momentum flux limit in Pa (default 16000 Pa for revamp)
   * @return true if momentum flux is below the limit
   */
  public boolean isInletMomentumWithinLimit(double limitPa) {
    return calcInletMomentumFlux() <= limitPa;
  }

  /**
   * Check if inlet momentum flux is within the default limit (16000 Pa).
   *
   * @return true if momentum flux is below the default limit
   */
  public boolean isInletMomentumWithinLimit() {
    return isInletMomentumWithinLimit(DEFAULT_INLET_MOMENTUM_LIMIT);
  }

  /**
   * Calculate the liquid area (circular segment) at a given level height.
   *
   * @param diameter    vessel internal diameter in meters
   * @param levelHeight liquid level height in meters
   * @return segment area in m²
   */
  public static double calcSegmentArea(double diameter, double levelHeight) {
    double r = diameter / 2.0;
    double h = Math.min(levelHeight, diameter);
    if (h <= 0) {
      return 0.0;
    }
    if (h <= r) {
      // Lower segment
      double theta = Math.acos((r - h) / r);
      return r * r * theta - (r - h) * Math.sqrt(2 * r * h - h * h);
    } else {
      // Upper segment - calculate complement
      double h2 = diameter - h;
      double theta2 = Math.acos((r - h2) / r);
      double lowerArea = r * r * theta2 - (r - h2) * Math.sqrt(2 * r * h2 - h2 * h2);
      return Math.PI * r * r - lowerArea;
    }
  }

  /**
   * Calculate oil retention time between NIL and NLL.
   * Per API 12J and TR3500 SR-83381: Should be 3-5 minutes for light oils.
   *
   * @return oil retention time in minutes
   */
  public double calcOilRetentionTime() {
    if (thermoSystem == null || !thermoSystem.hasPhaseType("oil")) {
      return Double.POSITIVE_INFINITY; // No oil - infinite retention
    }

    double nll = getMechanicalDesign().getNLL();
    double nil = getMechanicalDesign().getNIL();
    double effLiquidLength = getMechanicalDesign().getEffectiveLengthLiquid();

    if (nll <= 0) {
      nll = internalDiameter * 0.50;
    }
    if (nil <= 0) {
      nil = internalDiameter * 0.20;
    }
    if (effLiquidLength <= 0) {
      effLiquidLength = separatorLength * 0.82;
    }

    // Oil volume between NIL and NLL
    double oilArea = calcSegmentArea(internalDiameter, nll) - calcSegmentArea(internalDiameter, nil);
    double oilVolume = oilArea * effLiquidLength; // m³

    // Oil flow rate
    int oilIndex = thermoSystem.getPhaseNumberOfPhase("oil");
    double oilFlowRate = thermoSystem.getPhase(oilIndex).getFlowRate("m3/hr") / 60.0; // m³/min

    if (oilFlowRate <= 0) {
      return Double.POSITIVE_INFINITY;
    }

    return oilVolume / oilFlowRate; // minutes
  }

  /**
   * Calculate water retention time below NIL.
   * Per API 12J and TR3500 SR-83381: Should be 3-5 minutes for light oils.
   *
   * @return water retention time in minutes
   */
  public double calcWaterRetentionTime() {
    if (thermoSystem == null || !thermoSystem.hasPhaseType("aqueous")) {
      return Double.POSITIVE_INFINITY; // No water - infinite retention
    }

    double nil = getMechanicalDesign().getNIL();
    double effLiquidLength = getMechanicalDesign().getEffectiveLengthLiquid();

    if (nil <= 0) {
      nil = internalDiameter * 0.20;
    }
    if (effLiquidLength <= 0) {
      effLiquidLength = separatorLength * 0.82;
    }

    // Water volume below NIL
    double waterArea = calcSegmentArea(internalDiameter, nil);
    double waterVolume = waterArea * effLiquidLength; // m³

    // Water flow rate
    int waterIndex = thermoSystem.getPhaseNumberOfPhase("aqueous");
    double waterFlowRate = thermoSystem.getPhase(waterIndex).getFlowRate("m3/hr") / 60.0; // m³/min

    if (waterFlowRate <= 0) {
      return Double.POSITIVE_INFINITY;
    }

    return waterVolume / waterFlowRate; // minutes
  }

  /**
   * Check if oil retention time meets the minimum requirement.
   *
   * @param minMinutes minimum retention time in minutes (default 3 minutes per
   *                   API 12J)
   * @return true if retention time is above the minimum
   */
  public boolean isOilRetentionTimeAboveMinimum(double minMinutes) {
    return calcOilRetentionTime() >= minMinutes;
  }

  /**
   * Check if oil retention time meets the default minimum (3 minutes).
   *
   * @return true if retention time is above the default minimum
   */
  public boolean isOilRetentionTimeAboveMinimum() {
    return isOilRetentionTimeAboveMinimum(DEFAULT_MIN_OIL_RETENTION_TIME);
  }

  /**
   * Check if water retention time meets the minimum requirement.
   *
   * @param minMinutes minimum retention time in minutes (default 3 minutes per
   *                   API 12J)
   * @return true if retention time is above the minimum
   */
  public boolean isWaterRetentionTimeAboveMinimum(double minMinutes) {
    return calcWaterRetentionTime() >= minMinutes;
  }

  /**
   * Check if water retention time meets the default minimum (3 minutes).
   *
   * @return true if retention time is above the default minimum
   */
  public boolean isWaterRetentionTimeAboveMinimum() {
    return isWaterRetentionTimeAboveMinimum(DEFAULT_MIN_WATER_RETENTION_TIME);
  }

  /**
   * Check if all separator performance parameters are within limits.
   * Uses default limits from Equinor TR3500 and API 12J.
   *
   * @return true if all parameters are within limits
   */
  public boolean isWithinAllLimits() {
    return isKValueWithinLimit() && isDropletCutSizeWithinLimit()
        && isInletMomentumWithinLimit() && isOilRetentionTimeAboveMinimum()
        && isWaterRetentionTimeAboveMinimum();
  }

  /**
   * Get a summary of all performance parameters with limit checks.
   *
   * @return performance summary as formatted string
   */
  public String getPerformanceSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Separator Performance Summary: ").append(getName()).append(" ===\n");

    double kValue = calcKValueAtHLL();
    sb.append(String.format("K-value at HLL: %.4f m/s (limit: %.2f m/s) - %s%n",
        kValue, DEFAULT_K_VALUE_LIMIT, isKValueWithinLimit() ? "OK" : "EXCEEDED"));

    double cutSize = calcDropletCutSizeAtHLL() * 1e6;
    sb.append(String.format("Droplet cut size: %.1f µm (limit: %.0f µm) - %s%n",
        cutSize, DEFAULT_DROPLET_CUTSIZE_LIMIT * 1e6,
        isDropletCutSizeWithinLimit() ? "OK" : "EXCEEDED"));

    double momentum = calcInletMomentumFlux();
    sb.append(String.format("Inlet momentum: %.0f Pa (limit: %.0f Pa) - %s%n",
        momentum, DEFAULT_INLET_MOMENTUM_LIMIT,
        isInletMomentumWithinLimit() ? "OK" : "EXCEEDED"));

    double oilRetention = calcOilRetentionTime();
    sb.append(String.format("Oil retention time: %.1f min (min: %.1f min) - %s%n",
        oilRetention, DEFAULT_MIN_OIL_RETENTION_TIME,
        isOilRetentionTimeAboveMinimum() ? "OK" : "BELOW MINIMUM"));

    double waterRetention = calcWaterRetentionTime();
    sb.append(String.format("Water retention time: %.1f min (min: %.1f min) - %s%n",
        waterRetention, DEFAULT_MIN_WATER_RETENTION_TIME,
        isWaterRetentionTimeAboveMinimum() ? "OK" : "BELOW MINIMUM"));

    sb.append(String.format("%nOverall: %s%n", isWithinAllLimits() ? "ALL WITHIN LIMITS" : "LIMITS EXCEEDED"));

    return sb.toString();
  }

  /**
   * Custom deserialization to reinitialize transient fields.
   *
   * <p>
   * After deserialization, the capacity constraints need to be reinitialized
   * because the valueSupplier lambdas are transient and cannot be serialized.
   * This method clears any deserialized constraints and creates fresh ones
   * with proper value suppliers bound to this separator instance.
   * </p>
   *
   * @param in the ObjectInputStream to read from
   * @throws java.io.IOException    if an I/O error occurs
   * @throws ClassNotFoundException if the class of a serialized object cannot be
   *                                found
   */
  private void readObject(java.io.ObjectInputStream in)
      throws java.io.IOException, ClassNotFoundException {
    in.defaultReadObject();
    // Reinitialize capacity constraints with fresh value suppliers
    // The map was deserialized but lambdas (transient) are null
    if (capacityConstraints == null) {
      capacityConstraints = new LinkedHashMap<String, CapacityConstraint>();
    } else {
      capacityConstraints.clear();
    }
    initializeCapacityConstraints();
  }

  /**
   * Creates a new Builder for constructing a Separator with a fluent API.
   *
   * <p>
   * Example usage:
   * </p>
   * 
   * <pre>
   * Separator sep = Separator.builder("V-100").inletStream(feed).orientation("horizontal")
   *     .length(5.0).diameter(2.0).liquidLevel(0.5).build();
   * </pre>
   *
   * @param name the name of the separator
   * @return a new Builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builder class for constructing Separator instances with a fluent API.
   *
   * <p>
   * Provides a readable and maintainable way to construct separators with
   * geometry, orientation,
   * efficiency, and entrainment specifications.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Builder {
    private final String name;
    private StreamInterface inletStream = null;
    private String orientation = "horizontal";
    private double separatorLength = 5.0;
    private double internalDiameter = 1.0;
    private double liquidLevel = -1.0;
    private double designLiquidLevelFraction = 0.8;
    private double pressureDrop = 0.0;
    private double efficiency = 1.0;
    private double liquidCarryoverFraction = 0.0;
    private double gasCarryunderFraction = 0.0;
    private double oilInGas = 0.0;
    private String oilInGasSpec = "mole";
    private double waterInGas = 0.0;
    private String waterInGasSpec = "mole";
    private double gasInLiquid = 0.0;
    private String gasInLiquidSpec = "mole";
    private String specifiedStream = "feed";
    private double heatInput = 0.0;
    private boolean calculateSteadyState = true;

    /**
     * Creates a new Builder with the specified separator name.
     *
     * @param name the name of the separator
     */
    public Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the inlet stream for the separator.
     *
     * @param stream the inlet stream
     * @return this builder for chaining
     */
    public Builder inletStream(StreamInterface stream) {
      this.inletStream = stream;
      return this;
    }

    /**
     * Sets the separator orientation.
     *
     * @param orientation "horizontal" or "vertical"
     * @return this builder for chaining
     */
    public Builder orientation(String orientation) {
      this.orientation = orientation;
      return this;
    }

    /**
     * Sets the separator as horizontal orientation.
     *
     * @return this builder for chaining
     */
    public Builder horizontal() {
      this.orientation = "horizontal";
      return this;
    }

    /**
     * Sets the separator as vertical orientation.
     *
     * @return this builder for chaining
     */
    public Builder vertical() {
      this.orientation = "vertical";
      return this;
    }

    /**
     * Sets the separator length in meters.
     *
     * @param length separator length in meters
     * @return this builder for chaining
     */
    public Builder length(double length) {
      this.separatorLength = length;
      return this;
    }

    /**
     * Sets the internal diameter in meters.
     *
     * @param diameter internal diameter in meters
     * @return this builder for chaining
     */
    public Builder diameter(double diameter) {
      this.internalDiameter = diameter;
      return this;
    }

    /**
     * Sets the liquid level in meters.
     *
     * @param level liquid level height in meters
     * @return this builder for chaining
     */
    public Builder liquidLevel(double level) {
      this.liquidLevel = level;
      return this;
    }

    /**
     * Sets the design liquid level as a fraction of diameter (0.0-1.0).
     *
     * @param fraction liquid level fraction
     * @return this builder for chaining
     */
    public Builder designLiquidLevelFraction(double fraction) {
      this.designLiquidLevelFraction = fraction;
      return this;
    }

    /**
     * Sets the pressure drop across the separator in bar.
     *
     * @param dp pressure drop in bar
     * @return this builder for chaining
     */
    public Builder pressureDrop(double dp) {
      this.pressureDrop = dp;
      return this;
    }

    /**
     * Sets the separation efficiency (0.0-1.0).
     *
     * @param eff efficiency value
     * @return this builder for chaining
     */
    public Builder efficiency(double eff) {
      this.efficiency = eff;
      return this;
    }

    /**
     * Sets the liquid carryover fraction to gas outlet (0.0-1.0).
     *
     * @param fraction liquid carryover fraction
     * @return this builder for chaining
     */
    public Builder liquidCarryover(double fraction) {
      this.liquidCarryoverFraction = fraction;
      return this;
    }

    /**
     * Sets the gas carryunder fraction to liquid outlet (0.0-1.0).
     *
     * @param fraction gas carryunder fraction
     * @return this builder for chaining
     */
    public Builder gasCarryunder(double fraction) {
      this.gasCarryunderFraction = fraction;
      return this;
    }

    /**
     * Sets oil entrainment in gas phase.
     *
     * @param value entrainment value
     * @param spec  specification type ("mole", "mass", "volume")
     * @return this builder for chaining
     */
    public Builder oilInGas(double value, String spec) {
      this.oilInGas = value;
      this.oilInGasSpec = spec;
      return this;
    }

    /**
     * Sets water entrainment in gas phase.
     *
     * @param value entrainment value
     * @param spec  specification type ("mole", "mass", "volume")
     * @return this builder for chaining
     */
    public Builder waterInGas(double value, String spec) {
      this.waterInGas = value;
      this.waterInGasSpec = spec;
      return this;
    }

    /**
     * Sets gas entrainment in liquid phase.
     *
     * @param value entrainment value
     * @param spec  specification type ("mole", "mass", "volume")
     * @return this builder for chaining
     */
    public Builder gasInLiquid(double value, String spec) {
      this.gasInLiquid = value;
      this.gasInLiquidSpec = spec;
      return this;
    }

    /**
     * Sets the reference stream for entrainment specifications.
     *
     * @param streamType "feed", "gas", or "liquid"
     * @return this builder for chaining
     */
    public Builder specifiedStream(String streamType) {
      this.specifiedStream = streamType;
      return this;
    }

    /**
     * Sets heat input to the separator in Watts.
     *
     * @param heat heat input in W
     * @return this builder for chaining
     */
    public Builder heatInput(double heat) {
      this.heatInput = heat;
      return this;
    }

    /**
     * Enables transient (dynamic) calculation mode.
     *
     * @return this builder for chaining
     */
    public Builder transientMode() {
      this.calculateSteadyState = false;
      return this;
    }

    /**
     * Enables steady-state calculation mode (default).
     *
     * @return this builder for chaining
     */
    public Builder steadyStateMode() {
      this.calculateSteadyState = true;
      return this;
    }

    /**
     * Builds and returns the configured Separator instance.
     *
     * @return a new Separator instance with the specified configuration
     */
    public Separator build() {
      Separator sep;
      if (inletStream != null) {
        sep = new Separator(name, inletStream);
      } else {
        sep = new Separator(name);
      }

      sep.setOrientation(orientation);
      sep.setSeparatorLength(separatorLength);
      sep.setInternalDiameter(internalDiameter);

      if (liquidLevel >= 0) {
        sep.setLiquidLevel(liquidLevel);
      } else {
        sep.setLiquidLevel(internalDiameter * designLiquidLevelFraction);
      }

      sep.setDesignLiquidLevelFraction(designLiquidLevelFraction);
      sep.setPressureDrop(pressureDrop);
      sep.setEfficiency(efficiency);
      sep.setLiquidCarryoverFraction(liquidCarryoverFraction);
      sep.setGasCarryunderFraction(gasCarryunderFraction);

      if (oilInGas > 0) {
        sep.setEntrainment(oilInGas, oilInGasSpec, specifiedStream, "oil", "gas");
      }
      if (waterInGas > 0) {
        sep.setEntrainment(waterInGas, waterInGasSpec, specifiedStream, "aqueous", "gas");
      }
      if (gasInLiquid > 0) {
        sep.setEntrainment(gasInLiquid, gasInLiquidSpec, specifiedStream, "gas", "liquid");
      }

      if (heatInput != 0.0) {
        sep.setHeatInput(heatInput, "W");
      }

      sep.setCalculateSteadyState(calculateSteadyState);

      return sep;
    }
  }
}
