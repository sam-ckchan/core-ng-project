package core.framework.redis;

import java.util.List;

/**
 * @author rexthk
 */
public interface RedisList {
    String pop(String key);

    void push(String key, String... values);

    default List<String> range(String key) {
        return range(key, 0, -1);
    }

    List<String> range(String key, int start, int end);
}
