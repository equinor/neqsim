package neqsim.process.costestimation.electrolyzer;

import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.electrolyzer.ElectrolyzerMechanicalDesign;

/**
 * CAPEX estimation for water electrolyzers.
 *
 * <p>
 * Uses a stack-power-based correlation calibrated to public benchmarks (IRENA Hydrogen
 * Decarbonisation Pathways 2022; IEA Global Hydrogen Review 2023; Buttler &amp; Spliethoff,
 * Renewable and Sustainable Energy Reviews 82 (2018) 2440-2454). Specific installed CAPEX values
 * used as the 2024-2025 base:
 * </p>
 *
 * <table>
 * <caption>Specific installed CAPEX, USD/kW (system, including BOP)</caption>
 * <tr>
 * <th>Technology</th>
 * <th>Specific CAPEX</th>
 * </tr>
 * <tr>
 * <td>PEM</td>
 * <td>1250 USD/kW</td>
 * </tr>
 * <tr>
 * <td>Alkaline</td>
 * <td>800 USD/kW</td>
 * </tr>
 * <tr>
 * <td>SOEC</td>
 * <td>2500 USD/kW (early commercial)</td>
 * </tr>
 * <tr>
 * <td>AEM</td>
 * <td>1500 USD/kW (emerging)</td>
 * </tr>
 * </table>
 *
 * <p>
 * A scale exponent of 0.85 is applied to capture the economies of scale typical of modular
 * electrochemical equipment.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class ElectrolyzerCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference stack power for the scale-exponent correlation (kW). */
  private static final double P_REFERENCE_KW = 1000.0;

  /** Scale exponent (six-tenths rule, somewhat closer to 1 for modular stacks). */
  private static final double SCALE_EXPONENT = 0.85;

  /** Specific CAPEX, USD/kW for the PEM technology, 2024-2025 basis. */
  private static final double SPECIFIC_CAPEX_PEM = 1250.0;
  /** Specific CAPEX, USD/kW for the alkaline technology, 2024-2025 basis. */
  private static final double SPECIFIC_CAPEX_ALKALINE = 800.0;
  /** Specific CAPEX, USD/kW for the SOEC technology, 2024-2025 basis. */
  private static final double SPECIFIC_CAPEX_SOEC = 2500.0;
  /** Specific CAPEX, USD/kW for the AEM technology, 2024-2025 basis. */
  private static final double SPECIFIC_CAPEX_AEM = 1500.0;

  /** Reference CEPCI used to calibrate the specific-CAPEX values (2024 annual average). */
  private static final double REFERENCE_CEPCI = 800.0;

  /** Electrolyzer technology: "PEM", "ALKALINE", "SOEC", or "AEM". */
  private String technology = "PEM";

  /** Whether the cost includes balance-of-plant (rectifier, water treatment, gas drying). */
  private boolean includeBalanceOfPlant = true;

  /**
   * Construct an electrolyzer cost estimate from a mechanical design.
   *
   * @param mechanicalEquipment the electrolyzer mechanical design
   */
  public ElectrolyzerCostEstimate(ElectrolyzerMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("electrolyzer");
  }

  /**
   * Set the electrolyzer technology.
   *
   * @param technology "PEM", "ALKALINE", "SOEC", or "AEM"
   */
  public void setTechnology(String technology) {
    if (technology == null) {
      throw new IllegalArgumentException("technology must not be null");
    }
    this.technology = technology.toUpperCase();
  }

  /**
   * Get the electrolyzer technology.
   *
   * @return technology name
   */
  public String getTechnology() {
    return technology;
  }

  /**
   * Set whether to include balance-of-plant (BOP) costs (rectifier, water treatment, gas drying,
   * controls). When {@code false} the correlation is reduced by 35%.
   *
   * @param include true to include balance of plant
   */
  public void setIncludeBalanceOfPlant(boolean include) {
    this.includeBalanceOfPlant = include;
  }

  /**
   * Get the specific installed CAPEX (USD/kW) used by this technology selection.
   *
   * @return specific CAPEX (USD/kW)
   */
  public double getSpecificCapexUsdPerKw() {
    if ("ALKALINE".equals(technology)) {
      return SPECIFIC_CAPEX_ALKALINE;
    } else if ("SOEC".equals(technology)) {
      return SPECIFIC_CAPEX_SOEC;
    } else if ("AEM".equals(technology)) {
      return SPECIFIC_CAPEX_AEM;
    }
    return SPECIFIC_CAPEX_PEM;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Cost = stackPowerKW &middot; specificCapex(technology) &middot; (stackPowerKW /
   * P_REFERENCE)^(SCALE_EXPONENT - 1) &middot; (currentCepci / referenceCepci)
   * </p>
   *
   * <p>
   * The scale-exponent term shifts the cost from "linear in size" toward six-tenths-rule behaviour.
   * If the stack power is zero (mechanical design not yet calculated) the method returns 0.
   * </p>
   */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }
    ElectrolyzerMechanicalDesign mech = (ElectrolyzerMechanicalDesign) mechanicalEquipment;
    double powerKW = mech.getTotalPowerKW();
    if (powerKW <= 0.0) {
      return 0.0;
    }
    double specific = getSpecificCapexUsdPerKw();
    double scaleFactor = Math.pow(powerKW / P_REFERENCE_KW, SCALE_EXPONENT - 1.0);
    double cost = powerKW * specific * scaleFactor;

    // Strip BOP if not requested (~35% of total system cost per IRENA 2022).
    if (!includeBalanceOfPlant) {
      cost *= 0.65;
    }

    // CEPCI escalation from the 2024 basis.
    double cepciRatio = getCostCalculator().getCurrentCepci() / REFERENCE_CEPCI;
    return cost * cepciRatio;
  }
}
