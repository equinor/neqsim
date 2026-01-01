package neqsim.process.equipment.separator;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.SeparatorResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ThreePhaseSeparator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThreePhaseSeparator extends Separator {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ThreePhaseSeparator.class);

  StreamInterface waterOutStream = new Stream("waterOutStream", waterSystem);

  String specifiedStream = "feed";
  double gasInAqueous = 0.00;
  String gasInAqueousSpec = "mole";

  double gasInOil = 0.00;
  String gasInOilSpec = "mole";

  double oilInGas = 0.00;
  String oilInGasSpec = "mole";

  double oilInAqueous = 0.00;
  String oilInAqueousSpec = "mole";

  double aqueousInGas = 0.00;
  String aqueousInGasSpec = "mole";

  double aqueousInOil = 0.00;
  String aqueousInOilSpec = "mole";

  boolean useTempMultiPhaseCheck = false;

  private double lastEnthalpy;
  private double lastFlowRate;
  private double lastPressure;

  /** Water level height in meters (bottom of separator to water-oil interface). */
  private double waterLevel = 0.0;
  /** Oil level height in meters (bottom of separator to top of oil phase). */
  private double oilLevel = 0.0;

  /** Gas outlet valve flow fraction (0.0 = fully closed, 1.0 = fully open). */
  private double gasOutletFlowFraction = 1.0;
  /** Oil outlet valve flow fraction (0.0 = fully closed, 1.0 = fully open). */
  private double oilOutletFlowFraction = 1.0;
  /** Water outlet valve flow fraction (0.0 = fully closed, 1.0 = fully open). */
  private double waterOutletFlowFraction = 1.0;

  /**
   * Constructor for ThreePhaseSeparator.
   *
   * @param name name of separator
   */
  public ThreePhaseSeparator(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for ThreePhaseSeparator.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ThreePhaseSeparator(String name, StreamInterface inletStream) {
    super(name, inletStream);
  }

  /**
   * <p>
   * setEntrainment.
   * </p>
   *
   * @param val a double
   * @param specType a {@link java.lang.String} object
   * @param specifiedStream a {@link java.lang.String} object
   * @param phaseFrom a {@link java.lang.String} object
   * @param phaseTo a {@link java.lang.String} object
   */
  public void setEntrainment(double val, String specType, String specifiedStream, String phaseFrom,
      String phaseTo) {
    this.specifiedStream = specifiedStream;
    if (phaseFrom.equals("gas") && phaseTo.equals("aqueous")) {
      gasInAqueous = val;
      gasInAqueousSpec = specType;
    }
    if (phaseFrom.equals("gas") && phaseTo.equals("oil")) {
      gasInOil = val;
      gasInOilSpec = specType;
    }
    if (phaseFrom.equals("oil") && phaseTo.equals("aqueous")) {
      oilInAqueous = val;
      oilInAqueousSpec = specType;
    }
    if (phaseFrom.equals("oil") && phaseTo.equals("gas")) {
      oilInGas = val;
      oilInGasSpec = specType;
    }
    if (phaseFrom.equals("aqueous") && phaseTo.equals("gas")) {
      aqueousInGas = val;
      aqueousInGasSpec = specType;
    }
    if (phaseFrom.equals("aqueous") && phaseTo.equals("oil")) {
      aqueousInOil = val;
      aqueousInOilSpec = specType;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inletStream) {
    super.setInletStream(inletStream);

    thermoSystem = inletStream.getThermoSystem().clone();
    waterSystem = thermoSystem.phaseToSystem(thermoSystem.getPhases()[1]);
    waterOutStream = new Stream("waterOutStream", waterSystem);
  }

  /**
   * <p>
   * Getter for the field <code>waterOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getWaterOutStream() {
    return waterOutStream;
  }

  /**
   * <p>
   * getOilOutStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOilOutStream() {
    return liquidOutStream;
  }

  /**
   * Set the water level (height from bottom of separator to water-oil interface).
   *
   * @param level water level in meters
   */
  public void setWaterLevel(double level) {
    this.waterLevel = Math.max(0.0, level);
    updateLiquidLevelFromWaterAndOil();
  }

  /**
   * Get the water level (height from bottom of separator to water-oil interface).
   *
   * @return water level in meters
   */
  public double getWaterLevel() {
    return waterLevel;
  }

  /**
   * Set the oil level (height from bottom of separator to top of oil phase).
   *
   * @param level oil level in meters
   */
  public void setOilLevel(double level) {
    this.oilLevel = Math.max(0.0, level);
    updateLiquidLevelFromWaterAndOil();
  }

  /**
   * Get the oil level (height from bottom of separator to top of oil phase).
   *
   * @return oil level in meters
   */
  public double getOilLevel() {
    return oilLevel;
  }

  /**
   * Get the oil phase thickness (oil level minus water level).
   *
   * @return oil thickness in meters
   */
  public double getOilThickness() {
    return Math.max(0.0, oilLevel - waterLevel);
  }

  /**
   * Set the gas outlet valve flow fraction (simulates valve position).
   *
   * @param fraction flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public void setGasOutletFlowFraction(double fraction) {
    this.gasOutletFlowFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Get the gas outlet valve flow fraction.
   *
   * @return flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public double getGasOutletFlowFraction() {
    return gasOutletFlowFraction;
  }

  /**
   * Set the oil outlet valve flow fraction (simulates valve position).
   *
   * @param fraction flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public void setOilOutletFlowFraction(double fraction) {
    this.oilOutletFlowFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Get the oil outlet valve flow fraction.
   *
   * @return flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public double getOilOutletFlowFraction() {
    return oilOutletFlowFraction;
  }

  /**
   * Set the water outlet valve flow fraction (simulates valve position).
   *
   * @param fraction flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public void setWaterOutletFlowFraction(double fraction) {
    this.waterOutletFlowFraction = Math.max(0.0, Math.min(1.0, fraction));
  }

  /**
   * Get the water outlet valve flow fraction.
   *
   * @return flow fraction (0.0 = fully closed, 1.0 = fully open)
   */
  public double getWaterOutletFlowFraction() {
    return waterOutletFlowFraction;
  }

  /**
   * Updates the parent class liquidLevel based on water and oil levels.
   */
  private void updateLiquidLevelFromWaterAndOil() {
    // Total liquid level is the oil level (which includes water below it)
    liquidLevel = oilLevel;
    liquidVolume = calcLiquidVolume();
  }

  /**
   * Updates water and oil levels from phase volumes in the thermodynamic system.
   */
  private void updateWaterAndOilLevelsFromPhases() {
    if (thermoSystem == null) {
      return;
    }

    thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    double waterVolume = 0.0;
    double oilVolume = 0.0;

    if (thermoSystem.hasPhaseType("aqueous")) {
      waterVolume = thermoSystem.getPhase("aqueous").getVolume("m3");
    }
    if (thermoSystem.hasPhaseType("oil")) {
      oilVolume = thermoSystem.getPhase("oil").getVolume("m3");
    }

    // For horizontal separator
    if ("horizontal".equalsIgnoreCase(getOrientation())) {
      waterLevel = levelFromVolume(waterVolume);
      oilLevel = levelFromVolume(waterVolume + oilVolume);
    } else if ("vertical".equalsIgnoreCase(getOrientation())) {
      // For vertical separator, levels are simply heights
      double crossArea = Math.PI * Math.pow(getInternalDiameter() / 2.0, 2);
      if (crossArea > 0) {
        waterLevel = waterVolume / crossArea;
        oilLevel = (waterVolume + oilVolume) / crossArea;
      }
    }

    liquidLevel = oilLevel;
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

    if (!thermoSystem2.doMultiPhaseCheck()) {
      useTempMultiPhaseCheck = true;
      thermoSystem2.setMultiPhaseCheck(true);
    }

    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem2);

    // Handle heat input for steady-state operations
    if (isSetHeatInput() && getHeatInput() != 0.0) {
      // Add heat input to the system enthalpy
      double currentEnthalpy = thermoSystem2.getEnthalpy(); // Default unit is J
      double newEnthalpy = currentEnthalpy + getHeatInput(); // getHeatInput() is in watts (J/s) -
                                                             // for steady state we add directly

      // Perform HP flash (enthalpy-pressure flash) with heat input
      thermoOps.PHflash(newEnthalpy, 0); // Second parameter is typically 0
    } else {
      thermoOps.TPflash();
    }

    if (useTempMultiPhaseCheck) {
      thermoSystem2.setMultiPhaseCheck(false);
    }
    // thermoSystem.display();
    thermoSystem2.addPhaseFractionToPhase(gasInAqueous, gasInAqueousSpec, specifiedStream, "gas",
        "aqueous");
    thermoSystem2.addPhaseFractionToPhase(gasInOil, gasInOilSpec, specifiedStream, "gas", "oil");
    thermoSystem2.addPhaseFractionToPhase(oilInAqueous, oilInAqueousSpec, specifiedStream, "oil",
        "aqueous");
    thermoSystem2.addPhaseFractionToPhase(oilInGas, oilInGasSpec, specifiedStream, "oil", "gas");
    thermoSystem2.addPhaseFractionToPhase(aqueousInGas, aqueousInGasSpec, specifiedStream,
        "aqueous", "gas");
    thermoSystem2.addPhaseFractionToPhase(aqueousInOil, aqueousInOilSpec, specifiedStream,
        "aqueous", "oil");
    // thermoSystem.init_x_y();
    // thermoSystem.display();
    // thermoSystem.init(3);
    // thermoSystem.setMultiPhaseCheck(false);

    // //gasSystem = thermoSystem.phaseToSystem(0);
    // //gasOutStream.setThermoSystem(gasSystem);
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

    // quidSystem = thermoSystem.phaseToSystem(1);
    // liquidOutStream.setThermoSystem(liquidSystem);
    if (thermoSystem2.hasPhaseType("oil")) {
      // thermoSystem.display();
      liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "oil");
      liquidOutStream.getFluid().init(2);
      // thermoSystem.display();
    } else {
      // No oil phase - set empty system with very low flow
      SystemInterface emptyOilSystem = thermoSystem2.getEmptySystemClone();
      emptyOilSystem.setTotalFlowRate(1e-20, "kg/hr");
      emptyOilSystem.init(0);
      liquidOutStream.setThermoSystem(emptyOilSystem);
    }

    // waterSystem = thermoSystem.phaseToSystem(2);
    // waterOutStream.setThermoSystem(waterSystem);
    if (thermoSystem2.hasPhaseType("aqueous")) {
      waterOutStream.setThermoSystemFromPhase(thermoSystem2, "aqueous");
      waterOutStream.getFluid().init(2);
    } else {
      // No aqueous phase - set empty system with very low flow
      SystemInterface emptyAqueousSystem = thermoSystem2.getEmptySystemClone();
      emptyAqueousSystem.setTotalFlowRate(1e-20, "kg/hr");
      emptyAqueousSystem.init(0);
      waterOutStream.setThermoSystem(emptyAqueousSystem);
    }
    if (thermoSystem2.hasPhaseType("gas") && thermoSystem2.getNumberOfComponents() > 1) {
      gasOutStream.run(id);
    } else if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.getFluid().init(3);
    }
    if (thermoSystem2.hasPhaseType("oil") && thermoSystem2.getNumberOfComponents() > 1) {
      liquidOutStream.run(id);
    } else if (thermoSystem2.hasPhaseType("oil")) {
      try {
        liquidOutStream.getFluid().init(3);
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    if (thermoSystem2.hasPhaseType("aqueous") && thermoSystem2.getNumberOfComponents() > 1) {
      waterOutStream.run(id);
    } else if (thermoSystem2.hasPhaseType("aqueous")) {
      try {
        waterOutStream.getFluid().init(3);
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

  /**
   * Initializes three-phase separator for transient calculations.
   */
  @Override
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
        double volumeLoc = 0.0;
        if (thermoSystem.hasPhaseType("oil")) {
          volumeLoc += thermoSystem.getPhase("oil").getVolume("m3");
        }
        if (thermoSystem.hasPhaseType("aqueous")) {
          volumeLoc += thermoSystem.getPhase("aqueous").getVolume("m3");
        }
        liquidLevel = levelFromVolume(volumeLoc);
        liquidVolume = calcLiquidVolume();
        updateWaterAndOilLevelsFromPhases();
      } else {
        liquidLevel = 0.0;
        liquidVolume = 0.0;
        waterLevel = 0.0;
        oilLevel = 0.0;
      }
      enforceHeadspace();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    isInitTransient = true;
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
        if (thermoSystem.hasPhaseType("oil")) {
          liquidOutStream.getThermoSystem().init(2);
        }
        if (thermoSystem.hasPhaseType("aqueous")) {
          waterOutStream.getThermoSystem().init(2);
        }
      } catch (Exception e) {
        logger.error(e.getMessage());
      }

      boolean hasOil = false;
      boolean hasAqueous = false;
      double deOil = 0.0;
      double deAqueous = 0.0;

      if (thermoSystem.hasPhaseType("oil")) {
        hasOil = true;
        deOil = -liquidOutStream.getThermoSystem().getEnthalpy() * oilOutletFlowFraction;
      }
      if (thermoSystem.hasPhaseType("aqueous")) {
        hasAqueous = true;
        deAqueous = -waterOutStream.getThermoSystem().getEnthalpy() * waterOutletFlowFraction;
      }

      double deltaEnergy = inletStreamMixer.getOutletStream().getThermoSystem().getEnthalpy()
          - gasOutStream.getThermoSystem().getEnthalpy() * gasOutletFlowFraction + deOil
          + deAqueous;

      // Add external heat input (e.g., from flare radiation)
      if (isSetHeatInput() && getHeatInput() != 0.0) {
        deltaEnergy += getHeatInput(); // Heat input in watts (J/s)
      }

      double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy;
      thermoSystem.init(0);

      for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
        double dncomp = 0.0;
        dncomp +=
            inletStreamMixer.getOutletStream().getThermoSystem().getComponent(i).getNumberOfmoles();
        double dniOil = 0.0;
        double dniAqueous = 0.0;
        if (hasOil) {
          dniOil = -liquidOutStream.getThermoSystem().getComponent(i).getNumberOfmoles()
              * oilOutletFlowFraction;
        }
        if (hasAqueous) {
          dniAqueous = -waterOutStream.getThermoSystem().getComponent(i).getNumberOfmoles()
              * waterOutletFlowFraction;
        }
        dncomp += -gasOutStream.getThermoSystem().getComponent(i).getNumberOfmoles()
            * gasOutletFlowFraction + dniOil + dniAqueous;

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
        if (thermoSystem.hasPhaseType("oil")) {
          liquidOutStream.getFluid()
              .setMolarComposition(thermoSystem.getPhase("oil").getMolarComposition());
        }
        if (thermoSystem.hasPhaseType("aqueous")) {
          waterOutStream.getFluid()
              .setMolarComposition(thermoSystem.getPhase("aqueous").getMolarComposition());
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
        updateWaterAndOilLevelsFromPhases();
      } else {
        waterLevel = 0.0;
        oilLevel = 0.0;
      }
      liquidVolume = calcLiquidVolume();
      enforceHeadspace();

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

    if (thermoSystem.hasPhaseType("oil")) {
      liquidOutStream.getThermoSystem().setTemperature(temp);
      liquidOutStream.getThermoSystem().setPressure(pres);
    }

    if (thermoSystem.hasPhaseType("aqueous")) {
      waterOutStream.getThermoSystem().setTemperature(temp);
      waterOutStream.getThermoSystem().setPressure(pres);
    }

    inletStreamMixer.setPressure(pres);

    UUID id = UUID.randomUUID();

    inletStreamMixer.run(id);
    if (getThermoSystem().getNumberOfComponents() > 1) {
      gasOutStream.run(id);
      if (thermoSystem.hasPhaseType("oil")) {
        liquidOutStream.run(id);
      }
      if (thermoSystem.hasPhaseType("aqueous")) {
        waterOutStream.run(id);
      }
    } else {
      gasOutStream.getFluid().init(2);
      gasOutStream.getFluid().initPhysicalProperties("density");
      if (thermoSystem.hasPhaseType("oil")) {
        liquidOutStream.getFluid().init(2);
        liquidOutStream.getFluid().initPhysicalProperties("density");
      }
      if (thermoSystem.hasPhaseType("aqueous")) {
        waterOutStream.getFluid().init(2);
        waterOutStream.getFluid().initPhysicalProperties("density");
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    thermoSystem.display("from here " + getName());
    // gasOutStream.getThermoSystem().initPhysicalProperties();
    // waterOutStream.getThermoSystem().initPhysicalProperties();
    // try {
    // System.out.println("Gas Volume Flow Out " +
    // gasOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*gasOutStream.getThermoSystem().getPhase(0).getMolarMass()/gasOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
    // + " m^3/h");
    // } finally {
    // }
    // try {
    // waterOutStream.getThermoSystem().display();
    // waterOutStream.run();
    // System.out.println("Water/MEG Volume Flow Out " +
    // waterOutStream.getThermoSystem().getPhase(0).getNumberOfMolesInPhase()*waterOutStream.getThermoSystem().getPhase(0).getMolarMass()/waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity()*3600.0
    // + " m^3/h");
    // System.out.println("Density MEG " +
    // waterOutStream.getThermoSystem().getPhase(0).getPhysicalProperties().getDensity());
    // } finally {
    // }
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    double entrop = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      if (inletStreamMixer.getStream(i).getFlowRate(unit) > 1e-10) {
        inletStreamMixer.getStream(i).getFluid().init(3);
        entrop += inletStreamMixer.getStream(i).getFluid().getEntropy(unit);
      }
    }

    if (thermoSystem.hasPhaseType("aqueous")) {
      getWaterOutStream().getThermoSystem().init(3);
      entrop -= getWaterOutStream().getThermoSystem().getEntropy(unit);
    }
    if (thermoSystem.hasPhaseType("oil")) {
      getOilOutStream().getThermoSystem().init(3);
      entrop -= getOilOutStream().getThermoSystem().getEntropy(unit);
    }
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      entrop -= getGasOutStream().getThermoSystem().getEntropy(unit);
    }

    return entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      if (inletStreamMixer.getStream(i).getFlowRate(unit) > 1e-10) {
        inletStreamMixer.getStream(i).getFluid().init(3);
        inletFlow += inletStreamMixer.getStream(i).getFluid().getFlowRate(unit);
      }
    }

    // Only initialize and get flow rates for phases that actually exist
    double waterFlow = 0.0;
    double oilFlow = 0.0;
    double gasFlow = 0.0;

    if (thermoSystem.hasPhaseType("aqueous")) {
      getWaterOutStream().getThermoSystem().init(3);
      waterFlow = getWaterOutStream().getThermoSystem().getFlowRate(unit);
      if (waterFlow < 1e-10) {
        waterFlow = 0.0;
      }
    }

    if (thermoSystem.hasPhaseType("oil")) {
      getOilOutStream().getThermoSystem().init(3);
      oilFlow = getOilOutStream().getThermoSystem().getFlowRate(unit);
      if (oilFlow < 1e-10) {
        oilFlow = 0.0;
      }
    }

    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      gasFlow = getGasOutStream().getThermoSystem().getFlowRate(unit);
      if (gasFlow < 1e-10) {
        gasFlow = 0.0;
      }
    }

    return waterFlow + oilFlow + gasFlow - inletFlow;
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

    if (thermoSystem.hasPhaseType("aqueous")) {
      getWaterOutStream().getThermoSystem().init(3);
      exergy -= getWaterOutStream().getThermoSystem().getExergy(surroundingTemperature, unit);
    }
    if (thermoSystem.hasPhaseType("oil")) {
      getOilOutStream().getThermoSystem().init(3);
      exergy -= getOilOutStream().getThermoSystem().getExergy(surroundingTemperature, unit);
    }
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
      exergy -= getGasOutStream().getThermoSystem().getExergy(surroundingTemperature, unit);
    }

    return exergy;
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
}
