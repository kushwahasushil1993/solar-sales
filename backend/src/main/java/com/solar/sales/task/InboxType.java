package com.solar.sales.task;

public enum InboxType {
    /** To an agent: a task was assigned to you (opening it accepts the task). */
    TASK_ASSIGNED,
    /** To an agent: a task was taken away / given to someone else. */
    TASK_UNASSIGNED,
    /** To admins: an agent accepted an assigned task. */
    TASK_ACCEPTED,
    /** To admins: an agent picked up an open task. */
    TASK_PICKED,
    /** To admins: an agent changed a task's installation status. */
    TASK_STATUS_UPDATED,
    /** To admins: someone signed up and waits for a role (sales or agent). */
    ACCOUNT_PENDING
}
