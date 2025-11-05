package neqsim.process.equipment.valve;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;

/**
 * <p>
 * SafetyValve class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public class SafetyValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private double pressureSpec = 10.0;
  private double fullOpenPressure = 10.0;
  private final List<RelievingScenario> relievingScenarios = new ArrayList<>();
  private String activeScenarioName;

  /**
   * Constructor for SafetyValve.
   *
   * @param name name of valve
   */
  public SafetyValve(String name) {
    super(name);
  }

  /**
   * <p>
   * Constructor for SafetyValve.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.Stream} object
   */
  public SafetyValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  /**
   * Adds a relieving scenario definition to the valve.
   *
   * @param scenario the scenario to add
   * @return the current valve instance for chaining
   */
  public SafetyValve addScenario(RelievingScenario scenario) {
    if (scenario == null) {
      return this;
    }
    relievingScenarios.add(scenario);
    if (activeScenarioName == null) {
      activeScenarioName = scenario.getName();
    }
    return this;
  }

  /**
   * Replace the relieving scenarios with the provided collection.
   *
   * @param scenarios scenarios to store on the valve
   */
  public void setRelievingScenarios(List<RelievingScenario> scenarios) {
    relievingScenarios.clear();
    activeScenarioName = null;
    if (scenarios != null) {
      scenarios.forEach(this::addScenario);
    }
  }

  /**
   * Removes all relieving scenarios from the valve.
   */
  public void clearRelievingScenarios() {
    relievingScenarios.clear();
    activeScenarioName = null;
  }

  /**
   * @return immutable view of configured scenarios
   */
  public List<RelievingScenario> getRelievingScenarios() {
    return Collections.unmodifiableList(relievingScenarios);
  }

  /**
   * Sets the active relieving scenario by name. If the scenario does not exist the active
   * definition is not changed.
   *
   * @param name scenario identifier
   */
  public void setActiveScenario(String name) {
    if (name == null) {
      return;
    }
    boolean exists = relievingScenarios.stream().anyMatch(s -> s.getName().equals(name));
    if (exists) {
      activeScenarioName = name;
    }
  }

  /**
   * @return the name of the active scenario, if any
   */
  public Optional<String> getActiveScenarioName() {
    return Optional.ofNullable(activeScenarioName);
  }

  /**
   * Returns the active relieving scenario or {@code Optional.empty()} if no scenario is active.
   *
   * @return optional containing the active scenario
   */
  public Optional<RelievingScenario> getActiveScenario() {
    if (activeScenarioName == null) {
      return Optional.empty();
    }
    return relievingScenarios.stream().filter(s -> s.getName().equals(activeScenarioName))
        .findFirst();
  }

  /**
   * Ensures that at least one scenario exists by creating a default vapor relieving scenario when
   * no user supplied definitions are present.
   */
  public void ensureDefaultScenario() {
    if (!relievingScenarios.isEmpty()) {
      return;
    }
    RelievingScenario scenario = new RelievingScenario.Builder("default")
        .fluidService(FluidService.GAS).relievingStream(getInletStream()).setPressure(pressureSpec)
        .overpressureFraction(0.1).backPressure(0.0).build();
    addScenario(scenario);
  }

  /**
   * <p>
   * Getter for the field <code>pressureSpec</code>.
   * </p>
   *
   * @return the pressureSpec
   */
  public double getPressureSpec() {
    return pressureSpec;
  }

  /**
   * <p>
   * Setter for the field <code>pressureSpec</code>.
   * </p>
   *
   * @param pressureSpec the pressureSpec to set
   */
  public void setPressureSpec(double pressureSpec) {
    this.pressureSpec = pressureSpec;
  }

  /**
   * <p>
   * Getter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @return the fullOpenPressure
   */
  public double getFullOpenPressure() {
    return fullOpenPressure;
  }

  /**
   * <p>
   * Setter for the field <code>fullOpenPressure</code>.
   * </p>
   *
   * @param fullOpenPressure the fullOpenPressure to set
   */
  public void setFullOpenPressure(double fullOpenPressure) {
    this.fullOpenPressure = fullOpenPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt, java.util.UUID id) {
    // Automatically adjust valve opening based on inlet pressure
    if (!getCalculateSteadyState()) {
      double inletPressure = getInletStream().getPressure("bara");
      double opening;

      if (inletPressure < pressureSpec) {
        // PSV closed below set pressure
        opening = 0.0;
      } else if (inletPressure >= fullOpenPressure) {
        // PSV fully open at or above full open pressure
        opening = 100.0;
      } else {
        // PSV opening proportional to pressure between set and full open
        opening = 100.0 * (inletPressure - pressureSpec) / (fullOpenPressure - pressureSpec);
      }

      // Set the calculated opening
      setPercentValveOpening(opening);
    }

    // Call parent runTransient to perform the actual valve calculations
    super.runTransient(dt, id);
  }

  /** Supported fluid service categories used for selecting the sizing strategy. */
  public enum FluidService {
    GAS, LIQUID, MULTIPHASE, FIRE
  }

  /** Available sizing standards for the relieving calculations. */
  public enum SizingStandard {
    API_520, ISO_4126
  }

  /** Immutable description of a relieving scenario. */
  public static final class RelievingScenario implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final FluidService fluidService;
    private final StreamInterface relievingStream;
    private final Double setPressure;
    private final double overpressureFraction;
    private final double backPressure;
    private final SizingStandard sizingStandard;
    private final Double dischargeCoefficient;
    private final Double backPressureCorrection;
    private final Double installationCorrection;

    private RelievingScenario(Builder builder) {
      this.name = builder.name;
      this.fluidService = builder.fluidService;
      this.relievingStream = builder.relievingStream;
      this.setPressure = builder.setPressure;
      this.overpressureFraction = builder.overpressureFraction;
      this.backPressure = builder.backPressure;
      this.sizingStandard = builder.sizingStandard;
      this.dischargeCoefficient = builder.dischargeCoefficient;
      this.backPressureCorrection = builder.backPressureCorrection;
      this.installationCorrection = builder.installationCorrection;
    }

    public String getName() {
      return name;
    }

    public FluidService getFluidService() {
      return fluidService;
    }

    public StreamInterface getRelievingStream() {
      return relievingStream;
    }

    public Optional<Double> getSetPressure() {
      return Optional.ofNullable(setPressure);
    }

    public double getOverpressureFraction() {
      return overpressureFraction;
    }

    public double getBackPressure() {
      return backPressure;
    }

    public SizingStandard getSizingStandard() {
      return sizingStandard;
    }

    public Optional<Double> getDischargeCoefficient() {
      return Optional.ofNullable(dischargeCoefficient);
    }

    public Optional<Double> getBackPressureCorrection() {
      return Optional.ofNullable(backPressureCorrection);
    }

    public Optional<Double> getInstallationCorrection() {
      return Optional.ofNullable(installationCorrection);
    }

    /** Builder for {@link RelievingScenario}. */
    public static final class Builder {
      private final String name;
      private FluidService fluidService = FluidService.GAS;
      private StreamInterface relievingStream;
      private Double setPressure;
      private double overpressureFraction = 0.1;
      private double backPressure = 0.0;
      private SizingStandard sizingStandard = SizingStandard.API_520;
      private Double dischargeCoefficient;
      private Double backPressureCorrection;
      private Double installationCorrection;

      public Builder(String name) {
        this.name = name;
      }

      public Builder fluidService(FluidService fluidService) {
        if (fluidService != null) {
          this.fluidService = fluidService;
        }
        return this;
      }

      public Builder relievingStream(StreamInterface relievingStream) {
        this.relievingStream = relievingStream;
        return this;
      }

      public Builder setPressure(double setPressure) {
        this.setPressure = setPressure;
        return this;
      }

      public Builder overpressureFraction(double overpressureFraction) {
        this.overpressureFraction = overpressureFraction;
        return this;
      }

      public Builder backPressure(double backPressure) {
        this.backPressure = backPressure;
        return this;
      }

      public Builder sizingStandard(SizingStandard sizingStandard) {
        if (sizingStandard != null) {
          this.sizingStandard = sizingStandard;
        }
        return this;
      }

      public Builder dischargeCoefficient(double dischargeCoefficient) {
        this.dischargeCoefficient = dischargeCoefficient;
        return this;
      }

      public Builder backPressureCorrection(double backPressureCorrection) {
        this.backPressureCorrection = backPressureCorrection;
        return this;
      }

      public Builder installationCorrection(double installationCorrection) {
        this.installationCorrection = installationCorrection;
        return this;
      }

      public RelievingScenario build() {
        return new RelievingScenario(this);
      }
    }
  }
}
