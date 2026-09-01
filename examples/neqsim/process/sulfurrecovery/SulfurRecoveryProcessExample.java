package neqsim.process.sulfurrecovery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryPerformance;
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryProcessBuilder;
import neqsim.process.equipment.reactor.sulfurrecovery.SulfurRecoveryUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Complete straight-through Claus SRU with tail-gas treatment and incineration. */
public final class SulfurRecoveryProcessExample {
  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(SulfurRecoveryProcessExample.class);

  /** Utility example; do not instantiate. */
  private SulfurRecoveryProcessExample() {}

  /** Run the sulfur-recovery example. */
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(313.15, 2.0);
    fluid.addComponent("H2S", 10.0);
    fluid.addComponent("CO2", 2.0);
    fluid.addComponent("methane", 0.05);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream acidGas = new Stream("acid gas", fluid);
    acidGas.run();
    SulfurRecoveryUnit sru = new SulfurRecoveryProcessBuilder("SRU", acidGas)
        .configuration(SulfurRecoveryUnit.Configuration.STRAIGHT_THROUGH)
        .catalyticStages(2)
        .tailGasTreatment(true)
        .incinerator(true)
        .build();
    sru.run();

    SulfurRecoveryPerformance result = sru.getPerformance();
    logger.info("Sulfur product: {} kg/h", result.getRecoveredSulfurKgPerHour());
    logger.info("Overall recovery: {} %", result.getOverallSulfurRecoveryPercent());
    logger.info("Tail H2S/SO2 ratio: {}", result.getTailGasH2SToSO2Ratio());
    logger.info("Stack SO2: {} kg/h", result.getStackSO2KgPerHour());
    logger.info("Relative sulfur balance error: {}", result.getSulfurBalanceRelativeError());
  }
}
