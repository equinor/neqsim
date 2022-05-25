/*
 * Separator.java
 *
 * Created on 12. mars 2001, 19:48
 */
package neqsim.processSimulation.processEquipment.separator;

import java.util.ArrayList;
import java.util.Objects;

import neqsim.processSimulation.mechanicalDesign.separator.SeparatorMechanicalDesign;
import neqsim.processSimulation.processEquipment.ProcessEquipmentBaseClass;
import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.separator.sectionType.ManwaySection;
import neqsim.processSimulation.processEquipment.separator.sectionType.MeshSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.NozzleSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection;
import neqsim.processSimulation.processEquipment.separator.sectionType.ValveSection;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * Separator class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Separator extends ProcessEquipmentBaseClass implements SeparatorInterface {
  private static final long serialVersionUID = 1000;

  SystemInterface thermoSystem, gasSystem, waterSystem, liquidSystem, thermoSystemCloned,
      thermoSystem2;
  private String orientation = "horizontal";
  StreamInterface gasOutStream;
  StreamInterface liquidOutStream;
  private double pressureDrop = 0.0;
  private double internalDiameter = 1.0;
  public int numberOfInputStreams = 0;
  Mixer inletStreamMixer = new Mixer("Separator Inlet Stream Mixer");
  private double efficiency = 1.0;
  private double liquidCarryoverFraction = 0.0;
  private double gasCarryunderFraction = 0.0;
  private double separatorLength = 5.0;
  double liquidVolume = 1.0, gasVolume = 18.0;
  private double liquidLevel = liquidVolume / (liquidVolume + gasVolume);
  private double designLiquidLevelFraction = 0.8;
  ArrayList<SeparatorSection> separatorSection = new ArrayList<SeparatorSection>();

  /**
   * Constructor for Separator.
   */
  @Deprecated
  public Separator() {
    super("Separator");
    setCalculateSteadyState(false);
  }

  /**
   * Constructor for Separator.
   *
   * @param inletStream a
   *                    {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *                    object
   */
  @Deprecated
  public Separator(StreamInterface inletStream) {
    this("Separator", inletStream);
  }

  /**
   * Constructor for Separator.
   * 
   * @param name Name of separator
   */
  public Separator(String name) {
    super(name);
  }

  /**
   * Constructor for Separator.
   *
   * @param name        a {@link java.lang.String} object
   * @param inletStream a
   *                    {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *                    object
   */
  public Separator(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  public SeparatorMechanicalDesign gMechanicalDesign() {
    return new SeparatorMechanicalDesign(this);
  }

  /**
   * <p>
   * setInletStream.
   * </p>
   *
   * @param inletStream a
   *                    {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
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
   * @param newStream a
   *                  {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
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
   * @return a
   *         {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *         object
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a
   *         {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *         object
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * getGas.
   * </p>
   *
   * @return a
   *         {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *         object
   */
  public StreamInterface getGas() {
    return getGasOutStream();
  }

  /**
   * <p>
   * getLiquid.
   * </p>
   *
   * @return a
   *         {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
   *         object
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
  public void run() {
    inletStreamMixer.run();
    thermoSystem2 = inletStreamMixer.getOutStream().getThermoSystem().clone();
    thermoSystem2.setPressure(thermoSystem2.getPressure() - pressureDrop);

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
      gasOutStream.run();
    } else {
      gasOutStream.getFluid().init(3);
    }
    if (thermoSystem2.hasPhaseType("aqueous") || thermoSystem2.hasPhaseType("oil")) {
      liquidOutStream.run();
    } else {
      liquidOutStream.getFluid().init(3);
    }
    // liquidOutStream.setThermoSystemFromPhase(thermoSystem2, "aqueous");
    try {
      thermoSystem = thermoSystem2.clone();
      thermoSystem.setTotalNumberOfMoles(1.0e-10);
      thermoSystem.init(1);
      // System.out.println("number of phases " + thermoSystem.getNumberOfPhases());
      double totalliquidVolume = 0.0;
      for (int j = 0; j < thermoSystem.getNumberOfPhases(); j++) {
        double relFact = gasVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);
        if (j >= 1) {
          relFact = liquidVolume / (thermoSystem2.getPhase(j).getVolume() * 1.0e-5);

          totalliquidVolume += liquidVolume / thermoSystem2.getPhase(j).getMolarVolume();
        }
        for (int i = 0; i < thermoSystem.getPhase(j).getNumberOfComponents(); i++) {
          thermoSystem.addComponent(thermoSystem.getPhase(j).getComponent(i).getComponentNumber(),
              relFact * thermoSystem2.getPhase(j).getComponent(i).getNumberOfMolesInPhase(), j);
        }
      }

      if (thermoSystem.hasPhaseType("gas")) {
        thermoSystem.setBeta(gasVolume / thermoSystem2.getPhase(0).getMolarVolume()
            / (gasVolume / thermoSystem2.getPhase(0).getMolarVolume() + totalliquidVolume));
      }
      thermoSystem.initBeta();
      thermoSystem.init(3);
      // System.out.println("moles in separator " + thermoSystem.getNumberOfMoles());
      // double volume1 = thermoSystem.getVolume();
      // System.out.println("volume1 bef " + volume1);
      // System.out.println("beta " + thermoSystem.getBeta());

      liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
      liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
          * getSeparatorLength();
      gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter()
          * getInternalDiameter() * getSeparatorLength();
      // System.out.println("moles out" +
      // liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    } catch (Exception e) {
      e.printStackTrace();
    }
    thermoSystem = thermoSystem2;
  }

  /** {@inheritDoc} */
  @Override
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
  public void runTransient(double dt) {
    if (getCalculateSteadyState()) {
      run();
      return;
    }

    inletStreamMixer.run();

    // System.out.println("moles out" +
    // liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    // double inMoles =
    // inletStreamMixer.getOutStream().getThermoSystem().getTotalNumberOfMoles();
    // double gasoutMoles = gasOutStream.getThermoSystem().getNumberOfMoles();
    // double liqoutMoles = liquidOutStream.getThermoSystem().getNumberOfMoles();
    thermoSystem.init(3);
    gasOutStream.getThermoSystem().init(3);
    liquidOutStream.getThermoSystem().init(3);
    double volume1 = thermoSystem.getVolume();
    // System.out.println("volume1 " + volume1);
    double deltaEnergy = inletStreamMixer.getOutStream().getThermoSystem().getEnthalpy()
        - gasOutStream.getThermoSystem().getEnthalpy()
        - liquidOutStream.getThermoSystem().getEnthalpy();
    // System.out.println("enthalph delta " + deltaEnergy);
    double newEnergy = thermoSystem.getInternalEnergy() + dt * deltaEnergy;
    for (int i = 0; i < thermoSystem.getPhase(0).getNumberOfComponents(); i++) {
      double dn = inletStreamMixer.getOutStream().getThermoSystem().getPhase(0).getComponent(i)
          .getNumberOfMolesInPhase()
          + inletStreamMixer.getOutStream().getThermoSystem().getPhase(1).getComponent(i)
              .getNumberOfMolesInPhase()
          - gasOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase()
          - liquidOutStream.getThermoSystem().getPhase(0).getComponent(i).getNumberOfMolesInPhase();
      // System.out.println("dn " + dn);
      thermoSystem.addComponent(inletStreamMixer.getOutStream().getThermoSystem().getPhase(0)
          .getComponent(i).getComponentNumber(), dn * dt);
    }
    ThermodynamicOperations thermoOps = new ThermodynamicOperations(thermoSystem);
    thermoOps.VUflash(volume1, newEnergy);

    setTempPres(thermoSystem.getTemperature(), thermoSystem.getPressure());

    liquidLevel = thermoSystem.getPhase(1).getVolume() * 1e-5 / (liquidVolume + gasVolume);
    // System.out.println("liquid level " + liquidLevel);
    liquidVolume = getLiquidLevel() * 3.14 / 4.0 * getInternalDiameter() * getInternalDiameter()
        * getSeparatorLength();
    gasVolume = (1.0 - getLiquidLevel()) * 3.14 / 4.0 * getInternalDiameter()
        * getInternalDiameter() * getSeparatorLength();
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

    inletStreamMixer.run();
    gasOutStream.run();
    liquidOutStream.run();
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
    return thermoSystem.getPhase(0).getTotalVolume() / 1e5
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
    System.out.println("derating " + derating);
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
   * @param phase a int
   * @return a double
   */
  public double getDeRatedGasLoadFactor(int phase) {
    thermoSystem.initPhysicalProperties();
    double derating = 1.0;
    double surfaceTension = thermoSystem.getInterphaseProperties().getSurfaceTension(phase - 1, phase);
    if (surfaceTension < 10.0e-3) {
      derating = 1.0 - 0.5 * (10.0e-3 - surfaceTension) / 10.0e-3;
    }
    System.out.println("derating " + derating);
    double term1 = (thermoSystem.getPhase(phase).getPhysicalProperties().getDensity()
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
   * @return a
   *         {@link neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection}
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
   *         {@link neqsim.processSimulation.processEquipment.separator.sectionType.SeparatorSection}
   *         object
   */
  public SeparatorSection getSeparatorSection(String name) {
    for (SeparatorSection sec : separatorSection) {
      if (sec.getName().equals(name)) {
        return sec;
      }
    }
    System.out.println("no section with name: " + name + " found.....");
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
    if (type.equals("vane")) {
      separatorSection.add(new SeparatorSection(name, type, this));
    } else if (type.equals("meshpad")) {
      separatorSection.add(new MeshSection(name, type, this));
    } else if (type.equals("manway")) {
      separatorSection.add(new ManwaySection(name, type, this));
    } else if (type.equals("valve")) {
      separatorSection.add(new ValveSection(name, type, this));
    } else if (type.equals("nozzle")) {
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
      } catch (Exception e) {
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
  public double getExergyChange(String unit, double sourrondingTemperature) {
    double exergy = 0.0;
    for (int i = 0; i < numberOfInputStreams; i++) {
      inletStreamMixer.getStream(i).getFluid().init(3);
      exergy += inletStreamMixer.getStream(i).getFluid().getExergy(sourrondingTemperature, unit);
    }
    getLiquidOutStream().getThermoSystem().init(3);
    getGasOutStream().getThermoSystem().init(3);
    return getLiquidOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit)
        + getGasOutStream().getThermoSystem().getExergy(sourrondingTemperature, unit) - exergy;
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
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
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

  /*
   * private class SeparatorReport extends Object{ public Double gasLoadFactor;
   * SeparatorReport(){
   * gasLoadFactor = getGasLoadFactor(); } }
   * 
   * public SeparatorReport getReport(){ return this.new SeparatorReport(); }
   */
}
