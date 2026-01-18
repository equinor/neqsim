package neqsim.process.design;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Class representing process basis (design basis) for a process template.
 *
 * <p>
 * ProcessBasis encapsulates all the input data needed to configure a process template, including:
 * </p>
 * <ul>
 * <li>Feed stream conditions (composition, flow rate, P, T)</li>
 * <li>Product specifications (outlet pressures, temperatures)</li>
 * <li>Environmental conditions (ambient temperature, cooling medium)</li>
 * <li>Design constraints and safety factors</li>
 * <li>Company standards and TR documents</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ProcessBasis basis = ProcessBasis.builder().setFeedFluid(gasCondensate)
 *     .setFeedFlowRate(100000.0, "kg/hr").setFeedPressure(100.0, "bara")
 *     .setFeedTemperature(80.0, "C").addStagePressure(1, 70.0, "bara")
 *     .addStagePressure(2, 20.0, "bara").addStagePressure(3, 2.0, "bara")
 *     .setCompanyStandard("Equinor", "TR2000").setSafetyFactor(1.2).build();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessBasis {

  private SystemInterface feedFluid;
  private StreamInterface feedStream;
  private double feedFlowRate; // kg/hr
  private double feedPressure; // bara
  private double feedTemperature; // Kelvin

  private Map<Integer, Double> stagePressures = new HashMap<>(); // Stage -> bara
  private Map<Integer, Double> stageTemperatures = new HashMap<>(); // Stage -> K

  private double ambientTemperature = 288.15; // 15°C default
  private double coolingMediumTemperature = 288.15; // 15°C default

  private String companyStandard;
  private String trDocument;
  private double safetyFactor = 1.2;

  private List<ProductSpecification> productSpecs = new ArrayList<>();
  private Map<String, Double> constraints = new HashMap<>();
  private Map<String, Double> parameters = new HashMap<>();
  private Map<String, String> stringParameters = new HashMap<>();

  /**
   * Default constructor - for direct instantiation.
   */
  public ProcessBasis() {}

  /**
   * Create a new builder for ProcessBasis.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get the feed fluid.
   *
   * @return feed fluid
   */
  public SystemInterface getFeedFluid() {
    return feedFluid;
  }

  /**
   * Get the feed stream. If not explicitly set, creates one from feed fluid.
   *
   * @return feed stream
   */
  public StreamInterface getFeedStream() {
    if (feedStream == null && feedFluid != null) {
      Stream stream = new Stream("Feed", feedFluid);
      stream.setFlowRate(feedFlowRate, "kg/hr");
      feedStream = stream;
    }
    return feedStream;
  }

  /**
   * Get the feed flow rate in kg/hr.
   *
   * @return feed flow rate
   */
  public double getFeedFlowRate() {
    return feedFlowRate;
  }

  /**
   * Get the feed pressure in bara.
   *
   * @return feed pressure
   */
  public double getFeedPressure() {
    return feedPressure;
  }

  /**
   * Get the feed temperature in Kelvin.
   *
   * @return feed temperature
   */
  public double getFeedTemperature() {
    return feedTemperature;
  }

  /**
   * Get the pressure for a specific stage.
   *
   * @param stage stage number (1-based)
   * @return pressure in bara, or null if not set
   */
  public Double getStagePressure(int stage) {
    return stagePressures.get(stage);
  }

  /**
   * Get all stage pressures.
   *
   * @return map of stage number to pressure
   */
  public Map<Integer, Double> getStagePressures() {
    return new HashMap<>(stagePressures);
  }

  /**
   * Get the number of stages defined.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return stagePressures.size();
  }

  /**
   * Get the ambient temperature in Kelvin.
   *
   * @return ambient temperature
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Get the cooling medium temperature in Kelvin.
   *
   * @return cooling medium temperature
   */
  public double getCoolingMediumTemperature() {
    return coolingMediumTemperature;
  }

  /**
   * Get the company standard.
   *
   * @return company standard
   */
  public String getCompanyStandard() {
    return companyStandard;
  }

  /**
   * Get the TR document reference.
   *
   * @return TR document
   */
  public String getTRDocument() {
    return trDocument;
  }

  /**
   * Get the safety factor.
   *
   * @return safety factor
   */
  public double getSafetyFactor() {
    return safetyFactor;
  }

  /**
   * Get product specifications.
   *
   * @return list of product specifications
   */
  public List<ProductSpecification> getProductSpecs() {
    return new ArrayList<>(productSpecs);
  }

  /**
   * Get a constraint value by name.
   *
   * @param name constraint name
   * @return constraint value or null if not set
   */
  public Double getConstraint(String name) {
    return constraints.get(name);
  }

  /**
   * Get a parameter value by name with default.
   *
   * @param name parameter name
   * @param defaultValue default value if not set
   * @return parameter value or default
   */
  public double getParameter(String name, double defaultValue) {
    Double value = parameters.get(name);
    return value != null ? value : defaultValue;
  }

  /**
   * Set a parameter value.
   *
   * @param name parameter name
   * @param value parameter value
   */
  public void setParameter(String name, double value) {
    parameters.put(name, value);
  }

  /**
   * Get a string parameter value by name with default.
   *
   * @param name parameter name
   * @param defaultValue default value if not set
   * @return parameter value or default
   */
  public String getParameterString(String name, String defaultValue) {
    String value = stringParameters.get(name);
    return value != null ? value : defaultValue;
  }

  /**
   * Set a string parameter value.
   *
   * @param name parameter name
   * @param value parameter value
   */
  public void setParameterString(String name, String value) {
    stringParameters.put(name, value);
  }

  /**
   * Set the feed fluid directly (convenience method).
   *
   * @param fluid the fluid
   */
  public void setFeedFluid(SystemInterface fluid) {
    this.feedFluid = fluid;
  }

  /**
   * Set the feed pressure directly (convenience method).
   *
   * @param pressure pressure in bara
   */
  public void setFeedPressure(double pressure) {
    this.feedPressure = pressure;
  }

  /**
   * Set the feed temperature directly (convenience method).
   *
   * @param temperature temperature in Kelvin
   */
  public void setFeedTemperature(double temperature) {
    this.feedTemperature = temperature;
  }

  /**
   * Set the feed flow rate directly (convenience method).
   *
   * @param flowRate flow rate in kg/hr
   */
  public void setFeedFlowRate(double flowRate) {
    this.feedFlowRate = flowRate;
  }

  /**
   * Specification for a product stream.
   */
  public static class ProductSpecification {
    private String productName;
    private String type; // "gas", "oil", "water", "condensate"
    private Double targetPressure;
    private Double targetTemperature;
    private Double minFlowRate;
    private Double maxFlowRate;

    /**
     * Create a product specification.
     *
     * @param productName name of the product stream
     * @param type type of product
     */
    public ProductSpecification(String productName, String type) {
      this.productName = productName;
      this.type = type;
    }

    /**
     * Get the product name.
     *
     * @return product name
     */
    public String getProductName() {
      return productName;
    }

    /**
     * Get the product type.
     *
     * @return product type
     */
    public String getType() {
      return type;
    }

    /**
     * Get the target pressure.
     *
     * @return target pressure in bara
     */
    public Double getTargetPressure() {
      return targetPressure;
    }

    /**
     * Set the target pressure.
     *
     * @param pressure pressure in bara
     */
    public void setTargetPressure(double pressure) {
      this.targetPressure = pressure;
    }

    /**
     * Get the target temperature.
     *
     * @return target temperature in K
     */
    public Double getTargetTemperature() {
      return targetTemperature;
    }

    /**
     * Set the target temperature.
     *
     * @param temperature temperature in K
     */
    public void setTargetTemperature(double temperature) {
      this.targetTemperature = temperature;
    }
  }

  /**
   * Builder class for ProcessBasis.
   */
  public static class Builder {
    private ProcessBasis basis = new ProcessBasis();

    /**
     * Set the feed fluid.
     *
     * @param fluid the fluid
     * @return this builder
     */
    public Builder setFeedFluid(SystemInterface fluid) {
      basis.feedFluid = fluid;
      return this;
    }

    /**
     * Set the feed stream directly.
     *
     * @param stream the feed stream
     * @return this builder
     */
    public Builder setFeedStream(StreamInterface stream) {
      basis.feedStream = stream;
      return this;
    }

    /**
     * Set the feed flow rate.
     *
     * @param flowRate flow rate value
     * @param unit unit ("kg/hr", "Sm3/day", etc.)
     * @return this builder
     */
    public Builder setFeedFlowRate(double flowRate, String unit) {
      // Store as kg/hr
      if ("Sm3/day".equalsIgnoreCase(unit)) {
        // Approximation - actual conversion depends on fluid
        basis.feedFlowRate = flowRate * 0.8; // Rough estimate
      } else if ("MSm3/day".equalsIgnoreCase(unit)) {
        basis.feedFlowRate = flowRate * 800000.0; // Rough estimate
      } else {
        basis.feedFlowRate = flowRate; // Assume kg/hr
      }
      return this;
    }

    /**
     * Set the feed pressure.
     *
     * @param pressure pressure value
     * @param unit unit ("bara", "barg", "psia")
     * @return this builder
     */
    public Builder setFeedPressure(double pressure, String unit) {
      if ("barg".equalsIgnoreCase(unit)) {
        basis.feedPressure = pressure + 1.01325;
      } else if ("psia".equalsIgnoreCase(unit)) {
        basis.feedPressure = pressure / 14.5038;
      } else {
        basis.feedPressure = pressure; // Assume bara
      }
      return this;
    }

    /**
     * Set the feed temperature.
     *
     * @param temperature temperature value
     * @param unit unit ("K", "C", "F")
     * @return this builder
     */
    public Builder setFeedTemperature(double temperature, String unit) {
      if ("C".equalsIgnoreCase(unit)) {
        basis.feedTemperature = temperature + 273.15;
      } else if ("F".equalsIgnoreCase(unit)) {
        basis.feedTemperature = (temperature - 32) * 5 / 9 + 273.15;
      } else {
        basis.feedTemperature = temperature; // Assume Kelvin
      }
      return this;
    }

    /**
     * Add a stage pressure.
     *
     * @param stage stage number (1-based)
     * @param pressure pressure value
     * @param unit unit ("bara", "barg")
     * @return this builder
     */
    public Builder addStagePressure(int stage, double pressure, String unit) {
      double pressureBara = pressure;
      if ("barg".equalsIgnoreCase(unit)) {
        pressureBara = pressure + 1.01325;
      }
      basis.stagePressures.put(stage, pressureBara);
      return this;
    }

    /**
     * Set the company standard and TR document.
     *
     * @param company company name
     * @param trDoc TR document reference
     * @return this builder
     */
    public Builder setCompanyStandard(String company, String trDoc) {
      basis.companyStandard = company;
      basis.trDocument = trDoc;
      return this;
    }

    /**
     * Set the safety factor.
     *
     * @param factor safety factor (typically 1.1-1.3)
     * @return this builder
     */
    public Builder setSafetyFactor(double factor) {
      basis.safetyFactor = factor;
      return this;
    }

    /**
     * Set the ambient temperature.
     *
     * @param temperature temperature value
     * @param unit unit ("K", "C")
     * @return this builder
     */
    public Builder setAmbientTemperature(double temperature, String unit) {
      if ("C".equalsIgnoreCase(unit)) {
        basis.ambientTemperature = temperature + 273.15;
      } else {
        basis.ambientTemperature = temperature;
      }
      return this;
    }

    /**
     * Add a product specification.
     *
     * @param productName name of the product
     * @param type product type ("gas", "oil", etc.)
     * @return this builder
     */
    public Builder addProductSpec(String productName, String type) {
      basis.productSpecs.add(new ProductSpecification(productName, type));
      return this;
    }

    /**
     * Add a constraint.
     *
     * @param name constraint name
     * @param value constraint value
     * @return this builder
     */
    public Builder addConstraint(String name, double value) {
      basis.constraints.put(name, value);
      return this;
    }

    /**
     * Build the ProcessBasis.
     *
     * @return the configured ProcessBasis
     */
    public ProcessBasis build() {
      return basis;
    }
  }
}
