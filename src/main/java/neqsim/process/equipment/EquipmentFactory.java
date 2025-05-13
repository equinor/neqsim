package neqsim.process.equipment;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.util.GORfitter;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.reservoir.ReservoirCVDsim;
import neqsim.process.equipment.reservoir.ReservoirDiffLibsim;
import neqsim.process.equipment.stream.VirtualStream;
import neqsim.process.equipment.reservoir.ReservoirTPsim;
import neqsim.process.equipment.reservoir.SimpleReservoir;

public class EquipmentFactory {

  public static ProcessEquipmentInterface createEquipment(String name, String equipmentType) {
    switch (equipmentType) {
      case "ThrottlingValve":
        return new ThrottlingValve(name);
      case "Stream":
        return new Stream(name);
      case "Compressor":
        return new Compressor(name);
      case "Pump":
        return new Pump(name);
      case "Separator":
        return new Separator(name);
      case "HeatExchanger":
        return new HeatExchanger(name);
      case "Mixer":
        return new Mixer(name);
      case "Splitter":
        return new Splitter(name);
      case "Cooler":
        return new Cooler(name);
      case "Heater":
        return new Heater(name);
      case "Recycle":
        return new Recycle(name);
      case "ThreePhaseSeparator":
        return new ThreePhaseSeparator(name);
      case "Ejector":
        // Requires motiveStream and suctionStream, placeholders added
        return new Ejector(name, null, null);
      case "GORfitter":
        // Requires stream, placeholder added
        return new GORfitter(name, null);
      case "Adjuster":
        return new Adjuster(name);
      case "SetPoint":
        return new SetPoint(name);
      case "FlowRateAdjuster":
        return new FlowRateAdjuster(name);
      case "Calculator":
        return new Calculator(name);
      case "Expander":
        return new Expander(name);
      case "SimpleTEGAbsorber":
        return new SimpleTEGAbsorber(name);
      case "Tank":
        return new Tank(name);
      case "ComponentSplitter":
        return new ComponentSplitter(name);
      case "ReservoirCVDsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirCVDsim(name, null);
      case "ReservoirDiffLibsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirDiffLibsim(name, null);
      case "VirtualStream":
        return new VirtualStream(name);
      case "ReservoirTPsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirTPsim(name, null);
      case "SimpleReservoir":
        return new SimpleReservoir(name);
      case "Manifold":
        return new Manifold(name);

      // Add other equipment types here
      default:
        throw new IllegalArgumentException("Unknown equipment type: " + equipmentType);
    }
  }
}
