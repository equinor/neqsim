package neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhaseReactorFlowNode;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow.InterphaseStratifiedFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.TwoPhaseFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class TwoPhaseTrayTowerFlowNode extends TwoPhaseFlowNode {
    private static final long serialVersionUID = 1000;

    public TwoPhaseTrayTowerFlowNode() {
        this.flowNodeType = "stratified";
    }

    public TwoPhaseTrayTowerFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stratified";
        this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    public TwoPhaseTrayTowerFlowNode(SystemInterface system, SystemInterface interphaseSystem,
            GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stratified";
        this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    @Override
    public TwoPhaseTrayTowerFlowNode clone() {
        TwoPhaseTrayTowerFlowNode clonedSystem = null;
        try {
            clonedSystem = (TwoPhaseTrayTowerFlowNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedSystem;
    }

    @Override
    public void init() {
        inclination = 0.0;
        this.calcContactLength();
        super.init();
    }

    @Override
    public double calcContactLength() {
        double phaseAngel = pi * phaseFraction[1] + Math.pow(3.0 * pi / 2.0, 1.0 / 3.0)
                * (1.0 - 2.0 * phaseFraction[1] + Math.pow(phaseFraction[1], 1.0 / 3.0)
                        - Math.pow(phaseFraction[0], 1.0 / 3.0));

        wallContactLength[1] = phaseAngel * pipe.getDiameter();
        wallContactLength[0] = pi * pipe.getDiameter() - wallContactLength[1];
        interphaseContactLength[0] = pipe.getDiameter() * Math.sin(phaseAngel);
        interphaseContactLength[1] = pipe.getDiameter() * Math.sin(phaseAngel);
        // System.out.println("cont " + interphaseContactLength[1] + " " +
        // phaseFraction[1]);

        return wallContactLength[0];
    }

    @Override
    public FlowNodeInterface getNextNode() {
        TwoPhaseTrayTowerFlowNode newNode = (TwoPhaseTrayTowerFlowNode) this.clone();

        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
            // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
        }

        return newNode;
    }

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        /*
         * System.out.println("Starter....."); SystemSrkEos testSystem = new SystemSrkEos(275.3,
         * 1.01325); ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
         * PipeData pipe1 = new PipeData(10.0, 0.025);
         * 
         * testSystem.addComponent("methane", 0.011152181, 0); testSystem.addComponent("ethane",
         * 0.00011152181, 0); testSystem.addComponent("water", 0.00462204876, 1);
         * testSystem.addComponent("methane", 0.061152181, 0); testSystem.addComponent("water",
         * 0.00862204876, 1);
         */
        SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, 1.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        PipeData pipe1 = new PipeData(10.0, 0.025);

        testSystem.addComponent("methane", 0.061152181, 0);
        // testSystem.addComponent("CO2", 0.00061152181, 0);
        testSystem.addComponent("water", 0.1862204876, 1);
        // testSystem.addComponent("MDEA", 0.008,1);

        // testSystem.chemicalReactionInit();
        testSystem.setMixingRule(2);

        // testSystem.init(0);
        testSystem.init_x_y();

        // testSystem.getPhases()[1].setTemperature(294.0);
        // testSystem.getPhases()[0].setTemperature(299.0);

        FlowNodeInterface test = new TwoPhaseTrayTowerFlowNode(testSystem, pipe1);
        test.setInterphaseModelType(1);

        test.initFlowCalc();
        test.calcFluxes();
    }
}
