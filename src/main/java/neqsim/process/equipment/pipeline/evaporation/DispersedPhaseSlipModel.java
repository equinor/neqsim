package neqsim.process.equipment.pipeline.evaporation;

/** Axial-velocity and particle-relative-velocity closure for a dispersed phase. */
public enum DispersedPhaseSlipModel {
  /** Use the gas and liquid actual velocities supplied in {@link PipelineEvaporationConfig}. */
  USER_SPECIFIED,
  /** Recalculate slip from a local Schiller-Naumann terminal force balance. */
  TERMINAL_VELOCITY
}
