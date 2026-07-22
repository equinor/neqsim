package neqsim.process.equipment;

/**
 * EquipmentEnum class.
 *
 * @author esol
 */
public enum EquipmentEnum {
  Stream, ThrottlingValve, Compressor, Pump, Separator, GasScrubber, HeatExchanger, Cooler, Heater, Mixer, Splitter,
  Reactor, GibbsReactor, PlugFlowReactor, StirredTankReactor, CatalyticTubeReformer, ReformerFurnace, SyngasBurnerZone,
  AutothermalReformer, PartialOxidationReactor, QuenchSection, WaterGasShiftReactor, Column, DistillationColumn,
  ThreePhaseSeparator, ThreePhaseGasScrubber, Recycle, Ejector, GORfitter, Adjuster, SetPoint, FlowRateAdjuster,
  Calculator, SpreadsheetBlock, UnisimCalculator, Expander, SimpleTEGAbsorber, Tank, ComponentSplitter,
  ComponentCaptureUnit, ReservoirCVDsim, ReservoirDiffLibsim, VirtualStream, ReservoirTPsim, SimpleReservoir, Manifold,
  Flare, FlareStack, FuelCell, CO2Electrolyzer, Electrolyzer, WindTurbine, BatteryStorage, SolarPanel, WindFarm,
  OffshoreEnergySystem, AmmoniaSynthesisReactor, SubseaPowerCable, AdiabaticPipe, PipeBeggsAndBrills, WaterHammerPipe,
  StreamSaturatorUtil, WaterStripperColumn, Filter, SimpleAbsorber;

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return this.name();
  }
}
