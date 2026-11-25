package neqsim.thermodynamicoperations.flashops.saturationops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Transfers the source-space KCl salt-pair curvature law to independent dense-KCl states.
 *
 * <p>
 * The transfer is deliberately first order: at fixed model CO2 fugacity it maps the fitted
 * activity-coefficient increment {@code delta ln(gamma_CO2) = zeta(T) m_KCl^2} to
 * {@code x_CO2,candidate = x_CO2,baseline exp(-zeta(T) m_KCl^2)}. It neither changes production
 * parameters nor claims a coupled-flash result.
 *
 * @author OpenAI
 * @version 1.0
 */
class KClCO2SaltPairTransferAssessmentTest {
  private static final double REFERENCE_TEMPERATURE_K = 298.15;
  private static final double ZETA_298_KG2_PER_MOL2 = -0.0150603;
  private static final double ZETA_INVERSE_T_KG2_PER_MOL2 = -0.0780702;
  private static final double KCL_MOLAR_MASS_KG_PER_MOL = 0.0745513;
  private static final double TRANSFER_TEMPERATURE_K = 323.15;
  private static final String OUTPUT = "target/co2-kcl-salt-pair-transfer-assessment.csv";

  /**
   * Evaluates the frozen He-Morse curvature law against the independent 323.15 K pressure series.
   *
   * @throws Exception if the source fixture or diagnostic output cannot be read or written
   */
  @Tag("slow")
  @Test
  void frozenHeMorseLawTransfersToIndependentDenseKclSeries() throws Exception {
    List<KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference> references =
        KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.loadReferences();
    List<String> rows = new ArrayList<String>();
    rows.add("point_id,temperature_K,total_pressure_MPa,kcl_mass_fraction_percent,"
        + "kcl_molality_mol_per_kg_water,zeta_kg2_per_mol2,source_co2_mole_percent_salt_free,"
        + "baseline_co2_mole_percent_salt_free,candidate_co2_mole_percent_salt_free,"
        + "baseline_log_residual,candidate_log_residual,activity_correction_log,"
        + "baseline_relative_error_percent,candidate_relative_error_percent");

    int selected = 0;
    double baselineSquaredLogResidual = 0.0;
    double candidateSquaredLogResidual = 0.0;
    double baselineAbsoluteRelativeError = 0.0;
    double candidateAbsoluteRelativeError = 0.0;
    double[][] groupStatistics = new double[3][7];
    double zeta = zeta(TRANSFER_TEMPERATURE_K);
    for (KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Reference reference : references) {
      if (reference.temperatureK != TRANSFER_TEMPERATURE_K || reference.kclMassFractionPercent <= 0.0
          || reference.naclMassFractionPercent != 0.0 || reference.cacl2MassFractionPercent != 0.0
          || reference.mgcl2MassFractionPercent != 0.0) {
        continue;
      }
      KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.Result baseline =
          KClCO2SolubilityMousaviChapoyBurgass2024AssessmentTest.calculate(reference, 10.0);
      double molality = kclMolality(reference.kclMassFractionPercent);
      double activityCorrection = zeta * molality * molality;
      double candidateMolePercent = baseline.molecularMolePercent * Math.exp(-activityCorrection);
      double baselineLogResidual = Math.log(baseline.molecularMolePercent / reference.sourceCo2MolePercent);
      double candidateLogResidual = Math.log(candidateMolePercent / reference.sourceCo2MolePercent);
      double baselineRelativeError = 100.0
          * (baseline.molecularMolePercent / reference.sourceCo2MolePercent - 1.0);
      double candidateRelativeError = 100.0 * (candidateMolePercent / reference.sourceCo2MolePercent - 1.0);

      baselineSquaredLogResidual += baselineLogResidual * baselineLogResidual;
      candidateSquaredLogResidual += candidateLogResidual * candidateLogResidual;
      baselineAbsoluteRelativeError += Math.abs(baselineRelativeError);
      candidateAbsoluteRelativeError += Math.abs(candidateRelativeError);
      int group = concentrationGroup(reference.kclMassFractionPercent);
      groupStatistics[group][0]++;
      groupStatistics[group][1] += baselineLogResidual * baselineLogResidual;
      groupStatistics[group][2] += candidateLogResidual * candidateLogResidual;
      groupStatistics[group][3] += baselineLogResidual;
      groupStatistics[group][4] += candidateLogResidual;
      groupStatistics[group][5] += Math.abs(baselineRelativeError);
      groupStatistics[group][6] += Math.abs(candidateRelativeError);
      selected++;
      rows.add(reference.pointId + "," + reference.temperatureK + "," + reference.totalPressureMpa + ","
          + reference.kclMassFractionPercent + "," + molality + "," + zeta + ","
          + reference.sourceCo2MolePercent + "," + baseline.molecularMolePercent + ","
          + candidateMolePercent + "," + baselineLogResidual + "," + candidateLogResidual + ","
          + activityCorrection + "," + baselineRelativeError + "," + candidateRelativeError);
    }

    assertEquals(9, selected, "independent 323.15 K KCl states");
    double baselineLogRmse = Math.sqrt(baselineSquaredLogResidual / selected);
    double candidateLogRmse = Math.sqrt(candidateSquaredLogResidual / selected);
    double baselineMape = baselineAbsoluteRelativeError / selected;
    double candidateMape = candidateAbsoluteRelativeError / selected;
    for (int group = 0; group < groupStatistics.length; group++) {
      double count = groupStatistics[group][0];
      rows.add("SUMMARY-KCL" + (group == 0 ? 10 : group == 1 ? 15 : 22) + ",,,,,,,,,"
          + Math.sqrt(groupStatistics[group][1] / count) + ","
          + Math.sqrt(groupStatistics[group][2] / count) + ",," + (groupStatistics[group][5] / count) + ","
          + (groupStatistics[group][6] / count));
    }
    rows.add("SUMMARY,,,,,,,,," + baselineLogRmse + "," + candidateLogRmse + ",,"
        + baselineMape + "," + candidateMape);
    Files.createDirectories(Paths.get("target"));
    Files.write(Paths.get(OUTPUT), rows, StandardCharsets.UTF_8);

    assertTrue(Double.isFinite(zeta) && zeta < 0.0, "source-space curvature remains salting-out at 323.15 K");
    assertTrue(candidateLogRmse < baselineLogRmse, "frozen law must improve independent log-RMSE before activation");
    assertTrue(candidateMape < baselineMape, "frozen law must improve independent MAPE before activation");
    assertTrue(groupStatistics[0][2] < groupStatistics[0][1], "10 wt% KCl transfer must improve log-RMSE");
    assertTrue(groupStatistics[1][2] < groupStatistics[1][1], "15 wt% KCl transfer must improve log-RMSE");
    assertTrue(groupStatistics[2][2] > groupStatistics[2][1], "22 wt% KCl exposes transfer breakdown");
    assertTrue(groupStatistics[2][3] < 0.0 && groupStatistics[2][4] > 0.0,
        "22 wt% KCl transfer must retain the observed residual-sign reversal");
  }

  /**
   * Evaluates the frozen two-coefficient inverse-temperature law.
   *
   * @param temperatureK absolute temperature in kelvin
   * @return salt-pair curvature coefficient in kg^2/mol^2
   */
  private static double zeta(double temperatureK) {
    return ZETA_298_KG2_PER_MOL2
        + ZETA_INVERSE_T_KG2_PER_MOL2 * (REFERENCE_TEMPERATURE_K / temperatureK - 1.0);
  }

  /**
   * Converts an anhydrous KCl mass fraction to molality on the source water basis.
   *
   * @param kclMassFractionPercent KCl mass fraction in percent
   * @return KCl formula-unit molality in mol/kg water
   */
  private static double kclMolality(double kclMassFractionPercent) {
    return kclMassFractionPercent / (100.0 - kclMassFractionPercent) / KCL_MOLAR_MASS_KG_PER_MOL;
  }

  /**
   * Maps the three predeclared source concentrations to stable summary rows.
   *
   * @param kclMassFractionPercent KCl mass fraction in percent
   * @return zero-based group index for 10, 15 or 22 mass percent KCl
   */
  private static int concentrationGroup(double kclMassFractionPercent) {
    if (kclMassFractionPercent == 10.0) {
      return 0;
    }
    if (kclMassFractionPercent == 15.0) {
      return 1;
    }
    if (kclMassFractionPercent == 22.0) {
      return 2;
    }
    throw new IllegalArgumentException("Unexpected KCl mass fraction " + kclMassFractionPercent);
  }
}
