package com.customersupport.agent;

import com.customersupport.agent.tools.TechnicalTools;
import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.KnowledgeBase;
import com.customersupport.service.ServiceStatus;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Technical support specialist. With an LLM it searches the knowledge base and the status
 * page itself, possibly several times with different keywords, before answering. Offline,
 * it answers from the best-matching article and hands unknown problems to a human.
 */
@Component
public class TechnicalSupportAgent implements NodeAction<SupportState> {

    static final String NAME = "technical";

    private static final String SYSTEM_PROMPT = """
            You are a technical support engineer. Before answering, search the knowledge base (try different \
            keywords if the first search finds nothing) and check the service status when the problem could be \
            an outage. Give clear, numbered troubleshooting steps based on what you found. If nothing you found \
            solves the problem, call escalateToHuman.""";

    private final LlmClient llm;
    private final KnowledgeBase knowledgeBase;
    private final ServiceStatus serviceStatus;

    public TechnicalSupportAgent(LlmClient llm, KnowledgeBase knowledgeBase, ServiceStatus serviceStatus) {
        this.llm = llm;
        this.knowledgeBase = knowledgeBase;
        this.serviceStatus = serviceStatus;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        TechnicalTools tools = new TechnicalTools(knowledgeBase, serviceStatus);
        return llm.runWithTools(SYSTEM_PROMPT, state.promptWithHistory(), tools)
                .map(run -> AgentReply.fromRun(state, NAME, run, tools))
                .orElseGet(() -> knowledgeBase.search(state.message())
                        .map(a -> AgentReply.resolved(state, NAME, "Here's how to fix this (" + a.id() + ": "
                                + a.title() + "): " + a.solution() + " If the problem continues, reply and we'll dig deeper."))
                        .orElseGet(() -> AgentReply.escalate(state, NAME, "No knowledge base article matches this issue")));
    }
}
