package neqsim.process.processmodel.lifecycle.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Idempotent in-process event bus suitable for tests, embedded services and transport adapters. */
public final class InMemoryModelChangeEventBus implements ModelChangeEventPublisher {
  private final List<ModelChangeEventSubscriber> subscribers = new CopyOnWriteArrayList<ModelChangeEventSubscriber>();
  private final List<ModelChangeEvent> history = new ArrayList<ModelChangeEvent>();
  private final Map<String, ModelChangeEvent> byIdempotencyKey = new LinkedHashMap<String, ModelChangeEvent>();

  public void subscribe(ModelChangeEventSubscriber subscriber) {
    if (subscriber == null) {
      throw new IllegalArgumentException("subscriber must not be null");
    }
    if (!subscribers.contains(subscriber)) {
      subscribers.add(subscriber);
    }
  }

  public void unsubscribe(ModelChangeEventSubscriber subscriber) {
    subscribers.remove(subscriber);
  }

  @Override
  public ModelChangePublishResult publish(ModelChangeEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("event must not be null");
    }
    synchronized (this) {
      ModelChangeEvent previous = byIdempotencyKey.get(event.getIdempotencyKey());
      if (previous != null) {
        requireSamePayload(previous, event);
        return new ModelChangePublishResult(ModelChangePublishResult.Status.DUPLICATE, previous.getEventId(), 0,
            Collections.<String>emptyList());
      }
      byIdempotencyKey.put(event.getIdempotencyKey(), event);
      history.add(event);
    }
    return deliver(event);
  }

  /** Replays all retained events after the supplied event id, or all events when it is blank. */
  public int replayAfter(String eventId, ModelChangeEventSubscriber subscriber) {
    if (subscriber == null) {
      throw new IllegalArgumentException("subscriber must not be null");
    }
    List<ModelChangeEvent> snapshot;
    synchronized (this) {
      int start = 0;
      if (eventId != null && !eventId.trim().isEmpty()) {
        start = indexOf(eventId) + 1;
      }
      snapshot = new ArrayList<ModelChangeEvent>(history.subList(start, history.size()));
    }
    for (ModelChangeEvent event : snapshot) {
      subscriber.onModelChange(event);
    }
    return snapshot.size();
  }

  public synchronized List<ModelChangeEvent> getHistory() {
    return Collections.unmodifiableList(new ArrayList<ModelChangeEvent>(history));
  }

  private ModelChangePublishResult deliver(ModelChangeEvent event) {
    int deliveryCount = 0;
    List<String> failures = new ArrayList<String>();
    for (ModelChangeEventSubscriber subscriber : subscribers) {
      try {
        subscriber.onModelChange(event);
        deliveryCount++;
      } catch (RuntimeException ex) {
        failures.add(ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
      }
    }
    return new ModelChangePublishResult(ModelChangePublishResult.Status.PUBLISHED, event.getEventId(), deliveryCount,
        failures);
  }

  private int indexOf(String eventId) {
    for (int i = 0; i < history.size(); i++) {
      if (history.get(i).getEventId().equals(eventId)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Replay event id is not retained: " + eventId);
  }

  static void requireSamePayload(ModelChangeEvent previous, ModelChangeEvent event) {
    if (!previous.getPayloadFingerprint().equals(event.getPayloadFingerprint())) {
      throw new IllegalArgumentException("Idempotency key collision for " + event.getIdempotencyKey());
    }
  }
}
