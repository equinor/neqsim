package neqsim.process.equipment.splitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.monitor.ComponentSplitterResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * Splitter class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ComponentSplitter extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ComponentSplitter.class);

  SystemInterface thermoSystem;
  StreamInterface inletStream;
  StreamInterface[] splitStream;
  protected int splitNumber = 1;
  double[] splitFactor = new double[1];
  /**
   * Basis on which {@link #splitFactor} is interpreted: {@code "molar"} (default) or {@code "mass"}. Mirrors the
   * UniSim/HYSYS Component Splitter "Split Basis". For a per-component "fraction of feed to overhead" specification the
   * molar and mass bases yield the same mole split (a single component's mass fraction equals its mole fraction), but
   * the basis is retained for API fidelity and correct handling if a mass-basis specification is supplied.
   */
  protected String splitBasis = "molar";

  /**
   * Constructor for Splitter.
   *
   * @param name name of splitter
   */
  public ComponentSplitter(String name) {
    super(name);
  }

  /**
   * Constructor for Splitter.
   *
   * @param name a {@link java.lang.String} object
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public ComponentSplitter(String name, StreamInterface inletStream) {
    this(name);
    this.setInletStream(inletStream);
  }

  /**
   * Getter for the field <code>inletStream</code>.
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * setSplitFactors.
   *
   * @param factors an array of type double
   */
  public void setSplitFactors(double[] factors) {
    splitFactor = factors;
  }

  /**
   * Set the per-component split factors together with the basis on which they are expressed.
   *
   * @param factors fraction of each component's feed routed to the first (overhead) split stream
   * @param basis {@code "molar"} or {@code "mass"} (case-insensitive); any other value defaults to molar
   */
  public void setSplitFactors(double[] factors, String basis) {
    splitFactor = factors;
    setSplitBasis(basis);
  }

  /**
   * Setter for the field <code>splitBasis</code>.
   *
   * @param basis {@code "molar"} or {@code "mass"} (case-insensitive); any other value defaults to molar
   */
  public void setSplitBasis(String basis) {
    if (basis != null && "mass".equalsIgnoreCase(basis.trim())) {
      this.splitBasis = "mass";
    } else {
      this.splitBasis = "molar";
    }
  }

  /**
   * Getter for the field <code>splitBasis</code>.
   *
   * @return the split basis, {@code "molar"} or {@code "mass"}
   */
  public String getSplitBasis() {
    return splitBasis;
  }

  /**
   * Getter for the field <code>splitFactor</code>.
   *
   * @return an array of type double with the split factors
   */
  public double[] getSplitFactors() {
    return splitFactor;
  }

  /**
   * Setter for the field <code>inletStream</code>.
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    splitStream = new Stream[2];
    try {
      for (int i = 0; i < splitStream.length; i++) {
        // todo: why not inletStream.clone("Split Stream_" + i)
        splitStream[i] = new Stream("Split Stream_" + i, inletStream.getThermoSystem().clone());
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }

  /**
   * Getter for the field <code>splitStream</code>.
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSplitStream(int i) {
    return splitStream[i];
  }

  /**
   * Getter for the field <code>splitNumber</code>.
   *
   * @return number of split outlets
   */
  public int getSplitNumber() {
    return splitStream != null ? splitStream.length : splitNumber;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    if (inletStream != null) {
      return Collections.singletonList(inletStream);
    }
    return Collections.emptyList();
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    if (splitStream != null) {
      return Collections.unmodifiableList(Arrays.asList(splitStream));
    }
    return Collections.emptyList();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    boolean massBasis = "mass".equalsIgnoreCase(splitBasis);
    for (int i = 0; i < 2; i++) {
      thermoSystem = inletStream.getThermoSystem().clone();
      thermoSystem.setEmptyFluid();
      double totalMoles = 0.0;
      for (int k = 0; k < thermoSystem.getNumberOfComponents(); k++) {
        double feedMoles = inletStream.getThermoSystem().getComponent(k).getNumberOfmoles();
        // Fraction of this component's feed routed to split stream 0 (overhead);
        // stream 1 (bottoms) receives the remainder.
        double fractionToOverhead = splitFactor[k];
        double fraction = (i == 0) ? fractionToOverhead : (1.0 - fractionToOverhead);
        double moles;
        if (massBasis) {
          // Mass basis: the split factor is the fraction of this component's
          // MASS routed to the stream. For a single component the mole split
          // equals the mass split (mass = moles * molarMass, and molarMass
          // cancels), so the result matches the molar basis while honouring a
          // mass-basis specification.
          double molarMass = inletStream.getThermoSystem().getComponent(k).getMolarMass();
          double feedMass = feedMoles * molarMass;
          double massToStream = feedMass * fraction;
          moles = (molarMass > 0.0) ? massToStream / molarMass : feedMoles * fraction;
        } else {
          moles = feedMoles * fraction;
        }
        thermoSystem.addComponent(k, moles);
        totalMoles += moles;
      }

      if (totalMoles > 0.0) {
        thermoSystem.init(0);
        splitStream[i].setThermoSystem(thermoSystem);
        ThermodynamicOperations thermoOps = new ThermodynamicOperations(splitStream[i].getThermoSystem());
        thermoOps.TPflash();
      } else {
        splitStream[i].setThermoSystem(thermoSystem);
      }
    }
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = inletStream.getThermoSystem().getFlowRate(unit);
    double outletFlow = 0.0;
    for (int i = 0; i < splitStream.length; i++) {
      outletFlow += splitStream[i].getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(new ComponentSplitterResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ComponentSplitterResponse res = new ComponentSplitterResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
  }
}
