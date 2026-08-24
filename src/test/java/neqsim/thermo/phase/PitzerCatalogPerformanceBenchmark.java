package neqsim.thermo.phase;

import java.util.Arrays;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkEos;

/** Manual median benchmark for the PHREEQC catalog kernel, complete aqueous properties, and neutral SRK control. */
public final class PitzerCatalogPerformanceBenchmark {
  private static volatile double sink;

  private PitzerCatalogPerformanceBenchmark() {
  }

  /**
   * Runs fixed-work batches after warmup and prints median nanoseconds per calculation.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    SystemSrkEos neutral = createNeutralSystem();
    for (int warmup = 0; warmup < 500; warmup++) {
      neutral.init(3);
    }
    long neutralBeforeCatalog = medianBatches(() -> neutralPropertyChecksum(neutral), 100);

    SystemPitzer pitzer = createPitzerSystem();
    PhasePitzer aqueous = (PhasePitzer) pitzer.getPhase(1);
    for (int warmup = 0; warmup < 500; warmup++) {
      sink += kernelChecksum(aqueous);
      pitzer.init(3);
      neutral.init(3);
    }

    System.out.println("pitzerCatalogKernelNs=" + medianBatches(() -> kernelChecksum(aqueous), 4000));
    System.out.println("pitzerCompletePropertyNs=" + medianBatches(() -> {
      pitzer.init(3);
      pitzer.initPhysicalProperties();
      return pitzer.getPhase(1).getDensity() + pitzer.getPhase(1).getEnthalpy() + pitzer.getPhase(1).getCp();
    }, 100));
    long neutralAfterCatalog = medianBatches(() -> neutralPropertyChecksum(neutral), 100);
    System.out.println("neutralSrkBeforeCatalogNs=" + neutralBeforeCatalog);
    System.out.println("neutralSrkAfterCatalogNs=" + neutralAfterCatalog);
    System.out.println("neutralCatalogLoadedRatio=" + (double) neutralAfterCatalog / neutralBeforeCatalog);
    if (!Double.isFinite(sink)) {
      throw new IllegalStateException("Benchmark checksum is not finite");
    }
  }

  private static long medianBatches(Work work, int iterations) {
    long[] samples = new long[9];
    for (int sample = 0; sample < samples.length; sample++) {
      long start = System.nanoTime();
      for (int iteration = 0; iteration < iterations; iteration++) {
        sink += work.run();
      }
      samples[sample] = (System.nanoTime() - start) / iterations;
    }
    Arrays.sort(samples);
    return samples[samples.length / 2];
  }

  private static double kernelChecksum(PhasePitzer phase) {
    double value = phase.getOsmoticCoefficientOfWater();
    for (int component = 0; component < phase.getNumberOfComponents(); component++) {
      if (Math.abs(phase.getComponent(component).getIonicCharge()) >= 0.5) {
        ComponentGePitzer ion = (ComponentGePitzer) phase.getComponent(component);
        value += ion.getGamma(phase, phase.getNumberOfComponents(), phase.getTemperature(), phase.getPressure(),
            phase.getType());
      }
    }
    return value;
  }

  private static double neutralPropertyChecksum(SystemSrkEos system) {
    system.init(3);
    system.initPhysicalProperties();
    return system.getPhase(0).getDensity() + system.getPhase(0).getEnthalpy() + system.getPhase(0).getCp();
  }

  private static SystemPitzer createPitzerSystem() {
    SystemPitzer system = new SystemPitzer(298.15, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Ca++", 0.2);
    system.addComponent("Mg++", 0.3);
    system.addComponent("Cl-", 0.4);
    system.addComponent("SO4--", 0.3);
    system.setMixingRule("classic");
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    return system;
  }

  private static SystemSrkEos createNeutralSystem() {
    SystemSrkEos system = new SystemSrkEos(298.15, 20.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");
    system.init(0);
    return system;
  }

  private interface Work {
    double run();
  }
}
