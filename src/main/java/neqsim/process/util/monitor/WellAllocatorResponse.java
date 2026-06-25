package neqsim.process.util.monitor;

import java.util.HashMap;
import neqsim.process.measurementdevice.WellAllocator;

/**
 * WellAllocatorResponse class.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class WellAllocatorResponse {
  public String name;
  public HashMap<String, Value> data = new HashMap<String, Value>();

  /**
   * Constructor for WellAllocatorResponse.
   *
   * @param inputAllocator a {@link neqsim.process.measurementdevice.WellAllocator} object
   */
  public WellAllocatorResponse(WellAllocator inputAllocator) {
    name = inputAllocator.getName();
    data.put("gas export rate",
        new Value(Double.toString(inputAllocator.getMeasuredValue("gas export rate", "kg/hr")), "kg/hr"));
    data.put("oil export rate",
        new Value(Double.toString(inputAllocator.getMeasuredValue("oil export rate", "kg/hr")), "kg/hr"));
    data.put("total export rate",
        new Value(Double.toString(inputAllocator.getMeasuredValue("total export rate", "kg/hr")), "kg/hr"));
  }
}
