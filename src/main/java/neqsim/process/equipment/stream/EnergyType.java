package neqsim.process.equipment.stream;

/**
 * Physical energy domain carried by an {@link EnergyStream}.
 *
 * <p>
 * The type is metadata used to prevent accidental connections such as wiring an electrical generator directly to a
 * heat-duty port. {@link #UNSPECIFIED} preserves compatibility with legacy energy streams that did not declare a
 * domain.
 * </p>
 *
 * @author NeqSim
 * @version 2.0
 */
public enum EnergyType {
  /** Legacy or otherwise unspecified energy domain. */
  UNSPECIFIED,
  /** Heat transferred across an equipment boundary. */
  HEAT,
  /** Mechanical shaft work transferred between rotating equipment. */
  SHAFT_WORK,
  /** Electrical power transferred between electrical equipment and loads. */
  ELECTRICAL,
  /** Chemical or fuel energy rate based on a declared heating-value convention. */
  CHEMICAL
}
