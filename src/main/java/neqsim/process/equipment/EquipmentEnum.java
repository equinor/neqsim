package neqsim.process.equipment;

/**
 * <p>
 * EquipmentEnum class.
 * </p>
 *
 * @author esol
 */
public enum EquipmentEnum {
  Stream, ThrottlingValve, Compressor, Pump, Separator, GasScrubber, HeatExchanger, Cooler, Heater, Mixer, Splitter, Reactor, GibbsReactor, PlugFlowReactor, StirredTankReactor, Column, DistillationColumn, ThreePhaseSeparator, Recycle, Ejector, GORfitter, Adjuster, SetPoint, FlowRateAdjuster, Calculator, SpreadsheetBlock, UnisimCalculator, Expander, SimpleTEGAbsorber, Tank, ComponentSplitter, ReservoirCVDsim, ReservoirDiffLibsim, VirtualStream, ReservoirTPsim, SimpleReservoir, Manifold, Flare, FlareStack, FuelCell, CO2Electrolyzer, Electrolyzer, WindTurbine, BatteryStorage, SolarPanel, WindFarm, OffshoreEnergySystem, AmmoniaSynthesisReactor, SubseaPowerCable, AdiabaticPipe, PipeBeggsAndBrills, WaterHammerPipe, StreamSaturatorUtil;

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return this.name();
  }
}
