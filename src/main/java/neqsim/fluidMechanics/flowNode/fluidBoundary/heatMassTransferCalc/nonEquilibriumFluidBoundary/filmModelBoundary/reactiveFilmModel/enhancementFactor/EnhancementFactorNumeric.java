package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundaryNonReactive.FluidBoundarySystemNonReactive;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundarySystemReactive.FluidBoundarySystemReactive;

/**
 * <p>
 * EnhancementFactorNumeric class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnhancementFactorNumeric extends EnhancementFactor {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for EnhancementFactorNumeric.
     * </p>
     */
    public EnhancementFactorNumeric() {
        super();
    }

    /**
     * <p>
     * Constructor for EnhancementFactorNumeric.
     * </p>
     *
     * @param fluidBoundary a
     *        {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface}
     *        object
     */
    public EnhancementFactorNumeric(FluidBoundaryInterface fluidBoundary) {
        super(fluidBoundary);
        // fluidBoundary.setNumericSolve(true);
        reactiveInterface = new FluidBoundarySystemReactive(fluidBoundary);
        nonReactiveInterface = new FluidBoundarySystemNonReactive(fluidBoundary);
        reactiveInterface.createSystem();
        nonReactiveInterface.createSystem();
        // numericInterface.createSystem();
    }

    /**
     * <p>
     * calcEnhancementMatrix.
     * </p>
     *
     * @param phase a int
     */
    public void calcEnhancementMatrix(int phase) {
        reactiveInterface.createSystem();
        nonReactiveInterface.createSystem();
        nonReactiveInterface.solve();
        reactiveInterface.solve();
        for (int i = 0; i < fluidBoundary.getBulkSystem().getPhases()[phase]
                .getNumberOfComponents(); i++) {
            for (int j = 0; j < fluidBoundary.getBulkSystem().getPhases()[phase]
                    .getNumberOfComponents(); j++) {
                // enhancementFactor[1].set(i,j,0);
                // System.out.println("num enhancement " + enhancementFactor[1].get(i,j));
            }
        }
    }
}
