package com.customersupport.agent;

import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.KnowledgeBase;
import com.customersupport.service.KnowledgeBase.Article;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Technical support specialist. Grounds its answer in the knowledge base; when no
 * article matches and no LLM is available, it hands the case to a human engineer.
 */
@Component
public class TechnicalSupportAgent implements NodeAction<SupportState> {

    static final String NAME = "technical";

    private static final String SYSTEM_PROMPT = """
            You are a technical support engineer. Give clear, numbered troubleshooting steps. \
            Prefer the knowledge base article when one is provided. """;

    private final LlmClient llm;
    private final KnowledgeBase knowledgeBase;

    public TechnicalSupportAgent(LlmClient llm, KnowledgeBase knowledgeBase) {
        this.llm = llm;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        Optional<Article> article = knowledgeBase.search(state.message());

        String kbContext = article
                .map(a -> "Knowledge base article " + a.id() + " - " + a.title() + ":\n" + a.solution())
                .orElse("No matching knowledge base article.");

        Optional<String> answer = llm.complete(SYSTEM_PROMPT + AgentReply.ESCALATION_INSTRUCTIONS,
                kbContext + "\n\nCustomer message:\n" + state.message());
        if (answer.isPresent()) {
            return AgentReply.fromLlm(NAME, answer.get());
        }

        return article
                .map(a -> AgentReply.resolved(NAME, "Here's how to fix this (" + a.id() + ": " + a.title() + "): "
                        + a.solution() + " If the problem continues, reply and we'll dig deeper."))
                .orElseGet(() -> AgentReply.escalate(NAME, "No knowledge base article matches this issue"));
    }
}
