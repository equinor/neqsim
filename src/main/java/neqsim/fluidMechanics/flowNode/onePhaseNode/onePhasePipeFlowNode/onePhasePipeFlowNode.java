package neqsim.fluidMechanics.flowNode.onePhaseNode.onePhasePipeFlowNode;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.interphaseTransportCoefficient.interphaseOnePhase.interphasePipeFlow.InterphasePipeFlow;
import neqsim.fluidMechanics.flowNode.onePhaseNode.onePhaseFlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.fluidMechanics.geometryDefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class onePhasePipeFlowNode extends onePhaseFlowNode {

    private static final long serialVersionUID = 1000;

    public onePhasePipeFlowNode() {
    }

    public onePhasePipeFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        super(system, pipe);
        this.interphaseTransportCoefficient = new InterphasePipeFlow(this);
        phaseOps = new ThermodynamicOperations(this.getBulkSystem());
        phaseOps.TPflash();
        initBulkSystem();
    }

    @Override
    public onePhasePipeFlowNode clone() {
        onePhasePipeFlowNode clonedSystem = null;
        try {
            clonedSystem = (onePhasePipeFlowNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return clonedSystem;
    }

    @Override
	public void init() {
        super.init();
    }

    @Override
	public double calcReynoldsNumber() {
        reynoldsNumber[0] = getVelocity() * pipe.getDiameter()
                / getBulkSystem().getPhases()[0].getPhysicalProperties().getKinematicViscosity();
        return reynoldsNumber[0];
    }

    public static void main(String[] args) {

        System.out.println("Starter.....");
        SystemSrkEos testSystem = new SystemSrkEos(300.3, 200.0);

        GeometryDefinitionInterface pipe1 = new PipeData(1, 0.0025);
        testSystem.addComponent("methane", 50000.0);
        testSystem.addComponent("ethane", 1.0);

        testSystem.init(0);
        testSystem.init(1);

        FlowNodeInterface[] test = new onePhasePipeFlowNode[100];

        test[0] = new onePhasePipeFlowNode(testSystem, pipe1);
        // test[0].setFrictionFactorType(0);

        // test[0].init()
        test[0].initFlowCalc();
        test[0].init();

        // test[0].getVolumetricFlow();
        System.out.println("flow: " + test[0].getVolumetricFlow() + " velocity: " + test[0].getVelocity()
                + " reynolds number " + test[0].getReynoldsNumber() + "friction : " + test[0].getWallFrictionFactor());
    }

}