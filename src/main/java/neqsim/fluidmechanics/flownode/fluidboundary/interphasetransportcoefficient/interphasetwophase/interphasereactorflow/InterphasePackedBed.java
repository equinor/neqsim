package neqsim.fluidmechanics.flownode.fluidboundary.interphasetransportcoefficient.interphasetwophase.interphasereactorflow;

import neqsim.fluidmechanics.flownode.FlowNodeInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * <p>
 * InterphasePackedBed class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class InterphasePackedBed extends InterphaseReactorFlow
    implements neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for InterphasePackedBed.
   * </p>
   */
  public InterphasePackedBed() {}

  /**
   * <p>
   * Constructor for InterphasePackedBed.
   * </p>
   *
   * @param node a {@link neqsim.fluidmechanics.flownode.FlowNodeInterface} object
   */
  public InterphasePackedBed(FlowNodeInterface node) {
    // flowNode = node;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallFrictionFactor(int phase, FlowNodeInterface node) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterPhaseFrictionFactor(int phase, FlowNodeInterface node) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseHeatTransferCoefficient(int phase, double prandtlNumber,
      FlowNodeInterface node) {
    PhaseInterface phaseObject = node.getBulkSystem().getPhase(phase);
    double diffusivity = getEffectiveDiffusivity(phaseObject);
    double density = getFinitePositive(phaseObject.getPhysicalProperties().getDensity(),
        phaseObject.getDensity("kg/m3"));
    double viscosity = getFinitePositive(phaseObject.getPhysicalProperties().getViscosity(),
        phaseObject.getViscosity("kg/msec"));
    double schmidtNumber = viscosity / Math.max(density * diffusivity, 1.0e-30);
    double massTransferCoefficient =
        calcInterphaseMassTransferCoefficient(phase, schmidtNumber, node);
    if (!isFinitePositive(massTransferCoefficient)) {
      return 0.0;
    }
    double heatCapacity = getHeatCapacityMass(phaseObject);
    double correctedPrandtlNumber = getFinitePositive(prandtlNumber, heatCapacity * viscosity
        / Math.max(phaseObject.getPhysicalProperties().getConductivity(), 1.0e-30));
    return massTransferCoefficient * density * heatCapacity
        * Math.pow(schmidtNumber / correctedPrandtlNumber, 2.0 / 3.0);
  }

  /** {@inheritDoc} */
  @Override
  public double calcWallMassTransferCoefficient(int phase, double schmidtNumber,
      FlowNodeInterface node) {
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcInterphaseMassTransferCoefficient(int phaseNum, double schmidtNumber,
      FlowNodeInterface node) {
    double redMassTrans = 0;
    double massTrans = 0;
    if (phaseNum == 1) {
      // massTrans = 0.0002;
      redMassTrans =
          0.0051 * Math.pow(node.getReynoldsNumber(phaseNum), 0.67) * Math.pow(schmidtNumber, -0.5)
              * Math.pow(node.getGeometry().getPacking().getSurfaceAreaPrVolume()
                  * node.getGeometry().getPacking().getSize(), 0.4);
      massTrans = redMassTrans * Math.pow(
          node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
              * gravity,
          1.0 / 3.0);
    }
    if (phaseNum == 0) {
      redMassTrans =
          3.6 * Math.pow(node.getReynoldsNumber(phaseNum), 0.7) * Math.pow(schmidtNumber, 0.33)
              * Math.pow(node.getGeometry().getPacking().getSurfaceAreaPrVolume()
                  * node.getGeometry().getPacking().getSize(), -2.0);
      massTrans = redMassTrans * node.getGeometry().getPacking().getSurfaceAreaPrVolume()
          * node.getBulkSystem().getPhase(phaseNum).getPhysicalProperties().getKinematicViscosity()
          / schmidtNumber;
    }
    return massTrans;
  }

  /**
   * Get an effective component diffusivity for the phase.
   *
   * @param phase phase to inspect
   * @return effective diffusivity in m2/s
   */
  private double getEffectiveDiffusivity(PhaseInterface phase) {
    double sum = 0.0;
    int count = 0;
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      try {
        double value = phase.getPhysicalProperties().getEffectiveDiffusionCoefficient(component);
        if (isFinitePositive(value)) {
          sum += value;
          count++;
        }
      } catch (RuntimeException ex) {
        // Fallback below keeps legacy packed-bed transport robust.
      }
    }
    if (count > 0) {
      return sum / count;
    }
    return phase.getType().toString().equalsIgnoreCase("gas") ? 1.5e-5 : 1.5e-9;
  }

  /**
   * Get mass heat capacity for a phase.
   *
   * @param phase phase to inspect
   * @return heat capacity in J/(kg K)
   */
  private double getHeatCapacityMass(PhaseInterface phase) {
    try {
      return getFinitePositive(phase.getCp("J/kgK"), 2000.0);
    } catch (RuntimeException ex) {
      double moles = getFinitePositive(phase.getNumberOfMolesInPhase(), 1.0);
      double molarMass = getFinitePositive(phase.getMolarMass(), 0.020);
      return getFinitePositive(phase.getCp() / (moles * molarMass), 2000.0);
    }
  }

  /**
   * Return a fallback when a value is not positive and finite.
   *
   * @param value value to inspect
   * @param fallback fallback value
   * @return value if finite and positive, otherwise fallback
   */
  private double getFinitePositive(double value, double fallback) {
    return isFinitePositive(value) ? value : fallback;
  }

  /**
   * Check if a value is positive and finite.
   *
   * @param value value to inspect
   * @return true if positive and finite
   */
  private boolean isFinitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }
}
