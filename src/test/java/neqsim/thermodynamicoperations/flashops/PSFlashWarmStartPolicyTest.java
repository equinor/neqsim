package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Tests model-aware K-value warm starts and representative CPA robustness in {@link PSFlash}. */
public class PSFlashWarmStartPolicyTest {

  /** CPA PS flashes keep inner TP flashes cold and restore the caller's setting. */
  @Test
  public void cpaDisablesInnerWarmStart() {
    SystemInterface system = new SystemSrkCPAstatoil(373.15, 1.2);
    RecordingPSFlash flash = new RecordingPSFlash(system);
    RecordingTPflash innerFlash = new RecordingTPflash(system);
    flash.setInnerFlash(innerFlash);

    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(true);
      flash.run();

      assertEquals(1, innerFlash.getRunCount());
      assertFalse(innerFlash.wasWarmStartEnabled(), "The first TP flash must use a cold start");
      assertFalse(flash.wasWarmStartEnabledDuringSolve(),
          "CPA PS iterations must not reuse K-values across temperatures");
      assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
          "PSFlash must restore the caller's warm-start setting");
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /** Cubic-EOS PS flashes retain the established inner warm-start acceleration. */
  @Test
  public void cubicEosRetainsInnerWarmStart() {
    SystemInterface system = new SystemSrkEos(300.15, 20.0);
    RecordingPSFlash flash = new RecordingPSFlash(system);
    RecordingTPflash innerFlash = new RecordingTPflash(system);
    flash.setInnerFlash(innerFlash);

    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      flash.run();

      assertEquals(1, innerFlash.getRunCount());
      assertFalse(innerFlash.wasWarmStartEnabled(), "The first TP flash must use a cold start");
      assertTrue(flash.wasWarmStartEnabledDuringSolve(),
          "Cubic-EOS PS iterations should reuse K-values after the first TP flash");
      assertFalse(ThermodynamicModelSettings.isUseWarmStartKValues(),
          "PSFlash must restore the caller's warm-start setting");
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /**
   * A representative rich-TEG CPA fluid must solve repeatably at two nearby discharge pressures.
   */
  @Test
  public void richTegCpaFlashConvergesRepeatablyAtNearbyPressures() {
    double[] dischargePressuresBara = new double[] { 1.35, 1.50 };

    for (double dischargePressureBara : dischargePressuresBara) {
      SystemInterface system = createRichTegFluid();
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
      system.initProperties();
      double targetEntropy = system.getEntropy();

      system.setPressure(dischargePressureBara);
      system.setTemperature(433.15);
      operations.PSflash(targetEntropy);
      system.initProperties();

      double firstTemperature = system.getTemperature();
      assertEquals(targetEntropy, system.getEntropy(), 1.0e-3,
          "CPA PS flash must satisfy entropy at " + dischargePressureBara + " bara");
      assertEquals(dischargePressureBara, system.getPressure("bara"), 1.0e-10,
          "PS flash must preserve specified pressure");
      assertTrue(Double.isFinite(firstTemperature) && firstTemperature > 350.0 && firstTemperature < 500.0,
          "CPA PS flash temperature must remain physical");

      system.setTemperature(firstTemperature + 5.0);
      operations.PSflash(targetEntropy);
      system.initProperties();

      assertEquals(targetEntropy, system.getEntropy(), 1.0e-3,
          "Repeated CPA PS flash must satisfy entropy at " + dischargePressureBara + " bara");
      assertEquals(firstTemperature, system.getTemperature(), 0.05,
          "Repeated CPA PS flash must return the same physical state");
    }
  }

  /** Create the rich TEG/water CPA fluid used by the regenerator engineering regression. */
  private SystemInterface createRichTegFluid() {
    SystemInterface system = new SystemSrkCPAstatoil(418.15, 1.21325);
    system.addComponent("nitrogen", 0.00005);
    system.addComponent("water", 0.19995);
    system.addComponent("TEG", 0.8);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);
    return system;
  }

  /** PS flash with a deterministic outer solve used to observe the warm-start policy. */
  private static final class RecordingPSFlash extends PSFlash {
    private boolean warmStartEnabledDuringSolve;

    RecordingPSFlash(SystemInterface system) {
      super(system, 0.0, 0);
    }

    void setInnerFlash(Flash innerFlash) {
      tpFlash = innerFlash;
    }

    boolean wasWarmStartEnabledDuringSolve() {
      return warmStartEnabledDuringSolve;
    }

    @Override
    public double solveQ() {
      warmStartEnabledDuringSolve = ThermodynamicModelSettings.isUseWarmStartKValues();
      return system.getTemperature();
    }
  }

  /** TP flash test double that records the policy used for its invocation. */
  private static final class RecordingTPflash extends TPflash {
    private int runCount;
    private boolean warmStartEnabled;

    RecordingTPflash(SystemInterface system) {
      super(system);
    }

    int getRunCount() {
      return runCount;
    }

    boolean wasWarmStartEnabled() {
      return warmStartEnabled;
    }

    @Override
    public void run() {
      runCount++;
      warmStartEnabled = ThermodynamicModelSettings.isUseWarmStartKValues();
    }
  }
}
