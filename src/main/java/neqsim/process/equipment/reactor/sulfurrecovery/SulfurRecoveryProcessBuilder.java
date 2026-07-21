package neqsim.process.equipment.reactor.sulfurrecovery;

import neqsim.process.equipment.stream.StreamInterface;

/** Fluent builder for a configured {@link SulfurRecoveryUnit}. */
public final class SulfurRecoveryProcessBuilder {
  private final String name;
  private final StreamInterface acidGasFeed;
  private SulfurRecoveryUnit.Configuration configuration =
      SulfurRecoveryUnit.Configuration.STRAIGHT_THROUGH;
  private int catalyticStages = 2;
  private boolean tailGasTreatment;
  private boolean incinerator = true;
  private double oxygenMoleFraction = 0.21;
  private double targetH2SToSO2Ratio = 2.0;
  private double splitFlowFurnaceFraction = 0.35;

  /** Create a builder for a named SRU and acid-gas feed. */
  public SulfurRecoveryProcessBuilder(String name, StreamInterface acidGasFeed) {
    if (acidGasFeed == null) {
      throw new IllegalArgumentException("acidGasFeed cannot be null");
    }
    this.name = name;
    this.acidGasFeed = acidGasFeed;
  }

  /** Select process configuration. */
  public SulfurRecoveryProcessBuilder configuration(
      SulfurRecoveryUnit.Configuration configuration) {
    if (configuration == null) {
      throw new IllegalArgumentException("configuration cannot be null");
    }
    this.configuration = configuration;
    if (configuration == SulfurRecoveryUnit.Configuration.OXYGEN_ENRICHED
        && oxygenMoleFraction <= 0.21) {
      oxygenMoleFraction = 0.35;
    }
    return this;
  }

  /** Select one to three catalytic stages. */
  public SulfurRecoveryProcessBuilder catalyticStages(int stages) {
    catalyticStages = stages;
    return this;
  }

  /** Enable or disable tail-gas treatment with acid-gas recycle. */
  public SulfurRecoveryProcessBuilder tailGasTreatment(boolean enabled) {
    tailGasTreatment = enabled;
    return this;
  }

  /** Enable or disable thermal incineration. */
  public SulfurRecoveryProcessBuilder incinerator(boolean enabled) {
    incinerator = enabled;
    return this;
  }

  /** Set oxidant oxygen mole fraction. */
  public SulfurRecoveryProcessBuilder oxidantOxygenMoleFraction(double fraction) {
    oxygenMoleFraction = fraction;
    return this;
  }

  /** Set the final Claus H2S/SO2 ratio target. */
  public SulfurRecoveryProcessBuilder targetH2SToSO2Ratio(double ratio) {
    targetH2SToSO2Ratio = ratio;
    return this;
  }

  /** Set split-flow furnace fraction. */
  public SulfurRecoveryProcessBuilder splitFlowFurnaceFraction(double fraction) {
    splitFlowFurnaceFraction = fraction;
    return this;
  }

  /** Build the configured integrated unit without running it. */
  public SulfurRecoveryUnit build() {
    SulfurRecoveryUnit unit = new SulfurRecoveryUnit(name, acidGasFeed);
    unit.setConfiguration(configuration);
    unit.setNumberOfCatalyticStages(catalyticStages);
    unit.setTailGasTreatmentEnabled(tailGasTreatment);
    unit.setIncineratorEnabled(incinerator);
    unit.setOxidantOxygenMoleFraction(oxygenMoleFraction);
    unit.setTargetH2SToSO2Ratio(targetH2SToSO2Ratio);
    unit.setSplitFlowFurnaceFraction(splitFlowFurnaceFraction);
    return unit;
  }
}
