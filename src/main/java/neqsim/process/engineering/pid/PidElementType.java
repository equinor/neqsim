package neqsim.process.engineering.pid;

/** Functional P&amp;ID element categories used by the governed synthesis model. */
public enum PidElementType {
  MEASUREMENT, INDICATOR, CONTROLLER, CONTROL_VALVE, ISOLATION_VALVE, SHUTDOWN_VALVE,
  BLOWDOWN_VALVE, CHECK_VALVE, SAFETY_RELIEF_VALVE, RUPTURE_DISK, ALARM, TRIP,
  SAFETY_FUNCTION, DRAIN, VENT, SAMPLE_POINT, NOZZLE, SIGNAL, OFF_PAGE_CONNECTOR
}
