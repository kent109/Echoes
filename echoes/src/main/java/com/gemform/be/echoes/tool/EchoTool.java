package com.gemform.be.echoes.tool;

import com.gemform.be.echoes.repository.EchoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class EchoTool {

    private final EchoRepository echoRepository;

    public EchoTool(EchoRepository echoRepository) {
        this.echoRepository = echoRepository;
    }

    @Tool(name = "count_echoes", description = "统计数据库中回声条目的总数量")
    public long countEchoes() {
        return echoRepository.count();
    }
}
