package neqsim.physicalproperties.methods.liquidphysicalproperties;

import neqsim.physicalproperties.methods.methodinterface.ConductivityInterface;
import neqsim.physicalproperties.methods.methodinterface.DensityInterface;
import neqsim.physicalproperties.methods.methodinterface.ViscosityInterface;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseWaterIAPWS;
import neqsim.thermo.util.steam.CoolingWaterTransport;

/**
 * Consistent liquid reference-water density and transport adapters, including bulk seawater.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ReferenceWaterTransportProperties extends LiquidPhysicalPropertyMethod
    implements DensityInterface, ViscosityInterface, ConductivityInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Creates a property adapter bound to a reference-water phase.
   *
   * @param properties physical properties owning this method
   */
  public ReferenceWaterTransportProperties(PhysicalProperties properties) {
    super(properties);
  }

  /** {@inheritDoc} */
  @Override
  public ReferenceWaterTransportProperties clone() {
    return (ReferenceWaterTransportProperties) super.clone();
  }

  /** {@inheritDoc} */
  @Override
  public double calcDensity() {
    return liquidPhase.getPhase().getDensity();
  }

  /** {@inheritDoc} */
  @Override
  public double calcViscosity() {
    PhaseWaterIAPWS water = (PhaseWaterIAPWS) liquidPhase.getPhase();
    if (water.getSalinity() == 0.0 && (water.getTemperature() > 353.0 || water.getPressure() > 120.0
        || water.getTemperature() < 273.15 || water.getPressure() < 1.0)) {
      return neqsim.physicalproperties.methods.liquidphysicalproperties.viscosity.Viscosity
          .calcWaterViscosity(water.getTemperature(), water.getPressure()) / 1000.0;
    }
    return CoolingWaterTransport.viscosity(water.getTemperature(), water.getPressure(), water.getSalinity());
  }

  /** {@inheritDoc} */
  @Override
  public double getPureComponentViscosity(int i) {
    PhaseWaterIAPWS water = (PhaseWaterIAPWS) liquidPhase.getPhase();
    if (water.getSalinity() == 0.0) {
      return calcViscosity() * 1000.0;
    }
    return CoolingWaterTransport.viscosity(water.getTemperature(), water.getPressure(), 0.0) * 1000.0;
  }

  /** {@inheritDoc} */
  @Override
  public double calcConductivity() {
    PhaseWaterIAPWS water = (PhaseWaterIAPWS) liquidPhase.getPhase();
    if (water.getSalinity() == 0.0 && (water.getTemperature() > 353.0 || water.getPressure() > 120.0
        || water.getTemperature() < 273.15 || water.getPressure() < 1.0)) {
      return new neqsim.physicalproperties.methods.commonphasephysicalproperties.conductivity.WaterConductivityMethod(
          liquidPhase).calcConductivity();
    }
    return CoolingWaterTransport.conductivity(water.getTemperature(), water.getPressure(), water.getSalinity());
  }
}
