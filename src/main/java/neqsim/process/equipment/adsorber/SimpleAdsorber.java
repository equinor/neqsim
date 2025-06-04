package neqsim.process.equipment.adsorber;

import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.adsorber.AdsorberMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SimpleAdsorber class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleAdsorber extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  boolean setTemperature = false;
  StreamInterface[] outStream = new Stream[2];
  StreamInterface[] inStream = new Stream[2];
  SystemInterface system;
  protected double temperatureOut = 0;

  protected double dT = 0.0;

  private int numberOfStages = 5;
  private double numberOfTheoreticalStages = 3.0;
  double absorptionEfficiency = 0.5;
  private double HTU = 0.85;
  private double NTU = 2.0;
  private double stageEfficiency = 0.25;

  /**
   * <p>
   * Constructor for SimpleAdsorber.
   * </p>
   *
   * @param name name of the unit operation
   */
  public SimpleAdsorber(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for SimpleAdsorber.
   * </p>
   *
   * @param name name of the unit operation
   * @param inStream1 a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SimpleAdsorber(String name, StreamInterface inStream1) {
    this(name);
    this.inStream[0] = inStream1;
    this.inStream[1] = inStream1;
    outStream[0] = inStream1.clone();
    outStream[1] = inStream1.clone();
    setName(name);

    SystemInterface systemOut1 = inStream1.getThermoSystem().clone();
    outStream[0].setThermoSystem(systemOut1);

    double molCO2 = inStream1.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
    System.out.println("mol CO2 " + molCO2);
    SystemInterface systemOut0 = inStream1.getThermoSystem().clone();
    systemOut0.init(0);
    systemOut0.addComponent("MDEA", molCO2 * absorptionEfficiency);
    systemOut0.addComponent("water", molCO2 * absorptionEfficiency * 10.0);
    systemOut0.chemicalReactionInit();
    systemOut0.createDatabase(true);
    systemOut0.setMixingRule(4);
    outStream[1].setThermoSystem(systemOut0);
    outStream[1].run();
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    outStream[0].setName(name + "_Sout1");
    outStream[1].setName(name + "_Sout2");
  }

  /**
   * <p>
   * Setter for the field <code>dT</code>.
   * </p>
   *
   * @param dT a double
   */
  public void setdT(double dT) {
    this.dT = dT;
  }

  /**
   * <p>
   * Getter for the field <code>outStream</code>.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getOutStream(int i) {
    return outStream[i];
  }

  /**
   * <p>
   * setOutTemperature.
   * </p>
   *
   * @param temperature a double
   */
  public void setOutTemperature(double temperature) {
    this.temperatureOut = temperature;
  }

  /**
   * <p>
   * Get temperature of outstream i.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getOutTemperature(int i) {
    return outStream[i].getThermoSystem().getTemperature();
  }

  /**
   * <p>
   * Get temperature of instream i.
   * </p>
   *
   * @param i a int
   * @return a double
   */
  public double getInTemperature(int i) {
    return inStream[i].getThermoSystem().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface systemOut1 = inStream[1].getThermoSystem().clone();
    outStream[0].setThermoSystem(systemOut1);
    outStream[0].run(id);
    outStream[1].run(id);

    double error = 1e5;
    error = absorptionEfficiency - (outStream[1].getThermoSystem().getPhase(1).getComponent("CO2")
        .getNumberOfMolesInPhase()
        + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-")
            .getNumberOfMolesInPhase())
        / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase()
            + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                .getNumberOfMolesInPhase());
    int iter = 0;
    do {
      iter++;
      double factor =
          (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase()
              + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                  .getNumberOfMolesInPhase());
      // outStream[1].getThermoSystem().addComponent("CO2",(20.0-outStream[1].getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfMolesInPhase()),0);
      outStream[1].getThermoSystem().addComponent("MDEA", -error * factor);
      outStream[1].getThermoSystem().addComponent("water", -error * 10.0 * factor);
      outStream[1].run(id);
      error = absorptionEfficiency - ((outStream[1].getThermoSystem().getPhase(1)
          .getComponent("CO2").getNumberOfMolesInPhase()
          + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-")
              .getNumberOfMolesInPhase())
          / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA")
              .getNumberOfMolesInPhase()
              + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+")
                  .getNumberOfMolesInPhase()));

      System.out.println("error " + error);
    } while (Math.abs(error) > 1e-4 && iter < 30
        && outStream[1].getThermoSystem().getPhase(1).getBeta() > 0
        && outStream[0].getThermoSystem().getPhase(1).getBeta() > 0);
    outStream[1].setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    outStream[0].displayResult();
    outStream[1].displayResult();
  }

  /**
   * <p>
   * setAproachToEquilibrium.
   * </p>
   *
   * @param eff a double
   */
  public void setAproachToEquilibrium(double eff) {
    this.absorptionEfficiency = eff;
  }

  /**
   * <p>
   * Getter for the field <code>numberOfTheoreticalStages</code>.
   * </p>
   *
   * @return a double
   */
  public double getNumberOfTheoreticalStages() {
    return numberOfTheoreticalStages;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfTheoreticalStages</code>.
   * </p>
   *
   * @param numberOfTheoreticalStages a double
   */
  public void setNumberOfTheoreticalStages(double numberOfTheoreticalStages) {
    this.numberOfTheoreticalStages = numberOfTheoreticalStages;
  }

  /**
   * <p>
   * Getter for the field <code>numberOfStages</code>.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * <p>
   * Setter for the field <code>numberOfStages</code>.
   * </p>
   *
   * @param numberOfStages a int
   */
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * <p>
   * Getter for the field <code>stageEfficiency</code>.
   * </p>
   *
   * @return a double
   */
  public double getStageEfficiency() {
    return stageEfficiency;
  }

  /**
   * <p>
   * Setter for the field <code>stageEfficiency</code>.
   * </p>
   *
   * @param stageEfficiency a double
   */
  public void setStageEfficiency(double stageEfficiency) {
    this.stageEfficiency = stageEfficiency;
  }

  /**
   * <p>
   * getHTU.
   * </p>
   *
   * @return a double
   */
  public double getHTU() {
    return HTU;
  }

  /**
   * <p>
   * setHTU.
   * </p>
   *
   * @param HTU a double
   */
  public void setHTU(double HTU) {
    this.HTU = HTU;
  }

  /**
   * <p>
   * getNTU.
   * </p>
   *
   * @return a double
   */
  public double getNTU() {
    return NTU;
  }

  /**
   * <p>
   * setNTU.
   * </p>
   *
   * @param NTU a double
   */
  public void setNTU(double NTU) {
    this.NTU = NTU;
  }

  /** {@inheritDoc} */
  @Override
  public AdsorberMechanicalDesign getMechanicalDesign() {
    return new AdsorberMechanicalDesign(this);
  }
}
