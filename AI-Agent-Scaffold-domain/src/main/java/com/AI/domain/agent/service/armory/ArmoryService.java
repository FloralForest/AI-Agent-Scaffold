package com.AI.domain.agent.service.armory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.AI.domain.agent.model.entity.ArmoryCommandEntity;
import com.AI.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.AI.domain.agent.model.valobj.AiAgentRegisterVO;
import com.AI.domain.agent.service.IArmoryService;
import com.AI.domain.agent.service.armory.factory.DefaultArmoryFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 智能体装配应用服务：逐个读取配置表，为每个智能体创建独立上下文并启动策略树。
 */
@Slf4j
@Service
public class ArmoryService implements IArmoryService {

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    /** 对配置列表逐项执行 RootNode 开始的装配流程。 */
    @Override
    public void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception {
        for (AiAgentConfigTableVO table : tables) {
            StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> handler = defaultArmoryFactory.armoryStrategyHandler();
            handler.apply(
                    ArmoryCommandEntity.builder()
                            .aiAgentConfigTableVO(table)
                            .build(),
                    new DefaultArmoryFactory.DynamicContext());
        }
    }

}
