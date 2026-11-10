package com.AI.domain.agent.service.armory.factory;

import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.model.valobj.AiAgentRegisterVO;
import com.AI.domain.agent.service.armory.node.RootNode;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.*;


/**
 * 装配策略树工厂：提供 RootNode 作为统一入口，并创建节点间共享的动态上下文。
 */
@Service
public class DefaultArmoryFactory {
    @Resource
    private RootNode rootNode;

    /** 返回装配策略树的根处理器；具体节点由各节点的 get() 方法继续选择。 */
    public StrategyHandler<ArmoryCommandEntity, DynamicContext, AiAgentRegisterVO> armoryStrategyHandler() {
        return rootNode;
    }

    /**
     * 节点间共享的装配上下文，保存模型客户端、Agent 集合和工作流中间结果。
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    // 满足策略树的上下文类型约束，保留现有数据存取接口和行为。
    public static class DynamicContext extends cn.bugstack.wrench.design.framework.tree.DynamicContext {
        /**
         * LLM API
         */
        private OpenAiApi openAiApi;

        /**
         * LLM ChatModel
         */
        private ChatModel chatModel;

        /**
         * 当做最后一个智能体节点
         */
        //private SequentialAgent sequentialAgent;


        /**
         * 智能体配置组
         */
        private Map<String, BaseAgent> agentGroup = new HashMap<>();

        private List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = new ArrayList<>();

        private Map<String, Object> dataObjects = new HashMap<>();

        public <T> void setValue(String key, T value) {
            dataObjects.put(key, value);
        }

        // 泛型擦除后无法校验 Object 的实际类型，调用方必须保证同一 key 的存取类型一致。
        @SuppressWarnings("unchecked")
        public <T> T getValue(String key) {
            return (T) dataObjects.get(key);
        }

        public List<BaseAgent> queryAgentList(List<String> agentNames) {
            if (agentNames == null || agentNames.isEmpty() || agentGroup == null) {
                return Collections.emptyList();
            }

            List<BaseAgent> agents = new ArrayList<>();
            for (String name : agentNames) {
                BaseAgent agent = agentGroup.get(name);
                if (agent!=null){
                    agents.add(agent);
                }
            }

            return agents;
        }

    }
}
