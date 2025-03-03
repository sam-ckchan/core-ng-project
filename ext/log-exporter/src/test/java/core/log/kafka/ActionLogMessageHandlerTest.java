package core.log.kafka;

import core.framework.inject.Inject;
import core.framework.kafka.Message;
import core.framework.log.message.ActionLogMessage;
import core.framework.log.message.PerformanceStatMessage;
import core.framework.util.Files;
import core.log.IntegrationTest;
import core.log.domain.ActionLogSchema;
import core.log.service.ArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @author neo
 */
class ActionLogMessageHandlerTest extends IntegrationTest {
    @Inject
    ActionLogSchema schema;
    private ActionLogMessageHandler handler;

    @BeforeEach
    void createActionLogMessageHandler() {
        handler = new ActionLogMessageHandler();
        handler.archiveService = new ArchiveService();
        handler.archiveService.logDir = Files.tempDir();
        handler.schema = schema;
    }

    @AfterEach
    void cleanup() {
        Files.deleteDir(handler.archiveService.logDir);
    }

    @Test
    void handle() throws IOException {
        var message = new ActionLogMessage();
        message.date = Instant.parse("2022-11-07T00:00:00Z");
        message.id = "id";
        message.app = "app";
        message.action = "action";
        message.result = "OK";
        message.host = "host";
        message.elapsed = 1000L;
        message.context = Map.of("key", List.of("value1", "value2"));
        message.performanceStats = Map.of("kafka", perfStats(1, 1000L, 10, 5),
            "http", perfStats(1, 2000L, null, null));
        message.traceLog = "trace";
        handler.handle(List.of(new Message<>("key", message)));
        handler.handle(List.of(new Message<>("key", message)));
    }

    private PerformanceStatMessage perfStats(int count, long totalElapsed, Integer readEntries, Integer writeEntries) {
        final PerformanceStatMessage stats = new PerformanceStatMessage();
        stats.count = count;
        stats.totalElapsed = totalElapsed;
        stats.readEntries = readEntries;
        stats.writeEntries = writeEntries;
        return stats;
    }
}
