package neqsim.process.equipment.subsea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.util.validation.ValidationResult;

/**
 * Subsea separation station: a three-phase separator on the seabed with boosting of the separated liquid and water.
 *
 * <p>
 * The station splits the inlet into a gas stream, a liquid (oil) stream that goes to the host and a water stream that
 * is reinjected or discharged. It is built from {@link ThreePhaseSeparator} and two {@link Pump} units, so the phase
 * split comes from a rigorous flash of the inlet at the station pressure. Imperfect separation is represented by
 * carry-over fractions on a feed basis: water that stays in the oil, oil that goes with the water and gas that is
 * carried under into the liquid.
 * </p>
 *
 * <p>
 * Typical use is capacity screening: the water and gas volumes that no longer reach the host are read from the outlet
 * streams, and the station power from {@link #getTotalPowerKW()}. Hydrate, wax and slug control, solids handling,
 * qualification and the umbilical are outside the model.
 * </p>
 *
 * <pre>{@code
 * SubseaSeparationStation station = new SubseaSeparationStation("SSS", wellStream);
 * station.setPressureDrop(2.0);
 * station.setWaterRemovalEfficiency(0.95);
 * station.setLiquidExportPressure(60.0);
 * station.setWaterInjectionPressure(250.0);
 * station.run();
 * double waterToHostFraction = 1.0 - station.getWaterRemovedFraction();
 * }</pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SubseaSeparationStation extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Inlet stream. */
  private StreamInterface inStream;
  /** Separator. */
  private final ThreePhaseSeparator separator;
  /** Pump for the liquid sent to the host. */
  private final Pump liquidPump;
  /** Pump for the water sent to reinjection. */
  private final Pump waterPump;

  /** Pressure drop over the station [bar]. */
  private double pressureDrop = 0.0;
  /** Fraction of the feed water that leaves through the water outlet [-]. */
  private double waterRemovalEfficiency = 1.0;
  /** Fraction of the feed oil carried to the water outlet [-]. */
  private double oilInWaterFraction = 0.0;
  /** Fraction of the feed gas carried under into the liquid outlet [-]. */
  private double gasCarryUnderFraction = 0.0;
  /** Liquid export pressure [bara], zero for no boosting. */
  private double liquidExportPressure = 0.0;
  /** Water injection pressure [bara], zero for no boosting. */
  private double waterInjectionPressure = 0.0;
  /** Pump isentropic efficiency [-]. */
  private double pumpEfficiency = 0.7;

  /** True when the liquid pump was in service in the last run. */
  private boolean liquidBoosted = false;
  /** True when the water pump was in service in the last run. */
  private boolean waterBoosted = false;

  /**
   * Creates a station.
   *
   * @param name equipment name
   * @param inletStream inlet stream (well stream or manifold outlet)
   * @throws IllegalArgumentException if the inlet stream is null
   */
  public SubseaSeparationStation(String name, StreamInterface inletStream) {
    super(name);
    if (inletStream == null) {
      throw new IllegalArgumentException("Equipment '" + name + "' requires a non-null inlet stream");
    }
    this.inStream = inletStream;
    this.separator = new ThreePhaseSeparator(name + " separator", inletStream);
    this.liquidPump = new Pump(name + " liquid pump", separator.getOilOutStream());
    this.waterPump = new Pump(name + " water pump", separator.getWaterOutStream());
  }

  /**
   * Sets the pressure drop over the station.
   *
   * @param pressureDropBar pressure drop [bar]
   */
  public void setPressureDrop(double pressureDropBar) {
    this.pressureDrop = pressureDropBar;
  }

  /**
   * Sets the fraction of the feed water that leaves through the water outlet.
   *
   * @param efficiency water removal efficiency between 0 and 1
   * @throws IllegalArgumentException if the value is outside [0, 1]
   */
  public void setWaterRemovalEfficiency(double efficiency) {
    requireFraction(efficiency, "water removal efficiency");
    this.waterRemovalEfficiency = efficiency;
  }

  /**
   * Sets the fraction of the feed oil carried over to the water outlet.
   *
   * @param fraction oil-in-water carry-over on a mass basis of the feed oil, between 0 and 1
   * @throws IllegalArgumentException if the value is outside [0, 1]
   */
  public void setOilInWaterFraction(double fraction) {
    requireFraction(fraction, "oil in water fraction");
    this.oilInWaterFraction = fraction;
  }

  /**
   * Sets the fraction of the feed gas carried under into the liquid outlet.
   *
   * @param fraction gas carry-under on a mass basis of the feed gas, between 0 and 1
   * @throws IllegalArgumentException if the value is outside [0, 1]
   */
  public void setGasCarryUnderFraction(double fraction) {
    requireFraction(fraction, "gas carry-under fraction");
    this.gasCarryUnderFraction = fraction;
  }

  /**
   * Sets the pressure to which the liquid is boosted.
   *
   * @param pressureBara liquid export pressure [bara]; zero or below the station pressure means no boosting
   */
  public void setLiquidExportPressure(double pressureBara) {
    this.liquidExportPressure = pressureBara;
  }

  /**
   * Sets the pressure to which the separated water is boosted.
   *
   * @param pressureBara water injection pressure [bara]; zero or below the station pressure means no boosting
   */
  public void setWaterInjectionPressure(double pressureBara) {
    this.waterInjectionPressure = pressureBara;
  }

  /**
   * Sets the isentropic efficiency of both pumps.
   *
   * @param efficiency isentropic efficiency [-], greater than zero and at most one
   * @throws IllegalArgumentException if the value is outside (0, 1]
   */
  public void setPumpEfficiency(double efficiency) {
    if (efficiency <= 0.0 || efficiency > 1.0) {
      throw new IllegalArgumentException("pump efficiency must be in (0, 1]");
    }
    this.pumpEfficiency = efficiency;
  }

  /**
   * Sets the vessel geometry used for residence times and gas load.
   *
   * @param internalDiameterM internal diameter [m]
   * @param lengthM length [m]
   * @param orientation {@code "horizontal"} or {@code "vertical"}
   */
  public void setVesselDimensions(double internalDiameterM, double lengthM, String orientation) {
    separator.setInternalDiameter(internalDiameterM);
    separator.setSeparatorLength(lengthM);
    separator.setOrientation(orientation);
  }

  /**
   * Internal separator, for residence times, gas load factor and mechanical design.
   *
   * @return the three-phase separator
   */
  public ThreePhaseSeparator getSeparator() {
    return separator;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    separator.setPressureDrop(pressureDrop);
    separator.setEntrainment(1.0 - waterRemovalEfficiency, "mass", "feed", "aqueous", "oil");
    separator.setEntrainment(oilInWaterFraction, "mass", "feed", "oil", "aqueous");
    separator.setEntrainment(gasCarryUnderFraction, "mass", "feed", "gas", "oil");
    separator.run(id);

    liquidBoosted = liquidExportPressure > separator.getOilOutStream().getPressure() + 1.0e-6
        && separator.getOilOutStream().getFlowRate("kg/hr") > 1.0e-6;
    if (liquidBoosted) {
      liquidPump.setIsentropicEfficiency(pumpEfficiency);
      liquidPump.setOutletPressure(liquidExportPressure);
      liquidPump.run(id);
    }
    waterBoosted = waterInjectionPressure > separator.getWaterOutStream().getPressure() + 1.0e-6
        && separator.getWaterOutStream().getFlowRate("kg/hr") > 1.0e-6;
    if (waterBoosted) {
      waterPump.setIsentropicEfficiency(pumpEfficiency);
      waterPump.setOutletPressure(waterInjectionPressure);
      waterPump.run(id);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Gas outlet stream.
   *
   * @return gas stream at the station pressure
   */
  public StreamInterface getGasOutStream() {
    return separator.getGasOutStream();
  }

  /**
   * Liquid (oil) outlet stream to the host, boosted when a liquid export pressure above the station pressure is set.
   *
   * @return liquid stream
   */
  public StreamInterface getLiquidOutStream() {
    return liquidBoosted ? liquidPump.getOutletStream() : separator.getOilOutStream();
  }

  /**
   * Water outlet stream, boosted when a water injection pressure above the station pressure is set.
   *
   * @return water stream
   */
  public StreamInterface getWaterOutStream() {
    return waterBoosted ? waterPump.getOutletStream() : separator.getWaterOutStream();
  }

  /**
   * Power of the liquid pump.
   *
   * @return power [kW], zero when the pump is not in service
   */
  public double getLiquidPumpPowerKW() {
    return liquidBoosted ? liquidPump.getPower("kW") : 0.0;
  }

  /**
   * Power of the water pump.
   *
   * @return power [kW], zero when the pump is not in service
   */
  public double getWaterPumpPowerKW() {
    return waterBoosted ? waterPump.getPower("kW") : 0.0;
  }

  /**
   * Total pump power of the station.
   *
   * @return power [kW]
   */
  public double getTotalPowerKW() {
    return getLiquidPumpPowerKW() + getWaterPumpPowerKW();
  }

  /**
   * Fraction of the feed water (mass) that leaves through the water outlet.
   *
   * @return removed fraction [-], zero when the feed has no water
   */
  public double getWaterRemovedFraction() {
    double feed = componentMassFlow(inStream, "water");
    if (feed <= 0.0) {
      return 0.0;
    }
    return componentMassFlow(getWaterOutStream(), "water") / feed;
  }

  /**
   * Water mass fraction of the liquid sent to the host.
   *
   * @return water mass fraction of the liquid outlet [-]
   */
  public double getWaterMassFractionInLiquidOut() {
    double total = getLiquidOutStream().getFlowRate("kg/hr");
    if (total <= 0.0) {
      return 0.0;
    }
    return componentMassFlow(getLiquidOutStream(), "water") / total;
  }

  /**
   * Mass flow of one component in a stream, summed over its phases.
   *
   * <p>
   * Carried-over material is stored in the phases of the outlet fluid, not in the system totals, so the sum is taken
   * over the phases.
   * </p>
   *
   * @param stream stream
   * @param component component name
   * @return mass flow [kg/hr], zero when the component is absent
   */
  private static double componentMassFlow(StreamInterface stream, String component) {
    if (stream.getFluid() == null || !stream.getFluid().hasComponent(component)) {
      return 0.0;
    }
    double total = 0.0;
    for (int i = 0; i < stream.getFluid().getNumberOfPhases(); i++) {
      neqsim.thermo.component.ComponentInterface c = stream.getFluid().getPhase(i).getComponent(component);
      total += c.getNumberOfMolesInPhase() * c.getMolarMass() * 3600.0;
    }
    return total;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.singletonList(inStream);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outlets = new ArrayList<StreamInterface>(3);
    outlets.add(getGasOutStream());
    outlets.add(getLiquidOutStream());
    outlets.add(getWaterOutStream());
    return Collections.unmodifiableList(outlets);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double out = getGasOutStream().getFlowRate(unit) + getLiquidOutStream().getFlowRate(unit)
        + getWaterOutStream().getFlowRate(unit);
    return out - inStream.getFlowRate(unit);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (inStream == null || inStream.getThermoSystem() == null) {
      result.addError("stream", "No inlet stream", "Create the station with a stream that has a fluid");
    } else if (!inStream.getFluid().hasComponent("water")) {
      result.addWarning("fluid", "Inlet fluid has no water component",
          "Add water to the fluid; otherwise the water outlet is empty");
    }
    if (pressureDrop < 0.0) {
      result.addError("pressureDrop", "Negative pressure drop", "Use a pressure drop of zero or more");
    }
    return result;
  }

  /**
   * Checks that a value is a fraction.
   *
   * @param value value to check
   * @param label name used in the message
   * @throws IllegalArgumentException if the value is outside [0, 1]
   */
  private static void requireFraction(double value, String label) {
    if (value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(label + " must be between 0 and 1");
    }
  }
}
