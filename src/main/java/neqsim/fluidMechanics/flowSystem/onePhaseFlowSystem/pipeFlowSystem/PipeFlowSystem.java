package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem;

import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.onePhaseFlowVisualization.pipeFlowVisualization.PipeFlowVisualization;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>PipeFlowSystem class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeFlowSystem extends neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.OnePhaseFlowSystem {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for PipeFlowSystem.</p>
     */
    public PipeFlowSystem() {
    }

	/** {@inheritDoc} */
    @Override
	public void createSystem() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        flowLeg = new neqsim.fluidMechanics.flowLeg.pipeLeg.PipeLeg[this.getNumberOfLegs()];

        for (int i = 0; i < getNumberOfLegs(); i++) {
            flowLeg[i] = new neqsim.fluidMechanics.flowLeg.pipeLeg.PipeLeg();
        }

        flowNode = new neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode.onePhasePipeFlowNode[totalNumberOfNodes];
        System.out.println("nodes: " + totalNumberOfNodes);
        flowNode[0] = new neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode.onePhasePipeFlowNode(
                thermoSystem, this.equipmentGeometry[0]);
        flowNode[0].initFlowCalc();
        super.createSystem();
        this.setNodes();
    }

	/** {@inheritDoc} */
    @Override
	public void init() {
        for (int j = 0; j < getTotalNumberOfNodes(); j++) {
            flowNode[j].initFlowCalc();
            flowNode[j].setVelocityIn(this.flowNode[j].getVelocity());
        }

        for (int k = 0; k < getTotalNumberOfNodes() - 1; k++) {
            this.flowNode[k].setVelocityOut(this.flowNode[k + 1].getVelocityIn());
        }
    }

	/** {@inheritDoc} */
    @Override
	public void solveSteadyState(int solverType) { // should set solve-type: int 1-bare masse og impuls 2 energi og
                                                   // impuls 3 energi impuls og komponenter
        double[] times = { 0.0 };
        display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), 1);
        getTimeSeries().setTimes(times);
        SystemInterface[] systems = { flowNode[0].getBulkSystem() };
        getTimeSeries().setInletThermoSystems(systems);
        getTimeSeries().setNumberOfTimeStepsInInterval(1);
        double[] outletFlowRates = { 0.0, 0.0 }; // this is not yet implemented
        getTimeSeries().setOutletMolarFlowRate(outletFlowRates);
        // SteadystateOnePhasePipeFlowSolver pipeSolve = new
        // SteadystateOnePhasePipeFlowSolver(this, getSystemLength(),
        // getTotalNumberOfNodes());
        flowSolver = new neqsim.fluidMechanics.flowSolver.onePhaseFlowSolver.onePhasePipeFlowSolver.OnePhaseFixedStaggeredGrid(
                this, getSystemLength(), getTotalNumberOfNodes(), false);
        flowSolver.setSolverType(solverType);
        flowSolver.solveTDMA();
        getTimeSeries().init(this);
        display.setNextData(this);
    }

	/** {@inheritDoc} */
    @Override
	public void solveTransient(int solverType) {
        getTimeSeries().init(this);
        display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), getTimeSeries().getTime().length);
        flowSolver.setDynamic(true);
        flowSolver.setSolverType(solverType);
        for (int i = 0; i < this.getTimeSeries().getTime().length; i++) {
            getNode(0).setBulkSystem(this.getTimeSeries().getThermoSystem()[i]);
            // getNode(0).getBulkSystem().setPressure();
            getNode(0).initFlowCalc();
            getNode(0).setVelocityIn(getNode(0).getVelocity());
            flowNode[0].setVelocityOut(this.flowNode[0].getVelocity());
            // flowNode[1].setVelocityIn(this.flowNode[0].getVelocity());
            // flowNode[getTotalNumberOfNodes()-1].setVelocity(this.getTimeSeries().getOutletMolarFlowRates()[i]);
            System.out.println("vel: " + this.flowNode[0].getVelocity());
            getSolver().setTimeStep(this.getTimeSeries().getTimeStep()[i]);
            // System.out.println("time step: " + i + " " +
            // this.getTimeSeries().getTimeStep()[i]);
            // System.out.println("time: " + i + " " + this.getTimeSeries().getTime()[i]);
            // flowSolver.solveTDMA();
            display.setNextData(this, this.getTimeSeries().getTime(i));
        }
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        System.out.println("Starting.....");

        SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
        testSystem.addComponent("methane", 0.9);
        testSystem.addComponent("ethane", 0.1);
        testSystem.createDatabase(true);
        testSystem.init(0);
        testSystem.init(3);
        testSystem.initPhysicalProperties();
        testSystem.setTotalFlowRate(60.0, "MSm3/day");
        neqsim.fluidMechanics.flowSystem.FlowSystemInterface pipe = new PipeFlowSystem();

        double[] height = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        double[] diameter = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
        double[] roughness = { 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5, 1.0e-5 };
        double[] outHeatCoef = { 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0 };
        double[] wallHeacCoef = { 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0, 15.0 };

        double[] length = { 0, 10000, 50000, 150000, 200000, 400000, 500000, 600000, 650000, 700000, 750000 };
        double[] outerTemperature = { 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0, 278.0 };

        neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface[] pipeGeometry = new neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData[10];

        for (int i = 0; i < pipeGeometry.length; i++) {
            pipeGeometry[i] = new neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData();
            pipeGeometry[i].setDiameter(diameter[i]);
            pipeGeometry[i].setInnerSurfaceRoughness(roughness[i]);
        }
        pipe.setInletThermoSystem(testSystem);
        pipe.setNumberOfLegs(10);
        pipe.setNumberOfNodesInLeg(20);
        pipe.setEquipmentGeometry(pipeGeometry);
        pipe.setLegHeights(height);
        pipe.setLegPositions(length);
        pipe.setLegOuterTemperatures(outerTemperature);
        pipe.setLegWallHeatTransferCoefficients(wallHeacCoef);
        pipe.setLegOuterHeatTransferCoefficients(outHeatCoef);

        pipe.createSystem();
        pipe.init();
        pipe.solveSteadyState(10);
        pipe.print();

        // transient solver
        double[] times = { 0, 10000, 20000 };// , 30000, 40000, 50000};//, 60000, 70000, 80000, 90000};
        pipe.getTimeSeries().setTimes(times);

        SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
        testSystem2.addComponent("methane", 29000.0);
        testSystem2.addComponent("ethane", 1221.10);

        testSystem2 = new neqsim.thermo.system.SystemSrkEos(315.15, 200.0);
        testSystem2.addComponent("methane", 26000.0);
        testSystem2.addComponent("ethane", 1.10);
        testSystem2.init(0);
        testSystem2.init(3);
        testSystem2.initPhysicalProperties();

        SystemInterface testSystem3 = new neqsim.thermo.system.SystemSrkEos(285.15, 200.0);
        testSystem.addComponent("methane", 29000.0);
        testSystem.addComponent("ethane", 1221.10);
        testSystem3.init(0);

        SystemInterface[] systems = { testSystem, testSystem2, testSystem2 };// , testSystem2, testSystem2,
                                                                             // testSystem2};//,testSystem2,testSystem2,testSystem2,testSystem2,testSystem2};
        pipe.getTimeSeries().setInletThermoSystems(systems);
        pipe.getTimeSeries().setNumberOfTimeStepsInInterval(10);
        // double[] outletFlowRates = {0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01, 0.01,
        // 0.01, 0.01, 0.01};
        // pipe.getTimeSeries().setOutletMolarFlowRate(outletFlowRates);

        // pipe.solveTransient(20);
        // pipe.getDisplay().displayResult("composition");
        // pipe.getDisplay().displayResult("pressure");
        // pipe.getDisplay().displayResult("composition");
        // pipe.getDisplay().createNetCdfFile("c:/temp5.nc");
        // pipe.getDisplay(1).displayResult();

    }
}
