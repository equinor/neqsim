package neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhasePipeFlowNode;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.interphasePipeFlow.InterphaseStratifiedFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.TwoPhaseFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class StratifiedFlowNode extends TwoPhaseFlowNode {
    private static final long serialVersionUID = 1000;

    public StratifiedFlowNode() {
        this.flowNodeType = "stratified";
    }

    public StratifiedFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stratified";
        this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    public StratifiedFlowNode(SystemInterface system, SystemInterface interphaseSystem,
            GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stratified";
        this.interphaseTransportCoefficient = new InterphaseStratifiedFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    @Override
    public StratifiedFlowNode clone() {
        StratifiedFlowNode clonedSystem = null;
        try {
            clonedSystem = (StratifiedFlowNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedSystem;
    }

    @Override
    public void init() {
        inclination = 0.0;
        this.calcContactLength();
        // System.out.println("len " + this.calcContactLength());
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
        return wallContactLength[0];
    }

    @Override
    public FlowNodeInterface getNextNode() {
        StratifiedFlowNode newNode = (StratifiedFlowNode) this.clone();

        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
            // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
        }

        return newNode;
    }

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        // SystemInterface testSystem = new SystemSrkEos(273.15 + 11.0, 60.0);
        SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil(325.3, 100.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        PipeData pipe1 = new PipeData(0.250203, 0.00025);
        testSystem.addComponent("methane", 0.1, "MSm3/day", 0);
        testSystem.addComponent("water", 0.4 * 5.0, "kg/hr", 1);
        testSystem.addComponent("MEG", 0.6 * 5.0, "kg/hr", 1);
        // testSystem.addComponent("nitrogen", 25.0, 0);
        // testSystem.addComponent("CO2", 250.0, 0);
        // testSystem.addComponent("methane", 5.0, 0);
        // testSystem.addComponent("nitrogen", 5.0, 1);
        // testSystem.addComponent("CO2", 250.0, 1);
        // testSystem.addComponent("methane", 25.0, 1);
        // testSystem.addComponent("n-pentane", 25.0, 1);
        // testSystem.addComponent("MDEA", 0.08, 1);
        // testSystem.getPhase(1).setTemperature(275);
        // testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        // testSystem.getPhase(0).setTemperature(273.15 + 100.0);
        testSystem.initPhysicalProperties();

        // testSystem.addComponent("nitrogen", testSystem.getPhase(1).getMolarVolume() /
        // testSystem.getPhase(0).getMolarVolume() *
        // testSystem.getPhase(0).getComponent("CO2").getNumberOfmoles(), 0);

        // testSystem.getChemicalReactionOperations().solveChemEq(1);
        testSystem.init_x_y();
        testSystem.initBeta();
        testSystem.init(3);

        // testOps.TPflash();
        testSystem.display();
        // testSystem.setTemperature(273.15+20);
        // testSystem.initPhysicalProperties();

        // FlowNodeInterface test = new StratifiedFlowNode(testSystem, pipe1);
        // FlowNodeInterface test = new AnnularFlow(testSystem, pipe1);
        FlowNodeInterface test = new DropletFlowNode(testSystem, pipe1);
        test.setInterphaseModelType(1);
        test.setLengthOfNode(0.001);
        test.getGeometry().getSurroundingEnvironment().setTemperature(273.15 + 4.0);

        test.getFluidBoundary().setHeatTransferCalc(false);
        test.getFluidBoundary().setMassTransferCalc(true);
        double length = 0;
        test.initFlowCalc();
        double[][] temperatures2 = new double[3][1000];
        int k = 0;
        for (int i = 0; i < 100000; i++) {
            length += test.getLengthOfNode();
            test.initFlowCalc();
            test.calcFluxes();
            if (i > 1 && (i % 1000) == 0) {
                k++;
                test.display("length " + length);
                System.out.println("length " + length + " wt% MEG "
                        + test.getBulkSystem().getPhase("aqueous").getWtFrac("MEG") * 100.0);
                // test.getBulkSystem().display("length " + length);
                // test.getInterphaseSystem().display("length " + length);
                // test.getFluidBoundary().display("length " + length);
                // test.setLengthOfNode(0.000005 + test.getLengthOfNode() / 2.0);
                temperatures2[0][k] = length;
                temperatures2[1][k] = test.getGeometry().getInnerWallTemperature();
                // test.getFluidBoundary().display("test");
            }

            // test.getBulkSystem().display();
            test.update();
            // test.getFluidBoundary().display("length " + length);
            // test.getInterphaseSystem().display("length " + length);

            // test.getFluidBoundary().display("test");
        }

        for (int i = 0; i < k; i++) {
            System.out.println("len temp  " + temperatures2[0][i] + " " + temperatures2[1][i]);
        }
        System.out.println("contact length " + test.getInterphaseContactArea());
    }
}
