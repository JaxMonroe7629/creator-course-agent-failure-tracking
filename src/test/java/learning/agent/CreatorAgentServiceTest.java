package learning.agent;

import java.util.ArrayList;
import java.util.List;

public final class CreatorAgentServiceTest {
    public static void main(String[] args) throws Exception {
        List<AgentFailure> captured = new ArrayList<>();
        CreatorAgentService service = new CreatorAgentService(captured::add);
        CommerceRun run = new CommerceRun("run-17", "course-storytelling", "asset-lesson-4", "learner-9");

        RunResult result = service.run(
                run,
                ignored -> { },
                ignored -> { throw new StageException("subscriber-update", "audience segment needs review"); },
                ignored -> { throw new AssertionError("processing must not run after subscriber update stops"); });

        check(result.state() == RunState.DELIVERED_WITH_FOLLOW_UP_REQUIRED, "state preserves completed delivery");
        check(captured.size() == 1, "one failure is captured");
        AgentFailure failure = captured.get(0);
        check(failure.courseId().equals("course-storytelling"), "course context is retained");
        check(failure.assetId().equals("asset-lesson-4"), "asset context is retained");
        check(failure.stage().equals("subscriber-update"), "the actionable stage drives grouping");
        System.out.println("PASS: delivered asset remains delivered and subscriber-update is captured once");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
