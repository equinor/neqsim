package neqsim.thermodynamicoperations.flashops.saturationops;

import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Manual median benchmark for aqueous and gas-oil-aqueous Pitzer precipitation calculations. */
public final class SaltPrecipitationPerformanceBenchmark {
  private static final Logger LOGGER = LogManager.getLogger(SaltPrecipitationPerformanceBenchmark.class);
  private static volatile double sink;

  private SaltPrecipitationPerformanceBenchmark() {
  }

  /**
   * Runs fixed-work batches after warmup and prints median nanoseconds per calculation.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    SystemSrkEos neutral = createNeutralSystem();
    for (int warmup = 0; warmup < 100; warmup++) {
      sink += neutralFlashChecksum(neutral);
    }
    long neutralBefore = medianBatches(() -> neutralFlashChecksum(neutral), 20);

    for (int warmup = 0; warmup < 2; warmup++) {
      sink += precipitationChecksum(false);
      sink += precipitationChecksum(true);
    }
    long aqueousPrecipitation = medianBatches(() -> precipitationChecksum(false), 2);
    long multiphasePrecipitation = medianBatches(() -> precipitationChecksum(true), 2);
    long simultaneousAqueousPrecipitation = medianBatches(() -> simultaneousPrecipitationChecksum(false), 2);
    long simultaneousMultiphasePrecipitation = medianBatches(() -> simultaneousPrecipitationChecksum(true), 2);
    long neutralAfter = medianBatches(() -> neutralFlashChecksum(neutral), 20);

    SaltPrecipitationResult evidence = precipitate(false);
    LOGGER.info("aqueousPrecipitationNs={}", aqueousPrecipitation);
    LOGGER.info("gasOilAqueousPrecipitationNs={}", multiphasePrecipitation);
    LOGGER.info("simultaneousAqueousPrecipitationNs={}", simultaneousAqueousPrecipitation);
    LOGGER.info("simultaneousGasOilAqueousPrecipitationNs={}", simultaneousMultiphasePrecipitation);
    LOGGER.info("neutralSrkBeforeNs={}", neutralBefore);
    LOGGER.info("neutralSrkAfterNs={}", neutralAfter);
    LOGGER.info("neutralSrkRatio={}", (double) neutralAfter / neutralBefore);
    LOGGER.info("initialSaturationRatio={}", evidence.getInitialSaturationRatio());
    LOGGER.info("finalSaturationRatio={}", evidence.getFinalSaturationRatio());
    LOGGER.info("precipitatedMoles={}", evidence.getPrecipitatedMoles());
    LOGGER.info("ionBalanceResidualMoles={}", evidence.getMaximumIonBalanceResidualMoles());
    MultiSaltPrecipitationResult simultaneousEvidence = precipitateSimultaneously(false);
    LOGGER.info("simultaneousEquilibriumUpdates={}", simultaneousEvidence.getEquilibriumUpdates());
    LOGGER.info("simultaneousComplementarityViolation={}", simultaneousEvidence.getMaximumComplementarityViolation());
    LOGGER.info("simultaneousBalanceResidualMoles={}", simultaneousEvidence.getMaximumComponentBalanceResidualMoles());
    if (!Double.isFinite(sink)) {
      throw new IllegalStateException("Benchmark checksum is not finite");
    }
  }

  private static long medianBatches(Work work, int iterations) {
    long[] samples = new long[5];
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

  private static double precipitationChecksum(boolean includeHydrocarbons) {
    SaltPrecipitationResult result = precipitate(includeHydrocarbons);
    return result.getPrecipitatedMoles() + result.getFinalSaturationRatio()
        + result.getMaximumIonBalanceResidualMoles();
  }

  private static SaltPrecipitationResult precipitate(boolean includeHydrocarbons) {
    SystemPitzer system = createPitzerSystem(includeHydrocarbons);
    return new ThermodynamicOperations(system).precipitateScale("CaSO4_A");
  }

  private static double simultaneousPrecipitationChecksum(boolean includeHydrocarbons) {
    MultiSaltPrecipitationResult result = precipitateSimultaneously(includeHydrocarbons);
    return result.getTotalPrecipitatedMassGrams() + result.getMaximumComplementarityViolation()
        + result.getMaximumComponentBalanceResidualMoles();
  }

  private static MultiSaltPrecipitationResult precipitateSimultaneously(boolean includeHydrocarbons) {
    SystemPitzer system = createPitzerSystem(includeHydrocarbons);
    return new ThermodynamicOperations(system).precipitateScales("CaSO4_A", "CaSO4_G");
  }

  private static double neutralFlashChecksum(SystemSrkEos system) {
    new ThermodynamicOperations(system).TPflash();
    system.initPhysicalProperties();
    return system.getPhase(0).getDensity() + system.getPhase(0).getEnthalpy() + system.getPhase(0).getCp();
  }

  private static SystemPitzer createPitzerSystem(boolean includeHydrocarbons) {
    SystemPitzer system = new SystemPitzer(298.15, includeHydrocarbons ? 50.0 : 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("Na+", 1.0);
    system.addComponent("Ca++", 0.2);
    system.addComponent("Mg++", 0.0);
    system.addComponent("Cl-", 1.0);
    system.addComponent("SO4--", 0.2);
    system.init(0);
    system.applyPhreeqcCalciumMagnesiumChlorideSulfateParameters();
    if (includeHydrocarbons) {
      system.addComponent("methane", 5.0);
      system.addComponent("n-heptane", 2.0);
    }
    system.setMixingRule("classic");
    system.setMultiPhaseCheck(true);
    return system;
  }

  private static SystemSrkEos createNeutralSystem() {
    SystemSrkEos system = new SystemSrkEos(298.15, 20.0);
    system.addComponent("methane", 0.8);
    system.addComponent("ethane", 0.2);
    system.setMixingRule("classic");
    return system;
  }

  private interface Work {
    double run();
  }
}
