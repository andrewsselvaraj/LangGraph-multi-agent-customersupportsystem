package com.customersupport.graph;

import com.customersupport.agent.BillingAgent;
import com.customersupport.agent.EscalationAgent;
import com.customersupport.agent.GeneralAgent;
import com.customersupport.agent.RouterAgent;
import com.customersupport.agent.TechnicalSupportAgent;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.KnowledgeBase;
import com.customersupport.service.TicketService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupportGraphTest {

    private final TicketService tickets = new TicketService();

    private SupportGraph graph(LlmClient llm) throws Exception {
        CustomerAccountService accounts = new CustomerAccountService();
        return new SupportGraph(
                new RouterAgent(llm),
                new BillingAgent(llm, accounts, new BigDecimal("500")),
                new TechnicalSupportAgent(llm, new KnowledgeBase()),
                new GeneralAgent(llm),
                new EscalationAgent(tickets, accounts));
    }

    private SupportGraph offlineGraph() throws Exception {
        return graph(new LlmClient((ChatModel) null));
    }

    @Test
    void billingQuestionIsAnsweredByBillingAgent() throws Exception {
        SupportState state = offlineGraph().run("C-1001", "What is on my latest invoice?");

        assertThat(state.category()).isEqualTo(Category.BILLING);
        assertThat(state.handledBy()).contains("billing");
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("INV-9001"));
        assertThat(state.ticketId()).isEmpty();
        assertThat(state.trace()).hasSize(2);
    }

    @Test
    void duplicateChargeIsRefundedWithoutHuman() throws Exception {
        SupportState state = offlineGraph().run("C-1003", "I was charged twice this month, please refund me");

        assertThat(state.handledBy()).contains("billing");
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("duplicate charge"));
        assertThat(tickets.all()).isEmpty();
    }

    @Test
    void largeRefundIsEscalatedToHuman() throws Exception {
        SupportState state = offlineGraph().run("C-1002", "I want a refund of $899 for last month's invoice");

        assertThat(state.category()).isEqualTo(Category.BILLING);
        assertThat(state.handledBy()).contains("escalation");
        assertThat(state.ticketId()).isPresent();
        assertThat(state.escalationReason()).hasValueSatisfying(r -> assertThat(r).contains("approval limit"));
        assertThat(tickets.all()).singleElement()
                .satisfies(t -> assertThat(t.priority()).isEqualTo(TicketService.Priority.HIGH));
        assertThat(state.trace()).hasSize(3);
    }

    @Test
    void technicalIssueUsesKnowledgeBase() throws Exception {
        SupportState state = offlineGraph().run("C-1001", "I forgot my password and can't log in");

        assertThat(state.category()).isEqualTo(Category.TECHNICAL);
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("KB-101"));
    }

    @Test
    void unknownTechnicalIssueIsEscalated() throws Exception {
        SupportState state = offlineGraph().run("C-1001", "The export button is broken and not working");

        assertThat(state.category()).isEqualTo(Category.TECHNICAL);
        assertThat(state.ticketId()).isPresent();
    }

    @Test
    void generalQuestionIsAnsweredFromFaq() throws Exception {
        SupportState state = offlineGraph().run("anonymous", "What are your opening hours?");

        assertThat(state.category()).isEqualTo(Category.GENERAL);
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("Monday to Friday"));
    }

    @Test
    void requestForHumanGoesStraightToEscalation() throws Exception {
        SupportState state = offlineGraph().run("C-1001", "This is unacceptable, I want to speak to a manager");

        assertThat(state.category()).isEqualTo(Category.ESCALATE);
        assertThat(state.trace()).hasSize(2);
        assertThat(tickets.all()).singleElement()
                .satisfies(t -> assertThat(t.priority()).isEqualTo(TicketService.Priority.URGENT));
    }

    @Test
    void llmDrivesRoutingAndSpecialistAnswers() throws Exception {
        LlmClient llm = new LlmClient(new ScriptedChatModel(
                "TECHNICAL | sync problem",
                "1. Sign out and back in. 2. Re-enable sync."));

        SupportState state = graph(llm).run("C-1001", "My calendar stopped updating");

        assertThat(state.category()).isEqualTo(Category.TECHNICAL);
        assertThat(state.routingReason()).contains("LLM: sync problem");
        assertThat(state.response()).contains("1. Sign out and back in. 2. Re-enable sync.");
    }

    @Test
    void llmSpecialistCanRequestEscalation() throws Exception {
        LlmClient llm = new LlmClient(new ScriptedChatModel(
                "GENERAL | account question",
                "[ESCALATE] Ownership transfer needs identity verification"));

        SupportState state = graph(llm).run("C-1001", "Please transfer my account to my colleague");

        assertThat(state.handledBy()).contains("escalation");
        assertThat(state.escalationReason()).contains("Ownership transfer needs identity verification");
    }

    @Test
    void mermaidDiagramListsAllAgents() throws Exception {
        assertThat(offlineGraph().mermaid())
                .contains("router", "billing", "technical", "general", "escalation");
    }

    /** Returns canned answers in order, one per call. */
    private static final class ScriptedChatModel implements ChatModel {
        private final Deque<String> answers;

        ScriptedChatModel(String... answers) {
            this.answers = new ArrayDeque<>(List.of(answers));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from(answers.removeFirst())).build();
        }
    }
}
