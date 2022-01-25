package neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.pipeFlowSystem;

import neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization.onePhaseFlowVisualization.pipeFlowVisualization.PipeFlowVisualization;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PipeFlowSystem class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class PipeFlowSystem
        extends neqsim.fluidMechanics.flowSystem.onePhaseFlowSystem.OnePhaseFlowSystem {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for PipeFlowSystem.
     * </p>
     */
    public PipeFlowSystem() {}

    /** {@inheritDoc} */
    @Override
    public void createSystem() {
        thermoSystem.init(0);
        thermoSystem.init(1);
        flowLeg = new neqsim.fluidMechanics.flowLeg.pipeLeg.PipeLeg[this.getNumberOfLegs()];

        for (int i = 0; i < getNumberOfLegs(); i++) {
            flowLeg[i] = new neqsim.fluidMechanics.flowLeg.pipeLeg.PipeLeg();
        }

        flowNode =
                new neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode.onePhasePipeFlowNode[totalNumberOfNodes];
        System.out.println("nodes: " + totalNumberOfNodes);
        flowNode[0] =
                new neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode.onePhasePipeFlowNode(
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
    public void solveSteadyState(int solverType) { // should set solve-type: int 1-bare masse og
                                                   // impuls 2 energi og
                                                   // impuls 3 energi impuls og komponenter
        double[] times = {0.0};
        display = new PipeFlowVisualization(this.getTotalNumberOfNodes(), 1);
        getTimeSeries().setTimes(times);
        SystemInterface[] systems = {flowNode[0].getBulkSystem()};
        getTimeSeries().setInletThermoSystems(systems);
        getTimeSeries().setNumberOfTimeStepsInInterval(1);
        double[] outletFlowRates = {0.0, 0.0}; // this is not yet implemented
        getTimeSeries().setOutletMolarFlowRate(outletFlowRates);
        // SteadystateOnePhasePipeFlowSolver pipeSolve = new
        // SteadystateOnePhasePipeFlowSolver(this, getSystemLength(),
        // getTotalNumberOfNodes());
        flowSolver =
                new neqsim.fluidMechanics.flowSolver.onePhaseFlowSolver.onePhasePipeFlowSolver.OnePhaseFixedStaggeredGrid(
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
        display = new PipeFlowVisualization(this.getTotalNumberOfNodes(),
                getTimeSeries().getTime().length);
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
}
