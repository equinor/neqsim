/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */

package neqsim.process.equipment.separator;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.sectiontype.ManwaySection;
import neqsim.process.equipment.separator.sectiontype.MeshSection;
import neqsim.process.equipment.separator.sectiontype.NozzleSection;
import neqsim.process.equipment.separator.sectiontype.SeparatorSection;
import neqsim.process.equipment.separator.sectiontype.ValveSection;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.process.mechanicaldesign.separator.primaryseparation.PrimarySeparation;
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
public class Separator extends ProcessEquipmentBaseClass implements SeparatorInterface {
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

  /** Separator cross sectional area. */
  private double sepCrossArea = Math.PI * internalDiameter * internalDiameter / 4.0;

  /** Separator volume. */
  private double separatorVolume = sepCrossArea * separatorLength;

  double liquidVolume;
  double gasVolume;

  private double designLiquidLevelFraction = 0.8;
  ArrayList<SeparatorSection> separatorSection = new ArrayList<SeparatorSection>();

  SeparatorMechanicalDesign separatorMechanicalDesign;
  private PrimarySeparation primarySeparation;

  // Inlet stream properties - calculated during run()
  private double inletGasVelocity = 0.0; // m/s through nozzle
  private double vesselGasVelocity = 0.0; // m/s through vessel
  private double gasDensity = 0.0; // kg/m³
  private double liquidDensity = 0.0; // kg/m³
  private double liquidContent = 0.0; // volumetric fraction (0 to 1)

  private double lastEnthalpy;
  private double lastFlowRate;
  private double lastPressure;

  // Heat input capabilities
  private boolean setHeatInput = false;
  private double heatInput = 0.0; // W (watts)
  private String heatInputUnit = "W";

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
  }

  /**
   * Constructor for Separator.
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
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
   * @param val a double specifying the entrainment amount
   * @param specType a {@link java.lang.String} object describing the specification unit
   * @param specifiedStream a {@link java.lang.String} object describing the reference stream
   * @param phaseFrom a {@link java.lang.String} object describing the phase entrained from
   * @param phaseTo a {@link java.lang.String} object describing the phase entrained to
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
    updateInletProperties();
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
      thermoSystem.init(2);
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
        dncomp +=
            inletStreamMixer.getOutletStream().getThermoSystem().getComponent(i).getNumberOfmoles();
        double dniliq = 0.0;
        if (hasliq) {
          dniliq = -liquidOutStream.getThermoSystem().getComponent(i).getNumberOfmoles();
        }
        dncomp += -gasOutStream.getThermoSystem().getComponent(i).getNumberOfmoles() + dniliq;

        thermoSystem.addComponent(i, dncomp * dt);
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
   * Update inlet stream properties that are cached for use in separation calculations.
   * <p>
   * This method calculates and stores inlet gas velocity, vessel gas velocity, gas density, liquid
   * density, and liquid content. These values are used by the separation device calculations and
   * should be updated whenever the inlet stream changes.
   * </p>
   */
  private void updateInletProperties() {
    if (getFeedStream() == null || getFeedStream().getThermoSystem() == null) {
      return;
    }

    // Calculate inlet gas velocity through nozzle
    inletGasVelocity = getInletGasVelocity();

    // Calculate gas velocity through vessel
    // Use the separator's internal diameter for vessel area calculation
    double volumetricFlow = getInletGasVolumetricFlow();
    if (internalDiameter > 0.0) {
      double vesselArea = Math.PI * internalDiameter * internalDiameter / 4.0;
      vesselGasVelocity = volumetricFlow / vesselArea;
    } else {
      vesselGasVelocity = 0.0;
    }

    // Store gas density
    gasDensity = getInletGasDensity();

    // Store liquid density
    liquidDensity = getInletLiquidDensity();

    // Store liquid content
    liquidContent = getInletLiquidContent();
  }

  /**
   * <p>
   * Calculate the gas volumetric flow rate from the inlet stream.
   * </p>
   *
   * Extracts the gas phase volumetric flow from the feed stream.
   *
   * @return gas volumetric flow rate in m³/s
   */
  public double getInletGasVolumetricFlow() {
    if (getFeedStream() == null || getFeedStream().getThermoSystem() == null) {
      return 0.0;
    }
    return getFeedStream().getThermoSystem().getPhase(0).getVolume() / 1e5; // Convert from cm³/s to
                                                                            // m³/s
  }

  /**
   * Calculate the gas velocity through the inlet nozzle.
   * 
   * Uses the inlet nozzle diameter from primary separation to calculate velocity. Velocity =
   * volumetric flow / nozzle area.
   *
   * @return gas velocity through nozzle in m/s
   */
  public double getInletGasVelocity() {
    if (primarySeparation == null) {
      return 0.0;
    }
    double volumetricFlow = getInletGasVolumetricFlow();
    double nozzleDiameter = primarySeparation.getInletNozzleDiameter();

    if (nozzleDiameter <= 0.0) {
      return 0.0;
    }

    double nozzleArea = Math.PI * nozzleDiameter * nozzleDiameter / 4.0;
    return volumetricFlow / nozzleArea;
  }

  /**
   * Get the gas density from the inlet stream.
   * 
   * Extracts the gas phase density and initializes physical properties if needed.
   *
   * @return gas density in kg/m³
   */
  public double getInletGasDensity() {
    if (getFeedStream() == null || getFeedStream().getThermoSystem() == null) {
      return 0.0;
    }
    SystemInterface fluid = getFeedStream().getThermoSystem();
    if (fluid.getNumberOfPhases() > 0) {
      fluid.initPhysicalProperties();
      return fluid.getPhase(0).getPhysicalProperties().getDensity();
    }
    return 0.0;
  }

  /**
   * Get the liquid density from the inlet stream.
   * 
   * Extracts the liquid phase density. For single-phase gas, returns a default liquid density.
   *
   * @return liquid density in kg/m³
   */
  public double getInletLiquidDensity() {
    if (getFeedStream() == null || getFeedStream().getThermoSystem() == null) {
      return 800.0; // Default liquid density
    }
    SystemInterface fluid = getFeedStream().getThermoSystem();
    fluid.initPhysicalProperties();

    if (fluid.getNumberOfPhases() > 1 && fluid.hasPhaseType("oil")
        || fluid.hasPhaseType("aqueous")) {
      return fluid.getPhase(1).getPhysicalProperties().getDensity();
    }

    // Default value if liquid phase not present
    return 800.0;
  }

  /**
   * Get the inlet liquid content (volumetric fraction) from the inlet stream.
   * 
   * Calculates the volumetric fraction of liquid in the inlet stream. Liquid content = liquid
   * volume / total volume.
   *
   * @return inlet liquid content as volumetric fraction (0 to 1)
   */
  public double getInletLiquidContent() {
    if (getFeedStream() == null || getFeedStream().getThermoSystem() == null) {
      return 0.0;
    }
    SystemInterface fluid = getFeedStream().getThermoSystem();

    double totalVolume = fluid.getVolume(); // cm³/s
    if (totalVolume <= 0.0) {
      return 0.0;
    }

    double liquidVolume = 0.0;
    if (fluid.getNumberOfPhases() > 1) {
      for (int i = 1; i < fluid.getNumberOfPhases(); i++) {
        if (fluid.getPhase(i).getPhaseTypeName().equals("oil")
            || fluid.getPhase(i).getPhaseTypeName().equals("aqueous")) {
          liquidVolume += fluid.getPhase(i).getVolume();
        }
      }
    }

    return liquidVolume / totalVolume;
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
    double term1 = (thermoSystem.getPhase(1).getPhysicalProperties().getDensity()
        - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
        / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
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
    double term1 = 1.0 / gasAreaFraction
        * (thermoSystem.getPhase(2).getPhysicalProperties().getDensity()
            - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
        / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
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
    double surfaceTension =
        thermoSystem.getInterphaseProperties().getSurfaceTension(phaseNum - 1, phaseNum);
    if (surfaceTension < 10.0e-3) {
      derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
    }
    // System.out.println("derating " + derating);
    double term1 = (thermoSystem.getPhase(phaseNum).getPhysicalProperties().getDensity()
        - thermoSystem.getPhase(0).getPhysicalProperties().getDensity())
        / thermoSystem.getPhase(0).getPhysicalProperties().getDensity();
    return derating * getGasSuperficialVelocity() * Math.sqrt(1.0 / term1);
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
   * Calculates both gas and liquid fluid section areas for horizontal separators. Results can be
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
        // System.out.printf("Area func: radius %f d %f theta %f a %f area %f\n", internalRadius, d,
        // theta, a, lArea);

      } else if (level > internalRadius) {

        double d = level - internalRadius;
        double theta = Math.acos(d / internalRadius);
        double a = internalRadius * Math.sin(theta);
        double triArea = a * d;
        double circArea = (Math.PI - theta) * Math.pow(internalRadius, 2);
        lArea = circArea + triArea;
        // System.out.printf("Area func: radius %f d %f theta %f a %f area %f\n", internalRadius, d,
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
   * Keeps cached gas/liquid holdup volumes aligned with current geometry and level.
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
   * <p>
   * Estimates liquid level based on volume for horizontal separators using bisection method.
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
    double maxLiquidVolume =
        separatorVolume > 0.0 ? Math.max(separatorVolume - headspace, 0.0) : 0.0;
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

      return 0;

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
   * @return a {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection} object
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
   * @return a {@link neqsim.process.equipment.separator.sectiontype.SeparatorSection} object
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
      inletStreamMixer.getStream(i).getFluid().init(3);
      entrop += inletStreamMixer.getStream(i).getFluid().getEntropy(unit);
    }

    double liquidEntropy = 0.0;
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      try {
        getLiquidOutStream().getThermoSystem().init(3);
        liquidEntropy = getLiquidOutStream().getThermoSystem().getEntropy(unit);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }

    double gasEntropy = 0.0;
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasEntropy = getGasOutStream().getThermoSystem().getEntropy(unit);
    }

    return liquidEntropy + gasEntropy - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double flow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      flow += inletStreamMixer.getStream(i).getFluid().getFlowRate(unit);
    }

    double liquidFlow = 0.0;
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      getLiquidOutStream().getThermoSystem().init(3);
      liquidFlow = getLiquidOutStream().getThermoSystem().getFlowRate(unit);
    }

    double gasFlow = 0.0;
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasFlow = getGasOutStream().getThermoSystem().getFlowRate(unit);
    }

    return liquidFlow + gasFlow - flow;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    double exergy = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      exergy += inletStreamMixer.getStream(i).getFluid().getExergy(surroundingTemperature, unit);
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
    return new GsonBuilder().create().toJson(new SeparatorResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    SeparatorResponse res = new SeparatorResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }

  /**
   * Set heat input to the separator (e.g., from flare radiation, external heating).
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
   * @param unit heat duty unit (W, kW, MW, J/s, etc.)
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
   * @param unit heat duty unit
   */
  public void setHeatDuty(double heatDuty, String unit) {
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

  /*
   * private class SeparatorReport extends Object{ public Double gasLoadFactor; SeparatorReport(){
   * gasLoadFactor = getGasLoadFactor(); } }
   *
   * public SeparatorReport getReport(){ return this.new SeparatorReport(); }
   */

  /**
   * Set the primary separation device for this separator.
   *
   * @param primarySeparation the PrimarySeparation device
   */
  public void setPrimarySeparation(PrimarySeparation primarySeparation) {
    this.primarySeparation = primarySeparation;
    if (primarySeparation != null) {
      primarySeparation.setSeparator(this);
    }
  }

  /**
   * Get the primary separation device for this separator.
   *
   * @return the PrimarySeparation device, or null if not set
   */
  public PrimarySeparation getPrimarySeparation() {
    return primarySeparation;
  }

  /**
   * Print information about the primary separation device.
   * 
   * Displays details about the inlet primary separation including type, nozzle diameter, and
   * type-specific properties.
   */
  public void printPrimarySeparation() {
    System.out.println("========================================");
    System.out.println("Primary Separation for: " + this.getName());
    System.out.println("========================================");

    if (primarySeparation == null) {
      System.out.println("No primary separation device configured.");
    } else {
      System.out.println("Type: " + primarySeparation.getClass().getSimpleName());
      System.out.println("Name: " + primarySeparation.getName());
      System.out
          .println("Inlet Nozzle Diameter: " + primarySeparation.getInletNozzleDiameter() + " m");

      // Print type-specific properties
      String className = primarySeparation.getClass().getSimpleName();
      if (className.equals("InletVane")) {
        try {
          double deflectionAngle = (double) primarySeparation.getClass()
              .getMethod("getDeflectionAngle").invoke(primarySeparation);
          System.out.println("Deflection Angle: " + deflectionAngle + "°");
        } catch (Exception e) {
          logger.debug("Could not retrieve deflection angle: " + e.getMessage());
        }
      } else if (className.equals("InletVaneWithMeshpad")) {
        try {
          double vaneToMeshpadDistance = (double) primarySeparation.getClass()
              .getMethod("getVaneToMeshpadDistance").invoke(primarySeparation);
          double freeDistanceAboveMeshpad = (double) primarySeparation.getClass()
              .getMethod("getFreeDistanceAboveMeshpad").invoke(primarySeparation);
          System.out.println("Vane to Meshpad Distance: " + vaneToMeshpadDistance + " m");
          System.out.println("Free Distance Above Meshpad: " + freeDistanceAboveMeshpad + " m");
        } catch (Exception e) {
          logger.debug("Could not retrieve meshpad distances: " + e.getMessage());
        }
      } else if (className.equals("InletCyclones")) {
        try {
          int numberOfCyclones = (int) primarySeparation.getClass().getMethod("getNumberOfCyclones")
              .invoke(primarySeparation);
          double cycloneDiameter = (double) primarySeparation.getClass()
              .getMethod("getCycloneDiameter").invoke(primarySeparation);
          System.out.println("Number of Cyclones: " + numberOfCyclones);
          System.out.println("Cyclone Diameter: " + cycloneDiameter + " m");
        } catch (Exception e) {
          logger.debug("Could not retrieve cyclone properties: " + e.getMessage());
        }
      }
    }
    System.out.println("========================================");
  }

  /**
   * Print all deisting internals in this separator.
   * 
   * Displays information about each deisting internal including area, Euler number, and any special
   * properties such as drainage efficiency.
   */
  public void printDemistingInternals() {
    SeparatorMechanicalDesign mechanicalDesign = getMechanicalDesign();

    if (mechanicalDesign == null) {
      System.out.println("Mechanical design not initialized");
      return;
    }

    int numberOfInternals = mechanicalDesign.getNumberOfDemistingInternals();

    System.out.println("========================================");
    System.out.println("Deisting Internals for: " + this.getName());
    System.out.println("========================================");
    System.out.println("Total number of internals: " + numberOfInternals);
    System.out.println("Total deisting area: " + mechanicalDesign.getTotalDemistingArea() + " m²");
    System.out.println();

    if (numberOfInternals == 0) {
      System.out.println("No deisting internals configured.");
    } else {
      for (int i = 0; i < numberOfInternals; i++) {
        var internal = mechanicalDesign.getDemistingInternals().get(i);
        System.out.println("Internal " + (i + 1) + ":");
        System.out.println("  Type: " + internal.getClass().getSimpleName());
        System.out.println("  Area: " + internal.getArea() + " m²");
        System.out.println("  Euler Number: " + internal.getEuNumber());

        // Check if it's a DemistingInternalWithDrainage
        if (internal.getClass().getSimpleName().equals("DemistingInternalWithDrainage")) {
          try {
            double drainageEfficiency =
                (double) internal.getClass().getMethod("getDrainageEfficiency").invoke(internal);
            System.out.println("  Drainage Efficiency: " + drainageEfficiency);
          } catch (Exception e) {
            logger.debug("Could not retrieve drainage efficiency: " + e.getMessage());
          }
        }
        System.out.println();
      }
    }
    System.out.println("========================================");
  }
}
