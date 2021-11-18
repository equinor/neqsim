package neqsim.fluidMechanics.flowNode.twoPhaseNode.twoPhaseStirredCellNode;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseTwoPhase.stirredCell.InterphaseStirredCellFlow;
import neqsim.fluidMechanics.flowNode.twoPhaseNode.TwoPhaseFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.stirredCell.StirredCell;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class StirredCellNode extends TwoPhaseFlowNode {
    private static final long serialVersionUID = 1000;

    private double[] stirrerRate = {1.0, 1.0}, stirrerDiameter = {1.0, 1.0};
    private double dt = 1.0;

    public StirredCellNode() {
        this.flowNodeType = "stirred cell";
    }

    public StirredCellNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stirred cell";
        this.interphaseTransportCoefficient = new InterphaseStirredCellFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    public StirredCellNode(SystemInterface system, SystemInterface interphaseSystem,
            GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.flowNodeType = "stirred cell";
        this.interphaseTransportCoefficient = new InterphaseStirredCellFlow(this);
        this.fluidBoundary =
                new neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel(
                        this);
    }

    @Override
    public double calcHydraulicDiameter() {
        return getGeometry().getDiameter();
    }

    @Override
    public double calcReynoldNumber() {
        reynoldsNumber[1] = Math.pow(stirrerDiameter[1], 2.0) * stirrerRate[1]
                * bulkSystem.getPhases()[1].getPhysicalProperties().getDensity()
                / bulkSystem.getPhases()[1].getPhysicalProperties().getViscosity();
        reynoldsNumber[0] = Math.pow(stirrerDiameter[0], 2.0) * stirrerRate[0]
                * bulkSystem.getPhases()[0].getPhysicalProperties().getDensity()
                / bulkSystem.getPhases()[0].getPhysicalProperties().getViscosity();
        System.out.println("rey liq " + reynoldsNumber[1]);
        System.out.println("rey gas " + reynoldsNumber[0]);
        return reynoldsNumber[1];
    }

    @Override
    public Object clone() {
        StirredCellNode clonedSystem = null;
        try {
            clonedSystem = (StirredCellNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedSystem;
    }

    @Override
    public void init() {
        this.calcContactLength();
        super.init();
    }

    @Override
    public void initFlowCalc() {
        this.init();
    }

    @Override
    public double calcContactLength() {
        wallContactLength[1] = 1.0;
        wallContactLength[0] = 1.0;

        interphaseContactLength[0] = pi * Math.pow(pipe.getDiameter(), 2.0) / 4.0;
        interphaseContactLength[1] = interphaseContactLength[0];
        interphaseContactArea = interphaseContactLength[0];
        return wallContactLength[0];
    }

    @Override
    public double calcGasLiquidContactArea() {
        return pi * Math.pow(pipe.getDiameter(), 2.0) / 4.0;
    }

    @Override
    public void update() {
        for (int componentNumber = 0; componentNumber < getBulkSystem().getPhases()[0]
                .getNumberOfComponents(); componentNumber++) {
            double liquidMolarRate = getFluidBoundary().getInterphaseMolarFlux(componentNumber)
                    * getInterphaseContactArea() * getDt();
            double gasMolarRate = -getFluidBoundary().getInterphaseMolarFlux(componentNumber)
                    * getInterphaseContactArea() * getDt();
            // System.out.println("liquidMolarRate" + liquidMolarRate);
            getBulkSystem().getPhases()[0].addMoles(componentNumber, gasMolarRate);
            getBulkSystem().getPhases()[1].addMoles(componentNumber, liquidMolarRate);
        }
        // getBulkSystem().initBeta();
        getBulkSystem().init_x_y();
        getBulkSystem().init(1);

        if (bulkSystem.isChemicalSystem()) {
            getOperations().chemicalEquilibrium();
        }
        getBulkSystem().init(1);
    }

    @Override
    public FlowNodeInterface getNextNode() {
        StirredCellNode newNode = (StirredCellNode) this.clone();
        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            // newNode.getBulkSystem().getPhases()[0].addMoles(i, -molarMassTransfer[i]);
            // newNode.getBulkSystem().getPhases()[1].addMoles(i, +molarMassTransfer[i]);
        }
        return newNode;
    }

    /**
     * Getter for property stirrerRate.
     * 
     * @return Value of property stirrerRate.
     */
    public double getStirrerRate(int i) {
        return stirrerRate[i];
    }

    /**
     * Setter for property stirrerRate.
     * 
     * @param stirrerRate New value of property stirrerRate.
     */
    public void setStirrerSpeed(int i, double stirrerRate) {
        this.stirrerRate[i] = stirrerRate;
    }

    public void setStirrerSpeed(double stirrerRate) {
        this.stirrerRate[0] = stirrerRate;
        this.stirrerRate[1] = stirrerRate;
    }

    /**
     * Getter for property dt.
     * 
     * @return Value of property dt.
     */
    public double getDt() {
        return dt;
    }

    /**
     * Setter for property dt.
     * 
     * @param dt New value of property dt.
     */
    public void setDt(double dt) {
        this.dt = dt;
    }

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        // SystemInterface testSystem = new SystemFurstElectrolyteEos(275.3, 1.01325);
        // SystemInterface testSystem = new SystemSrkEos(313.3, 70.01325);
        SystemInterface testSystem = new SystemSrkCPAstatoil(313.3, 70.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        StirredCell pipe1 = new StirredCell(2.0, 0.05);

        testSystem.addComponent("methane", 0.1061152181, "MSm3/hr", 0);
        testSystem.addComponent("water", 10.206862204876, "kg/min", 0);
        testSystem.addComponent("methanol", 1011.206862204876, "kg/min", 1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.initPhysicalProperties();
        StirredCellNode test = new StirredCellNode(testSystem, pipe1);
        test.setInterphaseModelType(1);
        test.getFluidBoundary().useFiniteFluxCorrection(true);
        test.getFluidBoundary().useThermodynamicCorrections(true);
        test.setStirrerSpeed(111350.0 / 60.0);
        test.setStirrerDiameter(0.05);
        test.setDt(1.10);

        test.initFlowCalc();
        // testSystem.init(0);
        // testOps.TPflash();

        test.display();
        for (int i = 0; i < 120; i++) {
            test.initFlowCalc();
            test.calcFluxes();
            test.update();
            // test.display("new");
            test.getBulkSystem().display();
            // test.getFluidBoundary().display("test");
        }
    }

    /**
     * Getter for property stirrerDiameter.
     * 
     * @return Value of property stirrerDiameter.
     */
    public double[] getStirrerDiameter() {
        return this.stirrerDiameter;
    }

    public void setStirrerDiameter(double stirrerDiameter) {
        this.stirrerDiameter[0] = stirrerDiameter;
        this.stirrerDiameter[1] = stirrerDiameter;
    }

    /**
     * Setter for property stirrerDiameter.
     * 
     * @param stirrerDiameter New value of property stirrerDiameter.
     */
    public void setStirrerDiameter(double[] stirrerDiameter) {
        this.stirrerDiameter = stirrerDiameter;
    }
}
