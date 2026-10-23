package com.customersupport.graph;

import com.customersupport.agent.BillingAgent;
import com.customersupport.agent.EscalationAgent;
import com.customersupport.agent.GeneralAgent;
import com.customersupport.agent.RouterAgent;
import com.customersupport.agent.TechnicalSupportAgent;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Wires the agents into a LangGraph4j state graph:
 *
 * <pre>
 *   START -> router --(BILLING)----> billing ---.
 *                   --(TECHNICAL)--> technical -+--(needs human?)--> escalation -> END
 *                   --(GENERAL)----> general ---'          \--(resolved)--------> END
 *                   --(ESCALATE)------------------------------------> escalation
 * </pre>
 *
 * <p>The graph is compiled with a checkpointer, so each conversation id is a LangGraph thread:
 * a new message resumes from the state the previous turn left behind, history included.
 */
@Component
public class SupportGraph {

    public static final String ROUTER = "router";
    public static final String BILLING = "billing";
    public static final String TECHNICAL = "technical";
    public static final String GENERAL = "general";
    public static final String ESCALATION = "escalation";

    private final CompiledGraph<SupportState> graph;

    public SupportGraph(RouterAgent router,
                        BillingAgent billing,
                        TechnicalSupportAgent technical,
                        GeneralAgent general,
                        EscalationAgent escalation) throws GraphStateException {

        Map<String, String> specialistExits = Map.of(
                ESCALATION, ESCALATION,
                END, END);

        this.graph = new StateGraph<>(SupportState.SCHEMA, SupportState::new)
                .addNode(ROUTER, node_async(router))
                .addNode(BILLING, node_async(billing))
                .addNode(TECHNICAL, node_async(technical))
                .addNode(GENERAL, node_async(general))
                .addNode(ESCALATION, node_async(escalation))

                .addEdge(START, ROUTER)
                .addConditionalEdges(ROUTER, edge_async(SupportGraph::routeFromRouter), Map.of(
                        BILLING, BILLING,
                        TECHNICAL, TECHNICAL,
                        GENERAL, GENERAL,
                        ESCALATION, ESCALATION))

                .addConditionalEdges(BILLING, edge_async(SupportGraph::routeFromSpecialist), specialistExits)
                .addConditionalEdges(TECHNICAL, edge_async(SupportGraph::routeFromSpecialist), specialistExits)
                .addConditionalEdges(GENERAL, edge_async(SupportGraph::routeFromSpecialist), specialistExits)
                .addEdge(ESCALATION, END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        // keep checkpoints after a run so the next message can continue the conversation
                        .releaseThread(false)
                        .build());
    }

    static String routeFromRouter(SupportState state) {
        return switch (state.category()) {
            case BILLING -> BILLING;
            case TECHNICAL -> TECHNICAL;
            case GENERAL -> GENERAL;
            case ESCALATE -> ESCALATION;
        };
    }

    static String routeFromSpecialist(SupportState state) {
        return state.needsEscalation() ? ESCALATION : END;
    }

    /**
     * Runs one customer message as a new turn of the conversation. LangGraph4j merges this input
     * into the conversation's last checkpoint: the message is appended to the history, the customer
     * id and last category carry over, and the fields that describe a single turn are reset.
     */
    public SupportState run(String conversationId, String customerId, String message) {
        Map<String, Object> input = new HashMap<>();
        if (customerId != null && !customerId.isBlank()) {
            input.put(SupportState.CUSTOMER_ID, customerId);
        }
        input.put(SupportState.MESSAGE, message);
        input.put(SupportState.HISTORY, List.of("Customer: " + message));
        input.put(SupportState.TRACE, List.of());
        input.put(SupportState.NEEDS_ESCALATION, false);
        for (String perTurn : List.of(SupportState.ROUTING_REASON, SupportState.RESPONSE, SupportState.HANDLED_BY,
                SupportState.ESCALATION_REASON, SupportState.TICKET_ID)) {
            input.put(perTurn, AgentState.MARK_FOR_REMOVAL);
        }
        return graph.invoke(input, config(conversationId))
                .orElseThrow(() -> new IllegalStateException("Support graph produced no final state"));
    }

    /** Latest state of a conversation, if it exists. */
    public Optional<SupportState> conversation(String conversationId) {
        return graph.lastStateOf(config(conversationId)).map(StateSnapshot::state);
    }

    private static RunnableConfig config(String conversationId) {
        return RunnableConfig.builder().threadId(conversationId).build();
    }

    public String mermaid() {
        return graph.getGraph(GraphRepresentation.Type.MERMAID, "Customer Support Agents", false).content();
    }
}
