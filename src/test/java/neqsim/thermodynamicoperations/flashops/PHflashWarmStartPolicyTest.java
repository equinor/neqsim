package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Tests model-aware K-value warm starts in {@link PHflash}. */
public class PHflashWarmStartPolicyTest {

  /** CPA PH flashes keep inner TP flashes cold and restore the caller's setting. */
  @Test
  public void cpaDisablesInnerWarmStart() {
    SystemInterface system = new SystemSrkCPAstatoil(373.15, 1.2);
    RecordingPHflash flash = new RecordingPHflash(system);
    RecordingTPflash innerFlash = new RecordingTPflash(system);
    flash.setInnerFlash(innerFlash);

    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(true);
      flash.run();

      assertEquals(1, innerFlash.getRunCount());
      assertFalse(innerFlash.wasWarmStartEnabled(), "The first TP flash must use a cold start");
      assertFalse(flash.wasWarmStartEnabledDuringSolve(),
          "CPA PH iterations must not reuse K-values across temperatures");
      assertTrue(ThermodynamicModelSettings.isUseWarmStartKValues(),
          "PHflash must restore the caller's warm-start setting");
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /** Cubic-EOS PH flashes retain the established inner warm-start acceleration. */
  @Test
  public void cubicEosRetainsInnerWarmStart() {
    SystemInterface system = new SystemSrkEos(300.15, 20.0);
    RecordingPHflash flash = new RecordingPHflash(system);
    RecordingTPflash innerFlash = new RecordingTPflash(system);
    flash.setInnerFlash(innerFlash);

    boolean previousWarmStart = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      flash.run();

      assertEquals(1, innerFlash.getRunCount());
      assertFalse(innerFlash.wasWarmStartEnabled(), "The first TP flash must use a cold start");
      assertTrue(flash.wasWarmStartEnabledDuringSolve(),
          "Cubic-EOS PH iterations should reuse K-values after the first TP flash");
      assertFalse(ThermodynamicModelSettings.isUseWarmStartKValues(),
          "PHflash must restore the caller's warm-start setting");
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /** PH flash with a deterministic outer solve used to observe the warm-start policy. */
  private static final class RecordingPHflash extends PHflash {
    private boolean warmStartEnabledDuringSolve;

    RecordingPHflash(SystemInterface system) {
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
