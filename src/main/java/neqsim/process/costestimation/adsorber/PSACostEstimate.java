package neqsim.process.costestimation.adsorber;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.equipment.adsorber.PSACascade;
import neqsim.process.equipment.adsorber.PressureSwingAdsorptionBed;
import neqsim.process.mechanicaldesign.adsorber.AdsorberMechanicalDesign;

/**
 * CAPEX estimation for industrial Pressure-Swing-Adsorption (PSA) hydrogen-purification cascades.
 *
 * <p>
 * Uses a bed-count + bed-volume correlation calibrated to public benchmarks for blue-H2 plants (IEA
 * Global Hydrogen Review 2023; Voss, <em>Adsorption</em> 11 (2005) 527-529; Kvamsdal et al. "PSA
 * economics for SMR hydrogen plants", <em>Energy Procedia</em> 4 (2011) 1182-1189). Cost
 * components:
 * </p>
 *
 * <ol>
 * <li><strong>Pressure vessels</strong> — number-of-beds scaled by per-bed reference cost with a
 * scale exponent on bed volume (six-tenths rule for thick-wall ASME Section VIII Div. 1
 * vessels).</li>
 * <li><strong>Sorbent inventory</strong> — bulk mass &middot; unit sorbent price. Activated carbon
 * (~3-5 USD/kg) and zeolite 13X (~8-12 USD/kg) defaults from Yang (2003) updated to 2024
 * basis.</li>
 * <li><strong>Valve / switching skid</strong> — proportional to the number of beds. Industrial PSA
 * skids deploy 4-6 fast-acting butterfly or ball valves per bed.</li>
 * <li><strong>Balance of plant</strong> — controls, instrumentation, surge tank, vent silencer.
 * Optional; default included.</li>
 * </ol>
 *
 * <p>
 * Reference per-bed vessel cost: <strong>USD 250 000</strong> for a 2 m diameter &times; 4 m height
 * vessel rated to 30 barg at the 2024 CEPCI of 800. Scaled with bed volume to the
 * {@link #SCALE_EXPONENT} of 0.6.
 * </p>
 *
 * <table>
 * <caption>Sorbent unit price defaults (USD/kg, 2024 basis)</caption>
 * <tr>
 * <th>Sorbent</th>
 * <th>Unit price</th>
 * </tr>
 * <tr>
 * <td>Activated carbon (AC)</td>
 * <td>4.0</td>
 * </tr>
 * <tr>
 * <td>Zeolite 13X</td>
 * <td>10.0</td>
 * </tr>
 * </table>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class PSACostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference per-bed vessel cost (USD, 2024 basis). */
  private static final double REFERENCE_BED_COST_USD = 250000.0;

  /** Reference per-bed volume (m3) at which the per-bed cost is calibrated. */
  private static final double REFERENCE_BED_VOLUME_M3 = 12.566; // 2 m dia x 4 m

  /** Scale exponent for the bed-volume cost correlation (six-tenths rule). */
  private static final double SCALE_EXPONENT = 0.6;

  /** Per-bed switching-valve skid cost (USD, 2024 basis). */
  private static final double VALVE_SKID_COST_PER_BED_USD = 60000.0;

  /** Activated carbon unit price (USD/kg, 2024 basis). */
  private static final double SORBENT_PRICE_AC_USD_PER_KG = 4.0;

  /** Zeolite 13X unit price (USD/kg, 2024 basis). */
  private static final double SORBENT_PRICE_ZEOLITE_USD_PER_KG = 10.0;

  /** Reference CEPCI for the 2024 basis. */
  private static final double REFERENCE_CEPCI = 800.0;

  /** Number of beds in the cascade. */
  private int numberOfBeds = 4;

  /** Sorbent selection (drives the unit price). */
  private PressureSwingAdsorptionBed.SorbentType sorbent =
      PressureSwingAdsorptionBed.SorbentType.ACTIVATED_CARBON;

  /** Sorbent mass per bed (kg). */
  private double sorbentMassPerBedKg = 0.0;

  /** Whether to include balance-of-plant (controls, surge tank, vent silencer). */
  private boolean includeBalanceOfPlant = true;

  // --------------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------------

  /**
   * Default constructor.
   */
  public PSACostEstimate() {
    super();
    setEquipmentType("psa-cascade");
  }

  /**
   * Construct from an adsorber mechanical design.
   *
   * @param mechanicalEquipment adsorber mechanical design (per-bed sizing)
   */
  public PSACostEstimate(AdsorberMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("psa-cascade");
  }

  /**
   * Construct from a PSA cascade — populates bed count, sorbent, and sorbent mass from the cascade.
   *
   * @param cascade PSA cascade unit
   */
  public PSACostEstimate(PSACascade cascade) {
    super();
    setEquipmentType("psa-cascade");
    if (cascade == null) {
      throw new IllegalArgumentException("cascade must not be null");
    }
    this.numberOfBeds = cascade.getNumberOfBeds();
    PressureSwingAdsorptionBed bed = cascade.getTemplateBed();
    this.sorbent = bed.getSorbent();
    // Bed volume from template-bed geometry; sorbent mass = volume * (1-void) * bulk density.
    double dia = bed.getBedDiameter();
    double len = bed.getBedLength();
    double bedVolume = Math.PI * 0.25 * dia * dia * len;
    double bulkDensity = sorbent.getDefaultBulkDensity();
    // Bulk density already accounts for void; multiply by bed volume directly.
    this.sorbentMassPerBedKg = bedVolume * bulkDensity;
  }

  // --------------------------------------------------------------------------
  // Configuration
  // --------------------------------------------------------------------------

  /**
   * Set the number of beds in the cascade.
   *
   * @param numberOfBeds number of beds (&gt; 0)
   */
  public void setNumberOfBeds(int numberOfBeds) {
    if (numberOfBeds <= 0) {
      throw new IllegalArgumentException("numberOfBeds must be positive, got " + numberOfBeds);
    }
    this.numberOfBeds = numberOfBeds;
    resetCostCache();
  }

  /**
   * Get the number of beds in the cascade.
   *
   * @return number of beds
   */
  public int getNumberOfBeds() {
    return numberOfBeds;
  }

  /**
   * Set the sorbent selection (drives the per-kg cost).
   *
   * @param sorbent sorbent type
   */
  public void setSorbent(PressureSwingAdsorptionBed.SorbentType sorbent) {
    if (sorbent == null) {
      throw new IllegalArgumentException("sorbent must not be null");
    }
    this.sorbent = sorbent;
    resetCostCache();
  }

  /**
   * Get the sorbent selection.
   *
   * @return sorbent type
   */
  public PressureSwingAdsorptionBed.SorbentType getSorbent() {
    return sorbent;
  }

  /**
   * Set the sorbent mass per bed (kg).
   *
   * @param sorbentMassPerBedKg sorbent mass per bed (kg, non-negative)
   */
  public void setSorbentMassPerBedKg(double sorbentMassPerBedKg) {
    if (sorbentMassPerBedKg < 0.0) {
      throw new IllegalArgumentException(
          "sorbentMassPerBedKg must be non-negative, got " + sorbentMassPerBedKg);
    }
    this.sorbentMassPerBedKg = sorbentMassPerBedKg;
    resetCostCache();
  }

  /**
   * Get the sorbent mass per bed (kg).
   *
   * @return sorbent mass per bed (kg)
   */
  public double getSorbentMassPerBedKg() {
    return sorbentMassPerBedKg;
  }

  /**
   * Set whether to include balance-of-plant (controls, surge tank, vent silencer). When
   * {@code false} the correlation drops BoP, reducing cost by ~25%.
   *
   * @param include true to include balance of plant
   */
  public void setIncludeBalanceOfPlant(boolean include) {
    this.includeBalanceOfPlant = include;
    resetCostCache();
  }

  /**
   * Reset cached cost results after a mutable PSA cost input changes.
   */
  private void resetCostCache() {
    purchasedEquipmentCost = 0.0;
    bareModuleCost = 0.0;
    totalModuleCost = 0.0;
    grassRootsCost = 0.0;
    annualOperatingCost = 0.0;
    installationManHours = 0.0;
  }

  /**
   * Get the per-kg unit price of the selected sorbent (USD/kg, 2024 basis).
   *
   * @return sorbent unit price (USD/kg)
   */
  public double getSorbentUnitPriceUsdPerKg() {
    if (sorbent == PressureSwingAdsorptionBed.SorbentType.ZEOLITE_13X) {
      return SORBENT_PRICE_ZEOLITE_USD_PER_KG;
    }
    return SORBENT_PRICE_AC_USD_PER_KG;
  }

  // --------------------------------------------------------------------------
  // Cost calculation
  // --------------------------------------------------------------------------

  /**
   * {@inheritDoc}
   *
   * <p>
   * Cost = numberOfBeds &times; [referenceBedCost &times; (bedVolume / referenceBedVolume)^SCALE +
   * valveSkidCostPerBed + sorbentMassPerBed &middot; sorbentUnitPrice] &times; BoP factor &times;
   * CEPCI ratio.
   * </p>
   *
   * <p>
   * If a mechanical design is attached, the per-bed volume is derived from {@code internalDiameter}
   * and {@code tantanLength}; otherwise the reference volume is used.
   * </p>
   */
  @Override
  protected double calcPurchasedEquipmentCost() {
    double bedVolume = REFERENCE_BED_VOLUME_M3;
    if (mechanicalEquipment instanceof AdsorberMechanicalDesign) {
      AdsorberMechanicalDesign mech = (AdsorberMechanicalDesign) mechanicalEquipment;
      double dia = mech.getInnerDiameter();
      double len = mech.getTantanLength();
      if (dia > 0.0 && len > 0.0) {
        bedVolume = Math.PI * 0.25 * dia * dia * len;
      }
    }

    double scaleFactor = Math.pow(bedVolume / REFERENCE_BED_VOLUME_M3, SCALE_EXPONENT);
    double perBedVessel = REFERENCE_BED_COST_USD * scaleFactor;
    double perBedValves = VALVE_SKID_COST_PER_BED_USD;
    double perBedSorbent = sorbentMassPerBedKg * getSorbentUnitPriceUsdPerKg();
    double perBedTotal = perBedVessel + perBedValves + perBedSorbent;

    double cost = numberOfBeds * perBedTotal;

    if (!includeBalanceOfPlant) {
      cost *= 0.75;
    }

    double cepciRatio = getCostCalculator().getCurrentCepci() / REFERENCE_CEPCI;
    return cost * cepciRatio;
  }
}
