/*
 * Pipeline.java
 *
 * Created on 14. mars 2001, 22:30
 */

package neqsim.process.equipment.pipeline;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.fluidmechanics.flowsystem.FlowSystemInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Pipeline class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Pipeline extends TwoPortEquipment implements PipeLineInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Pipeline.class);

  protected String fileName = "c:/test5.nc";
  protected FlowSystemInterface pipe;
  protected SystemInterface system;
  String flowPattern = "stratified";
  double[] times;
  boolean equilibriumHeatTransfer = true;
  boolean equilibriumMassTransfer = false;
  int numberOfLegs = 1;
  int numberOfNodesInLeg = 30;
  double[] legHeights = {0, 0}; // ,0,0,0};
  double[] legPositions = {0.0, 1.0}; // 10.0,20.0,30.0,40.0};
  double[] pipeDiameters = {0.1507588, 0.1507588}; // , 1.207588, 1.207588, 1.207588};
  double[] outerTemperature = {278.0, 278.0}; // , 278.0, 278.0, 278.0};
  double[] pipeWallRoughness = {1e-5, 1e-5}; // , 1e-5, 1e-5, 1e-5};
  double[] outerHeatTransferCoeffs = {1e-5, 1e-5}; // , 1e-5, 1e-5, 1e-5};
  double[] wallHeatTransferCoeffs = {1e-5, 1e-5}; // , 1e-5, 1e-5, 1e-5};

  PipelineMechanicalDesign pipelineMechanicalDesign = null;

  /**
   * <p>
   * Constructor for Pipeline.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public Pipeline(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for Pipeline.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public Pipeline(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    pipelineMechanicalDesign = new PipelineMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public PipelineMechanicalDesign getMechanicalDesign() {
    return pipelineMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void setOutputFileName(String name) {
    this.fileName = name;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfLegs(int number) {
    this.numberOfLegs = number;
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityDuty() {
    return getOutStream().getFlowRate("m3/hr");
  }

  /** {@inheritDoc} */
  @Override
  public double getCapacityMax() {
    return getMechanicalDesign().maxDesignVolumeFlow;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfNodesInLeg(int number) {
    this.numberOfNodesInLeg = number;
  }

  /** {@inheritDoc} */
  @Override
  public void setHeightProfile(double[] heights) {
    if (heights.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of heights specified.");
      System.out.println("Number of heights must be number of legs + 1 ");
      return;
    }
    legHeights = new double[heights.length];
    System.arraycopy(heights, 0, legHeights, 0, legHeights.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setLegPositions(double[] positions) {
    if (positions.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of legpositions specified.");
      System.out.println("Number of legpositions must be number of legs + 1 ");
      return;
    }
    legPositions = new double[positions.length];
    System.arraycopy(positions, 0, legPositions, 0, legPositions.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeDiameters(double[] diameter) {
    if (diameter.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of diameters specified.");
      System.out.println("Number of diameters must be number of legs + 1 ");
      return;
    }
    pipeDiameters = new double[diameter.length];
    System.arraycopy(diameter, 0, pipeDiameters, 0, pipeDiameters.length);
  }

  /**
   * <p>
   * setPipeOuterHeatTransferCoefficients.
   * </p>
   *
   * @param heatCoefs an array of type double
   */
  public void setPipeOuterHeatTransferCoefficients(double[] heatCoefs) {
    if (heatCoefs.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of heatCoefs specified.");
      System.out.println("Number of heatCoefs must be number of legs + 1 ");
      return;
    }
    outerHeatTransferCoeffs = new double[heatCoefs.length];
    System.arraycopy(heatCoefs, 0, outerHeatTransferCoeffs, 0, outerHeatTransferCoeffs.length);
  }

  /**
   * <p>
   * setPipeWallHeatTransferCoefficients.
   * </p>
   *
   * @param heatCoefs an array of type double
   */
  public void setPipeWallHeatTransferCoefficients(double[] heatCoefs) {
    if (heatCoefs.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of heatCoefs specified.");
      System.out.println("Number of heatCoefs must be number of legs + 1 ");
      return;
    }
    wallHeatTransferCoeffs = new double[heatCoefs.length];
    System.arraycopy(heatCoefs, 0, wallHeatTransferCoeffs, 0, wallHeatTransferCoeffs.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setPipeWallRoughness(double[] rough) {
    if (rough.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of roughness points specified.");
      System.out.println("Number of roughness must be number of legs + 1 ");
      return;
    }
    pipeWallRoughness = new double[rough.length];
    System.arraycopy(rough, 0, pipeWallRoughness, 0, pipeWallRoughness.length);
  }

  /** {@inheritDoc} */
  @Override
  public void setOuterTemperatures(double[] outerTemp) {
    if (outerTemp.length != this.numberOfLegs + 1) {
      System.out.println("Wrong number of outer temperature points specified.");
      System.out.println("Number of outer temperature must be number of legs + 1 ");
      return;
    }
    outerTemperature = new double[outerTemp.length];
    System.arraycopy(outerTemp, 0, outerTemperature, 0, outerTemperature.length);
  }

  /**
   * <p>
   * Setter for the field <code>equilibriumMassTransfer</code>.
   * </p>
   *
   * @param test a boolean
   */
  public void setEquilibriumMassTransfer(boolean test) {
    equilibriumMassTransfer = test;
  }

  /**
   * <p>
   * Setter for the field <code>equilibriumHeatTransfer</code>.
   * </p>
   *
   * @param test a boolean
   */
  public void setEquilibriumHeatTransfer(boolean test) {
    equilibriumHeatTransfer = test;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    system = inStream.getThermoSystem().clone();
    GeometryDefinitionInterface[] pipeGemometry = new PipeData[numberOfLegs + 1];
    for (int i = 0; i < pipeDiameters.length; i++) {
      pipeGemometry[i] = new PipeData(pipeDiameters[i], pipeWallRoughness[i]);
    }
    pipe.setInletThermoSystem(system);
    pipe.setNumberOfLegs(numberOfLegs);
    pipe.setNumberOfNodesInLeg(numberOfNodesInLeg);
    pipe.setEquipmentGeometry(pipeGemometry);
    pipe.setLegOuterTemperatures(outerTemperature);
    pipe.setLegHeights(legHeights);
    pipe.setLegOuterHeatTransferCoefficients(outerHeatTransferCoeffs);
    pipe.setLegWallHeatTransferCoefficients(wallHeatTransferCoeffs);
    pipe.setLegPositions(legPositions);
    pipe.setInitialFlowPattern(flowPattern);
    pipe.createSystem();
    pipe.setEquilibriumMassTransfer(equilibriumMassTransfer);
    pipe.setEquilibriumHeatTransfer(equilibriumHeatTransfer);
    pipe.init();
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, UUID id) {
    pipe.solveTransient(2, id);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {}

  /** {@inheritDoc} */
  @Override
  public FlowSystemInterface getPipe() {
    return pipe;
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    this.flowPattern = flowPattern;
  }

  /**
   * Getter for property times.
   *
   * @return Value of property times.
   */
  public double[] getTimes() {
    return this.times;
  }

  /**
   * <p>
   * getSuperficialVelocity.
   * </p>
   *
   * @param phaseNum a int
   * @param node a int
   * @return a double
   */
  public double getSuperficialVelocity(int phaseNum, int node) {
    try {
      return outStream.getThermoSystem().getPhase(phaseNum).getNumberOfMolesInPhase()
          * outStream.getThermoSystem().getPhase(phaseNum).getMolarMass()
          / outStream.getThermoSystem().getPhase(phaseNum).getPhysicalProperties().getDensity()
          / (3.14 * pipeDiameters[node] * pipeDiameters[node] / 4.0);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
    }
    return 0.0;
  }

  /**
   * Setter for property times.
   *
   * @param times New value of property times.
   * @param systems an array of {@link neqsim.thermo.system.SystemInterface} objects
   * @param timestepininterval a int
   */
  public void setTimeSeries(double[] times, SystemInterface[] systems, int timestepininterval) {
    this.times = times;
    pipe.getTimeSeries().setTimes(times);
    pipe.getTimeSeries().setInletThermoSystems(systems);
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(timestepininterval);
  }

  /** {@inheritDoc} */
  @Override
  public double getEntropyProduction(String unit) {
    return outStream.getThermoSystem().getEntropy(unit)
        - inStream.getThermoSystem().getEntropy(unit);
  }

  /**
   * <p>
   * getOutletPressure.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getOutletPressure(String unit) {
    return outStream.getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new neqsim.process.util.monitor.PipelineResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(neqsim.process.util.report.ReportConfig cfg) {
    if (cfg != null && cfg
        .getDetailLevel(getName()) == neqsim.process.util.report.ReportConfig.DetailLevel.HIDE) {
      return null;
    }
    neqsim.process.util.monitor.PipelineResponse res =
        new neqsim.process.util.monitor.PipelineResponse(this);
    res.applyConfig(cfg);
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(res);
  }
}
