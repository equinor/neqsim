package neqsim.process.chemistry.hydrate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import neqsim.process.chemistry.ProductionChemical;
import neqsim.process.chemistry.ProductionChemical.ChemicalType;

/**
 * Product-specific evidence of hydrate slurry transport after a shutdown and restart. Each record describes one
 * observed operating point. Multiple observations can coexist.
 *
 * @author ESOL
 * @version 1.0
 */
public final class AntiAgglomerantQualification {
  /** Denominator and numerator for the reported experimental dose. */
  public enum DoseBasis {
    /** Grams of injected product per kilogram of water. */
    PRODUCT_G_PER_KG_WATER,
    /** Grams of active chemical per kilogram of water. */
    ACTIVE_G_PER_KG_WATER
  }

  /** Observed result of restarting the hydrate-bearing fluid. */
  public enum Outcome {
    /** Hydrate slurry was transported after restart. */
    TRANSPORTABLE_SLURRY,
    /** Blockage was observed during restart. */
    BLOCKED_RESTART
  }

  /** Operating point and protocol of a flow-loop experiment. */
  public static final class Conditions {
    private final String fluid;
    private final String protocol;
    private final DoseBasis basis;
    private final double dose;
    private final double waterCutPct;
    private final double pressureBara;
    private final double subcoolingK;
    private final double velocityMPerS;
    private final double shutInHours;

    /**
     * Specifies a tested or requested condition. Matching uses exact values in these units.
     *
     * @param fluid fluid identity including gas, oil and brine composition
     * @param protocol identifier for loop geometry, mixing, thermal history and restart procedure
     * @param basis dose basis
     * @param dose grams per kilogram on the stated basis
     * @param waterCutPct liquid water cut in volume percent at test conditions
     * @param pressureBara absolute pressure in bar
     * @param subcoolingK uninhibited hydrate equilibrium temperature minus test temperature in K
     * @param velocityMPerS mixture velocity before shut-in in m/s
     * @param shutInHours time at rest before restart in hours
     */
    public Conditions(String fluid, String protocol, DoseBasis basis, double dose, double waterCutPct,
        double pressureBara, double subcoolingK, double velocityMPerS, double shutInHours) {
      this.fluid = identifier(fluid);
      this.protocol = identifier(protocol);
      this.basis = Objects.requireNonNull(basis);
      this.dose = nonnegative(dose);
      this.waterCutPct = nonnegative(waterCutPct);
      this.pressureBara = nonnegative(pressureBara);
      this.subcoolingK = nonnegative(subcoolingK);
      this.velocityMPerS = nonnegative(velocityMPerS);
      this.shutInHours = nonnegative(shutInHours);
      if (waterCutPct > 100.0 || pressureBara == 0.0) {
        throw new IllegalArgumentException("Water cut must be at most 100% and pressure must be positive");
      }
    }

    /** @return fluid system identifier */
    public String getFluid() {
      return fluid;
    }

    /** @return restart protocol identifier */
    public String getProtocol() {
      return protocol;
    }

    /** @return reported dose basis */
    public DoseBasis getDoseBasis() {
      return basis;
    }

    /** @return dose in g/kg on the reported basis */
    public double getDose() {
      return dose;
    }

    /** @return liquid water cut in volume percent */
    public double getWaterCutPct() {
      return waterCutPct;
    }

    /** @return absolute pressure in bar */
    public double getPressureBara() {
      return pressureBara;
    }

    /** @return uninhibited subcooling in kelvin */
    public double getSubcoolingK() {
      return subcoolingK;
    }

    /** @return mixture velocity in m/s */
    public double getVelocityMPerS() {
      return velocityMPerS;
    }

    /** @return shut-in duration in hours */
    public double getShutInHours() {
      return shutInHours;
    }

    /**
     * Compares the two laboratory operating points.
     * 
     * @param other requested conditions
     * @return true for exactly matching operating conditions and protocols
     */
    private boolean covers(Conditions other) {
      return fluid.equals(other.fluid) && protocol.equals(other.protocol) && basis == other.basis && dose == other.dose
          && waterCutPct == other.waterCutPct && pressureBara == other.pressureBara && subcoolingK == other.subcoolingK
          && velocityMPerS == other.velocityMPerS && shutInHours == other.shutInHours;
    }
  }

  private final String name;
  private final String formulation;
  private final String ingredient;
  private final double activeWtPct;
  private final String solvent;
  private final Conditions conditions;
  private final Outcome outcome;
  private final String source;

  /**
   * Records a flow-loop restart observation of a named anti-agglomerant formulation.
   * 
   * @param product existing LDHI chemical identity
   * @param formulation formulation revision or batch identifier
   * @param conditions tested operating point
   * @param outcome observed restart outcome
   * @param source retrievable test report or synthetic fixture identifier
   */
  public AntiAgglomerantQualification(ProductionChemical product, String formulation, Conditions conditions,
      Outcome outcome, String source) {
    if (product.getType() != ChemicalType.HYDRATE_INHIBITOR_LDHI) {
      throw new IllegalArgumentException("Expected an LDHI product");
    }
    this.name = identifier(product.getName());
    this.formulation = identifier(formulation);
    this.ingredient = product.getActiveIngredient();
    this.activeWtPct = product.getActiveWtPct();
    this.solvent = product.getSolvent();
    this.conditions = Objects.requireNonNull(conditions);
    this.outcome = Objects.requireNonNull(outcome);
    this.source = identifier(source);
  }

  /**
   * Retrieves all matching flow-loop observations. An empty result means evidence is missing.
   * 
   * @param records available anti-agglomerant records
   * @param product requested LDHI product
   * @param formulation requested formulation revision or batch
   * @param point requested operating point and restart protocol
   * @return all matching observations, including contradictory repeats if present
   */
  public static List<AntiAgglomerantQualification> lookup(Iterable<AntiAgglomerantQualification> records,
      ProductionChemical product, String formulation, Conditions point) {
    List<AntiAgglomerantQualification> matches = new ArrayList<>();
    for (AntiAgglomerantQualification record : records) {
      if (product.getType() == ChemicalType.HYDRATE_INHIBITOR_LDHI && record.name.equals(product.getName())
          && record.formulation.equals(formulation) && Objects.equals(record.ingredient, product.getActiveIngredient())
          && record.activeWtPct == product.getActiveWtPct() && Objects.equals(record.solvent, product.getSolvent())
          && record.conditions.covers(point)) {
        matches.add(record);
      }
    }
    return matches;
  }

  /** @return the hydrate management mechanism tested */
  public String getMechanism() {
    return "anti-agglomeration";
  }

  /** @return the experimental method used to observe post-hydrate transport */
  public String getTestMethod() {
    return "flow-loop restart";
  }

  /** @return tested conditions and protocol */
  public Conditions getConditions() {
    return conditions;
  }

  /** @return tested commercial product name */
  public String getProductName() {
    return name;
  }

  /** @return tested formulation or batch identifier */
  public String getFormulation() {
    return formulation;
  }

  /** @return observed restart outcome */
  public Outcome getOutcome() {
    return outcome;
  }

  /** @return reference to the experimental record */
  public String getSource() {
    return source;
  }

  /**
   * Validates evidence identifiers.
   * 
   * @param value identifier
   * @return validated identifier
   */
  private static String identifier(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("A nonempty evidence identifier is required");
    }
    return value;
  }

  /**
   * Validates a nonnegative physical quantity.
   * 
   * @param value quantity in the declared units
   * @return validated quantity
   */
  private static double nonnegative(double value) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException("Operating conditions must be finite and nonnegative");
    }
    return value;
  }
}
