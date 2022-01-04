package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;

/**
 * <p>
 * SeparatorResponse class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SeparatorResponse {
    public String name;
    public Double gasLoadFactor;
    public Double massflow;
    public Fluid gasFluid, oilFluid;

    /**
     * <p>
     * Constructor for SeparatorResponse.
     * </p>
     */
    public SeparatorResponse() {}

    /**
     * <p>
     * Constructor for SeparatorResponse.
     * </p>
     *
     * @param inputSeparator a
     *        {@link neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator} object
     */
    public SeparatorResponse(ThreePhaseSeparator inputSeparator) {
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getOilOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }

    /**
     * <p>
     * Constructor for SeparatorResponse.
     * </p>
     *
     * @param inputSeparator a {@link neqsim.processSimulation.processEquipment.separator.Separator}
     *        object
     */
    public SeparatorResponse(Separator inputSeparator) {
        name = inputSeparator.getName();
        massflow = inputSeparator.getFluid().getFlowRate("kg/hr");
        gasLoadFactor = inputSeparator.getGasLoadFactor();
        oilFluid = new Fluid(inputSeparator.getLiquidOutStream().getFluid());
        gasFluid = new Fluid(inputSeparator.getGasOutStream().getFluid());
    }
}
