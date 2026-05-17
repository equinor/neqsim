package neqsim.process.equipment.distillation;

import java.io.Serializable;
import java.util.List;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Snapshot of tray variables used by distillation MESH residual diagnostics.
 *
 * @author esol
 * @version 1.0
 */
final class ColumnMeshState implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Small positive value used to avoid division by zero. */
  private static final double MIN_SCALE = 1.0e-30;
  /** Component names included in the state vector. */
  private final String[] componentNames;
  /** Tray temperatures in Kelvin. */
  private final double[] trayTemperatures;
  /** Vapor molar flow per tray in mol/hr. */
  private final double[] vaporFlowsMolHr;
  /** Liquid molar flow per tray in mol/hr. */
  private final double[] liquidFlowsMolHr;
  /** Vapor component molar flow per tray and component in mol/hr. */
  private final double[][] vaporComponentFlowsMolHr;
  /** Liquid component molar flow per tray and component in mol/hr. */
  private final double[][] liquidComponentFlowsMolHr;
  /** Feed component molar flow per tray and component in mol/hr. */
  private final double[][] feedComponentFlowsMolHr;
  /** Vapor mole fractions per tray and component. */
  private final double[][] vaporMoleFractions;
  /** Liquid mole fractions per tray and component. */
  private final double[][] liquidMoleFractions;

  /**
   * Create a state snapshot.
   *
   * @param componentNames component names included in the state
   * @param trayTemperatures tray temperatures in Kelvin
   * @param vaporFlowsMolHr vapor molar flows in mol/hr
   * @param liquidFlowsMolHr liquid molar flows in mol/hr
   * @param vaporComponentFlowsMolHr vapor component flows in mol/hr
   * @param liquidComponentFlowsMolHr liquid component flows in mol/hr
   * @param feedComponentFlowsMolHr feed component flows in mol/hr
   * @param vaporMoleFractions vapor mole fractions
   * @param liquidMoleFractions liquid mole fractions
   */
  private ColumnMeshState(String[] componentNames, double[] trayTemperatures,
      double[] vaporFlowsMolHr, double[] liquidFlowsMolHr, double[][] vaporComponentFlowsMolHr,
      double[][] liquidComponentFlowsMolHr, double[][] feedComponentFlowsMolHr,
      double[][] vaporMoleFractions, double[][] liquidMoleFractions) {
    this.componentNames = componentNames.clone();
    this.trayTemperatures = trayTemperatures.clone();
    this.vaporFlowsMolHr = vaporFlowsMolHr.clone();
    this.liquidFlowsMolHr = liquidFlowsMolHr.clone();
    this.vaporComponentFlowsMolHr = copy(vaporComponentFlowsMolHr);
    this.liquidComponentFlowsMolHr = copy(liquidComponentFlowsMolHr);
    this.feedComponentFlowsMolHr = copy(feedComponentFlowsMolHr);
    this.vaporMoleFractions = copy(vaporMoleFractions);
    this.liquidMoleFractions = copy(liquidMoleFractions);
  }

  /**
   * Build a state snapshot from a distillation column.
   *
   * @param column the column to inspect
   * @return state snapshot for residual evaluation
   */
  static ColumnMeshState from(DistillationColumn column) {
    String[] componentNames = resolveComponentNames(column);
    int trayCount = column.getTrays().size();
    int componentCount = componentNames.length;
    double[] trayTemperatures = new double[trayCount];
    double[] vaporFlows = new double[trayCount];
    double[] liquidFlows = new double[trayCount];
    double[][] vaporComponentFlows = new double[trayCount][componentCount];
    double[][] liquidComponentFlows = new double[trayCount][componentCount];
    double[][] feedComponentFlows = new double[trayCount][componentCount];
    double[][] vaporFractions = new double[trayCount][componentCount];
    double[][] liquidFractions = new double[trayCount][componentCount];

    for (int trayIndex = 0; trayIndex < trayCount; trayIndex++) {
      SimpleTray tray = column.getTray(trayIndex);
      trayTemperatures[trayIndex] = tray.getTemperature();
      StreamInterface vapor = tray.getGasOutStream();
      StreamInterface liquid = tray.getLiquidOutStream();
      vaporFlows[trayIndex] = flowRate(vapor, "mol/hr");
      liquidFlows[trayIndex] = flowRate(liquid, "mol/hr");
      for (int compIndex = 0; compIndex < componentCount; compIndex++) {
        String componentName = componentNames[compIndex];
        vaporComponentFlows[trayIndex][compIndex] = componentFlow(vapor, componentName);
        liquidComponentFlows[trayIndex][compIndex] = componentFlow(liquid, componentName);
        vaporFractions[trayIndex][compIndex] = componentFraction(vapor, componentName);
        liquidFractions[trayIndex][compIndex] = componentFraction(liquid, componentName);
      }
      List<StreamInterface> feeds = column.getFeedStreams(trayIndex);
      for (StreamInterface feed : feeds) {
        for (int compIndex = 0; compIndex < componentCount; compIndex++) {
          feedComponentFlows[trayIndex][compIndex] +=
              componentFlow(feed, componentNames[compIndex]);
        }
      }
    }

    return new ColumnMeshState(componentNames, trayTemperatures, vaporFlows, liquidFlows,
        vaporComponentFlows, liquidComponentFlows, feedComponentFlows, vaporFractions,
        liquidFractions);
  }

  /**
   * Resolve component names from available column products and tray states.
   *
   * @param column column to inspect
   * @return component names
   */
  private static String[] resolveComponentNames(DistillationColumn column) {
    String[] productNames = componentNames(column.getGasOutStream());
    if (productNames.length > 0) {
      return productNames;
    }
    productNames = componentNames(column.getLiquidOutStream());
    if (productNames.length > 0) {
      return productNames;
    }
    for (SimpleTray tray : column.getTrays()) {
      String[] trayNames = componentNames(tray.getGasOutStream());
      if (trayNames.length > 0) {
        return trayNames;
      }
      trayNames = componentNames(tray.getLiquidOutStream());
      if (trayNames.length > 0) {
        return trayNames;
      }
    }
    return new String[0];
  }

  /**
   * Get component names from a stream.
   *
   * @param stream stream to inspect
   * @return component names or an empty array
   */
  private static String[] componentNames(StreamInterface stream) {
    try {
      SystemInterface system = stream.getThermoSystem();
      if (system == null || system.getNumberOfComponents() == 0) {
        return new String[0];
      }
      return system.getCompNames();
    } catch (Exception ex) {
      return new String[0];
    }
  }

  /**
   * Get a total stream flow rate.
   *
   * @param stream stream to inspect
   * @param unit unit for flow
   * @return flow rate, or zero if unavailable
   */
  private static double flowRate(StreamInterface stream, String unit) {
    try {
      return stream.getFlowRate(unit);
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get a component flow from a stream.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component molar flow in mol/hr, or zero if unavailable
   */
  private static double componentFlow(StreamInterface stream, String componentName) {
    try {
      return stream.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get a component mole fraction from a stream.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component mole fraction, or zero if unavailable
   */
  private static double componentFraction(StreamInterface stream, String componentName) {
    try {
      SystemInterface system = stream.getThermoSystem();
      system.init(0);
      return system.getComponent(componentName).getz();
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Copy a two-dimensional double array.
   *
   * @param values array to copy
   * @return copied array
   */
  private static double[][] copy(double[][] values) {
    double[][] copied = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      copied[i] = values[i].clone();
    }
    return copied;
  }

  /**
   * Get tray count.
   *
   * @return number of trays
   */
  int getTrayCount() {
    return trayTemperatures.length;
  }

  /**
   * Get component count.
   *
   * @return number of components
   */
  int getComponentCount() {
    return componentNames.length;
  }

  /**
   * Get component names.
   *
   * @return component names
   */
  String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Get tray temperatures.
   *
   * @return tray temperatures in Kelvin
   */
  double[] getTrayTemperatures() {
    return trayTemperatures.clone();
  }

  /**
   * Get vapor component flow.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return molar component flow in mol/hr
   */
  double getVaporComponentFlow(int trayIndex, int componentIndex) {
    return vaporComponentFlowsMolHr[trayIndex][componentIndex];
  }

  /**
   * Get liquid component flow.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return molar component flow in mol/hr
   */
  double getLiquidComponentFlow(int trayIndex, int componentIndex) {
    return liquidComponentFlowsMolHr[trayIndex][componentIndex];
  }

  /**
   * Get feed component flow.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return molar component flow in mol/hr
   */
  double getFeedComponentFlow(int trayIndex, int componentIndex) {
    return feedComponentFlowsMolHr[trayIndex][componentIndex];
  }

  /**
   * Get vapor mole fraction.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return vapor mole fraction
   */
  double getVaporMoleFraction(int trayIndex, int componentIndex) {
    return vaporMoleFractions[trayIndex][componentIndex];
  }

  /**
   * Get liquid mole fraction.
   *
   * @param trayIndex tray index
   * @param componentIndex component index
   * @return liquid mole fraction
   */
  double getLiquidMoleFraction(int trayIndex, int componentIndex) {
    return liquidMoleFractions[trayIndex][componentIndex];
  }

  /**
   * Get vapor flow rate.
   *
   * @param trayIndex tray index
   * @return vapor flow in mol/hr
   */
  double getVaporFlow(int trayIndex) {
    return Math.max(0.0, vaporFlowsMolHr[trayIndex]);
  }

  /**
   * Get liquid flow rate.
   *
   * @param trayIndex tray index
   * @return liquid flow in mol/hr
   */
  double getLiquidFlow(int trayIndex) {
    return Math.max(0.0, liquidFlowsMolHr[trayIndex]);
  }

  /**
   * Get the minimum scale used in residual normalization.
   *
   * @return small positive scale
   */
  static double getMinimumScale() {
    return MIN_SCALE;
  }
}
