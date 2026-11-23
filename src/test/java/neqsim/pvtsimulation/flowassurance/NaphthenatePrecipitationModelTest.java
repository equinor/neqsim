package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.NaphthenateModelFitting.ObservationType;
import neqsim.pvtsimulation.flowassurance.NaphthenatePrecipitationModel.NaphthenateRisk;
import neqsim.pvtsimulation.flowassurance.NaphthenatePrecipitationModel.Parameter;

/**
 * Tests for {@link NaphthenatePrecipitationModel} and {@link NaphthenateModelFitting}.
 *
 * @author ESOL
 * @version 1.0
 */
class NaphthenatePrecipitationModelTest {

  /**
   * Creates the reference case: TAN 1 mg KOH/g, 10 mg/kg ARN, 1000 mg/L calcium, 50 % water cut, 60 C.
   *
   * @return model with default parameters
   */
  private NaphthenatePrecipitationModel referenceCase() {
    NaphthenatePrecipitationModel model = new NaphthenatePrecipitationModel();
    model.setWaterCut(0.5);
    model.setOilDensity(850.0);
    model.setTotalAcidNumber(1.0);
    model.setAcidMolarMass(300.0);
    model.setArnConcentration(10.0);
    model.setWaterComposition(1000.0, 10000.0, 500.0);
    model.setTemperature(333.15);
    return model;
  }

  /**
   * The carbonate constants reproduce the accepted 25 C values (pKH 1.47, pK1 6.35).
   */
  @Test
  void carbonateConstantsAt25C() {
    assertEquals(-1.468, NaphthenatePrecipitationModel.log10HenryConstantCO2(298.15), 0.005);
    assertEquals(-6.352, NaphthenatePrecipitationModel.log10FirstDissociationConstantCO2(298.15), 0.005);
    assertEquals(1.0, NaphthenatePrecipitationModel.daviesActivityCoefficient(2, 0.0, 298.15), 1.0e-12);
    assertTrue(NaphthenatePrecipitationModel.daviesActivityCoefficient(2, 0.5, 298.15) < 0.4);
  }

  /**
   * ARN precipitates sharply around its onset pH and the monoprotic soap at a higher pH.
   */
  @Test
  void precipitationFollowsOnsetPH() {
    NaphthenatePrecipitationModel model = referenceCase();
    double arnOnset = model.calculateArnOnsetPH();
    double soapOnset = model.calculateCalciumSoapOnsetPH();
    assertEquals(6.0, arnOnset, 0.1);
    assertTrue(soapOnset > arnOnset, "soap onset " + soapOnset + " should be above ARN onset " + arnOnset);

    model.setWaterPH(arnOnset - 1.0);
    model.run();
    assertEquals(NaphthenateRisk.NO_RISK, model.getRisk());
    assertEquals(0.0, model.getPrecipitatedArnFraction(), 0.0);
    assertEquals(0.0, model.getPrecipitatedCalciumSoap(), 0.0);
    assertTrue(model.getArnSaturationRatio() < 1.0);

    model.setWaterPH(arnOnset - 0.2);
    model.run();
    assertEquals(NaphthenateRisk.LOW_RISK, model.getRisk());

    model.setWaterPH(arnOnset + 0.5);
    model.run();
    assertEquals(NaphthenateRisk.ARN_DEPOSIT_RISK, model.getRisk());
    assertTrue(model.getPrecipitatedArnFraction() > 0.95);
    assertEquals(0.0, model.getPrecipitatedCalciumSoap(), 0.0);
    // 10 mg/kg ARN in 850 kg/m3 oil gives at most about 9 g of calcium naphthenate per m3 oil
    assertEquals(0.0090, model.getPrecipitatedArnNaphthenate(), 0.0005);

    model.setArnConcentration(0.0);
    model.setWaterPH(soapOnset + 0.5);
    model.run();
    assertEquals(NaphthenateRisk.SOAP_RISK, model.getRisk());
    assertTrue(model.getPrecipitatedCalciumSoap() > 0.0);
    assertTrue(model.getPrecipitatedAcidFraction() + model.getAcidFractionInWater() < 1.0);
    assertTrue(model.getFreeCalciumConcentration() < 1000.0);
  }

  /**
   * Precipitated soap and dissolved organic acid increase with pH.
   */
  @Test
  void soapAndDissolvedAcidIncreaseWithPH() {
    NaphthenatePrecipitationModel model = referenceCase();
    double previousSoap = -1.0;
    double previousAcid = -1.0;
    for (double pH = 5.0; pH <= 9.01; pH += 0.5) {
      model.setWaterPH(pH);
      model.run();
      assertTrue(model.getPrecipitatedCalciumSoap() >= previousSoap);
      assertTrue(model.getAqueousOrganicAcidConcentration() > previousAcid);
      previousSoap = model.getPrecipitatedCalciumSoap();
      previousAcid = model.getAqueousOrganicAcidConcentration();
    }
    assertTrue(previousSoap > 0.0);
  }

  /**
   * Lower CO2 partial pressure raises the pH and moves the system into the naphthenate region; released protons lower
   * the pH relative to the acid-free water.
   */
  @Test
  void depressurisationRaisesPHAndRisk() {
    NaphthenatePrecipitationModel model = referenceCase();
    model.setCO2PartialPressure(20.0);
    model.run();
    double highPressurePH = model.getWaterPH();
    assertEquals(NaphthenateRisk.NO_RISK, model.getRisk());

    model.setCO2PartialPressure(0.05);
    model.run();
    assertTrue(model.getWaterPH() > highPressurePH + 1.5);
    assertEquals(NaphthenateRisk.ARN_DEPOSIT_RISK, model.getRisk());
    assertTrue(model.getWaterPH() < model.getWaterPHBeforePrecipitation());

    NaphthenatePrecipitationModel acidFree = referenceCase();
    acidFree.setTotalAcidNumber(0.0);
    acidFree.setArnConcentration(0.0);
    acidFree.setCO2PartialPressure(0.05);
    acidFree.run();
    assertTrue(acidFree.getWaterPH() > model.getWaterPH());
    assertEquals(NaphthenateRisk.NO_RISK, acidFree.getRisk());
  }

  /**
   * Results require a calculation and invalid input is rejected.
   */
  @Test
  void inputIsValidated() {
    final NaphthenatePrecipitationModel model = referenceCase();
    assertThrows(IllegalStateException.class, () -> model.getWaterPH());
    assertThrows(IllegalArgumentException.class, () -> model.setWaterCut(1.0));
    model.setOilDensity(-1.0);
    assertThrows(IllegalStateException.class, () -> model.run());
  }

  /**
   * The JSON report holds results and flags uncalibrated parameters.
   */
  @Test
  void jsonReportFlagsScreeningParameters() {
    NaphthenatePrecipitationModel model = referenceCase();
    model.setWaterPH(6.5);
    model.run();
    String json = model.toJson();
    assertTrue(json.contains("\"arnOnsetPH\""));
    assertTrue(json.contains("\"calibrated\": false"));
    assertFalse(model.isCalibrated());
    assertTrue(model.getWarnings().toString().contains("screening parameters"));
  }

  /**
   * Synthetic bottle-test data generated with known parameters are recovered by the regression.
   */
  @Test
  void fittingRecoversParameters() {
    NaphthenatePrecipitationModel truth = referenceCase();
    truth.setParameter(Parameter.PKSP_CALCIUM_SOAP, 9.3);
    truth.setParameter(Parameter.LOG_PARTITION_COEFFICIENT, 3.6);
    truth.setParameter(Parameter.PK_ARN, 13.6);

    NaphthenateModelFitting fitting = new NaphthenateModelFitting();
    // partition tests without calcium: dissolved organic acid versus pH
    for (double pH = 6.0; pH <= 10.01; pH += 1.0) {
      NaphthenatePrecipitationModel experiment = truth.copy();
      experiment.setWaterComposition(0.0, 10000.0, 500.0);
      experiment.setArnConcentration(0.0);
      experiment.setWaterPH(pH);
      experiment.run();
      fitting.addObservation(startingPoint(experiment), ObservationType.AQUEOUS_ORGANIC_ACID,
          experiment.getAqueousOrganicAcidConcentration(), 0.02 * experiment.getAqueousOrganicAcidConcentration());
    }
    // bottle tests with calcium: soap mass and ARN precipitated fraction versus pH
    for (double pH = 7.5; pH <= 9.01; pH += 0.5) {
      NaphthenatePrecipitationModel experiment = truth.copy();
      experiment.setWaterPH(pH);
      experiment.run();
      assertTrue(experiment.getPrecipitatedCalciumSoap() > 0.0);
      fitting.addObservation(startingPoint(experiment), ObservationType.PRECIPITATED_CALCIUM_SOAP,
          experiment.getPrecipitatedCalciumSoap(), 0.05);
    }
    NaphthenatePrecipitationModel onset = truth.copy();
    fitting.addObservation(startingPoint(onset), ObservationType.ARN_ONSET_PH, onset.calculateArnOnsetPH(), 0.05);

    fitting.addFitParameter(Parameter.LOG_PARTITION_COEFFICIENT);
    fitting.addFitParameter(Parameter.PKSP_CALCIUM_SOAP);
    fitting.addFitParameter(Parameter.PK_ARN);
    assertTrue(fitting.solve());

    assertEquals(3.6, fitting.getFittedValue(Parameter.LOG_PARTITION_COEFFICIENT), 0.01);
    assertEquals(9.3, fitting.getFittedValue(Parameter.PKSP_CALCIUM_SOAP), 0.02);
    assertEquals(13.6, fitting.getFittedValue(Parameter.PK_ARN), 0.01);
    assertTrue(fitting.getRootMeanSquareWeightedResidual() < 0.1);
    assertTrue(fitting.getStandardError(Parameter.PK_ARN) > 0.0);
    assertEquals(10, fitting.getNumberOfObservations());

    NaphthenatePrecipitationModel calibrated = referenceCase();
    fitting.applyTo(calibrated);
    assertTrue(calibrated.isCalibrated());
    assertEquals(truth.calculateArnOnsetPH(), calibrated.calculateArnOnsetPH(), 0.01);
    assertEquals(truth.calculateCalciumSoapOnsetPH(), calibrated.calculateCalciumSoapOnsetPH(), 0.02);
    assertTrue(fitting.toMap().containsKey("fittedParameters"));
  }

  /**
   * Resets the parameters of an experiment to the defaults so that the regression starts away from the solution.
   *
   * @param experiment experiment with the true parameters
   * @return experiment with default parameters
   */
  private NaphthenatePrecipitationModel startingPoint(NaphthenatePrecipitationModel experiment) {
    NaphthenatePrecipitationModel start = experiment.copy();
    start.setParameter(Parameter.PKSP_CALCIUM_SOAP, 10.0);
    start.setParameter(Parameter.LOG_PARTITION_COEFFICIENT, 4.0);
    start.setParameter(Parameter.PK_ARN, 14.4);
    return start;
  }

  /**
   * A fit needs parameters and enough observations.
   */
  @Test
  void fittingRequiresParametersAndData() {
    final NaphthenateModelFitting fitting = new NaphthenateModelFitting();
    assertThrows(IllegalStateException.class, () -> fitting.solve());
    fitting.addFitParameter(Parameter.PK_ARN);
    assertThrows(IllegalStateException.class, () -> fitting.solve());
    assertThrows(IllegalArgumentException.class,
        () -> fitting.addObservation(referenceCase(), ObservationType.ARN_ONSET_PH, 6.0, 0.0));
  }
}
