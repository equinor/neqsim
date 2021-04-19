/*
 * EnhancementFactor.java
 *
 * Created on 3. august 2001, 08:45
 */

package neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.nonEquilibriumFluidBoundary.filmModelBoundary.reactiveFilmModel.enhancementFactor;

import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.FluidBoundaryInterface;
import neqsim.fluidMechanics.flowNode.fluidBoundary.heatMassTransferCalc.finiteVolumeBoundary.fluidBoundarySystem.FluidBoundarySystemInterface;

/**
 *
 * @author esol
 * @version
 */
public class EnhancementFactor implements EnhancementFactorInterface {

    private static final long serialVersionUID = 1000;

    protected double[] enhancementVec = null;
    protected double[] hattaNumber = null;
    protected FluidBoundaryInterface fluidBoundary;
    protected FluidBoundarySystemInterface nonReactiveInterface, reactiveInterface;

    public EnhancementFactor() {
    }

    public EnhancementFactor(FluidBoundaryInterface fluidBoundary) {
        this();
        this.fluidBoundary = fluidBoundary;
        enhancementVec = new double[fluidBoundary.getBulkSystem().getPhases()[0].getNumberOfComponents()];
        hattaNumber = new double[fluidBoundary.getBulkSystem().getPhases()[0].getNumberOfComponents()];
    }

    public void calcEnhancementVec(int phase, int enhancementType) {
        if (phase == 1) {
            this.calcEnhancementVec(phase);
        }
        if (phase == 0) {
            this.setOnesVec(phase);
        }
    }

    public void setOnesVec(int phase) {
        for (int j = 0; j < fluidBoundary.getBulkSystem().getPhases()[phase].getNumberOfComponents(); j++) {
            enhancementVec[j] = 1.0;
        }
    }

    @Override
	public void calcEnhancementVec(int phase) {
    }

    /**
     * Indexed getter for property enhancementVec.
     * 
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
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
     * @param index          Index of the property.
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
     * public FluidBoundarySystemInterface getNumericInterface(){ return
     * numericInterface; }
     */
}
