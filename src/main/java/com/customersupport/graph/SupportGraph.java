package com.customersupport.graph;

import com.customersupport.agent.BillingAgent;
import com.customersupport.agent.EscalationAgent;
import com.customersupport.agent.GeneralAgent;
import com.customersupport.agent.RouterAgent;
import com.customersupport.agent.TechnicalSupportAgent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;

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
                .compile();
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

    public SupportState run(String customerId, String message) {
        return graph.invoke(Map.of(
                        SupportState.CUSTOMER_ID, customerId,
                        SupportState.MESSAGE, message))
                .orElseThrow(() -> new IllegalStateException("Support graph produced no final state"));
    }

    public String mermaid() {
        return graph.getGraph(GraphRepresentation.Type.MERMAID, "Customer Support Agents", false).content();
    }
}
