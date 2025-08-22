package neqsim.process.equipment;

import neqsim.process.equipment.absorber.SimpleTEGAbsorber;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.electrolyzer.Electrolyzer;
import neqsim.process.equipment.battery.BatteryStorage;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reservoir.ReservoirCVDsim;
import neqsim.process.equipment.reservoir.ReservoirDiffLibsim;
import neqsim.process.equipment.reservoir.ReservoirTPsim;
import neqsim.process.equipment.reservoir.SimpleReservoir;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.VirtualStream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.FlowRateAdjuster;
import neqsim.process.equipment.util.GORfitter;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.util.SetPoint;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.flare.Flare;
import neqsim.process.equipment.flare.FlareStack;

/**
 * <p>
 * EquipmentFactory class.
 * </p>
 *
 * @author esol
 */
public class EquipmentFactory {
  /**
   * <p>
   * createEquipment.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param equipmentType a {@link java.lang.String} object
   * @return a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public static ProcessEquipmentInterface createEquipment(String name, String equipmentType) {
    if (equipmentType == null || equipmentType.trim().isEmpty()) {
      throw new IllegalArgumentException("Equipment type cannot be null or empty");
    }

    String normalizedType = equipmentType.trim().toLowerCase();
    switch (normalizedType) {
      case "throttlingvalve":
      case "valve":
        return new ThrottlingValve(name);
      case "stream":
        return new Stream(name);
      case "compressor":
        return new Compressor(name);
      case "pump":
        return new Pump(name);
      case "separator":
        return new Separator(name);
      case "heatexchanger":
        return new HeatExchanger(name);
      case "mixer":
        return new Mixer(name);
      case "splitter":
        return new Splitter(name);
      case "cooler":
        return new Cooler(name);
      case "heater":
        return new Heater(name);
      case "recycle":
        return new Recycle(name);
      case "threephaseseparator":
      case "separator_3phase":
        return new ThreePhaseSeparator(name);
      case "ejector":
        // Requires motiveStream and suctionStream, placeholders added
        return new Ejector(name, null, null);
      case "gorfitter":
        // Requires stream, placeholder added
        return new GORfitter(name, null);
      case "adjuster":
        return new Adjuster(name);
      case "setpoint":
        return new SetPoint(name);
      case "flowrateadjuster":
        return new FlowRateAdjuster(name);
      case "calculator":
        return new Calculator(name);
      case "expander":
        return new Expander(name);
      case "simpletegabsorber":
        return new SimpleTEGAbsorber(name);
      case "tank":
        return new Tank(name);
      case "componentsplitter":
        return new ComponentSplitter(name);
      case "reservoircvdsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirCVDsim(name, null);
      case "reservoirdifflibsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirDiffLibsim(name, null);
      case "virtualstream":
        return new VirtualStream(name);
      case "reservoirtpsim":
        // Requires reservoirFluid, placeholder added
        return new ReservoirTPsim(name, null);
      case "simplereservoir":
        return new SimpleReservoir(name);
      case "manifold":
        return new Manifold(name);
      case "flare":
        return new Flare(name);
      case "flarestack":
        return new FlareStack(name);
      case "electrolyzer":
        return new Electrolyzer(name);
      case "batterystorage":
        return new BatteryStorage(name);

      // Add other equipment types here
      default:
        throw new IllegalArgumentException("Unknown equipment type: " + equipmentType);
    }
  }
}
