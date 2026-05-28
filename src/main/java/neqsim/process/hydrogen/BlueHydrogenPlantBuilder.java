package neqsim.process.hydrogen;

import neqsim.process.processmodel.ProcessSystem;

/**
 * Builder for a blue-hydrogen plant template based on SMR with CO2-capture readiness metadata.
 *
 * <p>
 * The current implementation reuses the fired SMR and PSA template from
 * {@link SMRHydrogenPlantBuilder} and adds explicit capture-fraction metadata for downstream amine,
 * membrane, or compression modules. It provides a stable class and route label so future CO2
 * capture equipment can be inserted without changing user-facing code.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class BlueHydrogenPlantBuilder extends SMRHydrogenPlantBuilder {
  /** Target fraction of process CO2 captured downstream of shift/PSA tail gas handling. */
  private double co2CaptureFraction = 0.90;

  /**
   * Creates a blue-hydrogen builder with blue-H2 default naming.
   */
  public BlueHydrogenPlantBuilder() {
    setName("Blue Hydrogen Plant");
  }

  /**
   * Sets target CO2 capture fraction.
   *
   * @param fraction capture fraction between zero and one
   * @return this builder
   */
  public BlueHydrogenPlantBuilder setCo2CaptureFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("fraction must be finite and between zero and one");
    }
    this.co2CaptureFraction = fraction;
    return this;
  }

  /**
   * Gets target CO2 capture fraction.
   *
   * @return capture fraction
   */
  public double getCo2CaptureFraction() {
    return co2CaptureFraction;
  }

  /**
   * Gets a short capture-readiness description.
   *
   * @return capture-readiness description
   */
  public String getCaptureReadinessSummary() {
    return "SMR + PSA template with downstream CO2 capture target " + co2CaptureFraction;
  }

  /** {@inheritDoc} */
  @Override
  public ProcessSystem build() {
    return super.build();
  }
}
