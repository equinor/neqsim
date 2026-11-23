package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Runs the Java examples of {@code docs/pvtsimulation/flowassurance/asphaltene_modeling.md} (Flory-Huggins onset
 * calibration) and {@code docs/pvtsimulation/flowassurance/naphthenate_precipitation.md} and checks the values stated
 * in the comments of those examples.
 *
 * @author ESOL
 * @version 1.0
 */
class OrganicSolidsDocExamplesTest {

  /**
   * Example between the fh-onset-calibration markers of asphaltene_modeling.md.
   */
  @Test
  void floryHugginsOnsetCalibrationExample() {
    SystemInterface fluid = new SystemSrkEos(373.15, 500.0);
    fluid.addComponent("methane", 0.40);
    fluid.addComponent("ethane", 0.05);
    fluid.addComponent("propane", 0.03);
    fluid.addComponent("n-heptane", 0.32);
    fluid.addComponent("nC10", 0.20);
    fluid.setMixingRule("classic");

    FloryHugginsAsphalteneModel fh = new FloryHugginsAsphalteneModel(fluid, 373.15);
    fh.setAsphalteneWeightFraction(0.03); // n-C7 asphaltene content of the oil
    fh.setAsphalteneMW(750.0);
    fh.setAsphalteneDensity(1100.0);

    // One measured onset: reproduced exactly
    double deltaA = fh.calibrateToOnsetPressure(373.15, 300.0);
    double onset = fh.calculateOnsetPressure(373.15, 500.0, 1.0); // 300 bara
    double lowerOnset = fh.calculateLowerOnsetPressure(373.15, 500.0, 1.0);

    // Onsets at several temperatures: fits delta_a(T) = delta_a(Tref) + c (T - Tref)
    double[] temperatures = {333.15, 353.15, 373.15};
    double[] measuredOnsets = {340.0, 318.0, 300.0};
    FloryHugginsAsphalteneModel.OnsetFitResult fit = fh.fitToOnsetData(temperatures, measuredOnsets);
    double deltaRef = fit.getSolubilityParameter(); // MPa^0.5 at fit.getReferenceTemperature()
    double slope = fit.getTemperatureCoefficient(); // MPa^0.5/K
    double aad = fit.getAverageAbsoluteDeviation(); // bar, predicted vs measured onset

    assertTrue(deltaA > 18.0 && deltaA < 22.0);
    assertEquals(300.0, onset, 0.05);
    assertTrue(lowerOnset < onset);
    assertEquals(353.15, fit.getReferenceTemperature(), 1.0e-9);
    assertTrue(deltaRef > 18.0 && deltaRef < 22.0);
    assertFalse(Double.isNaN(slope));
    assertTrue(aad < 5.0, "average absolute deviation " + aad + " bar");
  }

  /**
   * Example between the naphthenate-screening markers of naphthenate_precipitation.md.
   */
  @Test
  void naphthenateScreeningExample() {
    NaphthenatePrecipitationModel model = new NaphthenatePrecipitationModel();
    model.setWaterCut(0.5); // 1 m3 liquid, 50 % water
    model.setOilDensity(850.0); // kg/m3
    model.setTotalAcidNumber(1.0); // mg KOH/g
    model.setAcidMolarMass(300.0); // g/mol
    model.setArnConcentration(10.0); // mg/kg oil
    model.setWaterComposition(1000.0, 10000.0, 500.0); // Ca, Na, HCO3- in mg/L
    model.setTemperature(333.15); // K

    double arnOnsetPH = model.calculateArnOnsetPH(); // about 6.0
    double soapOnsetPH = model.calculateCalciumSoapOnsetPH(); // about 6.8

    // First-stage separator: 5 bara CO2 partial pressure
    model.setCO2PartialPressure(5.0);
    model.run();
    double pHHighPressure = model.getWaterPH(); // about 5.2, NO_RISK
    assertEquals(NaphthenatePrecipitationModel.NaphthenateRisk.NO_RISK, model.getRisk());

    // Low-pressure separator: 0.2 bara CO2 partial pressure
    model.setCO2PartialPressure(0.2);
    model.run();
    double pHLowPressure = model.getWaterPH(); // about 6.5
    NaphthenatePrecipitationModel.NaphthenateRisk risk = model.getRisk(); // ARN_DEPOSIT_RISK
    double arnDeposit = model.getPrecipitatedArnNaphthenate(); // kg per m3 oil
    double soap = model.getPrecipitatedCalciumSoap(); // kg per m3 oil
    double dissolvedAcid = model.getAqueousOrganicAcidConcentration(); // mg/L in the water
    String json = model.toJson();

    assertEquals(6.0, arnOnsetPH, 0.05);
    assertEquals(6.8, soapOnsetPH, 0.05);
    assertEquals(5.2, pHHighPressure, 0.1);
    assertEquals(6.5, pHLowPressure, 0.1);
    assertEquals(NaphthenatePrecipitationModel.NaphthenateRisk.ARN_DEPOSIT_RISK, risk);
    assertTrue(arnDeposit > 0.0);
    assertEquals(0.0, soap, 0.0);
    assertTrue(dissolvedAcid > 0.0);
    assertTrue(json.contains("ARN_DEPOSIT_RISK"));
  }

  /**
   * Example between the naphthenate-fitting markers of naphthenate_precipitation.md.
   */
  @Test
  void naphthenateFittingExample() {
    NaphthenatePrecipitationModel bottleTest = new NaphthenatePrecipitationModel();
    bottleTest.setWaterCut(0.5);
    bottleTest.setOilDensity(850.0);
    bottleTest.setTotalAcidNumber(1.0);
    bottleTest.setAcidMolarMass(300.0);
    bottleTest.setArnConcentration(10.0);
    bottleTest.setWaterComposition(1000.0, 10000.0, 500.0);
    bottleTest.setTemperature(333.15);

    NaphthenateModelFitting fitting = new NaphthenateModelFitting();

    // Soap mass (kg/m3 oil) measured at three buffered pH values, standard deviation 0.1 kg/m3
    double[] testPH = {7.5, 8.0, 8.5};
    double[] soapMass = {2.6, 3.9, 4.4};
    for (int i = 0; i < testPH.length; i++) {
      bottleTest.setWaterPH(testPH[i]);
      fitting.addObservation(bottleTest, NaphthenateModelFitting.ObservationType.PRECIPITATED_CALCIUM_SOAP, soapMass[i],
          0.1);
    }
    // ARN deposit first seen at pH 6.3 +/- 0.1
    fitting.addObservation(bottleTest, NaphthenateModelFitting.ObservationType.ARN_ONSET_PH, 6.3, 0.1);

    fitting.addFitParameter(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
    fitting.addFitParameter(NaphthenatePrecipitationModel.Parameter.PK_ARN);
    fitting.solve();

    double pKsp = fitting.getFittedValue(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
    double pKspError = fitting.getStandardError(NaphthenatePrecipitationModel.Parameter.PKSP_CALCIUM_SOAP);
    double fitQuality = fitting.getRootMeanSquareWeightedResidual(); // near 1 = within stated uncertainty

    // Use the fitted parameters for field predictions
    NaphthenatePrecipitationModel field = bottleTest.copy();
    fitting.applyTo(field); // also marks the model as calibrated
    field.setCO2PartialPressure(0.2);
    field.run();

    assertTrue(pKsp > 8.0 && pKsp < 11.0, "fitted pKsp " + pKsp);
    assertTrue(pKspError > 0.0 && pKspError < 0.5, "pKsp standard error " + pKspError);
    assertTrue(fitQuality < 3.0, "fit quality " + fitQuality);
    assertTrue(field.isCalibrated());
    assertEquals(6.3, field.calculateArnOnsetPH(), 0.01);
  }
}
