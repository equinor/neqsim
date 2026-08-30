package neqsim.thermo.phase;

import java.util.Arrays;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
import neqsim.mcp.runners.ChemistryRunner;
import neqsim.thermo.component.ComponentGePitzer;
import neqsim.thermo.component.IapwsHenryLaw;
import neqsim.thermo.system.SystemPitzer;
import neqsim.thermo.system.SystemSrkEos;

/** Manual median benchmark for the PHREEQC catalog kernel, complete aqueous properties, and neutral SRK control. */
public final class PitzerCatalogPerformanceBenchmark {
  private static volatile double sink;
  private static final String QUALIFICATION_REQUEST = "{\"analysis\":\"pitzerQualification\","
      + "\"temperature_K\":298.15,\"pressure_bara\":1.01325,\"dataset\":\"phreeqc-na-k-cl\","
      + "\"validationTarget\":\"AQUEOUS_ACTIVITY_COEFFICIENTS\","
      + "\"components\":{\"water\":55.508,\"Na+\":0.5,\"K+\":0.5,\"Cl-\":1.0}}";
  private static final String SCALE_EQUILIBRIUM_REQUEST = "{\"analysis\":\"electrolyteScaleEquilibrium\","
      + "\"model\":\"pitzer\",\"dataset\":\"phreeqc-ca-mg-cl-so4\","
      + "\"temperature_K\":298.15,\"pressure_bara\":1.01325,\"mineral\":\"CaSO4_A\","
      + "\"components\":{\"water\":55.508,\"Na+\":1.0,\"Ca++\":0.2,\"Mg++\":0.0," + "\"Cl-\":1.0,\"SO4--\":0.2}}";

  private PitzerCatalogPerformanceBenchmark() {
  }

  /**
   * Runs fixed-work batches after warmup and prints median nanoseconds per calculation.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    for (int warmup = 0; warmup < 10000; warmup++) {
      sink += iapwsHenryKernelChecksum();
    }
    System.out.println(
        "iapwsHenryKernelNs=" + medianBatches(PitzerCatalogPerformanceBenchmark::iapwsHenryKernelChecksum, 10000));

    SystemSrkEos neutral = createNeutralSystem();
    for (int warmup = 0; warmup < 500; warmup++) {
      neutral.init(3);
    }
    long neutralBeforeCatalog = medianBatches(() -> neutralPropertyChecksum(neutral), 100);

    SystemPitzer pitzer = createPitzerSystem();
    PhasePitzer aqueous = (PhasePitzer) pitzer.getPhase(1);
    long selectionStart = System.nanoTime();
    sink += kernelChecksum(aqueous);
    long automaticCatalogSelection = System.nanoTime() - selectionStart;
    if (!PitzerParameterDatasets.PHREEQC_PITZER_CATALOG_ID.equals(aqueous.getParameterDatasetId())) {
      throw new IllegalStateException("Automatic Pitzer catalog selection did not activate");
    }
    System.out.println("pitzerAutomaticCatalogSelectionNs=" + automaticCatalogSelection);
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

    for (int warmup = 0; warmup < 100; warmup++) {
      sink += qualificationViewChecksum();
    }
    System.out.println("pitzerQualificationViewNs="
        + medianBatches(PitzerCatalogPerformanceBenchmark::qualificationViewChecksum, 100));
    for (int warmup = 0; warmup < 3; warmup++) {
      sink += scaleEquilibriumViewChecksum();
    }
    System.out.println("pitzerScaleEquilibriumViewNs="
        + medianBatches(PitzerCatalogPerformanceBenchmark::scaleEquilibriumViewChecksum, 5));

    SystemPitzer reactivePitzer = createReactiveH2sSystem(298.15);
    ChemicalReaction h2sReaction = reactivePitzer.getChemicalReactionOperations().getReactionList()
        .getReaction("water-H2S");
    for (int warmup = 0; warmup < 1000; warmup++) {
      sink += h2sReaction.getK(reactivePitzer.getPhase(1));
    }
    System.out.println(
        "pitzerH2sReactionConstantNs=" + medianBatches(() -> h2sReaction.getK(reactivePitzer.getPhase(1)), 10000));
    for (int warmup = 0; warmup < 5; warmup++) {
      sink += solveReactiveH2sState();
    }
    System.out.println("pitzerH2sCompleteEquilibriumNs="
        + medianBatches(PitzerCatalogPerformanceBenchmark::solveReactiveH2sState, 10));
    SystemPitzer h2sEvidence = solveReactiveH2sSystem(298.15);
    SystemPitzer warmerH2sEvidence = solveReactiveH2sSystem(318.15);
    PhasePitzer h2sPhase = (PhasePitzer) h2sEvidence.getPhase(1);
    System.out.println("pitzerH2sDataset=" + h2sPhase.getParameterDatasetId());
    System.out.println("pitzerH2sNeutralCoverage=" + h2sPhase.auditNeutralPitzerParameterCoverage().formatDiagnostic());
    System.out.println("pitzerH2sHasIons=" + h2sPhase.hasIons());
    System.out.println("pitzerH2sHasNeutralInteractions=" + h2sPhase.hasNeutralPitzerInteractions());
    System.out.println("pitzerH2sRawHenryBar=" + h2sPhase.getComponent("H2S").getHenryCoef(298.15));
    System.out.println("pitzerH2sFugacityCoefficient=" + h2sPhase.getComponent("H2S").getFugacityCoefficient());
    System.out.println("pitzerH2sActivityCoefficient=" + ((ComponentGePitzer) h2sPhase.getComponent("H2S")).getGamma());
    System.out.println("pitzerH2sMaximumReactionResidual="
        + h2sEvidence.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual());
    System.out.println("pitzerH2sMaximumElementResidual="
        + h2sEvidence.getChemicalReactionOperations().getMaximumAbsoluteElementBalanceResidual());
    System.out
        .println("pitzerH2sChargeMoles=" + h2sEvidence.getChemicalReactionOperations().getReactivePhaseChargeMoles());
    System.out.println("pitzerH2sNormalizedChargeResidual="
        + h2sEvidence.getChemicalReactionOperations().getNormalizedReactivePhaseChargeResidual());
    System.out.println(
        "pitzerH2sMolality298K=" + h2sEvidence.getPhase(1).getComponent("HS-").getMolality(h2sEvidence.getPhase(1)));
    System.out.println("pitzerH2sMolality318K="
        + warmerH2sEvidence.getPhase(1).getComponent("HS-").getMolality(warmerH2sEvidence.getPhase(1)));
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

  private static double iapwsHenryKernelChecksum() {
    return IapwsHenryLaw.getHenryCoefficientBar("CH4", 298.15)
        + IapwsHenryLaw.getLnHenryCoefficientTemperatureDerivative("CH4", 298.15);
  }

  private static double qualificationViewChecksum() {
    return ChemistryRunner.run(QUALIFICATION_REQUEST).hashCode();
  }

  private static double scaleEquilibriumViewChecksum() {
    return ChemistryRunner.run(SCALE_EQUILIBRIUM_REQUEST).hashCode();
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

  private static SystemPitzer createReactiveH2sSystem(double temperature) {
    SystemPitzer system = new SystemPitzer(temperature, 1.01325);
    system.addComponent("water", 55.508);
    system.addComponent("H2S", 0.01);
    system.setMultiPhaseCheck(false);
    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    system.init(1);
    return system;
  }

  private static double solveReactiveH2sState() {
    SystemPitzer system = solveReactiveH2sSystem(298.15);
    return system.getPhase(1).getComponent("HS-").getMolality(system.getPhase(1))
        + system.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual();
  }

  private static SystemPitzer solveReactiveH2sSystem(double temperature) {
    SystemPitzer system = createReactiveH2sSystem(temperature);
    if (!system.getChemicalReactionOperations().solveChemEq(1, 0)
        || !system.getChemicalReactionOperations().solveChemEq(1, 1)) {
      throw new IllegalStateException("Reactive H2S equilibrium did not converge");
    }
    system.init(1);
    return system;
  }

  private interface Work {
    double run();
  }
}
