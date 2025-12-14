package neqsim.process.util.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Event bus for publishing and subscribing to process events.
 *
 * <p>
 * Provides a publish-subscribe mechanism for process simulation events, enabling loose coupling
 * between simulation components and external systems (like AI optimization platforms).
 * </p>
 *
 * <p>
 * Supports:
 * </p>
 * <ul>
 * <li>Type-based filtering</li>
 * <li>Severity-based filtering</li>
 * <li>Synchronous and asynchronous event delivery</li>
 * <li>Event history for late subscribers</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessEventBus implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static ProcessEventBus instance;

  private final List<ProcessEventListener> globalListeners;
  private final Map<ProcessEvent.EventType, List<ProcessEventListener>> typeListeners;
  private final List<ProcessEvent> eventHistory;
  private int maxHistorySize = 1000;
  private boolean asyncDelivery = false;
  private transient ExecutorService executor;

  /**
   * Gets the singleton event bus instance.
   *
   * @return event bus instance
   */
  public static synchronized ProcessEventBus getInstance() {
    if (instance == null) {
      instance = new ProcessEventBus();
    }
    return instance;
  }

  /**
   * Creates a new event bus (for non-singleton use).
   */
  public ProcessEventBus() {
    this.globalListeners = new CopyOnWriteArrayList<>();
    this.typeListeners = new EnumMap<>(ProcessEvent.EventType.class);
    for (ProcessEvent.EventType type : ProcessEvent.EventType.values()) {
      typeListeners.put(type, new CopyOnWriteArrayList<>());
    }
    this.eventHistory = new ArrayList<>();
  }

  /**
   * Subscribes to all events.
   *
   * @param listener event listener
   */
  public void subscribe(ProcessEventListener listener) {
    if (listener != null && !globalListeners.contains(listener)) {
      globalListeners.add(listener);
    }
  }

  /**
   * Subscribes to events of a specific type.
   *
   * @param type event type
   * @param listener event listener
   */
  public void subscribe(ProcessEvent.EventType type, ProcessEventListener listener) {
    if (listener != null) {
      List<ProcessEventListener> listeners = typeListeners.get(type);
      if (!listeners.contains(listener)) {
        listeners.add(listener);
      }
    }
  }

  /**
   * Unsubscribes from all events.
   *
   * @param listener listener to remove
   */
  public void unsubscribe(ProcessEventListener listener) {
    globalListeners.remove(listener);
    for (List<ProcessEventListener> listeners : typeListeners.values()) {
      listeners.remove(listener);
    }
  }

  /**
   * Unsubscribes from a specific event type.
   *
   * @param type event type
   * @param listener listener to remove
   */
  public void unsubscribe(ProcessEvent.EventType type, ProcessEventListener listener) {
    typeListeners.get(type).remove(listener);
  }

  /**
   * Publishes an event to all subscribers.
   *
   * @param event event to publish
   */
  public void publish(ProcessEvent event) {
    if (event == null) {
      return;
    }

    // Add to history
    synchronized (eventHistory) {
      eventHistory.add(event);
      while (eventHistory.size() > maxHistorySize) {
        eventHistory.remove(0);
      }
    }

    // Deliver to listeners
    if (asyncDelivery) {
      deliverAsync(event);
    } else {
      deliverSync(event);
    }
  }

  /**
   * Publishes an info event.
   *
   * @param source event source
   * @param description description
   */
  public void publishInfo(String source, String description) {
    publish(ProcessEvent.info(source, description));
  }

  /**
   * Publishes a warning event.
   *
   * @param source event source
   * @param description description
   */
  public void publishWarning(String source, String description) {
    publish(ProcessEvent.warning(source, description));
  }

  /**
   * Publishes an alarm event.
   *
   * @param source event source
   * @param description description
   */
  public void publishAlarm(String source, String description) {
    publish(ProcessEvent.alarm(source, description));
  }

  private void deliverSync(ProcessEvent event) {
    // Global listeners
    for (ProcessEventListener listener : globalListeners) {
      try {
        listener.onEvent(event);
      } catch (Exception e) {
        // Log but don't propagate
      }
    }

    // Type-specific listeners
    List<ProcessEventListener> specific = typeListeners.get(event.getType());
    for (ProcessEventListener listener : specific) {
      try {
        listener.onEvent(event);
      } catch (Exception e) {
        // Log but don't propagate
      }
    }
  }

  private void deliverAsync(ProcessEvent event) {
    if (executor == null || executor.isShutdown()) {
      executor = Executors.newSingleThreadExecutor();
    }

    executor.submit(() -> deliverSync(event));
  }

  /**
   * Enables or disables asynchronous delivery.
   *
   * @param async true for async delivery
   */
  public void setAsyncDelivery(boolean async) {
    this.asyncDelivery = async;
  }

  /**
   * Sets the maximum history size.
   *
   * @param size maximum events to retain
   */
  public void setMaxHistorySize(int size) {
    this.maxHistorySize = size;
  }

  /**
   * Gets recent events from history.
   *
   * @param count number of events to retrieve
   * @return list of recent events
   */
  public List<ProcessEvent> getRecentEvents(int count) {
    synchronized (eventHistory) {
      int start = Math.max(0, eventHistory.size() - count);
      return new ArrayList<>(eventHistory.subList(start, eventHistory.size()));
    }
  }

  /**
   * Gets events by type from history.
   *
   * @param type event type
   * @param count maximum events to retrieve
   * @return list of events
   */
  public List<ProcessEvent> getEventsByType(ProcessEvent.EventType type, int count) {
    List<ProcessEvent> result = new ArrayList<>();
    synchronized (eventHistory) {
      for (int i = eventHistory.size() - 1; i >= 0 && result.size() < count; i--) {
        ProcessEvent event = eventHistory.get(i);
        if (event.getType() == type) {
          result.add(0, event);
        }
      }
    }
    return result;
  }

  /**
   * Gets events by severity from history.
   *
   * @param minSeverity minimum severity level
   * @param count maximum events to retrieve
   * @return list of events
   */
  public List<ProcessEvent> getEventsBySeverity(ProcessEvent.Severity minSeverity, int count) {
    List<ProcessEvent> result = new ArrayList<>();
    synchronized (eventHistory) {
      for (int i = eventHistory.size() - 1; i >= 0 && result.size() < count; i--) {
        ProcessEvent event = eventHistory.get(i);
        if (event.getSeverity().ordinal() >= minSeverity.ordinal()) {
          result.add(0, event);
        }
      }
    }
    return result;
  }

  /**
   * Clears event history.
   */
  public void clearHistory() {
    synchronized (eventHistory) {
      eventHistory.clear();
    }
  }

  /**
   * Gets the total event count in history.
   *
   * @return event count
   */
  public int getHistorySize() {
    synchronized (eventHistory) {
      return eventHistory.size();
    }
  }

  /**
   * Shuts down the event bus.
   */
  public void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
    globalListeners.clear();
    for (List<ProcessEventListener> listeners : typeListeners.values()) {
      listeners.clear();
    }
  }

  /**
   * Resets the singleton instance (for testing).
   */
  public static synchronized void resetInstance() {
    if (instance != null) {
      instance.shutdown();
      instance = null;
    }
  }
}
