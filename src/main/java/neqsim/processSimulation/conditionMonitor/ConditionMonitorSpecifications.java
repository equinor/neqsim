package neqsim.processSimulation.conditionMonitor;

public interface ConditionMonitorSpecifications extends java.io.Serializable {
    double HXmaxDeltaT = 5.0;
    String HXmaxDeltaT_ErrorMsg = "Too high temperature difference between streams. Max difference: " + HXmaxDeltaT;

}
