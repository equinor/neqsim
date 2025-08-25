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

  /** Length of separator volume. */
  private double separatorLength = 5.0;
  /** Inner diameter/height of separator volume. */
  private double internalDiameter = 1.0;

  /** LiquidLevel as volume fraction of liquidvolume/(liquid + gas volume). */
  private double liquidLevel = 0.5;
  double liquidVolume =
      Math.PI * internalDiameter * internalDiameter / 4.0 * separatorLength * liquidLevel;
  double gasVolume =
      Math.PI * internalDiameter * internalDiameter / 4.0 * separatorLength * (1.0 - liquidLevel);
  private double designLiquidLevelFraction = 0.8;
  ArrayList<SeparatorSection> separatorSection = new ArrayList<SeparatorSection>();

  SeparatorMechanicalDesign separatorMechanicalDesign;
  private double lastEnthalpy;
  private double lastFlowRate;
  private double lastPressure;

  /**
   * Constructor for Separator.
   *
   * @param name Name of separator
   */
  public Separator(String name) {
    super(name);
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
    if (liquidOutStream.getFluid().getClass().getName().equals("neqsim.thermo.system.SystemSoreideWhitson")) {
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
    if (gasOutStream.getFluid().getClass().getName().equals("neqsim.thermo.system.SystemSoreideWhitson")) {
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
    if (Math.abs(pressureDrop) > 1e-6) {
      ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem2);
      ops.TPflash();
      thermoSystem2.initProperties();
    }

    if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.setThermoSystemFromPhase(thermoSystem2, "gas");
      gasOutStream.getFluid().init(2);
    } else {
      gasOutStream.setThermoSystem(thermoSystem2.getEmptySystemClone());
    }
    if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "liquid");
      liquidOutStream.getFluid().init(2);
    } else {
      liquidOutStream.setThermoSystem(thermoSystem2.getEmptySystemClone());
    }
    if (thermoSystem2.hasPhaseType("gas")) {
      gasOutStream.run(id);
    } else {
      gasOutStream.getFluid().init(3);
    }
    if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.run(id);
    } else {
      try {
        liquidOutStream.getFluid().init(3);
      } catch (Exception e) {
        logger.error(e.getMessage());
      }
    }
    if (getCalculateSteadyState()) {
      thermoSystem = thermoSystem2;
    } else {
      try {
        liquidVolume =
            Math.PI * internalDiameter * internalDiameter / 4.0 * separatorLength * liquidLevel;
        gasVolume = Math.PI * internalDiameter * internalDiameter / 4.0 * separatorLength
            * (1.0 - liquidLevel);
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
            thermoSystem.addComponent(i, (relFact - 1.0)
                * thermoSystem2.getPhase(j).getComponent(i).getNumberOfMolesInPhase(), j);
          }
        }
        ThermodynamicOperations ops = new ThermodynamicOperations(thermoSystem);
        ops.TPflash();
        thermoSystem.init(3);
        thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
        if (thermoSystem.hasPhaseType("oil") || thermoSystem.hasPhaseType("aqueous")) {
          liquidLevel = thermoSystem.getPhase(1).getVolume("m3") / (liquidVolume + gasVolume);
          liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter()
              * getInternalDiameter() * getSeparatorLength();
        } else {
          liquidLevel = 0.0;
          liquidVolume = 0.0;
        }

        gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter()
            * getInternalDiameter() * getSeparatorLength();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
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
      inletStreamMixer.run(id);
      thermoSystem.init(3);
      thermoSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
      try {
        gasOutStream.getThermoSystem().init(3);
        liquidOutStream.getThermoSystem().init(3);
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
        liquidLevel = volumeLoc / (liquidVolume + gasVolume);
      }
      liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
          * getSeparatorLength();
      gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter()
          * getInternalDiameter() * getSeparatorLength();
      // System.out.println("gas volume " + gasVolume + " liq volime " +
      // liquidVolume);
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

  /** {@inheritDoc} */
  @Override
  public void setLiquidLevel(double liquidlev) {
    liquidLevel = liquidlev;
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
    this.internalDiameter = diameter;
  }

  /**
   * <p>
   * getGasSuperficialVelocity.
   * </p>
   *
   * @return a double
   */
  public double getGasSuperficialVelocity() {
    return thermoSystem.getPhase(0).getVolume("m3")
        / (neqsim.thermo.ThermodynamicConstantsInterface.pi * getInternalDiameter()
            * getInternalDiameter() / 4.0);
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
      gasAreaFraction = 1.0 - (liquidVolume / (liquidVolume + gasVolume));
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
    this.orientation = orientation;
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
    this.separatorLength = separatorLength;
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
    if (thermoSystem.hasPhaseType("aqueous") || thermoSystem.hasPhaseType("oil")) {
      try {
        getLiquidOutStream().getThermoSystem().init(3);
      } catch (Exception ex) {
      }
    }
    if (thermoSystem.hasPhaseType("gas")) {
      getGasOutStream().getThermoSystem().init(3);
    }

    return getLiquidOutStream().getThermoSystem().getEntropy(unit)
        + getGasOutStream().getThermoSystem().getEntropy(unit) - entrop;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double flow = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      flow += inletStreamMixer.getStream(i).getFluid().getFlowRate(unit);
    }
    getLiquidOutStream().getThermoSystem().init(3);
    getGasOutStream().getThermoSystem().init(3);
    return getLiquidOutStream().getThermoSystem().getFlowRate(unit)
        + getGasOutStream().getThermoSystem().getFlowRate(unit) - flow;
  }

  /** {@inheritDoc} */
  @Override
  public double getExergyChange(String unit, double surroundingTemperature) {
    double exergy = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      exergy += inletStreamMixer.getStream(i).getFluid().getExergy(surroundingTemperature, unit);
    }
    getLiquidOutStream().getThermoSystem().init(3);
    getGasOutStream().getThermoSystem().init(3);
    return getLiquidOutStream().getThermoSystem().getExergy(surroundingTemperature, unit)
        + getGasOutStream().getThermoSystem().getExergy(surroundingTemperature, unit) - exergy;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(designLiquidLevelFraction, efficiency,
        gasCarryunderFraction, gasOutStream, gasSystem, gasVolume, inletStreamMixer,
        internalDiameter, liquidCarryoverFraction, liquidLevel, liquidOutStream, liquidSystem,
        liquidVolume, numberOfInputStreams, orientation, pressureDrop, separatorLength,
        separatorSection, thermoSystem, thermoSystem2, thermoSystemCloned, waterSystem);
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
        && Objects.equals(orientation, other.orientation)
        && Double.doubleToLongBits(pressureDrop) == Double.doubleToLongBits(other.pressureDrop)
        && Double.doubleToLongBits(separatorLength) == Double
            .doubleToLongBits(other.separatorLength)
        && Objects.equals(separatorSection, other.separatorSection)
        && Objects.equals(thermoSystem, other.thermoSystem)
        && Objects.equals(thermoSystem2, other.thermoSystem2)
        && Objects.equals(thermoSystemCloned, other.thermoSystemCloned)
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

  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    SeparatorResponse res = new SeparatorResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
  }

  /*
   * private class SeparatorReport extends Object{ public Double gasLoadFactor; SeparatorReport(){
   * gasLoadFactor = getGasLoadFactor(); } }
   *
   * public SeparatorReport getReport(){ return this.new SeparatorReport(); }
   */
}
