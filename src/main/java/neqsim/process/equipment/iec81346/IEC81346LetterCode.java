package neqsim.process.equipment.iec81346;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import neqsim.process.equipment.EquipmentEnum;

/**
 * IEC 81346-2 letter codes for classification of objects in industrial plants.
 *
 * <p>
 * IEC 81346-2 defines a set of single-letter codes that classify equipment and objects by their
 * purpose or function. Each letter code represents a broad category such as "converting/separating"
 * (B), "processing/compressing" (K), or "flow controlling" (Q).
 * </p>
 *
 * <p>
 * This enum maps each IEC 81346-2 letter code to its standardized description and provides a static
 * mapping from NeqSim {@link EquipmentEnum} values to the corresponding letter code. This mapping
 * is used by the {@link ReferenceDesignationGenerator} to automatically assign reference
 * designations per IEC 81346.
 * </p>
 *
 * <p>
 * <strong>IEC 81346-2 letter code table (selected codes relevant to process industry):</strong>
 * </p>
 *
 * <table>
 * <caption>IEC 81346-2 letter codes used in process industry</caption>
 * <tr>
 * <th>Code</th>
 * <th>Description</th>
 * <th>Examples</th>
 * </tr>
 * <tr>
 * <td>A</td>
 * <td>Two or more purposes or tasks</td>
 * <td>Multi-functional assemblies</td>
 * </tr>
 * <tr>
 * <td>B</td>
 * <td>Converting, separating, changing form</td>
 * <td>Heat exchangers, separators, reactors, filters</td>
 * </tr>
 * <tr>
 * <td>C</td>
 * <td>Storing, presenting information</td>
 * <td>Tanks, vessels, accumulators</td>
 * </tr>
 * <tr>
 * <td>G</td>
 * <td>Generating, providing energy</td>
 * <td>Generators, fuel cells, solar panels, wind turbines</td>
 * </tr>
 * <tr>
 * <td>K</td>
 * <td>Processing, compressing, driving</td>
 * <td>Compressors, pumps, expanders, turbines</td>
 * </tr>
 * <tr>
 * <td>M</td>
 * <td>Providing mechanical energy</td>
 * <td>Gas turbines, motors, engines</td>
 * </tr>
 * <tr>
 * <td>N</td>
 * <td>Processing information</td>
 * <td>Calculators, adjusters, controllers</td>
 * </tr>
 * <tr>
 * <td>Q</td>
 * <td>Controlling flow, movement</td>
 * <td>Valves, dampers</td>
 * </tr>
 * <tr>
 * <td>S</td>
 * <td>Sensing, detecting, measuring</td>
 * <td>Pressure transmitters, temperature transmitters</td>
 * </tr>
 * <tr>
 * <td>T</td>
 * <td>Transporting, moving</td>
 * <td>Pipes, conveyors, pipelines</td>
 * </tr>
 * <tr>
 * <td>W</td>
 * <td>Guiding, conducting</td>
 * <td>Piping segments, ducting, cable trays</td>
 * </tr>
 * <tr>
 * <td>X</td>
 * <td>Connecting, branching</td>
 * <td>Mixers, splitters, manifolds</td>
 * </tr>
 * </table>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum IEC81346LetterCode implements Serializable {

  /** Two or more purposes or tasks (multi-functional assemblies). */
  A("Two or more purposes or tasks"),

  /** Converting, separating, changing form (heat exchangers, separators, reactors, filters). */
  B("Converting, separating, changing form"),

  /** Storing, presenting information (tanks, vessels, accumulators). */
  C("Storing, presenting information"),

  /** Generating, providing energy (generators, fuel cells, solar panels, wind turbines). */
  G("Generating, providing energy"),

  /** Processing, compressing, driving (compressors, pumps, expanders). */
  K("Processing, compressing, driving"),

  /** Providing mechanical energy (gas turbines, motors, engines). */
  M("Providing mechanical energy"),

  /** Processing information (calculators, adjusters, controllers). */
  N("Processing information"),

  /** Controlling flow and movement (valves, dampers). */
  Q("Controlling flow, movement"),

  /** Sensing, detecting, measuring (transmitters, analyzers, probes). */
  S("Sensing, detecting, measuring"),

  /** Transporting, moving (pipes, conveyors, pipelines). */
  T("Transporting, moving"),

  /** Guiding, conducting (piping segments, ducting, cable trays). */
  W("Guiding, conducting"),

  /** Connecting, branching (mixers, splitters, manifolds, junctions). */
  X("Connecting, branching");

  private final String description;

  /**
   * Unmodifiable mapping from NeqSim {@link EquipmentEnum} to IEC 81346-2 letter code.
   *
   * <p>
   * This mapping is used by the reference designation generator to automatically classify process
   * equipment per IEC 81346-2.
   * </p>
   */
  private static final Map<EquipmentEnum, IEC81346LetterCode> EQUIPMENT_MAP;

  static {
    Map<EquipmentEnum, IEC81346LetterCode> map =
        new EnumMap<EquipmentEnum, IEC81346LetterCode>(EquipmentEnum.class);

    // B — Converting, separating, changing form
    map.put(EquipmentEnum.Separator, B);
    map.put(EquipmentEnum.ThreePhaseSeparator, B);
    map.put(EquipmentEnum.HeatExchanger, B);
    map.put(EquipmentEnum.Cooler, B);
    map.put(EquipmentEnum.Heater, B);
    map.put(EquipmentEnum.Reactor, B);
    map.put(EquipmentEnum.DistillationColumn, B);
    map.put(EquipmentEnum.Column, B);
    map.put(EquipmentEnum.SimpleTEGAbsorber, B);
    map.put(EquipmentEnum.ComponentSplitter, B);
    map.put(EquipmentEnum.Ejector, B);
    map.put(EquipmentEnum.AmmoniaSynthesisReactor, B);

    // C — Storing
    map.put(EquipmentEnum.Tank, C);

    // G — Generating energy
    map.put(EquipmentEnum.FuelCell, G);
    map.put(EquipmentEnum.CO2Electrolyzer, G);
    map.put(EquipmentEnum.Electrolyzer, G);
    map.put(EquipmentEnum.WindTurbine, G);
    map.put(EquipmentEnum.SolarPanel, G);
    map.put(EquipmentEnum.WindFarm, G);
    map.put(EquipmentEnum.BatteryStorage, G);
    map.put(EquipmentEnum.OffshoreEnergySystem, G);
    map.put(EquipmentEnum.SubseaPowerCable, G);

    // K — Processing, compressing, driving
    map.put(EquipmentEnum.Compressor, K);
    map.put(EquipmentEnum.Pump, K);
    map.put(EquipmentEnum.Expander, K);

    // N — Processing information
    map.put(EquipmentEnum.Adjuster, N);
    map.put(EquipmentEnum.SetPoint, N);
    map.put(EquipmentEnum.FlowRateAdjuster, N);
    map.put(EquipmentEnum.Calculator, N);
    map.put(EquipmentEnum.Recycle, N);
    map.put(EquipmentEnum.GORfitter, N);

    // Q — Controlling flow
    map.put(EquipmentEnum.ThrottlingValve, Q);

    // T — Transporting
    map.put(EquipmentEnum.Stream, T);
    map.put(EquipmentEnum.VirtualStream, T);
    map.put(EquipmentEnum.AdiabaticPipe, T);
    map.put(EquipmentEnum.PipeBeggsAndBrills, T);

    // X — Connecting, branching
    map.put(EquipmentEnum.Mixer, X);
    map.put(EquipmentEnum.Splitter, X);
    map.put(EquipmentEnum.Manifold, X);

    // A — Multi-purpose / special
    map.put(EquipmentEnum.Flare, A);
    map.put(EquipmentEnum.FlareStack, A);
    map.put(EquipmentEnum.StreamSaturatorUtil, A);
    map.put(EquipmentEnum.SimpleReservoir, A);
    map.put(EquipmentEnum.ReservoirCVDsim, A);
    map.put(EquipmentEnum.ReservoirDiffLibsim, A);
    map.put(EquipmentEnum.ReservoirTPsim, A);

    EQUIPMENT_MAP = Collections.unmodifiableMap(map);
  }

  /**
   * Constructs an IEC 81346-2 letter code with its standardized description.
   *
   * @param description the standard description of the letter code per IEC 81346-2
   */
  IEC81346LetterCode(String description) {
    this.description = description;
  }

  /**
   * Returns the standardized description of this IEC 81346-2 letter code.
   *
   * @return the description string, e.g. "Converting, separating, changing form"
   */
  public String getDescription() {
    return description;
  }

  /**
   * Maps a NeqSim {@link EquipmentEnum} to its corresponding IEC 81346-2 letter code.
   *
   * @param equipmentType the NeqSim equipment type
   * @return the IEC 81346-2 letter code, or {@link #A} if no specific mapping exists
   */
  public static IEC81346LetterCode fromEquipmentEnum(EquipmentEnum equipmentType) {
    if (equipmentType == null) {
      return A;
    }
    IEC81346LetterCode code = EQUIPMENT_MAP.get(equipmentType);
    return code != null ? code : A;
  }

  /**
   * Maps a NeqSim equipment class to its corresponding IEC 81346-2 letter code.
   *
   * <p>
   * This method first attempts to resolve the letter code through the canonical
   * {@link #EQUIPMENT_MAP} by trying to match the equipment's simple class name to an
   * {@link EquipmentEnum} value. If that fails, it falls back to {@code instanceof} checks for
   * broad base-class detection (e.g. any {@code Separator} subclass maps to {@code B}).
   * </p>
   *
   * @param equipment the process equipment instance
   * @return the IEC 81346-2 letter code, or {@link #A} if the type is not recognized
   */
  public static IEC81346LetterCode fromEquipment(
      neqsim.process.equipment.ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return A;
    }

    // Primary path: try to resolve via EQUIPMENT_MAP using the class name as EquipmentEnum
    String className = equipment.getClass().getSimpleName();
    try {
      EquipmentEnum enumVal = EquipmentEnum.valueOf(className);
      IEC81346LetterCode code = EQUIPMENT_MAP.get(enumVal);
      if (code != null) {
        return code;
      }
    } catch (IllegalArgumentException ignored) {
      // Class name does not match any EquipmentEnum — fall through to instanceof
    }

    // Fallback: instanceof checks for base classes and interfaces
    if (equipment instanceof neqsim.process.equipment.separator.Separator) {
      return B;
    }
    if (equipment instanceof neqsim.process.equipment.heatexchanger.HeatExchanger
        || equipment instanceof neqsim.process.equipment.heatexchanger.Heater
        || equipment instanceof neqsim.process.equipment.heatexchanger.Cooler) {
      return B;
    }
    if (equipment instanceof neqsim.process.equipment.compressor.Compressor) {
      return K;
    }
    if (equipment instanceof neqsim.process.equipment.pump.Pump) {
      return K;
    }
    if (equipment instanceof neqsim.process.equipment.expander.Expander) {
      return K;
    }
    if (equipment instanceof neqsim.process.equipment.valve.ThrottlingValve) {
      return Q;
    }
    if (equipment instanceof neqsim.process.equipment.mixer.Mixer) {
      return X;
    }
    if (equipment instanceof neqsim.process.equipment.splitter.Splitter) {
      return X;
    }
    if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
      return T;
    }
    if (equipment instanceof neqsim.process.equipment.pipeline.Pipeline) {
      return T;
    }
    if (equipment instanceof neqsim.process.equipment.distillation.DistillationColumn) {
      return B;
    }
    if (equipment instanceof neqsim.process.equipment.tank.Tank) {
      return C;
    }
    if (equipment instanceof neqsim.process.equipment.reactor.GibbsReactor) {
      return B;
    }
    if (equipment instanceof neqsim.process.equipment.util.Recycle) {
      return N;
    }
    if (equipment instanceof neqsim.process.equipment.util.Adjuster) {
      return N;
    }
    return A;
  }

  /**
   * Returns the unmodifiable mapping from {@link EquipmentEnum} to IEC 81346-2 letter codes.
   *
   * @return unmodifiable map of all defined equipment-to-letter-code mappings
   */
  public static Map<EquipmentEnum, IEC81346LetterCode> getEquipmentMapping() {
    return EQUIPMENT_MAP;
  }
}
