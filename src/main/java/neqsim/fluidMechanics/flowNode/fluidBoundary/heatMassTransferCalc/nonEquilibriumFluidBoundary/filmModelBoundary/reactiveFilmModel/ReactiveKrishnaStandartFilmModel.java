/*
 * ReactiveKrishnaStandartFilmModel.java
 *
 * Created on 3. august 2001, 12:15
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel;

import Jama.*;
import neqsim.fluidMechanics.flowNode.FlowNodeInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.KrishnaStandartFilmModel;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactorAlg;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor.EnhancementFactorNumeric;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ReactiveKrishnaStandartFilmModel class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class ReactiveKrishnaStandartFilmModel extends KrishnaStandartFilmModel {
    private static final long serialVersionUID = 1000;

    int enhancementType = 1;

    /**
     * <p>
     * Constructor for ReactiveKrishnaStandartFilmModel.
     * </p>
     */
    public ReactiveKrishnaStandartFilmModel() {}

    /**
     * <p>
     * Constructor for ReactiveKrishnaStandartFilmModel.
     * </p>
     *
     * @param system a {@link neqsim.thermo.system.SystemInterface} object
     */
    public ReactiveKrishnaStandartFilmModel(SystemInterface system) {
        super(system);
        enhancementFactor = new EnhancementFactorAlg(this);
    }

    /**
     * <p>
     * Constructor for ReactiveKrishnaStandartFilmModel.
     * </p>
     *
     * @param flowNode a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public ReactiveKrishnaStandartFilmModel(FlowNodeInterface flowNode) {
        super(flowNode);
        enhancementFactor = new EnhancementFactorAlg(this);
    }

    // public double calcBinaryMassTransferCoefficients(int phase){
    // super.calcBinaryMassTransferCoefficients(phase);
    // enhancementFactor.calcEnhancementVec(phase);
    // Matrix enhancementvec = new Matrix(enhancementFactor.getEnhancementVec(),1);
    // //System.out.println("phase " + phase);
    // //enhancementvec.print(10,10);
    // for(int i=0;i<bulkSystem.getPhase(phase).getNumberOfComponents();i++){
    // for(int j=0;j<bulkSystem.getPhase(phase).getNumberOfComponents();j++){
    // binaryMassTransferCoefficient[phase][i][j] =
    // enhancementvec.get(0,i)*binaryMassTransferCoefficient[phase][i][j];
    // }
    // }
    // return 1;
    // }

    /** {@inheritDoc} */
    @Override
    public void calcTotalMassTransferCoefficientMatrix(int phase) {
        super.calcTotalMassTransferCoefficientMatrix(phase);
        enhancementFactor.calcEnhancementVec(phase);
        Matrix enhancementvec = new Matrix(enhancementFactor.getEnhancementVec(), 1);
        totalMassTransferCoefficientMatrix[phase] = massTransferCoefficientMatrix[phase].times(
                enhancementvec.get(0, getBulkSystem().getPhase(0).getNumberOfComponents() - 1));
    }

    /** {@inheritDoc} */
    @Override
    public void setEnhancementType(int type) {
        enhancementType = type;
        if (enhancementType == 1) {
            enhancementFactor = new EnhancementFactorAlg(this);
        } else {
            enhancementFactor = new EnhancementFactorNumeric(this);
        }
    }
}
