/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */
package neqsim.processSimulation.processEquipment.heatExchanger;

/**
 * <p>
 * HeatExchangerInterface interface.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public interface HeatExchangerInterface extends HeaterInterface {
    /**
     * <p>
     * getOutStream.
     * </p>
     *
     * @param i a int
     * @return a {@link neqsim.processSimulation.processEquipment.stream.StreamInterface} object
     */
    public neqsim.processSimulation.processEquipment.stream.StreamInterface getOutStream(int i);
}
