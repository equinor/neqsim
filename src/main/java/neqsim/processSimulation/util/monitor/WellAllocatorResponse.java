package neqsim.processSimulation.util.monitor;

import neqsim.processSimulation.measurementDevice.WellAllocator;

/**
 * <p>
 * WellAllocatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WellAllocatorResponse extends Response{
    public String name;
    public Double gasExportRate, oilExportRate, totalExportRate;

    /**
     * <p>
     * Constructor for WellAllocatorResponse.
     * </p>
     */
    public WellAllocatorResponse() {}

    /**
     * <p>
     * Constructor for WellAllocatorResponse.
     * </p>
     *
     * @param inputAllocator a {@link neqsim.processSimulation.measurementDevice.WellAllocator} object
     */
    public WellAllocatorResponse(WellAllocator inputAllocator) {
        name = inputAllocator.getName();
        gasExportRate = inputAllocator.getMeasuredValue("gas export rate");
        oilExportRate = inputAllocator.getMeasuredValue("oil export rate");
        totalExportRate = inputAllocator.getMeasuredValue("total export rate");
    }
}
