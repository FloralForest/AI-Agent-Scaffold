package com.AI.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.model.valobj.AiAgentRegisterVO;
import com.AI.domain.agent.service.armory.AbstractArmorySupport;
import com.AI.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.InMemoryRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 执行器装配节点：基于 SequentialAgent 创建 InMemoryRunner，并按 agentId 注册 AiAgentRegisterVO。
 * 此节点只准备执行对象，不创建对话会话，也不发起模型对话。
 * */
@Slf4j
@Service
public class RunnerNode extends AbstractArmorySupport {

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - RunnerNode");

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        String appName = aiAgentConfigTableVO.getAppName();
        String agentId = aiAgentConfigTableVO.getAgent().getAgentId();
        String agentName = aiAgentConfigTableVO.getAgent().getAgentName();
        String agentDesc = aiAgentConfigTableVO.getAgent().getAgentDesc();

        // 获取上下文对象
        SequentialAgent sequentialAgent = dynamicContext.getSequentialAgent();

        // 会话运行节点
        InMemoryRunner runner = new InMemoryRunner(sequentialAgent, appName);

        // 构建注册对象
        AiAgentRegisterVO aiAgentRegisterVO = AiAgentRegisterVO.builder()
                .agentId(agentId)
                .appName(appName)
                .agentName(agentName)
                .agentDesc(agentDesc)
                .runner(runner)
                .build();

        // 注册到 Spring 容器
        registerBean(agentId, AiAgentRegisterVO.class, aiAgentRegisterVO);

        return aiAgentRegisterVO;
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return defaultStrategyHandler;
    }

}
