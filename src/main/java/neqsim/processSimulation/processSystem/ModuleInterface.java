/*
 * ModuleInterface.java
 *
 * Created on 1. november 2006, 21:48
 */
package neqsim.processSimulation.processSystem;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 * <p>
 * ModuleInterface interface.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public interface ModuleInterface extends ProcessEquipmentInterface {
    /**
     * <p>
     * getOperations.
     * </p>
     *
     * @return a {@link neqsim.processSimulation.processSystem.ProcessSystem} object
     */
    public neqsim.processSimulation.processSystem.ProcessSystem getOperations();

    /**
     * <p>
     * addInputStream.
     * </p>
     *
     * @param streamName a {@link java.lang.String} object
     * @param stream a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface}
     *        object
     */
    public void addInputStream(String streamName, StreamInterface stream);

    /**
     * <p>
     * getOutputStream.
     * </p>
     *
     * @param streamName a {@link java.lang.String} object
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public StreamInterface getOutputStream(String streamName);

    /**
     * <p>
     * getPreferedThermodynamicModel.
     * </p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPreferedThermodynamicModel();

    /**
     * <p>
     * setPreferedThermodynamicModel.
     * </p>
     *
     * @param preferedThermodynamicModel a {@link java.lang.String} object
     */
    public void setPreferedThermodynamicModel(String preferedThermodynamicModel);

    /** {@inheritDoc} */
    @Override
    public void run();

    /**
     * <p>
     * initializeStreams.
     * </p>
     */
    public void initializeStreams();

    /**
     * <p>
     * initializeModule.
     * </p>
     */
    public void initializeModule();

    /**
     * <p>
     * setIsCalcDesign.
     * </p>
     *
     * @param isCalcDesign a boolean
     */
    public void setIsCalcDesign(boolean isCalcDesign);

    /**
     * <p>
     * isCalcDesign.
     * </p>
     *
     * @return a boolean
     */
    public boolean isCalcDesign();

    /**
     * <p>
     * getUnit.
     * </p>
     *
     * @param name a {@link java.lang.String} object
     * @return a {@link java.lang.Object} object
     */
    public Object getUnit(String name);

    /**
     * <p>
     * setProperty.
     * </p>
     *
     * @param propertyName a {@link java.lang.String} object
     * @param value a double
     */
    public void setProperty(String propertyName, double value);

    /** {@inheritDoc} */
    @Override
    public String getName();

    /** {@inheritDoc} */
    @Override
    public void setName(String name);

}
