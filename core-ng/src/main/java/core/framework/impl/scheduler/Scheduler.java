package core.framework.impl.scheduler;

import core.framework.api.async.Executor;
import core.framework.api.log.ActionLogContext;
import core.framework.api.scheduler.Job;
import core.framework.api.util.Exceptions;
import core.framework.api.util.Maps;
import core.framework.api.web.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author neo
 */
public final class Scheduler {
    public final Map<String, Trigger> triggers = Maps.newHashMap();
    private final Logger logger = LoggerFactory.getLogger(Scheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Executor executor;

    public Scheduler(Executor executor) {
        this.executor = executor;
    }

    public void start() {
        LocalDateTime now = LocalDateTime.now();
        triggers.forEach((name, trigger) -> {
            logger.info("schedule job, job={}, schedule={}, jobClass={}", name, trigger.schedule(), trigger.job.getClass().getCanonicalName());
            Duration delay = trigger.nextDelay(now);
            schedule(new JobTask(this, trigger), delay);
        });
        logger.info("scheduler started");
    }

    public void stop() {
        logger.info("stop scheduler");
        scheduler.shutdown();
    }

    public void addTrigger(Trigger trigger) {
        Class<? extends Job> jobClass = trigger.job.getClass();
        if (jobClass.isSynthetic())
            throw Exceptions.error("job class must not be anonymous class or lambda, please create static class, jobClass={}", jobClass.getCanonicalName());

        Trigger previous = triggers.putIfAbsent(trigger.name, trigger);
        if (previous != null)
            throw Exceptions.error("duplicated job found, name={}, previousJobClass={}", trigger.name, previous.job.getClass().getCanonicalName());
    }

    void schedule(JobTask task, Duration delay) {
        scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    void submitJob(Trigger trigger) {
        executor.submit("job/" + trigger.name, () -> {
            logger.info("execute scheduled job, job={}", trigger.name);
            Job job = trigger.job;
            ActionLogContext.put("job", trigger.name);
            ActionLogContext.put("jobClass", job.getClass().getCanonicalName());
            job.execute();
            return null;
        });
    }

    public void triggerNow(String name) {
        Trigger trigger = triggers.get(name);
        if (trigger == null) throw new NotFoundException("job not found, name=" + name);
        submitJob(trigger);
    }
}
