package core.framework.internal.web.sse;

import core.framework.internal.async.VirtualThread;
import core.framework.internal.log.ActionLog;
import core.framework.internal.log.LogManager;
import core.framework.internal.web.HTTPHandlerContext;
import core.framework.internal.web.request.RequestImpl;
import core.framework.internal.web.session.ReadOnlySession;
import core.framework.internal.web.session.SessionManager;
import core.framework.module.ServerSentEventConfig;
import core.framework.util.Strings;
import core.framework.web.exception.NotFoundException;
import core.framework.web.sse.ServerSentEventListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ServerSentEventHandler {
    private static final HttpString LAST_EVENT_ID = new HttpString("Last-Event-ID");
    private final Logger logger = LoggerFactory.getLogger(ServerSentEventHandler.class);
    private final LogManager logManager;
    private final SessionManager sessionManager;
    private final HTTPHandlerContext handlerContext;
    private final Map<String, ServerSentEventListenerHolder<?>> holders = new HashMap<>();

    public ServerSentEventHandler(LogManager logManager, SessionManager sessionManager, HTTPHandlerContext handlerContext) {
        this.logManager = logManager;
        this.sessionManager = sessionManager;
        this.handlerContext = handlerContext;
    }

    public boolean check(HttpString method, HeaderMap headers) {
        return Methods.GET.equals(method) && "text/event-stream".equals(headers.getFirst(Headers.ACCEPT));
    }

    public void handle(HttpServerExchange exchange) throws IOException {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream; charset=UTF-8");
        exchange.setPersistent(false);
        StreamSinkChannel sink = exchange.getResponseChannel();
        if (sink.flush()) {
            exchange.dispatch(() -> handle(exchange, sink));
        } else {
            ChannelListener<StreamSinkChannel> listener = ChannelListeners.flushingChannelListener(channel -> {
                    exchange.dispatch(() -> handle(exchange, sink));
                },
                (channel, e) -> {
                    logger.warn("failed to establish sse connection, error={}", e.getMessage(), e);
                    IoUtils.safeClose(exchange.getConnection());
                });
            sink.getWriteSetter().set(listener);
            sink.resumeWrites();
        }
    }

    @SuppressWarnings("PMD.ExceptionAsFlowControl")
    void handle(HttpServerExchange exchange, StreamSinkChannel sink) {
        VirtualThread.COUNT.increase();
        long httpDelay = System.nanoTime() - exchange.getRequestStartTime();
        ActionLog actionLog = logManager.begin("=== sse connect begin ===", null);
        var request = new RequestImpl(exchange, handlerContext.requestBeanReader);
        try {
            logger.debug("httpDelay={}", httpDelay);
            actionLog.stats.put("http_delay", (double) httpDelay);

            handlerContext.requestParser.parse(request, exchange, actionLog);
            if (handlerContext.accessControl != null) handlerContext.accessControl.validate(request.clientIP());  // check ip before checking routing, return 403 asap

            String path = request.path();
            @SuppressWarnings("unchecked")
            ServerSentEventListenerHolder<Object> holder = (ServerSentEventListenerHolder<Object>) holders.get(path);
            if (holder == null) throw new NotFoundException("not found, path=" + path, "PATH_NOT_FOUND");

            actionLog.action("sse:" + path + ":open");
            handlerContext.rateControl.validateRate(ServerSentEventConfig.SSE_CONNECT_GROUP, request.clientIP());

            var channel = new ServerSentEventChannelImpl<>(exchange, sink, holder.context, holder.eventBuilder);
            actionLog.context("channel", channel.id);
            sink.getWriteSetter().set(channel.writeListener);
            holder.context.add(channel);
            exchange.addExchangeCompleteListener(new ServerSentEventChannelCloseHandler<>(logManager, channel, holder.context, actionLog.id));

            channel.send(Strings.bytes("retry:15000\n\n"));

            request.session = ReadOnlySession.of(sessionManager.load(request, actionLog));
            String lastEventId = exchange.getRequestHeaders().getLast(LAST_EVENT_ID);
            if (lastEventId != null) actionLog.context("last_event_id", lastEventId);
            holder.listener.onConnect(request, channel, lastEventId);
            if (!channel.groups.isEmpty()) actionLog.context("group", channel.groups.toArray()); // may join group onConnect
        } catch (Throwable e) {
            logManager.logError(e);
            exchange.endExchange();
        } finally {
            logManager.end("=== sse connect end ===");
            VirtualThread.COUNT.decrease();
        }
    }

    public <T> void add(String path, Class<T> eventClass, ServerSentEventListener<T> listener, ServerSentEventContextImpl<T> context) {
        var previous = holders.put(path, new ServerSentEventListenerHolder<>(listener, eventClass, context));
        if (previous != null) throw new Error("found duplicate sse listener, path=" + path);
    }

    public void shutdown() {
        logger.info("close sse connections");
        for (ServerSentEventListenerHolder<?> holder : holders.values()) {
            for (var channel : holder.context.all()) {
                channel.close();
            }
        }
    }
}
