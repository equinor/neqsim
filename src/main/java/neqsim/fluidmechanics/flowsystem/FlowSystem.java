package neqsim.fluidmechanics.flowsystem;

import java.util.UUID;
import neqsim.fluidmechanics.flowleg.FlowLegInterface;
import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.fluidmechanics.flowsolver.FlowSolverInterface;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.util.fluidmechanicsvisualization.flowsystemvisualization.FlowSystemVisualizationInterface;
import neqsim.fluidmechanics.util.timeseries.TimeSeries;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * Abstract FlowSystem class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public abstract class FlowSystem implements FlowSystemInterface, java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Unique identifier of which solve/run call was last called successfully.
   */
  protected UUID calcIdentifier;

  protected FlowNodeInterface[] flowNode;
  protected FlowLegInterface[] flowLeg;
  protected String initFlowPattern = "annular";
  protected FlowSystemVisualizationInterface display;
  protected TimeSeries timeSeries = new TimeSeries();
  protected GeometryDefinitionInterface[] equipmentGeometry;
  protected SystemInterface thermoSystem;
  protected ThermodynamicOperations thermoOperations;
  protected double inletTemperature = 0;

  protected double inletPressure = 0;

  protected double endPressure = 0;

  protected double systemLength = 0;

  protected int numberOfFlowLegs = 0;

  protected int totalNumberOfNodes = 25;

  int[] numberOfNodesInLeg;
  double[] legHeights;

  double[] legPositions;

  double[] legOuterTemperatures;

  double[] legOuterHeatTransferCoefficients;

  double[] legWallHeatTransferCoefficients;

  protected FlowSolverInterface flowSolver;
  double inletMolarLiquidFlowRate = 0;

  double inletMolarGasFlowRate = 0;

  boolean equilibriumHeatTransfer = true;

  boolean equilibriumMassTransfer = false;

  /**
   * <p>
   * Constructor for FlowSystem.
   * </p>
   */
  public FlowSystem() {}

  /**
   * <p>
   * Constructor for FlowSystem.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public FlowSystem(SystemInterface system) {
    this.setInletThermoSystem(system);
  }

  /** {@inheritDoc} */
  @Override
  public void init() {}

  /** {@inheritDoc} */
  @Override
  public void createSystem() {
    thermoOperations = new ThermodynamicOperations(thermoSystem);
    this.flowLegInit();
  }

  /** {@inheritDoc} */
  @Override
  public FlowSolverInterface getSolver() {
    return flowSolver;
  }

  /** {@inheritDoc} */
  @Override
  public TimeSeries getTimeSeries() {
    return timeSeries;
  }

  /**
   * <p>
   * flowLegInit.
   * </p>
   */
  public void flowLegInit() {
    // TODO: add checks that input arguments have correct size to avoid generic
    // IndexOutOfBoundsException
    for (int i = 0; i < numberOfFlowLegs; i++) {
      this.flowLeg[i].setThermoSystem(thermoSystem);
      this.flowLeg[i].setEquipmentGeometry(equipmentGeometry[i]);
      this.flowLeg[i].setNumberOfNodes(numberOfNodesInLeg[i]);
      this.flowLeg[i].setHeightCoordinates(legHeights[i], legHeights[i + 1]);
      this.flowLeg[i].setOuterTemperatures(legOuterTemperatures[i], legOuterTemperatures[i + 1]);
      this.flowLeg[i].setLongitudionalCoordinates(legPositions[i], legPositions[i + 1]);
      this.flowLeg[i].setOuterHeatTransferCoefficients(legOuterHeatTransferCoefficients[i],
          legOuterHeatTransferCoefficients[i + 1]);
      this.flowLeg[i].setWallHeatTransferCoefficients(legWallHeatTransferCoefficients[i],
          legWallHeatTransferCoefficients[i + 1]);
      this.flowLeg[i].createFlowNodes(flowNode[0]);
    }

    totalNumberOfNodes = this.calcTotalNumberOfNodes();
    // System.out.println("total number of nodes : " + totalNumberOfNodes);
  }

  /** {@inheritDoc} */
  @Override
  public void setNodes() {
    flowNode[0].setDistanceToCenterOfNode(0.0);
    flowNode[0].setVerticalPositionOfNode(legHeights[0]);
    flowNode[0].setLengthOfNode(systemLength / 1000.0);
    flowNode[0].init();

    int k = 1;
    for (int i = 0; i < numberOfFlowLegs; i++) {
      for (int j = 0; j < getNumberOfNodesInLeg(i); j++) {
        this.flowNode[k++] = flowLeg[i].getNode(j);
      }
    }
    flowNode[totalNumberOfNodes - 1] = flowNode[totalNumberOfNodes - 2].getNextNode();
    flowNode[totalNumberOfNodes - 1].setLengthOfNode(systemLength / 1000.0);
    flowNode[totalNumberOfNodes - 1].setDistanceToCenterOfNode(
        legPositions[numberOfFlowLegs] + flowNode[totalNumberOfNodes - 1].getLengthOfNode() / 2.0);
    flowNode[totalNumberOfNodes - 1].setVerticalPositionOfNode(legHeights[numberOfFlowLegs]);
    if (endPressure != 0) {
      flowNode[totalNumberOfNodes - 1].getBulkSystem().setPressure(endPressure);
    }
    flowNode[totalNumberOfNodes - 1].init();
  }

  /** {@inheritDoc} */
  @Override
  public void setInletThermoSystem(SystemInterface thermoSystem) {
    this.thermoSystem = thermoSystem;
    this.inletPressure = thermoSystem.getPressure();
    this.inletTemperature = thermoSystem.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getSystemLength() {
    return systemLength;
  }

  /**
   * <p>
   * calcTotalNumberOfNodes.
   * </p>
   *
   * @return a int
   */
  public int calcTotalNumberOfNodes() {
    int number = 0;
    for (int i = 0; i < this.numberOfFlowLegs; i++) {
      number += flowLeg[i].getNumberOfNodes();
    }
    this.totalNumberOfNodes = number + 2;
    return this.totalNumberOfNodes;
  }

  /** {@inheritDoc} */
  @Override
  public int getTotalNumberOfNodes() {
    return this.totalNumberOfNodes;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletTemperature() {
    return this.inletTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void setEquipmentGeometry(GeometryDefinitionInterface[] equipmentGeometry) {
    this.equipmentGeometry = equipmentGeometry;
  }

  /** {@inheritDoc} */
  @Override
  public void setEndPressure(double endPressure) {
    this.endPressure = endPressure;
  }

  /** {@inheritDoc} */
  @Override
  public double getInletPressure() {
    return this.inletPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfLegs(int numberOfFlowLegs) {
    this.numberOfFlowLegs = numberOfFlowLegs;
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface getNode(int i) {
    return this.flowNode[i];
  }

  /** {@inheritDoc} */
  @Override
  public FlowNodeInterface[] getFlowNodes() {
    return this.flowNode;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfLegs() {
    return this.numberOfFlowLegs;
  }

  /** {@inheritDoc} */
  @Override
  public FlowSystemVisualizationInterface getDisplay() {
    return display;
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfNodesInLeg(int numberOfNodesInLeg) {
    this.numberOfNodesInLeg = new int[this.getNumberOfLegs()];
    for (int i = 0; i < this.getNumberOfLegs(); i++) {
      this.numberOfNodesInLeg[i] = numberOfNodesInLeg;
    }
    totalNumberOfNodes = numberOfNodesInLeg * this.getNumberOfLegs() + 2;
  }

  /** {@inheritDoc} */
  @Override
  public int getNumberOfNodesInLeg(int i) {
    return this.numberOfNodesInLeg[i];
  }

  /** {@inheritDoc} */
  @Override
  public void setLegHeights(double[] legHeights) {
    this.legHeights = legHeights;
  }

  /** {@inheritDoc} */
  @Override
  public void setLegPositions(double[] legPositions) {
    this.legPositions = legPositions;
    this.systemLength = legPositions[legPositions.length - 1];
  }

  /** {@inheritDoc} */
  @Override
  public void setLegOuterTemperatures(double[] temps) {
    this.legOuterTemperatures = temps;
  }

  /** {@inheritDoc} */
  @Override
  public void setLegOuterHeatTransferCoefficients(double[] coefs) {
    this.legOuterHeatTransferCoefficients = coefs;
  }

  /** {@inheritDoc} */
  @Override
  public void setLegWallHeatTransferCoefficients(double[] coefs) {
    this.legWallHeatTransferCoefficients = coefs;
  }

  /** {@inheritDoc} */
  @Override
  public double[] getLegHeights() {
    return this.legHeights;
  }

  /** {@inheritDoc} */
  @Override
  public void print() {
    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      System.out.println("node " + flowNode[i].getDistanceToCenterOfNode() + " pressure: "
          + flowNode[i].getBulkSystem().getPhases()[0].getPressure() + " temperature: "
          + flowNode[i].getBulkSystem().getPhases()[1].getTemperature() + "  flow: "
          + flowNode[i].getMassFlowRate(0) + " velocity: " + flowNode[i].getVelocity()
          + " reynolds number " + flowNode[i].getReynoldsNumber() + " friction : "
          + flowNode[i].getWallFrictionFactor() + " x1 : "
          + flowNode[i].getBulkSystem().getPhases()[0].getComponent(1).getx());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcFluxes() {}

  /** {@inheritDoc} */
  @Override
  public double getTotalMolarMassTransferRate(int component) {
    double tot = 0.0;
    for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
      tot += flowNode[i].getFluidBoundary().getInterphaseMolarFlux(component)
          * flowNode[i].getInterphaseContactArea();
    }
    return tot;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalMolarMassTransferRate(int component, int lastNode) {
    double tot = 0.0;
    for (int i = 0; i < lastNode; i++) {
      tot += flowNode[i].getFluidBoundary().getInterphaseMolarFlux(component)
          * flowNode[i].getInterphaseContactArea();
    }
    return tot;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalPressureDrop() {
    return flowNode[0].getBulkSystem().getPressure()
        - flowNode[getTotalNumberOfNodes() - 1].getBulkSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalPressureDrop(int lastNode) {
    return flowNode[0].getBulkSystem().getPressure()
        - flowNode[lastNode].getBulkSystem().getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public void setInitialFlowPattern(String flowPattern) {
    this.initFlowPattern = flowPattern;
  }

  /** {@inheritDoc} */
  @Override
  public void setFlowPattern(String flowPattern) {
    this.initFlowPattern = flowPattern;
    for (int i = 0; i < this.getNumberOfLegs(); i++) {
      flowLeg[i].setFlowPattern(flowPattern);
    }
  }

  /**
   * <p>
   * setEquilibriumMassTransferModel.
   * </p>
   *
   * @param startNode a int
   * @param endNode a int
   */
  public void setEquilibriumMassTransferModel(int startNode, int endNode) {
    for (int i = startNode; i < endNode; i++) {
      if (flowNode[i].getBulkSystem().isChemicalSystem()) {
        flowNode[i].setInterphaseModelType(0);
      } else {
        flowNode[i].setInterphaseModelType(0);
      }
      flowNode[i].getFluidBoundary().setMassTransferCalc(false);
    }
  }

  /**
   * <p>
   * setNonEquilibriumMassTransferModel.
   * </p>
   *
   * @param startNode a int
   * @param endNode a int
   */
  public void setNonEquilibriumMassTransferModel(int startNode, int endNode) {
    for (int i = startNode; i < endNode; i++) {
      if (flowNode[i].getBulkSystem().isChemicalSystem()) {
        flowNode[i].setInterphaseModelType(10);
      } else {
        flowNode[i].setInterphaseModelType(1);
      }
      flowNode[i].getFluidBoundary().setMassTransferCalc(true);
    }
  }

  /**
   * <p>
   * setNonEquilibriumHeatTransferModel.
   * </p>
   *
   * @param startNode a int
   * @param endNode a int
   */
  public void setNonEquilibriumHeatTransferModel(int startNode, int endNode) {
    for (int i = startNode; i < endNode; i++) {
      flowNode[i].getFluidBoundary().setHeatTransferCalc(true);
    }
  }

  /**
   * <p>
   * setEquilibriumHeatTransferModel.
   * </p>
   *
   * @param startNode a int
   * @param endNode a int
   */
  public void setEquilibriumHeatTransferModel(int startNode, int endNode) {
    for (int i = startNode; i < endNode; i++) {
      flowNode[i].getFluidBoundary().setHeatTransferCalc(false);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setEquilibriumMassTransfer(boolean test) {
    equilibriumMassTransfer = test;
    if (equilibriumMassTransfer) {
      setEquilibriumMassTransferModel(0, getTotalNumberOfNodes());
    } else {
      setNonEquilibriumMassTransferModel(0, getTotalNumberOfNodes());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setEquilibriumHeatTransfer(boolean test) {
    equilibriumHeatTransfer = test;
    if (equilibriumHeatTransfer) {
      setEquilibriumHeatTransferModel(0, getTotalNumberOfNodes());
    } else {
      setNonEquilibriumHeatTransferModel(0, getTotalNumberOfNodes());
    }
  }
}
