package neqsim.process.processmodel.lifecycle.event;

/** Consumer boundary for governed model-change notifications. */
@FunctionalInterface
public interface ModelChangeEventSubscriber {
  void onModelChange(ModelChangeEvent event);
}
