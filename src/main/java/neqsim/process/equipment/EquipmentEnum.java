package neqsim.process.equipment;

/**
 * <p>
 * EquipmentEnum class.
 * </p>
 *
 * @author esol
 */
public enum EquipmentEnum {
  Stream, ThrottlingValve, Compressor, Pump, Separator, HeatExchanger, Cooler, Heater, Mixer, Splitter, Reactor, Column, ThreePhaseSeparator, Recycle, Ejector, GORfitter, Adjuster, SetPoint, FlowRateAdjuster, Calculator, Expander, SimpleTEGAbsorber, Tank, ComponentSplitter, ReservoirCVDsim, ReservoirDiffLibsim, VirtualStream, ReservoirTPsim, SimpleReservoir, Manifold, Flare, FlareStack;

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return this.name();
  }
}
