/*
 * EnhancementFactorAlgebraic.java
 *
 * Created on 3. august 2001, 13:46
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundaryNonReactive.FluidBoundarySystemNonReactive;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.fluidBoundarySystemReactive.FluidBoundarySystemReactive;

/**
 *
 * @author esol
 * @version
 */
public class EnhancementFactorNumeric extends EnhancementFactor {

    private static final long serialVersionUID = 1000;

    public EnhancementFactorNumeric() {
        super();
    }

    public EnhancementFactorNumeric(FluidBoundaryInterface fluidBoundary) {
        super(fluidBoundary);
        // fluidBoundary.setNumericSolve(true);
        reactiveInterface = new FluidBoundarySystemReactive(fluidBoundary);
        nonReactiveInterface = new FluidBoundarySystemNonReactive(fluidBoundary);
        reactiveInterface.createSystem();
        nonReactiveInterface.createSystem();
        // numericInterface.createSystem();
    }

    public void calcEnhancementMatrix(int phase) {
        reactiveInterface.createSystem();
        nonReactiveInterface.createSystem();
        nonReactiveInterface.solve();
        reactiveInterface.solve();
        for (int i = 0; i < fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents(); i++) {
            for (int j = 0; j < fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents(); j++) {
                // enhancementFactor[1].set(i,j,0);
                // System.out.println("num enhancement " + enhancementFactor[1].get(i,j));
            }
        }

    }
}
