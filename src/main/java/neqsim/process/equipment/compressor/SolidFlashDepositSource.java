package neqsim.process.equipment.compressor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * {@link DepositSource} that computes a solid-precipitation deposition rate from a process stream using a NeqSim solid
 * (TP-solid) flash.
 *
 * <p>
 * This covers solids that the equation of state can drop out directly, most importantly elemental sulfur ({@code S8}),
 * but also wax or asphaltene when those solid checks are configured. The stream is cloned, multi-phase and the named
 * solid-phase check are enabled, a {@code TPSolidflash} is run at the stream conditions, and the resulting solid-phase
 * mass rate is multiplied by a capture fraction (the fraction of precipitated solids that stick to the impeller rather
 * than pass through).
 * </p>
 *
 * <p>
 * The stream should represent the fluid entering the compressor, including any entrained liquid (condensate/water
 * droplets) carried over from an upstream separator, since the entrained liquid is where dissolved sulfur or salt is
 * transported. Model entrainment upstream by mixing the carried-over liquid into the compressor feed (or by an
 * imperfect separator efficiency).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SolidFlashDepositSource implements DepositSource {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(SolidFlashDepositSource.class);

  private final StreamInterface stream;
  private final String solidComponent;
  private final DepositMechanism mechanism;
  private double captureFraction = 1.0;

  /**
   * Constructor.
   *
   * @param stream stream entering the compressor (including any entrained liquid)
   * @param solidComponent name of the component allowed to form a solid, for example "S8"
   * @param mechanism deposit mechanism (density) used by the deposit model
   * @param captureFraction fraction of precipitated solid that deposits on the machine (0-1)
   */
  public SolidFlashDepositSource(StreamInterface stream, String solidComponent, DepositMechanism mechanism,
      double captureFraction) {
    this.stream = stream;
    this.solidComponent = solidComponent;
    this.mechanism = mechanism;
    setCaptureFraction(captureFraction);
  }

  /** {@inheritDoc} */
  @Override
  public DepositMechanism getMechanism() {
    return mechanism;
  }

  /**
   * Total precipitated-solid rate at the stream conditions (before the capture fraction is applied). This is the
   * thermodynamic drop-out rate of the named solid.
   *
   * @param flowUnit mass-flow unit, for example "kg/hr"
   * @return precipitated solid rate in the requested unit (0 if no solid forms)
   */
  public double getPrecipitationRate(String flowUnit) {
    if (stream == null || stream.getThermoSystem() == null) {
      return 0.0;
    }
    try {
      SystemInterface sys = stream.getThermoSystem().clone();
      if (sys.getComponent(solidComponent) == null) {
        return 0.0;
      }
      sys.setMultiPhaseCheck(true);
      sys.setSolidPhaseCheck(solidComponent);
      ThermodynamicOperations ops = new ThermodynamicOperations(sys);
      ops.TPSolidflash();
      if (!sys.hasPhaseType("solid")) {
        return 0.0;
      }
      return sys.getPhaseOfType("solid").getFlowRate(flowUnit);
    } catch (Exception e) {
      logger.warn("Solid precipitation flash failed for component {}: {}", solidComponent, e.getMessage());
      return 0.0;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getDepositRate(String flowUnit) {
    return getPrecipitationRate(flowUnit) * captureFraction;
  }

  /**
   * Get the capture fraction.
   *
   * @return fraction of precipitated solid that deposits on the machine (0-1)
   */
  public double getCaptureFraction() {
    return captureFraction;
  }

  /**
   * Set the capture fraction.
   *
   * @param captureFraction fraction of precipitated solid that deposits on the machine (0-1)
   */
  public void setCaptureFraction(double captureFraction) {
    this.captureFraction = Math.max(0.0, Math.min(1.0, captureFraction));
  }
}
