package org.example.agentScope.util.hooksManager.hooks;

import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
//这里全是单例不会有问题吗？而且没有无状态的类，给spring管理的必要是？
public class AllHook {

    @Resource
    private MyPreActingHook myPreActingHook;
    @Resource
    private MyPostActingHook myPostActingHook;
    @Resource
    private MyPreCallHook myPreCallHook;
    @Resource
    private MyPreReasoningHook myPreReasoningHook;
    @Resource
    private MyReasoningChunkHook myReasoningChunkHook;
    @Resource
    private MyPostReasoningHook myPostReasoningHook;
    @Resource
    private MyPostCallHook myPostCallHook;
    @Resource
    private MyActingChunkHook myActingChunkHook;
    @Resource
    private MyErrorHook myErrorHook;



}
