package neqsim.process.equipment.manifold;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesign;
import neqsim.process.util.monitor.ManifoldResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * Manifold class.
 * </p>
 * A manifold is a process unit that can take in any number of streams and distribute them into a
 * number of output streams. In NeqSim it is created as a combination of a mixer and a splitter.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class Manifold extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Manifold.class);

  /** Mechanical design for the manifold. */
  private ManifoldMechanicalDesign mechanicalDesign;

  protected Mixer localmixer = new Mixer("tmpName");
  protected Splitter localsplitter = new Splitter("tmpName");

  double[] splitFactors = new double[1];

  /**
   * <p>
   * Constructor for Manifold with name as input.
   * </p>
   *
   * @param name name of manifold
   */
  public Manifold(String name) {
    super(name);
    setName(name);
  }

  /**
   * <p>
   * addStream.
   * </p>
   *
   * @param newStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public void addStream(StreamInterface newStream) {
    localmixer.addStream(newStream);
  }

  /**
   * <p>
   * setSplitFactors.
   * </p>
   *
   * @param splitFact an array of type double
   */
  public void setSplitFactors(double[] splitFact) {
    splitFactors = splitFact;
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.setSplitFactors(splitFactors);
  }

  /**
   * <p>
   * getSplitStream.
   * </p>
   *
   * @param i a int
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getSplitStream(int i) {
    return localsplitter.getSplitStream(i);
  }

  /**
   * <p>
   * getMixedStream.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface}
   */
  public StreamInterface getMixedStream() {
    return localmixer.getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    localmixer.run(id);
    localsplitter.setInletStream(localmixer.getOutletStream());
    localsplitter.run();
  }

  /** {@inheritDoc} */
  @Override
  public void setName(String name) {
    super.setName(name);
    localmixer.setName(name + "local mixer");
    localsplitter.setName(name + "local splitter");
  }

  /**
   * Get the number of output streams from the manifold.
   *
   * @return number of split streams
   */
  public int getNumberOfOutputStreams() {
    return localsplitter.getSplitFactors().length;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = localmixer.getMassBalance(unit)
        + localmixer.getOutletStream().getThermoSystem().getFlowRate(unit);
    double outletFlow = 0.0;
    for (int i = 0; i < getNumberOfOutputStreams(); i++) {
      outletFlow += getSplitStream(i).getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new ManifoldResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    ManifoldResponse res = new ManifoldResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new ManifoldMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public ManifoldMechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }
}
