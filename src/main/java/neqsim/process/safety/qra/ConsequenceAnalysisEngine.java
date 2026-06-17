package neqsim.process.safety.qra;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.safety.dispersion.GaussianPlume;
import neqsim.process.safety.dispersion.ProbitModel;
import neqsim.process.safety.fire.JetFireModel;
import neqsim.process.safety.fire.PoolFireModel;
import neqsim.process.safety.fire.VCEModel;
import neqsim.process.safety.risk.eta.EventTreeAnalyzer;

/**
 * High-level orchestrator for Quantitative Risk Analysis (QRA) of a single release scenario.
 *
 * <p>
 * Composes existing pieces — release source term, ignition event tree, fire / explosion models,
 * dispersion model and probit fatality calculation — and produces individual / societal risk
 * metrics for one scenario.
 *
 * <p>
 * Workflow:
 * <ol>
 * <li>Define initiating release (rate, duration, frequency)</li>
 * <li>Add an ignition event tree (immediate / delayed / no ignition)</li>
 * <li>Attach consequence models (jet, pool, VCE, dispersion+probit)</li>
 * <li>Call {@link #evaluate(double)} to get outcome frequencies and individual fatality risk per
 * outcome at a target receiver distance</li>
 * </ol>
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>CCPS — Guidelines for Chemical Process Quantitative Risk Analysis, 2nd Ed.</li>
 * <li>NORSOK Z-013 — Risk and emergency preparedness assessment</li>
 * <li>UK HSE R2P2 — Reducing risks, protecting people</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ConsequenceAnalysisEngine implements Serializable {
  private static final long serialVersionUID = 1L;

  private final String scenarioName;
  private final double releaseFrequencyPerYear;
  private final List<OutcomeBranch> outcomes = new ArrayList<>();

  /**
   * Construct a consequence-analysis scenario.
   *
   * @param scenarioName scenario identifier (e.g. "10 mm gas leak from V-100")
   * @param releaseFrequencyPerYear initiating release frequency in 1/year
   */
  public ConsequenceAnalysisEngine(String scenarioName, double releaseFrequencyPerYear) {
    this.scenarioName = scenarioName;
    this.releaseFrequencyPerYear = releaseFrequencyPerYear;
  }

  /**
   * Add an outcome branch with its conditional probability and a consequence calculator.
   *
   * @param outcomeName e.g. "Jet fire", "VCE", "Toxic dispersion"
   * @param conditionalProbability probability conditional on the release (e.g. immediate ignition
   *        probability)
   * @param consequence consequence model implementing {@link Consequence}
   * @return this engine for chaining
   */
  public ConsequenceAnalysisEngine addOutcome(String outcomeName, double conditionalProbability,
      Consequence consequence) {
    if (conditionalProbability < 0.0 || conditionalProbability > 1.0) {
      throw new IllegalArgumentException("probability must be in [0,1]");
    }
    outcomes.add(new OutcomeBranch(outcomeName, conditionalProbability, consequence));
    return this;
  }

  /**
   * Convenience: add a jet-fire outcome.
   *
   * @param probability conditional probability (e.g. immediate ignition)
   * @param model jet fire model already configured with mass flow and ΔHc
   * @param probit thermal probit (use {@link ProbitModel#thermalFatality()})
   * @param exposureSeconds receiver exposure time in s
   * @return this engine
   */
  public ConsequenceAnalysisEngine addJetFire(double probability, final JetFireModel model,
      final ProbitModel probit, final double exposureSeconds) {
    return addOutcome("Jet fire", probability, new Consequence() {
      @Override
      public double fatalityProbabilityAt(double distanceM) {
        double I = model.incidentHeatFlux(distanceM);
        double dose = exposureSeconds * Math.pow(I, 4.0 / 3.0) / 1.0e4;
        return probit.probabilityFromDose(dose);
      }
    });
  }

  /**
   * Convenience: add a pool-fire outcome.
   *
   * @param probability conditional probability
   * @param model pool fire model
   * @param probit thermal probit
   * @param exposureSeconds receiver exposure time in s
   * @return this engine
   */
  public ConsequenceAnalysisEngine addPoolFire(double probability, final PoolFireModel model,
      final ProbitModel probit, final double exposureSeconds) {
    return addOutcome("Pool fire", probability, new Consequence() {
      @Override
      public double fatalityProbabilityAt(double distanceM) {
        double I = model.incidentHeatFlux(distanceM);
        double dose = exposureSeconds * Math.pow(I, 4.0 / 3.0) / 1.0e4;
        return probit.probabilityFromDose(dose);
      }
    });
  }

  /**
   * Convenience: add a VCE outcome with blast-lung probit.
   *
   * @param probability conditional probability (delayed ignition)
   * @param model VCE model
   * @param probit blast probit (use {@link ProbitModel#blastLungFatality()})
   * @return this engine
   */
  public ConsequenceAnalysisEngine addVCE(double probability, final VCEModel model,
      final ProbitModel probit) {
    return addOutcome("VCE", probability, new Consequence() {
      @Override
      public double fatalityProbabilityAt(double distanceM) {
        return probit.probabilityFromDose(model.overpressurePa(distanceM));
      }
    });
  }

  /**
   * Convenience: add a toxic dispersion outcome (Gaussian plume + probit).
   *
   * @param probability conditional probability (no ignition)
   * @param plume Gaussian plume model
   * @param probit toxic probit (e.g. {@link ProbitModel#h2sFatality()})
   * @param molarMassKgPerMol toxic component MW
   * @param tempK plume temperature in K
   * @param pressureBara plume pressure in bara
   * @param exposureMinutes exposure duration in min
   * @return this engine
   */
  public ConsequenceAnalysisEngine addToxicDispersion(double probability,
      final GaussianPlume plume, final ProbitModel probit, final double molarMassKgPerMol,
      final double tempK, final double pressureBara, final double exposureMinutes) {
    return addOutcome("Toxic dispersion", probability, new Consequence() {
      @Override
      public double fatalityProbabilityAt(double distanceM) {
        double cKgPerM3 = plume.centerlineGroundConcentration(distanceM);
        // Convert kg/m³ → ppm (mole basis) using ideal gas
        double R = 8.314;
        double pPa = pressureBara * 1.0e5;
        double cMolPerM3 = cKgPerM3 / molarMassKgPerMol;
        double cPpm = cMolPerM3 * R * tempK / pPa * 1.0e6;
        double dose = Math.pow(cPpm, 1.0) * exposureMinutes; // probit absorbs the n
        return probit.probabilityFromDose(dose);
      }
    });
  }

  /**
   * Evaluate all outcomes at one receiver distance and return one record per outcome with
   * frequency × fatality probability (= individual fatality frequency contribution at that point).
   *
   * @param receiverDistanceM distance from release to receiver in m
   * @return list of outcome records
   */
  public List<OutcomeResult> evaluate(double receiverDistanceM) {
    List<OutcomeResult> out = new ArrayList<>();
    for (OutcomeBranch b : outcomes) {
      double freq = releaseFrequencyPerYear * b.probability;
      double pFat = b.consequence.fatalityProbabilityAt(receiverDistanceM);
      out.add(new OutcomeResult(b.name, freq, pFat, freq * pFat));
    }
    return out;
  }

  /**
   * Sum individual fatality frequency over all outcomes at the receiver distance.
   *
   * @param receiverDistanceM distance from release to receiver in m
   * @return total individual fatality risk per year
   */
  public double individualFatalityRiskPerYear(double receiverDistanceM) {
    double total = 0.0;
    for (OutcomeResult r : evaluate(receiverDistanceM)) {
      total += r.fatalityFrequencyPerYear;
    }
    return total;
  }

  /**
   * Build an event tree representation of the scenario for use with {@link EventTreeAnalyzer}.
   *
   * @return the event tree (one branch per outcome)
   */
  public EventTreeAnalyzer toEventTree() {
    EventTreeAnalyzer et = new EventTreeAnalyzer(scenarioName, releaseFrequencyPerYear);
    for (OutcomeBranch b : outcomes) {
      et.addBranch(b.name, b.probability);
    }
    return et;
  }

  /**
   * Build a multi-line text report.
   *
   * @param receiverDistanceM receiver distance in m
   * @return human-readable report
   */
  public String report(double receiverDistanceM) {
    StringBuilder sb = new StringBuilder();
    sb.append("QRA scenario: ").append(scenarioName).append('\n');
    sb.append(String.format("Release frequency: %.4e /yr%n", releaseFrequencyPerYear));
    sb.append(String.format("Receiver distance : %.1f m%n", receiverDistanceM));
    sb.append("---------------------------------------------------\n");
    double tot = 0.0;
    for (OutcomeResult r : evaluate(receiverDistanceM)) {
      sb.append(String.format("%-22s F=%.4e /yr  P_fatality=%.4f  IFR=%.4e /yr%n",
          r.outcomeName, r.outcomeFrequencyPerYear, r.fatalityProbability,
          r.fatalityFrequencyPerYear));
      tot += r.fatalityFrequencyPerYear;
    }
    sb.append(String.format("Total individual fatality risk: %.4e /yr%n", tot));
    return sb.toString();
  }

  /**
   * Plug-in interface for an arbitrary consequence model: distance → fatality probability.
   */
  public interface Consequence extends Serializable {
    /**
     * @param distanceM receiver distance in m
     * @return fatality probability in [0, 1]
     */
    double fatalityProbabilityAt(double distanceM);
  }

  /** Internal branch container. */
  private static class OutcomeBranch implements Serializable {
    private static final long serialVersionUID = 1L;
    final String name;
    final double probability;
    final Consequence consequence;

    OutcomeBranch(String name, double probability, Consequence consequence) {
      this.name = name;
      this.probability = probability;
      this.consequence = consequence;
    }
  }

  /** Result for one outcome branch at a receiver distance. */
  public static class OutcomeResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Outcome label. */
    public final String outcomeName;
    /** Outcome frequency (release × conditional probability) in 1/year. */
    public final double outcomeFrequencyPerYear;
    /** Conditional fatality probability at receiver. */
    public final double fatalityProbability;
    /** Individual fatality frequency contribution from this outcome in 1/year. */
    public final double fatalityFrequencyPerYear;

    OutcomeResult(String outcomeName, double outcomeFrequencyPerYear,
        double fatalityProbability, double fatalityFrequencyPerYear) {
      this.outcomeName = outcomeName;
      this.outcomeFrequencyPerYear = outcomeFrequencyPerYear;
      this.fatalityProbability = fatalityProbability;
      this.fatalityFrequencyPerYear = fatalityFrequencyPerYear;
    }
  }
}
