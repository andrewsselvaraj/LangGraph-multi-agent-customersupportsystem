package com.customersupport.agent;

import com.customersupport.graph.SupportState;
import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.TicketService;
import com.customersupport.service.TicketService.Priority;
import com.customersupport.service.TicketService.Ticket;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Human handoff. Opens a ticket in the human agents' queue with a priority based on
 * the reason and the customer's plan, and tells the customer what happens next.
 */
@Component
public class EscalationAgent implements NodeAction<SupportState> {

    static final String NAME = "escalation";

    private static final List<String> URGENT_WORDS = List.of(
            "lawyer", "legal", "sue", "lawsuit", "attorney", "furious", "outraged", "unacceptable");

    private final TicketService tickets;
    private final CustomerAccountService accounts;

    public EscalationAgent(TicketService tickets, CustomerAccountService accounts) {
        this.tickets = tickets;
        this.accounts = accounts;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        String reason = state.escalationReason()
                .or(state::routingReason)
                .orElse("Customer requested a human agent");
        Priority priority = priority(state);

        Ticket ticket = tickets.open(state.customerId(), state.message(), state.category().name(), reason, priority);

        String eta = switch (priority) {
            case URGENT -> "within 1 hour";
            case HIGH -> "within 4 hours";
            default -> "within 1 business day";
        };
        String response = "I've passed your request to a member of our support team (ticket " + ticket.id()
                + ", priority " + priority.name().toLowerCase(Locale.ROOT) + "). They'll get back to you " + eta
                + ". You don't need to repeat anything; they can see this conversation.";

        return Map.of(
                SupportState.HANDLED_BY, NAME,
                SupportState.NEEDS_ESCALATION, true,
                SupportState.ESCALATION_REASON, reason,
                SupportState.TICKET_ID, ticket.id(),
                SupportState.RESPONSE, response,
                SupportState.TRACE, List.of(NAME + ": opened " + ticket.id() + " (" + priority + ")"));
    }

    private Priority priority(SupportState state) {
        String lower = state.message().toLowerCase(Locale.ROOT);
        if (URGENT_WORDS.stream().anyMatch(lower::contains)) {
            return Priority.URGENT;
        }
        boolean enterprise = accounts.find(state.customerId())
                .map(a -> "Enterprise".equals(a.plan()))
                .orElse(false);
        return enterprise ? Priority.HIGH : Priority.NORMAL;
    }
}
