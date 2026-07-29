package neqsim.thermo.util.benchmark;

import java.util.Map;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Point;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Prediction;
import neqsim.thermo.util.benchmark.ThermodynamicBenchmark.Property;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** NeqSim bubble- and dew-point prediction adapter for experimental benchmark points. */
public final class NeqSimPhaseEquilibriumPrediction implements Prediction {
  /** Supported equation-of-state configurations. */
  public enum Model {
    /** Soave-Redlich-Kwong with database interaction parameters. */
    SRK,
    /** Peng-Robinson with database interaction parameters. */
    PR,
    /** Standard GERG-2008. */
    GERG_2008,
    /** GERG-2008-H2 with hydrogen-enhanced binary parameters and departure functions. */
    GERG_2008_H2
  }

  private final Model model;
  private final double hydrogenCarbonDioxideKij;

  /**
   * Creates a phase-equilibrium prediction adapter.
   *
   * @param model equation-of-state configuration
   */
  public NeqSimPhaseEquilibriumPrediction(Model model) {
    this(model, Double.NaN);
  }

  /**
   * Creates a cubic-EOS phase-equilibrium prediction adapter with a custom constant H2-CO2 interaction parameter.
   *
   * @param model cubic equation-of-state configuration
   * @param hydrogenCarbonDioxideKij dimensionless constant H2-CO2 binary interaction parameter
   */
  public NeqSimPhaseEquilibriumPrediction(Model model, double hydrogenCarbonDioxideKij) {
    if (model == null) {
      throw new IllegalArgumentException("Model is required");
    }
    if (Double.isFinite(hydrogenCarbonDioxideKij) && model != Model.SRK && model != Model.PR) {
      throw new IllegalArgumentException("A custom kij is supported only for SRK and PR");
    }
    this.model = model;
    this.hydrogenCarbonDioxideKij = hydrogenCarbonDioxideKij;
  }

  /** @return configured equation-of-state model */
  public Model getModel() {
    return model;
  }

  /** @return configured H2-CO2 interaction parameter, or NaN when database values are used */
  public double getHydrogenCarbonDioxideKij() {
    return hydrogenCarbonDioxideKij;
  }

  /** {@inheritDoc} */
  @Override
  public double predict(Point point) throws Exception {
    if (point.getProperty() != Property.BUBBLE_POINT_PRESSURE && point.getProperty() != Property.DEW_POINT_PRESSURE) {
      throw new IllegalArgumentException("Only bubble- and dew-point pressures are supported");
    }
    SystemInterface system = createSystem(point.getTemperatureK(), point.getPressureBara());
    for (Map.Entry<String, Double> component : point.getComposition().entrySet()) {
      system.addComponent(component.getKey(), component.getValue());
    }
    if (model == Model.SRK || model == Model.PR) {
      system.createDatabase(true);
      system.setMixingRule(2);
      if (Double.isFinite(hydrogenCarbonDioxideKij)) {
        system.setBinaryInteractionParameter("hydrogen", "CO2", hydrogenCarbonDioxideKij);
      }
    }
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    if (point.getProperty() == Property.BUBBLE_POINT_PRESSURE) {
      operations.bubblePointPressureFlash(false);
    } else {
      operations.dewPointPressureFlash();
    }
    double pressureBara = system.getPressure("bara");
    if (!Double.isFinite(pressureBara) || pressureBara <= 0.0) {
      throw new IllegalStateException(
          model + " returned invalid " + point.getProperty() + " at " + point.getTemperatureK() + " K");
    }
    return pressureBara;
  }

  private SystemInterface createSystem(double temperatureK, double pressureBara) {
    if (model == Model.SRK) {
      return new SystemSrkEos(temperatureK, pressureBara);
    }
    if (model == Model.PR) {
      return new SystemPrEos(temperatureK, pressureBara);
    }
    SystemGERG2008Eos system = new SystemGERG2008Eos(temperatureK, pressureBara);
    if (model == Model.GERG_2008_H2) {
      system.useHydrogenEnhancedModel();
    }
    return system;
  }
}
