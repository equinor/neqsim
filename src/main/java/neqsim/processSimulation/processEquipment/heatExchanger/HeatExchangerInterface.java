/*
 * SeparatorInterface.java
 *
 * Created on 22. august 2001, 17:22
 */

package neqsim.processSimulation.processEquipment.heatExchanger;

/**
 *
 * @author esol
 * @version
 */
public interface HeatExchangerInterface extends HeaterInterface {
    public neqsim.processSimulation.processEquipment.stream.StreamInterface getOutStream(int i);
}
