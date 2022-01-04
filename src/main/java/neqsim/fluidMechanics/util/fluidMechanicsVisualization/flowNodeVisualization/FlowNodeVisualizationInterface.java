/*
 * FlowNodeIVisualizationInterface.java
 *
 * Created on 5. august 2001, 16:28
 */
package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowNodeVisualization;

import neqsim.fluidMechanics.flowNode.FlowNodeInterface;

/**
 * <p>
 * FlowNodeVisualizationInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FlowNodeVisualizationInterface {
    /**
     * <p>
     * setData.
     * </p>
     *
     * @param node a {@link neqsim.fluidMechanics.flowNode.FlowNodeInterface} object
     */
    public void setData(FlowNodeInterface node);

    /**
     * <p>
     * getPressure.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getPressure(int i);

    /**
     * <p>
     * getTemperature.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getTemperature(int i);

    /**
     * <p>
     * getDistanceToCenterOfNode.
     * </p>
     *
     * @return a double
     */
    public double getDistanceToCenterOfNode();

    /**
     * <p>
     * getVelocity.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getVelocity(int i);

    /**
     * <p>
     * getBulkComposition.
     * </p>
     *
     * @param i a int
     * @param phase a int
     * @return a double
     */
    public double getBulkComposition(int i, int phase);

    /**
     * <p>
     * getInterfaceComposition.
     * </p>
     *
     * @param i a int
     * @param phase a int
     * @return a double
     */
    public double getInterfaceComposition(int i, int phase);

    /**
     * <p>
     * getNumberOfComponents.
     * </p>
     *
     * @return a int
     */
    public int getNumberOfComponents();

    /**
     * <p>
     * getPhaseFraction.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getPhaseFraction(int phase);

    /**
     * <p>
     * getMolarFlux.
     * </p>
     *
     * @param i a int
     * @param phase a int
     * @return a double
     */
    public double getMolarFlux(int i, int phase);

    /**
     * <p>
     * getInterfaceTemperature.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getInterfaceTemperature(int i);

    /**
     * <p>
     * getInterphaseContactLength.
     * </p>
     *
     * @return a double
     */
    public double getInterphaseContactLength();

    /**
     * <p>
     * getWallContactLength.
     * </p>
     *
     * @param phase a int
     * @return a double
     */
    public double getWallContactLength(int phase);

    /**
     * <p>
     * getReynoldsNumber.
     * </p>
     *
     * @param i a int
     * @return a double
     */
    public double getReynoldsNumber(int i);

    /**
     * <p>
     * getEffectiveMassTransferCoefficient.
     * </p>
     *
     * @param i a int
     * @param phase a int
     * @return a double
     */
    public double getEffectiveMassTransferCoefficient(int i, int phase);

    /**
     * <p>
     * getEffectiveSchmidtNumber.
     * </p>
     *
     * @param i a int
     * @param phase a int
     * @return a double
     */
    public double getEffectiveSchmidtNumber(int i, int phase);
}
