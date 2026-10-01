package learning.agent;

public final class CreatorAgentExample {
    public static void main(String[] args) throws Exception {
        AgentTrackingConfig config = AgentTrackingConfig.load();
        CreatorAgentService service = new CreatorAgentService(new InfraiErrors(config));
        CommerceRun run = new CommerceRun("run-course-204", "course-java-agents", "asset-workbook-v3", "learner-88");

        RunResult result = service.run(
                run,
                commerceRun -> System.out.println("Delivered " + commerceRun.assetId()),
                commerceRun -> { throw new StageException("subscriber-update", "audience segment needs review"); },
                commerceRun -> System.out.println("Processed content for " + commerceRun.courseId()));

        System.out.println(result.state() + ": " + result.summary());
    }
}
