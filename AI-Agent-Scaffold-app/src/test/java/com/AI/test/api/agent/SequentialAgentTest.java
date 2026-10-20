package com.AI.test.api.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("顺序智能体离线测试")
class SequentialAgentTest {

    @Test
    @DisplayName("依次完成初稿、审查、重构，并传递状态")
    void writerReviewerAndRefactorerShouldShareStateInOrder() {
        LlmAgent writer = OfflineAgentSupport.agent("writer", "编写初始代码", "draft",
                request -> "初始代码");
        LlmAgent reviewer = OfflineAgentSupport.agent("reviewer", "审查代码：{draft}", "review",
                request -> {
                    assertThat(request.getSystemInstructions().toString()).contains("初始代码");
                    return "补充参数校验";
                });
        LlmAgent refactorer = OfflineAgentSupport.agent("refactorer",
                "根据审查意见 {review} 重构代码：{draft}", "final_code", request -> {
                    assertThat(request.getSystemInstructions().toString())
                            .contains("初始代码", "补充参数校验");
                    return "已增加校验的代码";
                });

        // 后续智能体通过指令中的占位符，读取前一步保存的输出状态。
        SequentialAgent pipeline = SequentialAgent.builder().name("code_pipeline")
                .subAgents(writer, reviewer, refactorer).build();
        OfflineAgentSupport.RunResult result = OfflineAgentSupport.run(pipeline);

        assertThat(result.getState()).containsEntry("draft", "初始代码")
                .containsEntry("review", "补充参数校验")
                .containsEntry("final_code", "已增加校验的代码");
        assertThat(result.getEvents().stream().filter(Event::finalResponse).map(Event::author))
                .containsSubsequence("writer", "reviewer", "refactorer");
    }
}
