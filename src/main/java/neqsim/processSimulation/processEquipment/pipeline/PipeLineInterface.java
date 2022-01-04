/*
 * PipeLineInterface.java
 *
 * Created on 21. august 2001, 20:44
 */

package neqsim.processSimulation.processEquipment.pipeline;

import neqsim.fluidMechanics.flowSystem.FlowSystemInterface;

/**
 * <p>PipeLineInterface interface.</p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface PipeLineInterface {
    /**
     * <p>setNumberOfLegs.</p>
     *
     * @param number a int
     */
    public void setNumberOfLegs(int number);

    /**
     * <p>setHeightProfile.</p>
     *
     * @param heights an array of {@link double} objects
     */
    public void setHeightProfile(double[] heights);

    /**
     * <p>setLegPositions.</p>
     *
     * @param positions an array of {@link double} objects
     */
    public void setLegPositions(double[] positions);

    /**
     * <p>setPipeDiameters.</p>
     *
     * @param diameter an array of {@link double} objects
     */
    public void setPipeDiameters(double[] diameter);

    /**
     * <p>setPipeWallRoughness.</p>
     *
     * @param rough an array of {@link double} objects
     */
    public void setPipeWallRoughness(double[] rough);

    /**
     * <p>setOuterTemperatures.</p>
     *
     * @param outerTemp an array of {@link double} objects
     */
    public void setOuterTemperatures(double[] outerTemp);

    /**
     * <p>setNumberOfNodesInLeg.</p>
     *
     * @param number a int
     */
    public void setNumberOfNodesInLeg(int number);

    /**
     * <p>setOutputFileName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setOutputFileName(String name);

    /**
     * <p>setInitialFlowPattern.</p>
     *
     * @param flowPattern a {@link java.lang.String} object
     */
    public void setInitialFlowPattern(String flowPattern);

    /**
     * <p>getPipe.</p>
     *
     * @return a {@link neqsim.fluidMechanics.flowSystem.FlowSystemInterface} object
     */
    public FlowSystemInterface getPipe();

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName();

    /**
     * <p>setName.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name);
}
