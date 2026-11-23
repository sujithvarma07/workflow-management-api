package com.relic.backend.messaging;

import org.springframework.context.ApplicationEvent;

/**
 * An in-process Spring event, published synchronously inside the same
 * transaction that approves a task (see ApprovalServiceImpl). It exists
 * only so TaskCompletedEventPublisher can listen for it with
 * @TransactionalEventListener(phase = AFTER_COMMIT) - publishing straight to
 * RabbitMQ from inside the transaction would risk sending a message for a
 * change that then gets rolled back.
 */
public class TaskCompletedApplicationEvent extends ApplicationEvent {

    private final Long taskId;
    private final String taskTitle;
    private final Long projectId;

    public TaskCompletedApplicationEvent(Object source, Long taskId, String taskTitle, Long projectId) {
        super(source);
        this.taskId = taskId;
        this.taskTitle = taskTitle;
        this.projectId = projectId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public Long getProjectId() {
        return projectId;
    }
}
