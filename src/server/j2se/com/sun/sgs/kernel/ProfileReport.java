
package com.sun.sgs.kernel;

import java.util.List;


/**
 * The interface used to report profiling data associated with a complete
 * task run through the scheduler.
 */
public interface ProfileReport {

    /**
     * Returns the run task that generated this report.
     *
     * @return the <code>KernelRunnable</code> that was run
     */
    public KernelRunnable getTask();

    /**
     * Returns the owner of the run task.
     *
     * @return the <code>TaskOwner</code> for the task
     */
    public TaskOwner getTaskOwner();

    /**
     * Returns whether any of the task was transactional.
     *
     * @return <code>true</code> if any part of the task ran transactionally,
     *         <code>false</code> otherwise
     */
    public boolean wasTaskTransactional();

    /**
     * Returns whether the task successfully ran to completion. If this
     * task was transactional, then this means that the task committed
     * successfully.
     *
     * @return <code>true</code> if this task completed successfully,
     *         <code>false</code> otherwise
     */
    public boolean wasTaskSuccessful();

    /**
     * Returns the time at which that task was scheduled to run.
     *
     * @return the requested starting time for the task in milliseconds
     *         since January 1, 1970
     */
    public long getScheduledStartTime();

    /**
     * Returns the time at which the task actually started running.
     *
     * @return the actual starting time for the task in milliseconds
     *         since January 1, 1970
     */
    public long getActualStartTime();

    /**
     * Returns the length of time spent running the task. Note that this
     * is wall-clock time, not the time actually spent running on the
     * processor.
     *
     * @return the length in milliseconds to execute the task
     */
    public long getRunningTime();

    /**
     * Returns the number of times this task has been tied. If this is
     * the first time the task has been run, then this method returns 1.
     * 
     * @return the number of times this task has been tried
     */
    public int getRetryCount();

    /**
     * Returns the operations that were reported as executed during the
     * running of the task. If no operations were reported, then an
     * empty <code>List</code> is returned.
     *
     * @return a <code>List</code> of <code>ProfileOperation</code>
     *         representing the ordered set of reported operations
     */
    public List<ProfileOperation> getReportedOperations();

    /**
     * Returns the number of tasks in the same context as this report's task
     * that were in the scheduler and ready to run when this report's task
     * was started. Note that some schedulers may not differentiate between
     * application contexts, so this value may represent some other ready
     * count, such as the total number of tasks ready to run across all
     * contexts.
     *
     * @return the number of ready tasks in the same context.
     */
    public int getReadyCount();

}
