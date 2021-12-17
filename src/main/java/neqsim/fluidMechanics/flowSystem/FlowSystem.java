package neqsim.fluidMechanics.flowSystem;

import neqsim.fluidMechanics.flowLeg.FlowLegInterface;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowSolver.FlowSolverInterface;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.util.fluidMechanicsDataHandeling.FileWriterInterface;
import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.FlowSystemVisualizationInterface;
import neqsim.fluidMechanics.util.timeSeries.TimeSeries;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public abstract class FlowSystem implements FlowSystemInterface, java.io.Serializable {
    private static final long serialVersionUID = 1000;

    protected FlowNodeInterface[] flowNode;
    protected FlowLegInterface[] flowLeg;
    protected FileWriterInterface[] fileWriter;
    protected String initFlowPattern = "annular";
    protected FlowSystemVisualizationInterface display;
    protected TimeSeries timeSeries = new TimeSeries();
    protected GeometryDefinitionInterface[] equipmentGeometry;
    protected SystemInterface thermoSystem;
    protected ThermodynamicOperations thermoOperations;
    protected double inletTemperature = 0, inletPressure = 0, endPressure = 0, systemLength = 0;
    protected int numberOfFlowLegs = 0, totalNumberOfNodes = 25;
    int[] numberOfNodesInLeg;
    double[] legHeights, legPositions, legOuterTemperatures, legOuterHeatTransferCoefficients,
            legWallHeatTransferCoefficients;
    protected FlowSolverInterface flowSolver;
    double inletMolarLiquidFlowRate = 0, inletMolarGasFlowRate = 0;
    boolean equilibriumHeatTransfer = true, equilibriumMassTransfer = false;

    public FlowSystem() {}

    public FlowSystem(SystemInterface system) {
        System.out.println("Hei der");
    }

    @Override
    public void init() {}

    @Override
    public void createSystem() {
        thermoOperations = new ThermodynamicOperations(thermoSystem);
        this.flowLegInit();
    }

    @Override
    public FlowSolverInterface getSolver() {
        return flowSolver;
    }

    @Override
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    public void flowLegInit() {
        for (int i = 0; i < numberOfFlowLegs; i++) {
            this.flowLeg[i].setThermoSystem(thermoSystem);
            this.flowLeg[i].setEquipmentGeometry(equipmentGeometry[i]);
            this.flowLeg[i].setNumberOfNodes(numberOfNodesInLeg[i]);
            this.flowLeg[i].setHeightCoordinates(legHeights[i], legHeights[i + 1]);
            this.flowLeg[i].setOuterTemperatures(legOuterTemperatures[i],
                    legOuterTemperatures[i + 1]);
            this.flowLeg[i].setLongitudionalCoordinates(legPositions[i], legPositions[i + 1]);
            this.flowLeg[i].setOuterHeatTransferCOefficients(legOuterHeatTransferCoefficients[i],
                    legOuterHeatTransferCoefficients[i + 1]);
            this.flowLeg[i].setWallHeatTransferCOefficients(legWallHeatTransferCoefficients[i],
                    legWallHeatTransferCoefficients[i + 1]);
            this.flowLeg[i].createFlowNodes(flowNode[0]);
        }

        totalNumberOfNodes = this.calcTotalNumberOfNodes();
        System.out.println("total number of nodes : " + totalNumberOfNodes);
    }

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
        flowNode[totalNumberOfNodes - 1].setDistanceToCenterOfNode(legPositions[numberOfFlowLegs]
                + flowNode[totalNumberOfNodes - 1].getLengthOfNode() / 2.0);
        flowNode[totalNumberOfNodes - 1].setVerticalPositionOfNode(legHeights[numberOfFlowLegs]);
        if (endPressure != 0) {
            flowNode[totalNumberOfNodes - 1].getBulkSystem().setPressure(endPressure);
        }
        flowNode[totalNumberOfNodes - 1].init();
    }

    @Override
    public void setInletThermoSystem(SystemInterface thermoSystem) {
        this.thermoSystem = thermoSystem;
        this.inletPressure = thermoSystem.getPressure();
        this.inletTemperature = thermoSystem.getTemperature();
    }

    @Override
    public double getSystemLength() {
        return systemLength;
    }

    public int calcTotalNumberOfNodes() {
        int number = 0;
        for (int i = 0; i < this.numberOfFlowLegs; i++) {
            number += flowLeg[i].getNumberOfNodes();
        }
        this.totalNumberOfNodes = number + 2;
        return this.totalNumberOfNodes;
    }

    @Override
    public int getTotalNumberOfNodes() {
        return this.totalNumberOfNodes;
    }

    @Override
    public double getInletTemperature() {
        return this.inletTemperature;
    }

    @Override
    public void setEquipmentGeometry(GeometryDefinitionInterface[] equipmentGeometry) {
        this.equipmentGeometry = equipmentGeometry;
    }

    @Override
    public void setEndPressure(double endPressure) {
        this.endPressure = endPressure;
    }

    @Override
    public double getInletPressure() {
        return this.inletPressure;
    }

    @Override
    public void setNumberOfLegs(int numberOfFlowLegs) {
        this.numberOfFlowLegs = numberOfFlowLegs;
    }

    @Override
    public FlowNodeInterface getNode(int i) {
        return this.flowNode[i];
    }

    @Override
    public FlowNodeInterface[] getFlowNodes() {
        return this.flowNode;
    }

    @Override
    public int getNumberOfLegs() {
        return this.numberOfFlowLegs;
    }

    @Override
    public FlowSystemVisualizationInterface getDisplay() {
        return display;
    }

    @Override
    public FileWriterInterface getFileWriter(int i) {
        return fileWriter[i];
    }

    @Override
    public void setNumberOfNodesInLeg(int numberOfNodesInLeg) {
        this.numberOfNodesInLeg = new int[this.getNumberOfLegs()];
        for (int i = 0; i < this.getNumberOfLegs(); i++) {
            this.numberOfNodesInLeg[i] = numberOfNodesInLeg;
        }
        totalNumberOfNodes = numberOfNodesInLeg * this.getNumberOfLegs() + 2;
    }

    @Override
    public int getNumberOfNodesInLeg(int i) {
        return this.numberOfNodesInLeg[i];
    }

    @Override
    public void setLegHeights(double[] legHeights) {
        this.legHeights = legHeights;
    }

    @Override
    public void setLegPositions(double[] legPositions) {
        this.legPositions = legPositions;
        this.systemLength = legPositions[legPositions.length - 1];
    }

    @Override
    public void setLegOuterTemperatures(double[] temps) {
        this.legOuterTemperatures = temps;
    }

    @Override
    public void setLegOuterHeatTransferCoefficients(double[] coefs) {
        this.legOuterHeatTransferCoefficients = coefs;
    }

    @Override
    public void setLegWallHeatTransferCoefficients(double[] coefs) {
        this.legWallHeatTransferCoefficients = coefs;
    }

    @Override
    public double[] getLegHeights() {
        return this.legHeights;
    }

    @Override
    public void print() {
        for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
            System.out.println("node " + flowNode[i].getDistanceToCenterOfNode() + " pressure: "
                    + flowNode[i].getBulkSystem().getPhases()[0].getPressure() + " temperature: "
                    + flowNode[i].getBulkSystem().getPhases()[1].getTemperature() + "  flow: "
                    + flowNode[i].getMassFlowRate(0) + " velocity: " + flowNode[i].getVelocity()
                    + " reynolds number " + flowNode[i].getReynoldsNumber() + " friction : "
                    + flowNode[i].getWallFrictionFactor() + " x1 : "
                    + flowNode[i].getBulkSystem().getPhases()[0].getComponents()[1].getx());
        }
    }

    @Override
    public void calcFluxes() {}

    public static void main(String[] args) {
        System.out.println("Hei der!");
    }

    public void solveTransient() {}

    @Override
    public double getTotalMolarMassTransferRate(int component) {
        double tot = 0.0;
        for (int i = 0; i < getTotalNumberOfNodes() - 1; i++) {
            tot += flowNode[i].getFluidBoundary().getInterphaseMolarFlux(component)
                    * flowNode[i].getInterphaseContactArea();
        }
        return tot;
    }

    @Override
    public double getTotalMolarMassTransferRate(int component, int lastNode) {
        double tot = 0.0;
        for (int i = 0; i < lastNode; i++) {
            tot += flowNode[i].getFluidBoundary().getInterphaseMolarFlux(component)
                    * flowNode[i].getInterphaseContactArea();
        }
        return tot;
    }

    @Override
    public double getTotalPressureDrop() {
        return flowNode[0].getBulkSystem().getPressure()
                - flowNode[getTotalNumberOfNodes() - 1].getBulkSystem().getPressure();
    }

    @Override
    public double getTotalPressureDrop(int lastNode) {
        return flowNode[0].getBulkSystem().getPressure()
                - flowNode[lastNode].getBulkSystem().getPressure();
    }

    @Override
    public void setInitialFlowPattern(String flowPattern) {
        this.initFlowPattern = flowPattern;
    }

    @Override
    public void setFlowPattern(String flowPattern) {
        this.initFlowPattern = flowPattern;
        for (int i = 0; i < this.getNumberOfLegs(); i++) {
            flowLeg[i].setFlowPattern(flowPattern);
        }
    }

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

    public void setNonEquilibriumHeatTransferModel(int startNode, int endNode) {
        for (int i = startNode; i < endNode; i++) {
            flowNode[i].getFluidBoundary().setHeatTransferCalc(true);
        }
    }

    public void setEquilibriumHeatTransferModel(int startNode, int endNode) {
        for (int i = startNode; i < endNode; i++) {
            flowNode[i].getFluidBoundary().setHeatTransferCalc(false);
        }
    }

    @Override
    public void setEquilibriumMassTransfer(boolean test) {
        equilibriumMassTransfer = test;
        if (equilibriumMassTransfer) {
            setEquilibriumMassTransferModel(0, getTotalNumberOfNodes());
        } else {
            setNonEquilibriumMassTransferModel(0, getTotalNumberOfNodes());
        }
    }

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
