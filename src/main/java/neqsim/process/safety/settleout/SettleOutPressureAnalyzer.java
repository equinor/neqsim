package neqsim.process.safety.settleout;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/**
 * Compressor settle-out (and general gas-volume equalisation) pressure screening.
 *
 * <p>
 * Implements the HAZOP "More Pressure" deviation that follows a compressor trip. When a compressor stops, the gas held
 * on the low-pressure (suction) side and the gas held on the high-pressure (discharge) side equalise through the
 * machine and across non-tight check valves to a single intermediate <b>settle-out pressure</b>. Because the discharge
 * inventory is at a much higher pressure than the suction casing and suction-side piping/coolers were designed for, the
 * settle-out pressure can exceed the suction-system design pressure and challenge its relief protection.
 * </p>
 *
 * <p>
 * The analysis treats each connected gas volume as a compartment of fixed volume at a known pressure and temperature.
 * The molar inventory of every compartment is summed (conserved), a settle-out temperature is taken (mole-weighted
 * average by default, or user supplied), and the common settle-out pressure that fills the combined volume with the
 * total inventory is computed. With an optional {@link SystemInterface} gas the compressibility factor {@code Z} is
 * evaluated at each state and iterated at the settle-out condition; without it an ideal-gas basis is used.
 * </p>
 *
 * <p>
 * This is a screening calculation. It assumes rigid, fully connected gas volumes, no liquid hold-up, and equilibrium at
 * a single settle-out temperature. It does not model the equalisation transient, heat exchange with the surroundings,
 * or check-valve leak paths.
 * </p>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API STD 521 — Pressure-relieving and Depressuring Systems (settle-out / blocked-in gas)</li>
 * <li>API STD 617 — Axial and Centrifugal Compressors (settle-out pressure basis)</li>
 * <li>GPSA Engineering Data Book — compressor settle-out pressure</li>
 * <li>IEC 61882 — HAZOP "More Pressure" deviation</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class SettleOutPressureAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LogManager.getLogger(SettleOutPressureAnalyzer.class);

  /** Universal gas constant in J/(mol·K). */
  private static final double R_GAS = 8.314462618;

  private final List<Compartment> compartments = new ArrayList<Compartment>();
  private SystemInterface gas;
  private double settleOutTemperatureK = Double.NaN;
  private double protectedRatingBara = 0.0;
  private boolean ratingProvided = false;

  /**
   * Adds a connected gas volume (compartment) to the settle-out balance.
   *
   * @param name compartment label, for example "suction" or "discharge"
   * @param volume internal gas volume value in the supplied unit; must be positive
   * @param volumeUnit volume unit, supported values are "m3" and "litre"
   * @param pressure pressure value in the supplied unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}, for example bara or barg
   * @param temperature temperature value in the supplied unit
   * @param temperatureUnit temperature unit supported by {@link TemperatureUnit}, for example K or C
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the volume, pressure, or temperature is non-physical
   */
  public SettleOutPressureAnalyzer addVolume(String name, double volume, String volumeUnit, double pressure,
      String pressureUnit, double temperature, String temperatureUnit) {
    double volumeM3 = toCubicMetre(volume, volumeUnit);
    double pressureBara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    double temperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    if (volumeM3 <= 0.0) {
      throw new IllegalArgumentException("compartment volume must be positive");
    }
    if (pressureBara <= 0.0 || temperatureK <= 0.0) {
      throw new IllegalArgumentException("compartment pressure and temperature must be positive absolute values");
    }
    compartments.add(new Compartment(name, volumeM3, pressureBara, temperatureK));
    return this;
  }

  /**
   * Sets an optional real-gas {@link SystemInterface} used to evaluate the compressibility factor {@code Z} at each
   * compartment state and at the settle-out condition. When not supplied an ideal-gas basis is used.
   *
   * @param gas a NeqSim fluid with components and a mixing rule already configured; may be null for ideal gas
   * @return this analyzer for chaining
   */
  public SettleOutPressureAnalyzer setGas(SystemInterface gas) {
    this.gas = gas;
    return this;
  }

  /**
   * Sets the settle-out temperature. When not supplied a mole-weighted average of the compartment temperatures is used.
   *
   * @param temperature temperature value in the supplied unit
   * @param temperatureUnit temperature unit supported by {@link TemperatureUnit}, for example K or C
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the temperature is non-physical
   */
  public SettleOutPressureAnalyzer setSettleOutTemperature(double temperature, String temperatureUnit) {
    this.settleOutTemperatureK = new TemperatureUnit(temperature, temperatureUnit).getValue("K");
    if (settleOutTemperatureK <= 0.0) {
      throw new IllegalArgumentException("settle-out temperature must be a positive absolute value");
    }
    return this;
  }

  /**
   * Sets the design / relief set pressure of the protected (low-pressure) system that the settle-out pressure could
   * over-pressure, for the screening verdict.
   *
   * @param pressure protected-system rating value in the supplied unit; must be positive
   * @param pressureUnit pressure unit supported by {@link PressureUnit}
   * @return this analyzer for chaining
   * @throws IllegalArgumentException if the pressure is non-positive
   */
  public SettleOutPressureAnalyzer setProtectedPressureRating(double pressure, String pressureUnit) {
    double bara = new PressureUnit(pressure, pressureUnit).getValue("bara");
    if (bara <= 0.0) {
      throw new IllegalArgumentException("protected-system pressure rating must be positive");
    }
    this.protectedRatingBara = bara;
    this.ratingProvided = true;
    return this;
  }

  /**
   * Runs the settle-out balance and evaluates the screening verdict.
   *
   * @return the settle-out pressure result
   * @throws IllegalStateException if fewer than two compartments were supplied
   */
  public SettleOutPressureResult analyze() {
    if (compartments.size() < 2) {
      throw new IllegalStateException("at least two connected gas volumes are required for a settle-out balance");
    }
    List<String> warnings = new ArrayList<String>();

    double totalMoles = 0.0;
    double moleWeightedT = 0.0;
    double totalVolumeM3 = 0.0;
    for (Compartment c : compartments) {
      c.zFactor = compressibility(c.pressureBara, c.temperatureK, warnings);
      c.moles = c.pressureBara * 1.0e5 * c.volumeM3 / (c.zFactor * R_GAS * c.temperatureK);
      totalMoles += c.moles;
      moleWeightedT += c.moles * c.temperatureK;
      totalVolumeM3 += c.volumeM3;
    }

    double settleT = !Double.isNaN(settleOutTemperatureK) ? settleOutTemperatureK
        : (totalMoles > 0.0 ? moleWeightedT / totalMoles : Double.NaN);

    // Iterate the settle-out pressure with a real-gas Z at the settle-out condition.
    double zSettle = 1.0;
    double settleOutPressureBara = ideal(totalMoles, zSettle, settleT, totalVolumeM3);
    for (int iter = 0; iter < 10; iter++) {
      double zNew = compressibility(settleOutPressureBara, settleT, warnings);
      double pNew = ideal(totalMoles, zNew, settleT, totalVolumeM3);
      boolean converged = Math.abs(pNew - settleOutPressureBara) < 1.0e-4;
      zSettle = zNew;
      settleOutPressureBara = pNew;
      if (converged) {
        break;
      }
    }

    double maxCompartmentPressure = 0.0;
    double minCompartmentPressure = Double.MAX_VALUE;
    for (Compartment c : compartments) {
      maxCompartmentPressure = Math.max(maxCompartmentPressure, c.pressureBara);
      minCompartmentPressure = Math.min(minCompartmentPressure, c.pressureBara);
    }

    if (!ratingProvided) {
      warnings.add("No protected-system pressure rating supplied; provide setProtectedPressureRating for a verdict on "
          + "whether settle-out over-pressures the low-pressure system.");
    }

    double marginToRatingBar = ratingProvided ? protectedRatingBara - settleOutPressureBara : Double.NaN;
    boolean exceedsRating = ratingProvided && settleOutPressureBara > protectedRatingBara;
    SettleOutVerdict verdict;
    if (!ratingProvided) {
      verdict = SettleOutVerdict.NO_RATING;
    } else if (exceedsRating) {
      verdict = SettleOutVerdict.EXCEEDS_RATING;
    } else {
      verdict = SettleOutVerdict.WITHIN_RATING;
    }

    if (verdict == SettleOutVerdict.EXCEEDS_RATING) {
      warnings.add("Settle-out pressure exceeds the protected-system rating; confirm suction-side relief is sized for "
          + "the settle-out (blocked-in) case or provide an automatic blowdown.");
    }

    List<SettleOutPressureResult.CompartmentResult> breakdown = new ArrayList<SettleOutPressureResult.CompartmentResult>();
    for (Compartment c : compartments) {
      breakdown.add(new SettleOutPressureResult.CompartmentResult(c.name, c.volumeM3, c.pressureBara, c.temperatureK,
          c.zFactor, c.moles));
    }

    return new SettleOutPressureResult(settleOutPressureBara, settleT, zSettle, totalMoles, totalVolumeM3,
        maxCompartmentPressure, minCompartmentPressure, protectedRatingBara, ratingProvided, marginToRatingBar,
        exceedsRating, verdict, breakdown, warnings);
  }

  /**
   * Computes the ideal real-gas pressure that fills a volume with a fixed molar inventory at a temperature.
   *
   * @param moles total moles in mol
   * @param z compressibility factor (dimensionless)
   * @param temperatureK temperature in K
   * @param volumeM3 total volume in m3
   * @return pressure in bara
   */
  private double ideal(double moles, double z, double temperatureK, double volumeM3) {
    return moles * z * R_GAS * temperatureK / volumeM3 / 1.0e5;
  }

  /**
   * Returns the compressibility factor {@code Z} at a state, using the optional NeqSim gas when available and falling
   * back to ideal gas otherwise.
   *
   * @param pressureBara pressure in bara
   * @param temperatureK temperature in K
   * @param warnings warning sink for flash failures
   * @return compressibility factor; 1.0 when no gas is supplied or the flash fails
   */
  private double compressibility(double pressureBara, double temperatureK, List<String> warnings) {
    if (gas == null) {
      return 1.0;
    }
    try {
      SystemInterface state = gas.clone();
      state.setPressure(pressureBara, "bara");
      state.setTemperature(temperatureK);
      new ThermodynamicOperations(state).TPflash();
      state.initProperties();
      double z = state.getZ();
      if (z <= 0.0 || Double.isNaN(z)) {
        return 1.0;
      }
      return z;
    } catch (Exception ex) {
      warnings.add("Z evaluation failed at " + String.format("%.2f", pressureBara) + " bara; ideal gas assumed ("
          + ex.getMessage() + ").");
      logger.warn("Z evaluation failed during settle-out at {} bara: {}", pressureBara, ex.getMessage());
      return 1.0;
    }
  }

  /**
   * Converts a volume to cubic metres.
   *
   * @param value volume value
   * @param unit volume unit, supported values are "m3" and "litre"
   * @return volume in m3
   * @throws IllegalArgumentException if the unit is unsupported
   */
  private double toCubicMetre(double value, String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("volume unit must not be null");
    }
    String u = unit.trim().toLowerCase();
    if (u.equals("m3") || u.equals("m^3") || u.equals("m3a")) {
      return value;
    }
    if (u.equals("litre") || u.equals("liter") || u.equals("l")) {
      return value / 1000.0;
    }
    throw new IllegalArgumentException("unsupported volume unit: " + unit);
  }

  /**
   * Verdict of a settle-out pressure screening.
   */
  public enum SettleOutVerdict {
    /** No protected-system rating supplied; verdict not determined. */
    NO_RATING,
    /** Settle-out pressure stays at or below the protected-system rating. */
    WITHIN_RATING,
    /** Settle-out pressure exceeds the protected-system rating. */
    EXCEEDS_RATING
  }

  /**
   * Mutable working record for one connected gas volume.
   */
  private static final class Compartment implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final double volumeM3;
    private final double pressureBara;
    private final double temperatureK;
    private double zFactor = 1.0;
    private double moles = 0.0;

    private Compartment(String name, double volumeM3, double pressureBara, double temperatureK) {
      this.name = name;
      this.volumeM3 = volumeM3;
      this.pressureBara = pressureBara;
      this.temperatureK = temperatureK;
    }
  }
}
