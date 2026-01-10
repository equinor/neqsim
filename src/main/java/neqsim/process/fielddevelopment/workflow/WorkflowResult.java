package neqsim.process.fielddevelopment.workflow;

import java.io.Serializable;
import java.util.Map;
import neqsim.process.fielddevelopment.economics.CashFlowEngine;
import neqsim.process.fielddevelopment.economics.SensitivityAnalyzer;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.screening.EconomicsEstimator;
import neqsim.process.fielddevelopment.screening.FlowAssuranceReport;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem;
import neqsim.process.fielddevelopment.tieback.TiebackOption;
import neqsim.process.fielddevelopment.tieback.TiebackReport;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;

/**
 * Result container for field development workflow execution.
 *
 * <p>
 * Contains all outputs from a workflow run including flow assurance screening, economics, cash flow
 * analysis, and (for detailed studies) Monte Carlo results.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see FieldDevelopmentWorkflow
 */
public class WorkflowResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  // Metadata
  /** Project name. */
  public String projectName;

  /** Fidelity level used. */
  public FieldDevelopmentWorkflow.FidelityLevel fidelityLevel;

  // Flow assurance
  /** Flow assurance screening result. */
  public FlowAssuranceReport flowAssuranceResult;

  // Economics
  /** Economics estimation report. */
  public EconomicsEstimator.EconomicsReport economicsReport;

  // Production
  /** Gas production profile (year -> Sm3/day). */
  public Map<Integer, Double> gasProfile;

  /** Oil production profile (year -> bbl/day). */
  public Map<Integer, Double> oilProfile;

  /** Water production profile (year -> Sm3/day). */
  public Map<Integer, Double> waterProfile;

  // Cash flow
  /** Cash flow calculation result. */
  public CashFlowEngine.CashFlowResult cashFlowResult;

  // Key metrics
  /** Net present value at configured discount rate (MUSD). */
  public double npv;

  /** P10 NPV from Monte Carlo (MUSD) - only for DETAILED. */
  public double npvP10;

  /** P50 NPV from Monte Carlo (MUSD) - only for DETAILED. */
  public double npvP50;

  /** P90 NPV from Monte Carlo (MUSD) - only for DETAILED. */
  public double npvP90;

  /** Internal rate of return (0-1). */
  public double irr;

  /** Payback period in years. */
  public double paybackYears;

  /** Breakeven gas price (USD/Sm3). */
  public double breakevenGasPrice;

  /** Breakeven oil price (USD/bbl). */
  public double breakevenOilPrice;

  // Detailed analysis
  /** Monte Carlo result - only for DETAILED. */
  public SensitivityAnalyzer.MonteCarloResult monteCarloResult;

  /** Tornado analysis result - only for DETAILED. */
  public SensitivityAnalyzer.TornadoResult tornadoResult;

  /** Concept KPIs - only for CONCEPTUAL and DETAILED. */
  public ConceptKPIs conceptKPIs;

  // ============================================================================
  // MECHANICAL DESIGN, POWER & EMISSIONS - only for DETAILED
  // ============================================================================

  /** System mechanical design results - only for DETAILED. */
  public SystemMechanicalDesign mechanicalDesign;

  /** Total equipment weight [tonnes]. */
  public double totalEquipmentWeightTonnes;

  /** Total module footprint [m2]. */
  public double totalFootprintM2;

  /** Total power consumption [MW]. */
  public double totalPowerMW;

  /** Power breakdown by equipment type. */
  public Map<String, Double> powerBreakdownMW;

  /** Annual CO2 emissions [ktonnes/year]. */
  public double annualCO2eKtonnes;

  /** CO2 intensity [kg CO2e/boe]. */
  public double co2IntensityKgPerBoe;

  /** Emission breakdown by category. */
  public Map<String, Double> emissionBreakdown;

  /** Power supply type (used for emission calculation). */
  public String powerSupplyType;

  /** Grid emission factor [kg CO2/kWh]. */
  public double gridEmissionFactor;

  // ============================================================================
  // SUBSEA SYSTEM RESULTS
  // ============================================================================

  /** Subsea production system result. */
  public SubseaProductionSystem.SubseaSystemResult subseaSystemResult;

  /** Tieback analysis report (multiple host comparison). */
  public TiebackReport tiebackReport;

  /** Selected tieback option (best feasible). */
  public TiebackOption selectedTiebackOption;

  /** Total subsea CAPEX [MUSD]. */
  public double subseaCapexMusd;

  /** Arrival pressure at host facility [bara]. */
  public double arrivalPressureBara;

  /** Arrival temperature at host facility [°C]. */
  public double arrivalTemperatureC;

  /** Subsea simulation error message (if simulation failed). */
  public String subseaSimulationError;

  /**
   * Creates a new workflow result.
   *
   * @param projectName project name
   * @param fidelityLevel fidelity level used
   */
  public WorkflowResult(String projectName, FieldDevelopmentWorkflow.FidelityLevel fidelityLevel) {
    this.projectName = projectName;
    this.fidelityLevel = fidelityLevel;
  }

  /**
   * Gets a summary of the workflow result.
   *
   * @return markdown-formatted summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("# ").append(projectName).append(" - Workflow Result\n\n");
    sb.append("**Fidelity Level:** ").append(fidelityLevel).append("\n\n");

    // Economics summary
    sb.append("## Economics Summary\n\n");
    sb.append("| Metric | Value |\n");
    sb.append("|--------|-------|\n");
    if (economicsReport != null) {
      sb.append(String.format("| CAPEX | %.0f MUSD |\n", economicsReport.getTotalCapexMUSD()));
    }
    sb.append(String.format("| NPV | %.0f MUSD |\n", npv));
    if (fidelityLevel == FieldDevelopmentWorkflow.FidelityLevel.DETAILED) {
      sb.append(String.format("| NPV P10 | %.0f MUSD |\n", npvP10));
      sb.append(String.format("| NPV P50 | %.0f MUSD |\n", npvP50));
      sb.append(String.format("| NPV P90 | %.0f MUSD |\n", npvP90));
    }
    sb.append(String.format("| IRR | %.1f%% |\n", irr * 100));
    sb.append(String.format("| Payback | %.1f years |\n", paybackYears));
    if (breakevenGasPrice > 0) {
      sb.append(String.format("| Breakeven Gas | %.3f USD/Sm3 |\n", breakevenGasPrice));
    }
    sb.append("\n");

    // Flow assurance summary
    if (flowAssuranceResult != null) {
      sb.append("## Flow Assurance\n\n");
      sb.append("| Risk | Level |\n");
      sb.append("|------|-------|\n");
      sb.append(String.format("| Hydrate | %s |\n", flowAssuranceResult.getHydrateResult()));
      sb.append(String.format("| Wax | %s |\n", flowAssuranceResult.getWaxResult()));
      sb.append(String.format("| Corrosion | %s |\n", flowAssuranceResult.getCorrosionResult()));
      sb.append(
          String.format("| **Overall** | **%s** |\n", flowAssuranceResult.getOverallResult()));
      sb.append("\n");
    }

    // Production summary
    if (gasProfile != null && !gasProfile.isEmpty()) {
      sb.append("## Production Profile\n\n");
      double peakRate =
          gasProfile.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
      double cumulative = gasProfile.values().stream().mapToDouble(d -> d * 365).sum() / 1e9; // GSm3
      sb.append(String.format("- Peak rate: %.1f MSm3/day\n", peakRate / 1e6));
      sb.append(String.format("- Cumulative: %.1f GSm3\n", cumulative));
      sb.append(String.format("- Field life: %d years\n", gasProfile.size()));
      sb.append("\n");
    }

    // Monte Carlo summary
    if (monteCarloResult != null) {
      sb.append("## Uncertainty Analysis\n\n");
      sb.append(String.format("- P(NPV > 0): %.1f%%\n",
          monteCarloResult.getProbabilityPositiveNpv() * 100));
      sb.append(String.format("- NPV range: %.0f to %.0f MUSD\n", npvP10, npvP90));
      sb.append("\n");
    }

    // Mechanical design and power summary
    if (totalPowerMW > 0 || totalEquipmentWeightTonnes > 0) {
      sb.append("## Process & Mechanical Design\n\n");
      sb.append("| Parameter | Value |\n");
      sb.append("|-----------|-------|\n");
      if (totalEquipmentWeightTonnes > 0) {
        sb.append(
            String.format("| Equipment Weight | %.0f tonnes |\n", totalEquipmentWeightTonnes));
      }
      if (totalFootprintM2 > 0) {
        sb.append(String.format("| Module Footprint | %.0f m² |\n", totalFootprintM2));
      }
      if (totalPowerMW > 0) {
        sb.append(String.format("| Total Power | %.1f MW |\n", totalPowerMW));
      }
      sb.append("\n");
    }

    // CO2 emissions summary
    if (annualCO2eKtonnes > 0) {
      sb.append("## CO2 Emissions\n\n");
      sb.append("| Metric | Value |\n");
      sb.append("|--------|-------|\n");
      sb.append(String.format("| Power Supply | %s |\n",
          powerSupplyType != null ? powerSupplyType : "N/A"));
      sb.append(String.format("| Annual CO2e | %.1f ktonnes/yr |\n", annualCO2eKtonnes));
      sb.append(String.format("| CO2 Intensity | %.1f kg/boe |\n", co2IntensityKgPerBoe));
      sb.append("\n");
    }

    // Subsea system summary
    if (subseaSystemResult != null || subseaCapexMusd > 0) {
      sb.append("## Subsea Production System\n\n");
      sb.append("| Parameter | Value |\n");
      sb.append("|-----------|-------|\n");

      if (subseaSystemResult != null) {
        sb.append(String.format("| Architecture | %s |\n",
            subseaSystemResult.getClass().getSimpleName()));
        sb.append(String.format("| Arrival Pressure | %.1f bara |\n",
            subseaSystemResult.getArrivalPressureBara()));
        sb.append(String.format("| Arrival Temperature | %.1f °C |\n",
            subseaSystemResult.getArrivalTemperatureC()));
        sb.append(String.format("| Subsea CAPEX | %.0f MUSD |\n",
            subseaSystemResult.getTotalSubseaCapexMusd()));
      } else {
        if (arrivalPressureBara > 0) {
          sb.append(String.format("| Arrival Pressure | %.1f bara |\n", arrivalPressureBara));
        }
        if (arrivalTemperatureC > 0) {
          sb.append(String.format("| Arrival Temperature | %.1f °C |\n", arrivalTemperatureC));
        }
        if (subseaCapexMusd > 0) {
          sb.append(String.format("| Subsea CAPEX | %.0f MUSD |\n", subseaCapexMusd));
        }
      }

      if (selectedTiebackOption != null) {
        sb.append(String.format("| Selected Host | %s |\n", selectedTiebackOption.getHostName()));
        sb.append(String.format("| Tieback Distance | %.1f km |\n",
            selectedTiebackOption.getDistanceKm()));
      }

      sb.append("\n");

      if (subseaSimulationError != null) {
        sb.append("**Note:** Subsea simulation encountered an error: ");
        sb.append(subseaSimulationError).append("\n\n");
      }
    }

    // Tieback comparison (if multiple hosts evaluated)
    if (tiebackReport != null && tiebackReport.getOptionCount() > 1) {
      sb.append("## Tieback Options Comparison\n\n");
      sb.append("| Host | Distance (km) | NPV (MUSD) | Feasible |\n");
      sb.append("|------|---------------|------------|----------|\n");

      for (TiebackOption opt : tiebackReport.getOptions()) {
        sb.append(String.format("| %s | %.1f | %.0f | %s |\n", opt.getHostName(),
            opt.getDistanceKm(), opt.getNpvMusd(), opt.isFeasible() ? "✓" : "✗"));
      }
      sb.append("\n");
    }

    return sb.toString();
  }

  /**
   * Gets the production forecast as a markdown table.
   *
   * @return markdown table
   */
  public String getProductionTable() {
    if (gasProfile == null || gasProfile.isEmpty()) {
      return "No production profile available.";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("| Year | Gas (MSm3/d) | Cumulative (GSm3) |\n");
    sb.append("|------|--------------|-------------------|\n");

    double cumulative = 0;
    for (Map.Entry<Integer, Double> entry : gasProfile.entrySet()) {
      double annualGSm3 = entry.getValue() * 365 / 1e9;
      cumulative += annualGSm3;
      sb.append(String.format("| %d | %.2f | %.2f |\n", entry.getKey(), entry.getValue() / 1e6,
          cumulative));
    }

    return sb.toString();
  }

  /**
   * Gets the cash flow table.
   *
   * @return markdown cash flow table
   */
  public String getCashFlowTable() {
    if (cashFlowResult != null) {
      return cashFlowResult.toMarkdownTable();
    }
    return "No cash flow data available.";
  }

  /**
   * Checks if the project is economically viable.
   *
   * @return true if NPV > 0 and IRR > hurdle rate (typically 8%)
   */
  public boolean isViable() {
    return npv > 0 && irr > 0.08;
  }

  /**
   * Checks if the project is viable with confidence.
   *
   * @param minProbability minimum probability of positive NPV (0-1)
   * @return true if P(NPV>0) >= minProbability
   */
  public boolean isViableWithConfidence(double minProbability) {
    if (monteCarloResult == null) {
      return isViable();
    }
    return monteCarloResult.getProbabilityPositiveNpv() >= minProbability;
  }

  @Override
  public String toString() {
    return String.format("WorkflowResult[%s, NPV=%.0f MUSD, IRR=%.1f%%, Viable=%s]", projectName,
        npv, irr * 100, isViable());
  }
}
