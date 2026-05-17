package neqsim.process.equipment.distillation;

import java.util.ArrayList;
import java.util.List;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Evaluates scaled MESH residuals for a distillation column state.
 *
 * @author esol
 * @version 1.0
 */
final class ColumnMeshResidualEvaluator {
  /** Minimum composition/fugacity factor used in logarithmic residuals. */
  private static final double MIN_LOG_ARGUMENT = 1.0e-30;

  /** Utility class constructor. */
  private ColumnMeshResidualEvaluator() {}

  /**
   * Evaluate the current MESH residual vector for a column.
   *
   * @param column column to evaluate
   * @return residual vector with equation metadata
   */
  static ColumnMeshResidual evaluate(DistillationColumn column) {
    ColumnMeshState state = ColumnMeshState.from(column);
    ResidualBuilder builder = new ResidualBuilder();
    addMaterialResiduals(state, builder);
    addEquilibriumResiduals(column, state, builder);
    addSummationResiduals(state, builder);
    addEnergyResiduals(column, builder);
    addSpecificationResiduals(column, builder);
    return builder.build();
  }

  /**
   * Add tray component material residuals.
   *
   * @param state column state snapshot
   * @param builder residual builder
   */
  private static void addMaterialResiduals(ColumnMeshState state, ResidualBuilder builder) {
    String[] componentNames = state.getComponentNames();
    for (int tray = 0; tray < state.getTrayCount(); tray++) {
      for (int comp = 0; comp < state.getComponentCount(); comp++) {
        double vaporIn = tray > 0 ? state.getVaporComponentFlow(tray - 1, comp) : 0.0;
        double liquidIn =
            tray < state.getTrayCount() - 1 ? state.getLiquidComponentFlow(tray + 1, comp) : 0.0;
        double feedIn = state.getFeedComponentFlow(tray, comp);
        double vaporOut = state.getVaporComponentFlow(tray, comp);
        double liquidOut = state.getLiquidComponentFlow(tray, comp);
        double inlet = vaporIn + liquidIn + feedIn;
        double outlet = vaporOut + liquidOut;
        double scale =
            Math.max(ColumnMeshState.getMinimumScale(), Math.abs(inlet) + Math.abs(outlet));
        builder.add((outlet - inlet) / scale, ColumnMeshEquationType.MATERIAL, tray,
            componentNames[comp]);
      }
    }
  }

  /**
   * Add phase-equilibrium residuals from tray fugacity equality.
   *
   * @param column column to inspect
   * @param state column state snapshot
   * @param builder residual builder
   */
  private static void addEquilibriumResiduals(DistillationColumn column, ColumnMeshState state,
      ResidualBuilder builder) {
    String[] componentNames = state.getComponentNames();
    for (int trayIndex = 0; trayIndex < state.getTrayCount(); trayIndex++) {
      SimpleTray tray = column.getTray(trayIndex);
      try {
        SystemInterface system = tray.getThermoSystem();
        if (system.getNumberOfPhases() < 2) {
          continue;
        }
        system.init(1);
        PhaseInterface vaporPhase = system.getPhase(0);
        PhaseInterface liquidPhase = system.getPhase(1);
        for (int comp = 0; comp < state.getComponentCount(); comp++) {
          String componentName = componentNames[comp];
          double y = safePhaseFraction(vaporPhase, componentName);
          double x = safePhaseFraction(liquidPhase, componentName);
          double vaporPhi = safeFugacityCoefficient(vaporPhase, componentName);
          double liquidPhi = safeFugacityCoefficient(liquidPhase, componentName);
          double vaporFugacity = Math.max(MIN_LOG_ARGUMENT, y * vaporPhi);
          double liquidFugacity = Math.max(MIN_LOG_ARGUMENT, x * liquidPhi);
          builder.add(Math.log(vaporFugacity / liquidFugacity), ColumnMeshEquationType.EQUILIBRIUM,
              trayIndex, componentName);
        }
      } catch (Exception ex) {
        for (int comp = 0; comp < state.getComponentCount(); comp++) {
          builder.add(0.0, ColumnMeshEquationType.EQUILIBRIUM, trayIndex, componentNames[comp]);
        }
      }
    }
  }

  /**
   * Add vapor and liquid summation residuals.
   *
   * @param state column state snapshot
   * @param builder residual builder
   */
  private static void addSummationResiduals(ColumnMeshState state, ResidualBuilder builder) {
    for (int tray = 0; tray < state.getTrayCount(); tray++) {
      double vaporSum = 0.0;
      double liquidSum = 0.0;
      for (int comp = 0; comp < state.getComponentCount(); comp++) {
        vaporSum += state.getVaporMoleFraction(tray, comp);
        liquidSum += state.getLiquidMoleFraction(tray, comp);
      }
      if (state.getVaporFlow(tray) > ColumnMeshState.getMinimumScale()) {
        builder.add(vaporSum - 1.0, ColumnMeshEquationType.SUMMATION, tray, "vapor");
      }
      if (state.getLiquidFlow(tray) > ColumnMeshState.getMinimumScale()) {
        builder.add(liquidSum - 1.0, ColumnMeshEquationType.SUMMATION, tray, "liquid");
      }
    }
  }

  /**
   * Add tray energy residuals.
   *
   * @param column column to inspect
   * @param builder residual builder
   */
  private static void addEnergyResiduals(DistillationColumn column, ResidualBuilder builder) {
    for (int trayIndex = 0; trayIndex < column.getTrays().size(); trayIndex++) {
      SimpleTray tray = column.getTray(trayIndex);
      try {
        double targetEnthalpy = tray.calcMixStreamEnthalpy();
        SystemInterface traySystem = tray.getThermoSystem();
        traySystem.init(3);
        double outletEnthalpy = traySystem.getEnthalpy();
        double scale = Math.max(1.0, Math.abs(targetEnthalpy) + Math.abs(outletEnthalpy));
        builder.add((outletEnthalpy - targetEnthalpy) / scale, ColumnMeshEquationType.ENERGY,
            trayIndex, null);
      } catch (Exception ex) {
        builder.add(0.0, ColumnMeshEquationType.ENERGY, trayIndex, null);
      }
    }
  }

  /**
   * Add active column specification residuals.
   *
   * @param column column to inspect
   * @param builder residual builder
   */
  private static void addSpecificationResiduals(DistillationColumn column,
      ResidualBuilder builder) {
    column.updateSpecificationResidualDiagnostics();
    if (column.getTopSpecification() != null) {
      addFinite(column.getLastTopSpecificationResidual(), builder, "top");
    }
    if (column.getBottomSpecification() != null) {
      addFinite(column.getLastBottomSpecificationResidual(), builder, "bottom");
    }
  }

  /**
   * Add a specification residual if it is finite.
   *
   * @param value residual value
   * @param builder residual builder
   * @param label residual label
   */
  private static void addFinite(double value, ResidualBuilder builder, String label) {
    if (Double.isFinite(value)) {
      builder.add(value, ColumnMeshEquationType.SPECIFICATION, -1, label);
    }
  }

  /**
   * Get a component mole fraction from a phase.
   *
   * @param phase phase to inspect
   * @param componentName component name
   * @return mole fraction, or zero if unavailable
   */
  private static double safePhaseFraction(PhaseInterface phase, String componentName) {
    try {
      return phase.getComponent(componentName).getx();
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Get a component fugacity coefficient from a phase.
   *
   * @param phase phase to inspect
   * @param componentName component name
   * @return fugacity coefficient, or one if unavailable
   */
  private static double safeFugacityCoefficient(PhaseInterface phase, String componentName) {
    try {
      return Math.max(MIN_LOG_ARGUMENT, phase.getComponent(componentName).getFugacityCoefficient());
    } catch (Exception ex) {
      return 1.0;
    }
  }

  /** Builder for residual vectors and metadata. */
  private static final class ResidualBuilder {
    /** Residual values. */
    private final List<Double> values = new ArrayList<>();
    /** Equation types. */
    private final List<ColumnMeshEquationType> equationTypes = new ArrayList<>();
    /** Tray indices. */
    private final List<Integer> trayIndices = new ArrayList<>();
    /** Component labels. */
    private final List<String> componentNames = new ArrayList<>();

    /**
     * Add one residual entry.
     *
     * @param value residual value
     * @param equationType equation type
     * @param trayIndex tray index, or -1 for column-level equations
     * @param componentName component or equation label
     */
    void add(double value, ColumnMeshEquationType equationType, int trayIndex,
        String componentName) {
      values.add(Double.valueOf(value));
      equationTypes.add(equationType);
      trayIndices.add(Integer.valueOf(trayIndex));
      componentNames.add(componentName);
    }

    /**
     * Build an immutable residual vector.
     *
     * @return residual vector
     */
    ColumnMeshResidual build() {
      double[] residualValues = new double[values.size()];
      ColumnMeshEquationType[] residualTypes = new ColumnMeshEquationType[values.size()];
      int[] residualTrayIndices = new int[values.size()];
      String[] residualComponentNames = new String[values.size()];
      for (int i = 0; i < values.size(); i++) {
        residualValues[i] = values.get(i).doubleValue();
        residualTypes[i] = equationTypes.get(i);
        residualTrayIndices[i] = trayIndices.get(i).intValue();
        residualComponentNames[i] = componentNames.get(i);
      }
      return new ColumnMeshResidual(residualValues, residualTypes, residualTrayIndices,
          residualComponentNames);
    }
  }
}
