package neqsim.process.processmodel.lifecycle.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of an idempotent model-change publication. */
public final class ModelChangePublishResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  public enum Status {
    PUBLISHED, DUPLICATE
  }

  private final Status status;
  private final String eventId;
  private final int deliveryCount;
  private final List<String> deliveryFailures;

  ModelChangePublishResult(Status status, String eventId, int deliveryCount, List<String> deliveryFailures) {
    this.status = status;
    this.eventId = eventId;
    this.deliveryCount = deliveryCount;
    this.deliveryFailures = new ArrayList<String>(deliveryFailures);
  }

  public Status getStatus() {
    return status;
  }

  public String getEventId() {
    return eventId;
  }

  public int getDeliveryCount() {
    return deliveryCount;
  }

  public List<String> getDeliveryFailures() {
    return Collections.unmodifiableList(deliveryFailures);
  }
}
