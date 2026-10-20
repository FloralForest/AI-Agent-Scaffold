package com.AI.test.api.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("循环智能体离线测试")
class LoopAgentTest {

    @Test
    @DisplayName("审查通过后提前退出，不再调用改写模型")
    void refinementShouldStopWhenTheCriticApproves() {
        AtomicInteger critiques = new AtomicInteger();
        AtomicInteger refinements = new AtomicInteger();
        LlmAgent writer = OfflineAgentSupport.agent("writer", "编写文档初稿", "current_doc",
                request -> "文档第1版");
        LlmAgent critic = OfflineAgentSupport.agent("critic", "审查文档：{current_doc}", "critique",
                request -> critiques.incrementAndGet() == 1 ? "需要完善" : "审查通过");
        LlmAgent refiner = OfflineAgentSupport.builder("refiner",
                        "根据审查意见 {critique} 改进文档：{current_doc}", "current_doc",
                        request -> "文档第" + (refinements.incrementAndGet() + 1) + "版")
                .beforeAgentCallbackSync(context -> {
                    // 设置退出标记以结束当前循环，返回内容可跳过本轮改写模型调用。
                    if ("审查通过".equals(context.state().get("critique"))) {
                        context.eventActions().setEscalate(true);
                        return Optional.of(Content.fromParts(Part.fromText("文档审查通过")));
                    }
                    return Optional.empty();
                })
                .build();
        LoopAgent loop = LoopAgent.builder().name("refinement_loop")
                .subAgents(critic, refiner).maxIterations(5).build();
        SequentialAgent pipeline = SequentialAgent.builder().name("document_pipeline")
                .subAgents(writer, loop).build();

        OfflineAgentSupport.RunResult result = OfflineAgentSupport.run(pipeline);

        assertThat(critiques.get()).isEqualTo(2);
        assertThat(refinements.get()).isEqualTo(1);
        assertThat(result.getState()).containsEntry("current_doc", "文档第2版")
                .containsEntry("critique", "审查通过");
        assertThat(result.getEvents()).anyMatch(event -> event.actions().escalate().orElse(false));
    }

    @Test
    @DisplayName("始终未通过审查时，最多执行五轮")
    void refinementShouldNotExceedItsIterationLimit() {
        AtomicInteger refinements = new AtomicInteger();
        LlmAgent refiner = OfflineAgentSupport.agent("bounded_refiner", "改进文档初稿", "current_doc",
                request -> "文档第" + refinements.incrementAndGet() + "版");
        // 即使始终未通过审查，也必须在达到次数上限时结束，避免无限循环。
        LoopAgent loop = LoopAgent.builder().name("bounded_loop")
                .subAgents(refiner).maxIterations(5).build();

        OfflineAgentSupport.RunResult result = OfflineAgentSupport.run(loop);

        assertThat(refinements.get()).isEqualTo(5);
        assertThat(result.getState()).containsEntry("current_doc", "文档第5版");
    }
}
