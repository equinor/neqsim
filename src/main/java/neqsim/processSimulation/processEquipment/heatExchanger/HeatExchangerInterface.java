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
