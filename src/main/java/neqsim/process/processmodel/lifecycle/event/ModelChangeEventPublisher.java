package neqsim.process.processmodel.lifecycle.event;

/** Transport-neutral boundary implemented by local journals and external event-bus adapters. */
public interface ModelChangeEventPublisher {
  ModelChangePublishResult publish(ModelChangeEvent event);
}
