package neqsim.process.util.monitor;

import neqsim.process.measurementdevice.WellAllocator;

/**
 * <p>
 * WellAllocatorResponse class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WellAllocatorResponse {
  public String name;
  public Double gasExportRate, oilExportRate, totalExportRate;

  /**
   * <p>
   * Constructor for WellAllocatorResponse.
   * </p>
   *
   * @param inputAllocator a {@link neqsim.process.measurementdevice.WellAllocator} object
   */
  public WellAllocatorResponse(WellAllocator inputAllocator) {
    name = inputAllocator.getName();
    gasExportRate = inputAllocator.getMeasuredValue("gas export rate", "kg/hr");
    oilExportRate = inputAllocator.getMeasuredValue("oil export rate", "kg/hr");
    totalExportRate = inputAllocator.getMeasuredValue("total export rate", "kg/hr");
  }
}
