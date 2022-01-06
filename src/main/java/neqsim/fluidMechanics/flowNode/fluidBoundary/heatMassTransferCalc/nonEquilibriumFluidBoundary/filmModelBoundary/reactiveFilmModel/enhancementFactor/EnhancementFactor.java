/*
 * EnhancementFactor.java
 *
 * Created on 3. august 2001, 08:45
 */
package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystemInterface;

/**
 * <p>
 * EnhancementFactor class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class EnhancementFactor implements EnhancementFactorInterface {
    protected double[] enhancementVec = null;
    protected double[] hattaNumber = null;
    protected FluidBoundaryInterface fluidBoundary;
    protected FluidBoundarySystemInterface nonReactiveInterface, reactiveInterface;

    /**
     * <p>
     * Constructor for EnhancementFactor.
     * </p>
     */
    public EnhancementFactor() {}

    /**
     * <p>
     * Constructor for EnhancementFactor.
     * </p>
     *
     * @param fluidBoundary a
     *        {@link neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface}
     *        object
     */
    public EnhancementFactor(FluidBoundaryInterface fluidBoundary) {
        this();
        this.fluidBoundary = fluidBoundary;
        enhancementVec =
                new double[fluidBoundary.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        hattaNumber =
                new double[fluidBoundary.getBulkSystem().getPhases()[0].getNumberOfComponents()];
    }

    /**
     * <p>
     * calcEnhancementVec.
     * </p>
     *
     * @param phase a int
     * @param enhancementType a int
     */
    public void calcEnhancementVec(int phase, int enhancementType) {
        if (phase == 1) {
            this.calcEnhancementVec(phase);
        }
        if (phase == 0) {
            this.setOnesVec(phase);
        }
    }

    /**
     * <p>
     * setOnesVec.
     * </p>
     *
     * @param phase a int
     */
    public void setOnesVec(int phase) {
        for (int j = 0; j < fluidBoundary.getBulkSystem().getPhases()[phase]
                .getNumberOfComponents(); j++) {
            enhancementVec[j] = 1.0;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void calcEnhancementVec(int phase) {}

    /**
     * {@inheritDoc}
     *
     * Indexed getter for property enhancementVec.
     */
    @Override
    public double getEnhancementVec(int index) {
        return enhancementVec[index];
    }

    /**
     * Getter for property enhancementVec.
     *
     * @return Value of property enhancementVec.
     */
    public double[] getEnhancementVec() {
        return enhancementVec;
    }

    /**
     * Indexed setter for property enhancementVec.
     *
     * @param index Index of the property.
     * @param enhancementVec New value of the property at <CODE>index</CODE>.
     */
    public void setEnhancementVec(int index, double enhancementVec) {
        this.enhancementVec[index] = enhancementVec;
    }

    /**
     * Setter for property enhancementVec.
     *
     * @param enhancementVec New value of property enhancementVec.
     */
    public void setEnhancementVec(double[] enhancementVec) {
        this.enhancementVec = enhancementVec;
    }

    /**
     * Getter for property hattaNumber.
     *
     * @return Value of property hattaNumber.
     */
    public double[] getHattaNumber() {
        return this.hattaNumber;
    }

    /** {@inheritDoc} */
    @Override
    public double getHattaNumber(int i) {
        return this.hattaNumber[i];
    }

    /**
     * Setter for property hattaNumber.
     *
     * @param hattaNumber New value of property hattaNumber.
     */
    public void setHattaNumber(double[] hattaNumber) {
        this.hattaNumber = hattaNumber;
    }

    /*
     * public FluidBoundarySystemInterface getNumericInterface(){ return numericInterface; }
     */
}
