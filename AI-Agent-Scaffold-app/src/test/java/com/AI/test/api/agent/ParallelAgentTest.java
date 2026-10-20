package com.AI.test.api.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.models.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("并行智能体离线测试")
class ParallelAgentTest {

    @Test
    @DisplayName("三个研究分支并发执行，结果互不覆盖")
    void independentResearchersShouldRunConcurrentlyWithoutOverwritingState() {
        CountDownLatch started = new CountDownLatch(3);
        // 各分支使用不同状态键；并行分支之间不能依赖尚未完成的结果。
        LlmAgent market = OfflineAgentSupport.agent("market_researcher", "研究市场情况",
                "market_result", concurrentAnswer(started, "市场研究摘要"));
        LlmAgent technical = OfflineAgentSupport.agent("technical_researcher", "研究技术方案",
                "technical_result", concurrentAnswer(started, "技术研究摘要"));
        LlmAgent risks = OfflineAgentSupport.agent("risk_researcher", "研究潜在风险",
                "risk_result", concurrentAnswer(started, "风险研究摘要"));
        ParallelAgent researchers = ParallelAgent.builder().name("parallel_research")
                .subAgents(market, technical, risks).build();

        OfflineAgentSupport.RunResult result = OfflineAgentSupport.run(researchers);

        assertThat(result.getState()).containsEntry("market_result", "市场研究摘要")
                .containsEntry("technical_result", "技术研究摘要")
                .containsEntry("risk_result", "风险研究摘要");
        assertThat(started.getCount()).isZero();
    }

    private Function<LlmRequest, String> concurrentAnswer(CountDownLatch started, String answer) {
        return request -> {
            started.countDown();
            try {
                // 三个模型调用都启动后才允许任一分支结束，以验证实际并发。
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("并行示例执行被中断", exception);
            }
            return answer;
        };
    }
}
