package learning.agent;

import java.io.IOException;

public final class CreatorAgentService {
    private final FailureCapture failures;

    public CreatorAgentService(FailureCapture failures) {
        this.failures = failures;
    }

    public RunResult run(CommerceRun run, AgentStep deliverAsset, AgentStep updateSubscribers,
                         AgentStep processContent) throws Exception {
        deliverAsset.execute(run);
        try {
            updateSubscribers.execute(run);
            processContent.execute(run);
            return new RunResult(run.runId(), RunState.COMPLETED, "asset delivered and follow-up completed");
        } catch (Exception problem) {
            String stage = problem instanceof StageException staged ? staged.stage() : "follow-up";
            failures.capture(new AgentFailure(
                    run.runId(), run.courseId(), run.assetId(), stage,
                    problem.getMessage() == null ? problem.getClass().getSimpleName() : problem.getMessage(),
                    stackSummary(problem)));
            return new RunResult(run.runId(), RunState.DELIVERED_WITH_FOLLOW_UP_REQUIRED,
                    "asset delivered; " + stage + " queued for review");
        }
    }

    private static String stackSummary(Exception problem) {
        StackTraceElement first = problem.getStackTrace().length == 0 ? null : problem.getStackTrace()[0];
        return problem.getClass().getName() + ": " + problem.getMessage()
                + (first == null ? "" : " at " + first);
    }
}

interface FailureCapture {
    void capture(AgentFailure failure) throws IOException, InterruptedException;
}

@FunctionalInterface
interface AgentStep {
    void execute(CommerceRun run) throws Exception;
}

record CommerceRun(String runId, String courseId, String assetId, String subscriberId) {}

record AgentFailure(String runId, String courseId, String assetId, String stage,
                    String message, String exception) {}

record RunResult(String runId, RunState state, String summary) {}

enum RunState { COMPLETED, DELIVERED_WITH_FOLLOW_UP_REQUIRED }

final class StageException extends Exception {
    private final String stage;

    StageException(String stage, String message) {
        super(message);
        this.stage = stage;
    }

    String stage() { return stage; }
}
