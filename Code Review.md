## Code Review

> Time: 2026/3/31 2:10 GMT+8
> System: gpu-scheduler
> Editor: Sakurain

1. **High**: `RUNNING` tasks can be transitioned out of running state without cancelling simulator execution, causing stale execution and `runningTasks` map leakage.

   - In [GpuTaskService.java](), `preemptTask` transitions `RUNNING -> QUEUED` but never calls `TaskExecutionSimulator.cancelTask(taskId)`.

   - In [GpuTaskService.java](), `forceFailTask` transitions `RUNNING -> FAILED` without cancellation.

   - In [WorkerHeartbeatService.java](), stale worker recovery transitions `RUNNING -> QUEUED` without cancellation.

   - In [OpsController.java](), force-requeue can also route a running task to `QUEUED` through `transition(...)` without cancellation.

   - Simulator cleanup depends on `cancelTask(...)` or `getResult(...)` ([TaskExecutionSimulator.java](), [TaskExecutionSimulator.java]()); these paths are skipped in the above flows.

2. **Medium**: Possible NPE in task permission check when task owner is null (schema allows null `user_id`).

   - [GpuTaskService.java]() uses `task.getUserId().equals(requesterId)`.

   - DB schema explicitly allows `gpu_task.user_id` to become null via FK `ON DELETE SET NULL` ([gpu_task.sql](), [gpu_task.sql]()).

3. **Medium**: DLQ reprocess matching is substring-based and can target wrong records.

   - [TaskRetryDlqService.java]() searches by string contains (`"\"taskId\":" + taskId`) on JSON text.

   - This is not structurally safe and can produce false matches; parse JSON and compare numeric `taskId` instead.

Residual testing gaps:

- No dedicated tests found for `TaskRetryDlqService` and `TaskPreemptionService` edge cases above (especially simulator-cancel coupling and DLQ exact-match behavior).