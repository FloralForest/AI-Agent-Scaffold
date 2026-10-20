package com.AI.domain.agent.service.armory.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.model.valobj.AiAgentRegisterVO;
import com.AI.domain.agent.service.armory.AbstractArmorySupport;
import com.AI.domain.agent.service.armory.factory.DefaultArmoryFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

/**
 * AI API 装配节点：按配置创建 OpenAI 兼容 API 客户端，并保存到共享上下文。
 * 当前 get() 返回默认处理器，因此尚未把流程继续路由到 ChatModelNode。
 */
@Slf4j
@Service
public class AiApiNode extends AbstractArmorySupport {
    /** 预期的后续节点；当前 get() 尚未返回此节点。 */
    @Resource
    private ChatModelNode chatModelNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        // 编写 AiApi 构建

        // 路由到下一个节点，如果不需要路由了，可以 return 返回结果
        log.info("Ai Agent 装配操作 - AiApiNode");

        AiAgentConfigTableVO aiAgentConfigTableVO = requestParameter.getAiAgentConfigTableVO();
        AiAgentConfigTableVO.Module.AiApi aiApiConfig = aiAgentConfigTableVO.getModule().getAiApi();

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(aiApiConfig.getBaseUrl())
                .apiKey(aiApiConfig.getApiKey())
                .completionsPath(StringUtils.isNotBlank(aiApiConfig.getCompletionsPath()) ? aiApiConfig.getCompletionsPath() : "v1/chat/completions")
                .embeddingsPath(StringUtils.isNotBlank(aiApiConfig.getEmbeddingsPath()) ? aiApiConfig.getEmbeddingsPath() : "v1/embeddings")
                .build();

        dynamicContext.setOpenAiApi(openAiApi);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {

        // 如果不需要下一个节点了，可以配置 defaultStrategyHandler
        return chatModelNode;
    }

}
