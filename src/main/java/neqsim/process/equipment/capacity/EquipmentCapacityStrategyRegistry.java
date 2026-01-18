package neqsim.process.equipment.capacity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Registry for equipment capacity strategies.
 *
 * <p>
 * This registry maintains a collection of {@link EquipmentCapacityStrategy} implementations and
 * provides lookup functionality to find the appropriate strategy for any given equipment. The
 * registry uses a priority-based selection when multiple strategies support the same equipment.
 * </p>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * // Get the singleton instance
 * EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
 * 
 * // Register a custom strategy
 * registry.register(new MyCustomCompressorStrategy());
 * 
 * // Find strategy for equipment
 * EquipmentCapacityStrategy strategy = registry.findStrategy(myCompressor);
 * if (strategy != null) {
 *   double utilization = strategy.evaluateCapacity(myCompressor);
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EquipmentCapacityStrategy
 */
public class EquipmentCapacityStrategyRegistry {

  /** Singleton instance. */
  private static volatile EquipmentCapacityStrategyRegistry instance;

  /** Registered strategies. */
  private final List<EquipmentCapacityStrategy> strategies;

  /** Cache for equipment class to strategy mapping. */
  private final Map<Class<?>, EquipmentCapacityStrategy> strategyCache;

  /**
   * Private constructor for singleton pattern.
   */
  private EquipmentCapacityStrategyRegistry() {
    this.strategies = new ArrayList<>();
    this.strategyCache = new ConcurrentHashMap<>();
    registerDefaultStrategies();
  }

  /**
   * Gets the singleton instance of the registry.
   *
   * @return the registry instance
   */
  public static EquipmentCapacityStrategyRegistry getInstance() {
    if (instance == null) {
      synchronized (EquipmentCapacityStrategyRegistry.class) {
        if (instance == null) {
          instance = new EquipmentCapacityStrategyRegistry();
        }
      }
    }
    return instance;
  }

  /**
   * Registers the default strategies for common equipment types.
   */
  private void registerDefaultStrategies() {
    register(new CompressorCapacityStrategy());
    register(new SeparatorCapacityStrategy());
    register(new PipeCapacityStrategy());
    register(new ValveCapacityStrategy());
    register(new HeatExchangerCapacityStrategy());
    register(new PumpCapacityStrategy());
  }

  /**
   * Registers a capacity strategy.
   *
   * <p>
   * If a strategy with the same name already exists, it will be replaced.
   * </p>
   *
   * @param strategy the strategy to register
   */
  public void register(EquipmentCapacityStrategy strategy) {
    if (strategy == null) {
      return;
    }

    synchronized (strategies) {
      // Remove any existing strategy with the same name
      strategies.removeIf(s -> s.getName().equals(strategy.getName()));
      strategies.add(strategy);
      // Sort by priority (highest first)
      Collections.sort(strategies,
          Comparator.comparingInt(EquipmentCapacityStrategy::getPriority).reversed());
      // Clear cache when strategies change
      strategyCache.clear();
    }
  }

  /**
   * Unregisters a capacity strategy by name.
   *
   * @param strategyName the name of the strategy to remove
   * @return true if a strategy was removed
   */
  public boolean unregister(String strategyName) {
    synchronized (strategies) {
      boolean removed = strategies.removeIf(s -> s.getName().equals(strategyName));
      if (removed) {
        strategyCache.clear();
      }
      return removed;
    }
  }

  /**
   * Finds the best strategy for the given equipment.
   *
   * <p>
   * Searches through registered strategies in priority order and returns the first one that
   * supports the equipment.
   * </p>
   *
   * @param equipment the equipment to find a strategy for
   * @return the best matching strategy, or null if none found
   */
  public EquipmentCapacityStrategy findStrategy(ProcessEquipmentInterface equipment) {
    if (equipment == null) {
      return null;
    }

    // Check cache first
    Class<?> equipmentClass = equipment.getClass();
    EquipmentCapacityStrategy cached = strategyCache.get(equipmentClass);
    if (cached != null) {
      return cached;
    }

    // Find matching strategy
    synchronized (strategies) {
      for (EquipmentCapacityStrategy strategy : strategies) {
        if (strategy.supports(equipment)) {
          strategyCache.put(equipmentClass, strategy);
          return strategy;
        }
      }
    }

    return null;
  }

  /**
   * Gets all registered strategies.
   *
   * @return unmodifiable list of strategies
   */
  public List<EquipmentCapacityStrategy> getAllStrategies() {
    synchronized (strategies) {
      return Collections.unmodifiableList(new ArrayList<>(strategies));
    }
  }

  /**
   * Gets the number of registered strategies.
   *
   * @return strategy count
   */
  public int getStrategyCount() {
    return strategies.size();
  }

  /**
   * Clears all registered strategies and resets to defaults.
   */
  public void reset() {
    synchronized (strategies) {
      strategies.clear();
      strategyCache.clear();
      registerDefaultStrategies();
    }
  }

  /**
   * Evaluates capacity for equipment using the appropriate strategy.
   *
   * <p>
   * Convenience method that finds the strategy and evaluates capacity in one call.
   * </p>
   *
   * @param equipment the equipment to evaluate
   * @return capacity utilization, or -1 if no strategy found
   */
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    EquipmentCapacityStrategy strategy = findStrategy(equipment);
    if (strategy == null) {
      return -1.0;
    }
    return strategy.evaluateCapacity(equipment);
  }

  /**
   * Gets all constraints for equipment using the appropriate strategy.
   *
   * @param equipment the equipment to get constraints for
   * @return map of constraints, or empty map if no strategy found
   */
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    EquipmentCapacityStrategy strategy = findStrategy(equipment);
    if (strategy == null) {
      return Collections.emptyMap();
    }
    return strategy.getConstraints(equipment);
  }

  /**
   * Checks if equipment is within all hard limits using the appropriate strategy.
   *
   * @param equipment the equipment to check
   * @return true if within limits or no strategy found
   */
  public boolean isWithinHardLimits(ProcessEquipmentInterface equipment) {
    EquipmentCapacityStrategy strategy = findStrategy(equipment);
    if (strategy == null) {
      return true; // No strategy means no constraints
    }
    return strategy.isWithinHardLimits(equipment);
  }
}
