

/*
 * ModuleInterface.java
 *
 * Created on 1. november 2006, 21:48
 *
 * To change this template, choose Tools | Template Manager and open the template in the editor.
 */
package neqsim.processSimulation.processSystem;

import neqsim.processSimulation.processEquipment.ProcessEquipmentInterface;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;

/**
 *
 * @author ESOL
 */
public interface ModuleInterface extends ProcessEquipmentInterface {
    public neqsim.processSimulation.processSystem.ProcessSystem getOperations();

    public void addInputStream(String streamName, StreamInterface stream);

    public StreamInterface getOutputStream(String streamName);

    public String getPreferedThermodynamicModel();

    public void setPreferedThermodynamicModel(String preferedThermodynamicModel);

    @Override
    public void run();

    public void initializeStreams();

    public void initializeModule();

    public void setIsCalcDesign(boolean isCalcDesign);

    public boolean isCalcDesign();

    public Object getUnit(String name);

    public void setProperty(String propertyName, double value);

    @Override
    public String getName();

    @Override
    public void setName(String name);
}
