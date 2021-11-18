package neqsim.fluidMechanics.flowNode.onePhaseNode;

import neqsim.fluidMechanics.flowNode.FlowNode;
import neqsim.fluidMechanics.geometryDefinitions.GeometryDefinitionInterface;
import neqsim.thermo.system.SystemInterface;

public abstract class onePhaseFlowNode extends FlowNode {
    private static final long serialVersionUID = 1000;

    public onePhaseFlowNode() {}

    public onePhaseFlowNode(SystemInterface system) {}

    public onePhaseFlowNode(SystemInterface system, GeometryDefinitionInterface pipe) {
        super(system, pipe);
    }

    @Override
    public Object clone() {
        onePhaseFlowNode clonedSystem = null;
        try {
            clonedSystem = (onePhaseFlowNode) super.clone();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return clonedSystem;
    }

    @Override
    public void increaseMolarRate(double moles) {
        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            double diff =
                    (getBulkSystem().getPhases()[0].getComponents()[i].getx() * (molarFlowRate[0]
                            - getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()));
            getBulkSystem().addComponent(
                    getBulkSystem().getPhase(0).getComponent(i).getComponentName(), diff);
        }
        getBulkSystem().init_x_y();
        initFlowCalc();
    }

    @Override
    public void initFlowCalc() {
        initBulkSystem();
        molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
        massFlowRate[0] = molarFlowRate[0] * getBulkSystem().getPhases()[0].getMolarMass();
        volumetricFlowRate[0] = massFlowRate[0]
                / getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
        superficialVelocity[0] = volumetricFlowRate[0] / pipe.getArea();
        velocity[0] = superficialVelocity[0];
        this.init();
    }

    @Override
    public void updateMolarFlow() {
        for (int i = 0; i < getBulkSystem().getPhases()[0].getNumberOfComponents(); i++) {
            double diff =
                    (getBulkSystem().getPhases()[0].getComponents()[i].getx() * (molarFlowRate[0]
                            - getBulkSystem().getPhases()[0].getNumberOfMolesInPhase()));
            getBulkSystem().addComponent(
                    getBulkSystem().getPhase(0).getComponent(i).getComponentName(), diff);
        }
        getBulkSystem().init_x_y();
        getBulkSystem().init(3);
    }

    // public double initVelocity(){
    // initBulkSystem();
    // molarFlowRate[0] = getBulkSystem().getPhases()[0].getNumberOfMolesInPhase();
    // massFlowRate[0] =
    // molarFlowRate[0]*getBulkSystem().getPhases()[0].getMolarMass();
    // volumetricFlowRate[0] =
    // massFlowRate[0]/getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity();
    // superficialVelocity[0] = volumetricFlowRate[0]/pipe.getArea();
    // velocity[0] = superficialVelocity[0];
    // return velocity[0];
    // }

    public double calcReynoldsNumber() {
        reynoldsNumber[0] = getVelocity() * pipe.getDiameter()
                / getBulkSystem().getPhases()[0].getPhysicalProperties().getKinematicViscosity();
        return reynoldsNumber[0];
    }

    @Override
    public void init() {
        super.init();
        massFlowRate[0] =
                velocity[0] * getBulkSystem().getPhases()[0].getPhysicalProperties().getDensity()
                        * pipe.getArea();
        superficialVelocity[0] = velocity[0];
        molarFlowRate[0] = massFlowRate[0] / getBulkSystem().getPhases()[0].getMolarMass();
        volumetricFlowRate[0] = superficialVelocity[0] * pipe.getArea();
        this.updateMolarFlow();
        calcReynoldsNumber();
        wallFrictionFactor[0] = interphaseTransportCoefficient.calcWallFrictionFactor(this);
    }
}
