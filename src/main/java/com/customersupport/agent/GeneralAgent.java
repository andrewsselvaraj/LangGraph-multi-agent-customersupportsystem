package com.customersupport.agent;

import com.customersupport.agent.tools.GeneralTools;
import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.Faq;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Handles everything that is neither billing nor technical: FAQs, account and company questions. */
@Component
public class GeneralAgent implements NodeAction<SupportState> {

    static final String NAME = "general";

    private static final String SYSTEM_PROMPT = """
            You are a friendly general customer support agent for a SaaS company. Look up company policy \
            with searchFaq rather than guessing, and answer briefly. If the customer needs something you \
            cannot do, call escalateToHuman.""";

    private static final String DEFAULT_ANSWER = "Thanks for reaching out! I can help with billing, technical "
            + "problems, or general questions about your account. Could you tell me a bit more about what you need?";

    private final LlmClient llm;
    private final Faq faq;

    public GeneralAgent(LlmClient llm, Faq faq) {
        this.llm = llm;
        this.faq = faq;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        GeneralTools tools = new GeneralTools(faq);
        return llm.runWithTools(SYSTEM_PROMPT, state.promptWithHistory(), tools)
                .map(run -> AgentReply.fromRun(state, NAME, run, tools))
                .orElseGet(() -> AgentReply.resolved(state, NAME, faq.answer(state.message()).orElse(DEFAULT_ANSWER)));
    }
}
