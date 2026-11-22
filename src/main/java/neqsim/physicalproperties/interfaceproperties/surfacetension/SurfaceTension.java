/*
 * SurfaceTension.java
 *
 * Created on 13. august 2001, 13:14
 */

package neqsim.physicalproperties.interfaceproperties.surfacetension;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.physicalproperties.interfaceproperties.InterfaceProperties;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * SurfaceTension class.
 *
 * @author esol
 * @version $Id: $Id
 */
public class SurfaceTension extends InterfaceProperties implements SurfaceTensionInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger for explicit model fallback diagnostics. */
  private static final Logger logger = LogManager.getLogger(SurfaceTension.class);

  protected SystemInterface system;

  /**
   * Constructor for SurfaceTension.
   */
  public SurfaceTension() {
  }

  /**
   * Constructor for SurfaceTension.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SurfaceTension(SystemInterface system) {
    this.system = system;
  }

  /**
   * Copies this model's configuration for another fluid without retaining a computed profile.
   *
   * @param target fluid whose bulk phases the copied model will use
   * @return independently bound model with the same configuration
   */
  public SurfaceTension copyForSystem(SystemInterface target) {
    SurfaceTension copy = (SurfaceTension) super.clone();
    copy.system = target;
    copy.clearProfile();
    return copy;
  }

  /** Discards state-dependent profile data when a model is copied to another fluid. */
  protected void clearProfile() {
    // Correlations and cDFT do not retain an exposed density profile.
  }

  /**
   * Calculates only the physical mass density required by a correlation at the current bulk state.
   *
   * @param index active phase index
   * @return finite positive density in kg/m3 from the configured physical-property model
   * @throws IllegalStateException if the density model returns a non-finite or nonpositive value
   */
  protected double physicalDensity(int index) {
    PhaseInterface phase = system.getPhase(index);
    phase.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);
    double density = phase.getPhysicalProperties().getDensity();
    if (!Double.isFinite(density) || density <= 0.0) {
      throw new IllegalStateException("Invalid physical density for surface tension in phase " + index);
    }
    return density;
  }

  /**
   * Calculates the pure component surfacetension.
   *
   * @param componentNumber Number of component in phase's componentarray.
   * @return pure component surface tension
   * @throws UnsupportedOperationException if the selected model has no separate pure-component correlation
   */
  public double calcPureComponentSurfaceTension(int componentNumber) {
    throw new UnsupportedOperationException("Use calcSurfaceTension for this model's pure-fluid interface");
  }

  /** {@inheritDoc} */
  @Override
  public double calcSurfaceTension(int interface1, int interface2) {
    throw new UnsupportedOperationException("Select an interfacial-tension model before calculating");
  }

  /**
   * Validates the active bulk phases before a model evaluates their interface.
   *
   * @param interface1 first phase index
   * @param interface2 second phase index
   * @return false for a single-phase system, which retains the legacy zero/no-interface result
   * @throws IllegalArgumentException if the phase indices are inactive or identical
   * @throws IllegalStateException if a bulk state is not finite and physical
   */
  protected boolean validateInterface(int interface1, int interface2) {
    if (system == null) {
      throw new IllegalStateException("Surface tension requires a thermodynamic system");
    }
    if (system.getNumberOfPhases() < 2) {
      return false;
    }
    if (interface1 < 0 || interface2 < 0 || interface1 >= system.getNumberOfPhases()
        || interface2 >= system.getNumberOfPhases() || interface1 == interface2) {
      throw new IllegalArgumentException("Surface tension requires two distinct active phase indices");
    }
    validatePhase(interface1);
    validatePhase(interface2);
    return true;
  }

  /**
   * Checks one bulk phase without temporary arrays or repeated component sums.
   *
   * @param index active phase index
   * @throws IllegalStateException if the bulk state or composition is invalid
   */
  private void validatePhase(int index) {
    PhaseInterface phase = system.getPhase(index);
    if (!isPositiveFinite(phase.getTemperature()) || !isPositiveFinite(phase.getPressure())
        || !isPositiveFinite(phase.getMolarVolume()) || !isPositiveFinite(phase.getMolarMass())) {
      throw new IllegalStateException("Invalid bulk state for surface tension in phase " + index);
    }
    if (phase instanceof PhaseEosInterface) {
      GTSurfaceTensionUtils.validateState(phase);
    }
    double sum = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      double fraction = phase.getComponent(i).getx();
      if (!Double.isFinite(fraction) || fraction < 0.0) {
        throw new IllegalStateException("Invalid bulk composition for surface tension in phase " + index);
      }
      sum += fraction;
    }
    if (!Double.isFinite(sum) || Math.abs(sum - 1.0) > 1e-6) {
      throw new IllegalStateException("Bulk composition is not normalized for surface tension in phase " + index);
    }
  }

  /**
   * Tests one scalar bulk property without reading it twice.
   *
   * @param value bulk property value
   * @return whether the value is finite and positive
   */
  private static boolean isPositiveFinite(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  /**
   * Rejects non-finite or negative model results instead of exposing them to downstream calculations.
   *
   * @param tension calculated interfacial tension in N/m
   * @return the validated tension
   * @throws IllegalStateException if the result is non-finite or negative
   */
  protected double validateResult(double tension) {
    if (!Double.isFinite(tension) || tension < 0.0) {
      throw new IllegalStateException(getClass().getSimpleName() + " returned invalid surface tension: " + tension);
    }
    return tension;
  }

  /**
   * Selects the component with the largest bulk density contrast for density-path integration. A trace high-boiling
   * component can have almost identical densities in gas and oil in a three-phase fluid.
   *
   * @param interface1 first phase index
   * @param interface2 second phase index
   * @return component index with the largest absolute molar-density contrast
   * @throws IllegalStateException if the bulk phases have no density contrast
   */
  protected int getReferenceComponent(int interface1, int interface2) {
    int reference = 0;
    double largestContrast = 0.0;
    for (int i = 0; i < system.getPhase(interface1).getNumberOfComponents(); i++) {
      double contrast = Math
          .abs(system.getPhase(interface2).getComponent(i).getx() / system.getPhase(interface2).getMolarVolume()
              - system.getPhase(interface1).getComponent(i).getx() / system.getPhase(interface1).getMolarVolume());
      if (contrast > largestContrast) {
        reference = i;
        largestContrast = contrast;
      }
    }
    if (largestContrast <= 0.0 || !Double.isFinite(largestContrast)) {
      throw new IllegalStateException("Bulk phases have no finite density contrast for gradient theory");
    }
    return reference;
  }

  /**
   * Provides a documented correlation result when a density-profile solver fails.
   *
   * @param interface1 first phase index
   * @param interface2 second phase index
   * @param failure reason the selected model failed
   * @return validated Parachor tension for gas interfaces or Firozabadi-Ramley tension for liquid interfaces, in N/m
   * @throws IllegalStateException if the correlation also cannot produce a valid result
   */
  protected double correlationFallback(int interface1, int interface2, RuntimeException failure) {
    boolean gasInterface = system.getPhase(interface1).getType() == PhaseType.GAS
        || system.getPhase(interface2).getType() == PhaseType.GAS;
    SurfaceTension correlation = gasInterface ? new ParachorSurfaceTension(system)
        : new FirozabadiRamleyInterfaceTension(system);
    logger.warn("{} failed: {}. Falling back to {}.", getClass().getSimpleName(), failure.getMessage(),
        correlation.getClass().getSimpleName());
    try {
      return correlation.calcSurfaceTension(interface1, interface2);
    } catch (RuntimeException correlationFailure) {
      correlationFailure.addSuppressed(failure);
      throw correlationFailure;
    }
  }

  /**
   * getComponentWithHighestBoilingpoint.
   *
   * @return a int
   */
  public int getComponentWithHighestBoilingpoint() {
    int compNumb = 0;
    double boilPoint = 0;
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      if (system.getPhase(0).getComponent(i).getNormalBoilingPoint() > boilPoint) {
        compNumb = i;
        boilPoint = system.getPhase(0).getComponent(i).getNormalBoilingPoint();
      }
    }
    return compNumb;
  }
}
