package com.customersupport.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.Optional;

/**
 * Tools every specialist gets. A new instance is created for each request, so it can
 * remember whether the model asked for a human during this run.
 */
public abstract class SupportTools {

    private String escalationReason;

    @Tool("Hand the conversation to a human support agent. Use it when the request is outside policy, "
            + "needs an action you have no tool for, or the customer is very upset.")
    public String escalateToHuman(@P("One-sentence reason for the handoff") String reason) {
        this.escalationReason = reason;
        return "A human agent will take over. Tell the customer briefly that a colleague will follow up.";
    }

    public Optional<String> escalationReason() {
        return Optional.ofNullable(escalationReason);
    }
}
