package neqsim.process.equipment.absorber;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.absorber.AbsorberMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * SimpleAbsorber class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SimpleAbsorber extends Separator implements AbsorberInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger for diagnostic output. */
  private static final Logger logger = LogManager.getLogger(SimpleAbsorber.class);

  /** Fixed-point convergence tolerance used by the legacy loading calculation. */
  private static final double CONVERGENCE_TOLERANCE = 1.0e-4;

  /** Maximum number of legacy loading iterations. */
  private static final int MAX_ITERATIONS = 30;

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
  private double fsFactor = 0.0;

  /** Iterations completed by the most recent run. */
  private int lastIterationCount = 0;

  /** Final loading residual from the most recent run. */
  private double lastConvergenceError = Double.NaN;

  /** Whether the most recent run satisfied the fixed-point tolerance. */
  private boolean lastRunConverged = false;

  /** Exit reason from the most recent run. */
  private String lastRunExitReason = "NOT_RUN";

  /**
   * Constructor for SimpleAbsorber.
   *
   * @param name name of absorber
   */
  public SimpleAbsorber(String name) {
    super(name);
  }

  /**
   * Constructor for SimpleAbsorber.
   *
   * @param name name of absorber
   * @param inStream1 a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SimpleAbsorber(String name, StreamInterface inStream1) {
    super(name);
    this.inStream[0] = inStream1;
    this.inStream[1] = inStream1;
    outStream[0] = inStream1.clone();
    outStream[1] = inStream1.clone();
    setName(name);

    SystemInterface systemOut1 = inStream1.getThermoSystem().clone();
    outStream[0].setThermoSystem(systemOut1);

    double molCO2 = inStream1.getThermoSystem().getPhase(0).getComponent("CO2").getNumberOfmoles();
    logger.debug("{}: inlet CO2 amount = {} mol", getName(), molCO2);
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
    if (outStream[0] != null) {
      outStream[0].setName(name + "_Sout1");
    }
    if (outStream[1] != null) {
      outStream[1].setName(name + "_Sout2");
    }
  }

  /**
   * Setter for the field <code>dT</code>.
   *
   * @param dT a double
   */
  public void setdT(double dT) {
    this.dT = dT;
  }

  /**
   * Getter for the field <code>outStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getOutletStream() {
    return outStream[0];
  }

  /**
   * Getter for the field <code>outStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   * @deprecated use {@link #getOutletStream()} instead
   */
  @Deprecated
  public StreamInterface getOutStream() {
    return getOutletStream();
  }

  /**
   * Getter for the field <code>outStream</code>.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getOutletStream(int i) {
    return outStream[i];
  }

  /**
   * Getter for the field <code>outStream</code>.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   * @deprecated use {@link #getOutletStream(int)} instead
   */
  @Deprecated
  public StreamInterface getOutStream(int i) {
    return getOutletStream(i);
  }

  /**
   * getSolventInStream.
   *
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getSolventInStream() {
    return inStream[0];
  }

  /**
   * Getter for the field <code>inStream</code>.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.Stream} object
   */
  public StreamInterface getInStream(int i) {
    return inStream[i];
  }

  /**
   * Set the outlet temperature.
   *
   * @param temperature Temperature in Kelvin
   */
  public void setOutletTemperature(double temperature) {
    this.temperatureOut = temperature;
  }

  /**
   * Setter for property <code>temperatureOut</code>.
   *
   * @param temperature Temperature in Kelvin
   * @deprecated use {@link #setOutletTemperature(double)} instead
   */
  @Deprecated
  public void setOutTemperature(double temperature) {
    setOutletTemperature(temperature);
  }

  /**
   * Get temperature of outstream i.
   *
   * @param i a int
   * @return Temperature of outstream i in Kelvin
   */
  public double getOutletTemperature(int i) {
    return outStream[i].getThermoSystem().getTemperature();
  }

  /**
   * Get temperature of outstream i.
   *
   * @param i a int
   * @return Temperature of outstream i in Kelvin
   * @deprecated use {@link #getOutletTemperature(int)} instead
   */
  @Deprecated
  public double getOutTemperature(int i) {
    return getOutletTemperature(i);
  }

  /**
   * * Get temperature of instream i.
   *
   * @param i a int
   * @return Temperature of instream i in Kelvin
   */
  public double getInTemperature(int i) {
    return inStream[i].getThermoSystem().getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    lastIterationCount = 0;
    lastConvergenceError = Double.NaN;
    lastRunConverged = false;
    lastRunExitReason = "RUNNING";

    SystemInterface systemOut1 = inStream[1].getThermoSystem().clone();
    outStream[0].setThermoSystem(systemOut1);
    outStream[0].run(id);
    outStream[1].run(id);

    double error = absorptionEfficiency
        - (outStream[1].getThermoSystem().getPhase(1).getComponent("CO2").getNumberOfMolesInPhase()
            + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-").getNumberOfMolesInPhase())
            / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase()
                + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase());
    int iter = 0;
    do {
      iter++;
      double factor = (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase()
          + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase());
      outStream[1].getThermoSystem().addComponent("MDEA", -error * factor);
      outStream[1].getThermoSystem().addComponent("water", -error * 10.0 * factor);
      outStream[1].run();
      error = absorptionEfficiency
          - ((outStream[1].getThermoSystem().getPhase(1).getComponent("CO2").getNumberOfMolesInPhase()
              + outStream[1].getThermoSystem().getPhase(1).getComponent("HCO3-").getNumberOfMolesInPhase())
              / (outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA").getNumberOfMolesInPhase()
                  + outStream[1].getThermoSystem().getPhase(1).getComponent("MDEA+").getNumberOfMolesInPhase()));
      logger.debug("{}: loading iteration {} residual {}", getName(), iter, error);
    } while (Math.abs(error) > CONVERGENCE_TOLERANCE && iter < MAX_ITERATIONS
        && outStream[1].getThermoSystem().getPhase(1).getBeta() > 0
        && outStream[0].getThermoSystem().getPhase(1).getBeta() > 0);

    lastIterationCount = iter;
    lastConvergenceError = error;
    lastRunConverged = Double.isFinite(error) && Math.abs(error) <= CONVERGENCE_TOLERANCE;
    if (lastRunConverged) {
      lastRunExitReason = "CONVERGED";
    } else if (!Double.isFinite(error)) {
      lastRunExitReason = "NON_FINITE_RESIDUAL";
    } else if (iter >= MAX_ITERATIONS) {
      lastRunExitReason = "MAX_ITERATIONS";
    } else {
      lastRunExitReason = "PHASE_LOSS";
    }

    outStream[1].setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /**
   * Reports whether the most recent fixed-point loading calculation met its tolerance.
   *
   * @return true only when the most recent run converged
   */
  public boolean isLastRunConverged() {
    return lastRunConverged;
  }

  /**
   * Gets the number of fixed-point iterations completed by the most recent run.
   *
   * @return iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Gets the final loading residual from the most recent run.
   *
   * @return dimensionless loading residual, or NaN before a run
   */
  public double getLastConvergenceError() {
    return lastConvergenceError;
  }

  /**
   * Gets the exit reason from the most recent loading calculation.
   *
   * @return one of NOT_RUN, CONVERGED, NON_FINITE_RESIDUAL, MAX_ITERATIONS, or PHASE_LOSS
   */
  public String getLastRunExitReason() {
    return lastRunExitReason;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    outStream[0].displayResult();
    outStream[1].displayResult();
  }

  /** {@inheritDoc} */
  @Override
  public void setAproachToEquilibrium(double eff) {
    this.absorptionEfficiency = eff;
  }

  /**
   * Getter for the field <code>numberOfTheoreticalStages</code>.
   *
   * @return a double
   */
  public double getNumberOfTheoreticalStages() {
    return numberOfTheoreticalStages;
  }

  /**
   * Stores a legacy theoretical-stage metadata value. This value is not used by the SimpleAbsorber calculation.
   *
   * @param numberOfTheoreticalStages a double
   * @deprecated SimpleAbsorber has no staged calculation; use a staged or rate-based absorber model when stage count
   *             must affect the result
   */
  @Deprecated
  public void setNumberOfTheoreticalStages(double numberOfTheoreticalStages) {
    this.numberOfTheoreticalStages = numberOfTheoreticalStages;
  }

  /**
   * Getter for the field <code>numberOfStages</code>.
   *
   * @return a int
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Stores a legacy stage-count metadata value. This value is not used by the SimpleAbsorber calculation.
   *
   * @param numberOfStages a int
   * @deprecated SimpleAbsorber has no staged calculation; use a staged or rate-based absorber model when stage count
   *             must affect the result
   */
  @Deprecated
  public void setNumberOfStages(int numberOfStages) {
    this.numberOfStages = numberOfStages;
  }

  /**
   * Getter for the field <code>stageEfficiency</code>.
   *
   * @return a double
   */
  public double getStageEfficiency() {
    return stageEfficiency;
  }

  /**
   * Stores a legacy stage-efficiency metadata value. This value is not used by the SimpleAbsorber calculation.
   *
   * @param stageEfficiency a double
   * @deprecated SimpleAbsorber has no stage-efficiency calculation
   */
  @Deprecated
  public void setStageEfficiency(double stageEfficiency) {
    this.stageEfficiency = stageEfficiency;
  }

  /**
   * getHTU.
   *
   * @return a double
   */
  public double getHTU() {
    return HTU;
  }

  /**
   * Stores a legacy HTU metadata value. This value is not used by the SimpleAbsorber calculation.
   *
   * @param HTU a double
   * @deprecated SimpleAbsorber does not use an HTU/NTU rate-based model
   */
  @Deprecated
  public void setHTU(double HTU) {
    this.HTU = HTU;
  }

  /**
   * getNTU.
   *
   * @return a double
   */
  public double getNTU() {
    return NTU;
  }

  /**
   * Stores a legacy NTU metadata value. This value is not used by the SimpleAbsorber calculation.
   *
   * @param NTU a double
   * @deprecated SimpleAbsorber does not use an HTU/NTU rate-based model
   */
  @Deprecated
  public void setNTU(double NTU) {
    this.NTU = NTU;
  }

  /**
   * Getter for the field <code>fsFactor</code>.
   *
   * @return a double
   */
  public double getFsFactor() {
    if (getGasOutStream() == null || getGasOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
        * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
  }

  /**
   * getWettingRate.
   *
   * @return a double
   */
  public double getWettingRate() {
    if (getLiquidOutStream() == null || getLiquidOutStream().getThermoSystem() == null) {
      return 0.0;
    }
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    if (intArea <= 0.0) {
      return 0.0;
    }
    return getLiquidOutStream().getThermoSystem().getFlowRate("m3/hr") / intArea;
  }

  /** {@inheritDoc} */
  @Override
  public AbsorberMechanicalDesign getMechanicalDesign() {
    return new AbsorberMechanicalDesign(this);
  }
}
