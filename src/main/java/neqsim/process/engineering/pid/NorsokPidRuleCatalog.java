package neqsim.process.engineering.pid;

import neqsim.process.engineering.pid.rules.CompressorControlPidRule;
import neqsim.process.engineering.pid.rules.CompressorSafeguardingPidRule;
import neqsim.process.engineering.pid.rules.PressureReliefPidRule;
import neqsim.process.engineering.pid.rules.PumpControlPidRule;
import neqsim.process.engineering.pid.rules.PumpSafeguardingPidRule;
import neqsim.process.engineering.pid.rules.SeparatorControlPidRule;
import neqsim.process.engineering.pid.rules.SeparatorSafeguardingPidRule;
import neqsim.process.engineering.pid.rules.ThermalControlPidRule;
import neqsim.process.engineering.pid.rules.ThermalSafeguardingPidRule;

/** Standard offshore P&amp;ID proposal profiles assembled from independently reviewable rules. */
public final class NorsokPidRuleCatalog {
  private NorsokPidRuleCatalog() {
  }

  /**
   * Returns the control and field-instrumentation proposal profile.
   *
   * <p>
   * The profile proposes conventional ISA-style loops. Set points, ranges, failure actions, installation details and
   * control narratives remain review-required project inputs.
   * </p>
   */
  public static PidRuleCatalog controlAndInstrumentation() {
    return new PidRuleCatalog().add(new SeparatorControlPidRule()).add(new CompressorControlPidRule())
        .add(new ThermalControlPidRule()).add(new PumpControlPidRule());
  }

  /** Returns the complete control, instrumentation, isolation and safeguarding proposal profile. */
  public static PidRuleCatalog completeProposals() {
    return controlAndInstrumentation().add(new SeparatorSafeguardingPidRule()).add(new CompressorSafeguardingPidRule())
        .add(new PumpSafeguardingPidRule()).add(new ThermalSafeguardingPidRule()).add(new PressureReliefPidRule());
  }
}
