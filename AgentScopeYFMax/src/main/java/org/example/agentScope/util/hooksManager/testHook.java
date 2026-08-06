package org.example.agentScope.util.hooksManager;

import io.agentscope.core.hook.PreActingEvent;

public class testHook extends AbstractAgentHook {

    @Override
    public int priority() {
        return 100;
    }



}
