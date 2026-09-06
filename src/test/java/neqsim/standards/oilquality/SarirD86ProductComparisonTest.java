package neqsim.standards.oilquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests the strict Sarir product T95 comparison workflow. */
class SarirD86ProductComparisonTest {
  private Standard_ASTM_D86 calculatedStandard() {
    SystemInterface oil = new SystemSrkEos(273.15 + 25.0, 1.01325);
    oil.addComponent("nC10", 0.05);
    oil.addTBPfraction("C12", 0.20, 170.0 / 1000.0, 0.78);
    oil.addTBPfraction("C14", 0.25, 198.0 / 1000.0, 0.80);
    oil.addTBPfraction("C16", 0.25, 226.0 / 1000.0, 0.82);
    oil.addTBPfraction("C18", 0.15, 254.0 / 1000.0, 0.83);
    oil.addTBPfraction("C20", 0.10, 282.0 / 1000.0, 0.85);
    oil.setMixingRule(2);
    oil.init(0);

    Standard_ASTM_D86 standard = new Standard_ASTM_D86(oil);
    standard.calculate();
    return standard;
  }

  @Test
  void strictT95UsesQualifiedStandardAndPreservesSourceEvidence() {
    Standard_ASTM_D86 standard = calculatedStandard();
    String[] names = {"Light Naphtha", "Heavy Naphtha", "Kerosene", "Diesel"};
    double[] laboratory = {90.0, 160.0, 221.0, 346.0};
    double[] hysys = {97.0, 153.0, 214.0, 339.0};
    double[] specifications = {90.0, 160.0, 221.0, 327.0};
    double strictT95 = standard.getQualifiedD86Temperature(95.0);

    for (int i = 0; i < names.length; i++) {
      SarirD86ProductComparison.Result result =
          SarirD86ProductComparison.compareT95(standard, names[i]);

      assertEquals(names[i], result.getProductName());
      assertEquals(95.0, result.getRecoveryVolumePercent(), 0.0);
      assertEquals(strictT95, result.getNeqsimT95Celsius(), 1.0e-10);
      assertEquals(laboratory[i], result.getLaboratoryT95Celsius(), 0.0);
      assertEquals(hysys[i], result.getHysysT95Celsius(), 0.0);
      assertEquals(specifications[i], result.getSpecificationT95Celsius(), 0.0);
      assertEquals(
          100.0 * Math.abs(strictT95 - laboratory[i]) / laboratory[i],
          result.getNeqsimAbsoluteRelativeErrorPercent(),
          1.0e-12);
      assertEquals(
          100.0 * Math.abs(hysys[i] - laboratory[i]) / laboratory[i],
          result.getHysysAbsoluteRelativeErrorPercent(),
          1.0e-12);
      assertEquals(
          specifications[i] - strictT95,
          result.getSpecificationMarginCelsius(),
          1.0e-12);
    }
  }

  @Test
  void unsupportedOrUnresolvedInputsFailClosed() {
    Standard_ASTM_D86 standard = calculatedStandard();

    assertThrows(
        IllegalArgumentException.class,
        () -> SarirD86ProductComparison.compareT95(null, "Diesel"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SarirD86ProductComparison.compareT95(standard, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> SarirD86ProductComparison.compareT95(standard, "diesel"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SarirD86ProductComparison.compareT95(standard, "Residual"));

    Standard_ASTM_D86 uncalculated =
        new Standard_ASTM_D86(new SystemSrkEos(273.15 + 25.0, 1.01325));
    assertThrows(
        IllegalStateException.class,
        () -> SarirD86ProductComparison.compareT95(uncalculated, "Diesel"));
  }
}
