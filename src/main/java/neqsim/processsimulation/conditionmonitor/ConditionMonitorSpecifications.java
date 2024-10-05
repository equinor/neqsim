package neqsim.processsimulation.conditionmonitor;

/**
 * <p>
 * ConditionMonitorSpecifications interface.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public interface ConditionMonitorSpecifications extends java.io.Serializable {
  /** Constant <code>HXmaxDeltaT=5.0</code>. */
  double HXmaxDeltaT = 5.0;
  /**
   * Constant <code>HXmaxDeltaT_ErrorMsg="Too high temperature difference between"{trunked}</code>.
   */
  String HXmaxDeltaT_ErrorMsg =
      "Too high temperature difference between streams. Max difference: " + HXmaxDeltaT;
}
