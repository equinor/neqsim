package neqsim.fluidMechanics.util.fluidMechanicsVisualization.flowSystemVisualization;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;

/**
 * <p>
 * FlowSystemVisualizationInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface FlowSystemVisualizationInterface {
    /**
     * <p>
     * setPoints.
     * </p>
     */
    public void setPoints();

    /**
     * <p>
     * displayResult.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void displayResult(String name);

    /**
     * <p>
     * setNextData.
     * </p>
     *
     * @param system a {@link neqsim.fluidMechanics.flowSystem.FlowSystemInterface} object
     */
    public void setNextData(FlowSystemInterface system);

    /**
     * <p>
     * setNextData.
     * </p>
     *
     * @param system a {@link neqsim.fluidMechanics.flowSystem.FlowSystemInterface} object
     * @param abstime a double
     */
    public void setNextData(FlowSystemInterface system, double abstime);
}
