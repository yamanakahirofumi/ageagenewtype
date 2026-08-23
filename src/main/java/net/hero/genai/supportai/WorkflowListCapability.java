package net.hero.genai.supportai;

import net.hero.genai.workflow.Workflow;
import net.hero.genai.workflow.WorkflowService;
import net.hero.genai.workflow.WorkflowStep;

import java.util.List;

/**
 * Support AI capability to retrieve and format the list of available workflows.
 */
public final class WorkflowListCapability implements SupportAICapability {

    @Override
    public String getId() {
        return "list-workflows";
    }

    @Override
    public String execute(final String argument) {
        final WorkflowService service = WorkflowService.getInstance();
        final List<Workflow> workflows = service.getPredefinedWorkflows();
        final StringBuilder sb = new StringBuilder();
        sb.append("利用可能なワークフローの一覧は以下の通りです：\n\n");
        for (final Workflow wf : workflows) {
            sb.append("■ ").append(wf.name()).append(" (ID: ").append(wf.id()).append(")\n");
            sb.append("  概要: ").append(wf.description()).append("\n");
            sb.append("  ステップ:\n");
            for (final WorkflowStep step : wf.steps()) {
                sb.append("    フェーズ ").append(step.phase()).append(": ").append(step.name()).append(" [").append(step.type()).append("]\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
