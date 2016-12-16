package core.framework.api.kafka;

/**
 * @author neo
 */
@FunctionalInterface
public interface MessageHandler<T> {
    void handle(String key, T value) throws Exception;
}
