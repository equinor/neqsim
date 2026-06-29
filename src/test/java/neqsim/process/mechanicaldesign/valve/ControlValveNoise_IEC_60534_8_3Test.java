package neqsim.process.mechanicaldesign.valve;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ControlValveNoise_IEC_60534_8_3}.
 */
public class ControlValveNoise_IEC_60534_8_3Test {
  /**
   * A moderate gas pressure letdown should produce a physically reasonable SPL and a positive stream power.
   */
  @Test
  void testModerateLetdownNoise() {
    ControlValveNoise_IEC_60534_8_3 noise = new ControlValveNoise_IEC_60534_8_3();
    noise.setFlowConditions(5.0, 1.0e6, 5.0e5, 12.0, 6.0);
    noise.setAcousticProperties(430.0, 400.0, 1.3);
    noise.setGeometry(0.1, 0.15, 0.008);
    noise.setValveCoefficients(0.9, 0.4);
    noise.calcNoise();

    assertTrue(noise.getSoundPressureLevelDbA() > 30.0 && noise.getSoundPressureLevelDbA() < 160.0,
        "SPL should be in a physically reasonable range");
    assertTrue(noise.getOutletMach() > 0.0, "Outlet Mach number should be positive");
    assertTrue(noise.getFlowRegime() >= 1 && noise.getFlowRegime() <= 5, "Flow regime should be 1-5");
    assertTrue(noise.getMechanicalStreamPower() > 0.0, "Mechanical stream power should be positive");
    assertTrue(noise.getAcousticalEfficiency() > 0.0, "Acoustical efficiency should be positive");
    assertTrue(noise.getTransmissionLoss() >= 0.0, "Transmission loss should be non-negative");
    assertNotNull(noise.toJson());
  }

  /**
   * A larger pressure drop and higher flow should raise the sound pressure level.
   */
  @Test
  void testHigherDropLouderNoise() {
    ControlValveNoise_IEC_60534_8_3 mild = new ControlValveNoise_IEC_60534_8_3();
    mild.setFlowConditions(5.0, 1.0e6, 8.0e5, 12.0, 9.0);
    mild.setAcousticProperties(430.0, 410.0, 1.3);
    mild.setGeometry(0.1, 0.15, 0.008);
    mild.setValveCoefficients(0.9, 0.4);
    mild.calcNoise();

    ControlValveNoise_IEC_60534_8_3 severe = new ControlValveNoise_IEC_60534_8_3();
    severe.setFlowConditions(8.0, 5.0e6, 5.0e5, 40.0, 6.0);
    severe.setAcousticProperties(430.0, 400.0, 1.3);
    severe.setGeometry(0.1, 0.15, 0.008);
    severe.setValveCoefficients(0.9, 0.4);
    severe.calcNoise();

    assertTrue(severe.getSoundPressureLevelDbA() > mild.getSoundPressureLevelDbA(),
        "A more severe service should be louder");
  }

  /**
   * A thicker downstream pipe wall should give a higher transmission loss and lower external SPL.
   */
  @Test
  void testThickerWallQuieter() {
    ControlValveNoise_IEC_60534_8_3 thin = new ControlValveNoise_IEC_60534_8_3();
    thin.setFlowConditions(6.0, 2.0e6, 5.0e5, 20.0, 6.0);
    thin.setAcousticProperties(430.0, 400.0, 1.3);
    thin.setGeometry(0.1, 0.15, 0.006);
    thin.setValveCoefficients(0.9, 0.4);
    thin.calcNoise();

    ControlValveNoise_IEC_60534_8_3 thick = new ControlValveNoise_IEC_60534_8_3();
    thick.setFlowConditions(6.0, 2.0e6, 5.0e5, 20.0, 6.0);
    thick.setAcousticProperties(430.0, 400.0, 1.3);
    thick.setGeometry(0.1, 0.15, 0.016);
    thick.setValveCoefficients(0.9, 0.4);
    thick.calcNoise();

    assertTrue(thick.getTransmissionLoss() > thin.getTransmissionLoss(),
        "Thicker wall should have a higher transmission loss");
    assertTrue(thick.getSoundPressureLevelDbA() < thin.getSoundPressureLevelDbA(),
        "Thicker wall should give a lower external SPL");
  }
}
