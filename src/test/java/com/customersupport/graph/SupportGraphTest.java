package com.customersupport.graph;

import com.customersupport.agent.BillingAgent;
import com.customersupport.agent.EscalationAgent;
import com.customersupport.agent.GeneralAgent;
import com.customersupport.agent.RouterAgent;
import com.customersupport.agent.TechnicalSupportAgent;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.Faq;
import com.customersupport.service.KnowledgeBase;
import com.customersupport.service.ServiceStatus;
import com.customersupport.service.TicketService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportGraphTest {

    private final TicketService tickets = new TicketService();
    private final CustomerAccountService accounts = new CustomerAccountService();

    private SupportGraph graph(LlmClient llm) throws Exception {
        return new SupportGraph(
                new RouterAgent(llm),
                new BillingAgent(llm, accounts, new BigDecimal("500")),
                new TechnicalSupportAgent(llm, new KnowledgeBase(), new ServiceStatus()),
                new GeneralAgent(llm, new Faq()),
                new EscalationAgent(tickets, accounts));
    }

    private SupportGraph offlineGraph() throws Exception {
        return graph(new LlmClient((ChatModel) null));
    }

    private static String newConversation() {
        return UUID.randomUUID().toString();
    }

    // --- Offline (rule-based) behaviour -------------------------------------------------------

    @Test
    void billingQuestionIsAnsweredByBillingAgent() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), "C-1001", "What is on my latest invoice?");

        assertThat(state.category()).isEqualTo(Category.BILLING);
        assertThat(state.handledBy()).contains("billing");
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("INV-9001"));
        assertThat(state.ticketId()).isEmpty();
        assertThat(state.trace()).hasSize(2);
    }

    @Test
    void duplicateChargeIsRefundedWithoutHuman() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), "C-1003", "I was charged twice this month, please refund me");

        assertThat(state.handledBy()).contains("billing");
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("duplicate charge"));
        assertThat(accounts.refundFor("INV-9003")).isPresent();
        assertThat(tickets.all()).isEmpty();
    }

    @Test
    void largeRefundIsEscalatedToHuman() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), "C-1002", "I want a refund of $899 for last month's invoice");

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
        SupportState state = offlineGraph().run(newConversation(), "C-1001", "I forgot my password and can't log in");

        assertThat(state.category()).isEqualTo(Category.TECHNICAL);
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("KB-101"));
    }

    @Test
    void unknownTechnicalIssueIsEscalated() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), "C-1001", "The export button is broken and not working");

        assertThat(state.category()).isEqualTo(Category.TECHNICAL);
        assertThat(state.ticketId()).isPresent();
    }

    @Test
    void generalQuestionIsAnsweredFromFaq() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), null, "What are your opening hours?");

        assertThat(state.category()).isEqualTo(Category.GENERAL);
        assertThat(state.customerId()).isEqualTo("anonymous");
        assertThat(state.response()).hasValueSatisfying(r -> assertThat(r).contains("Monday to Friday"));
    }

    @Test
    void requestForHumanGoesStraightToEscalation() throws Exception {
        SupportState state = offlineGraph().run(newConversation(), "C-1001", "This is unacceptable, I want to speak to a manager");

        assertThat(state.category()).isEqualTo(Category.ESCALATE);
        assertThat(state.trace()).hasSize(2);
        assertThat(tickets.all()).singleElement()
                .satisfies(t -> assertThat(t.priority()).isEqualTo(TicketService.Priority.URGENT));
    }

    // --- Conversation memory -------------------------------------------------------------------

    @Test
    void conversationIsRememberedAcrossTurns() throws Exception {
        SupportGraph graph = offlineGraph();
        String conversation = newConversation();

        graph.run(conversation, "C-1001", "Which plan am I on?");
        SupportState second = graph.run(conversation, null, "ok, and when is the next one due?");

        assertThat(second.customerId()).isEqualTo("C-1001");
        assertThat(second.category()).isEqualTo(Category.BILLING);
        assertThat(second.routingReason()).hasValueSatisfying(r -> assertThat(r).contains("Follow-up"));
        assertThat(second.trace()).hasSize(2);
        assertThat(second.history()).hasSize(4)
                .first().isEqualTo("Customer: Which plan am I on?");
        assertThat(graph.conversation(conversation)).isPresent();
        assertThat(graph.conversation(newConversation())).isEmpty();
    }

    @Test
    void escalationFromAnEarlierTurnDoesNotLeakIntoTheNext() throws Exception {
        SupportGraph graph = offlineGraph();
        String conversation = newConversation();

        assertThat(graph.run(conversation, "C-1001", "I want to talk to a real person").ticketId()).isPresent();
        SupportState next = graph.run(conversation, null, "What are your opening hours?");

        assertThat(next.ticketId()).isEmpty();
        assertThat(next.needsEscalation()).isFalse();
        assertThat(next.handledBy()).contains("general");
    }

    @Test
    void escalationTicketCarriesTheConversation() throws Exception {
        SupportGraph graph = offlineGraph();
        String conversation = newConversation();

        graph.run(conversation, "C-1001", "What is my plan?");
        graph.run(conversation, null, "I want a manager now");

        assertThat(tickets.all()).singleElement()
                .satisfies(t -> assertThat(t.conversation()).hasSize(3));
    }

    // --- LLM with tool calling -----------------------------------------------------------------

    @Test
    void llmBillingAgentUsesToolsToRefund() throws Exception {
        LlmClient llm = new LlmClient(new ScriptedChatModel(
                AiMessage.from("BILLING | duplicate charge"),
                toolCall("listInvoices", "{}"),
                toolCall("issueRefund", "{\"invoiceId\": \"INV-9004\", \"amount\": 9.0, \"reason\": \"Duplicate charge\"}"),
                AiMessage.from("I've refunded the duplicate $9.00 charge.")));

        SupportState state = graph(llm).run(newConversation(), "C-1003", "Why do I see two charges this month?");

        assertThat(state.handledBy()).contains("billing");
        assertThat(state.response()).contains("I've refunded the duplicate $9.00 charge.");
        assertThat(accounts.refundFor("INV-9004")).isPresent();
        assertThat(state.trace()).hasSize(4)
                .anySatisfy(step -> assertThat(step).startsWith("billing -> issueRefund"));
    }

    @Test
    void refundToolRefusesAmountsAboveTheLimit() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                AiMessage.from("BILLING | refund"),
                toolCall("issueRefund", "{\"invoiceId\": \"INV-9002\", \"amount\": 899.0, \"reason\": \"unhappy\"}"),
                toolCall("escalateToHuman", "{\"reason\": \"Refund above limit\"}"),
                AiMessage.from("A colleague will follow up."));

        SupportState state = graph(new LlmClient(model)).run(newConversation(), "C-1002", "Please refund my last invoice");

        assertThat(accounts.refundFor("INV-9002")).isEmpty();
        assertThat(model.toolResults).first().asString().contains("need human approval");
        assertThat(state.handledBy()).contains("escalation");
        assertThat(state.escalationReason()).contains("Refund above limit");
    }

    @Test
    void llmTechnicalAgentSearchesKnowledgeBase() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                AiMessage.from("TECHNICAL | app problem"),
                toolCall("checkServiceStatus", "{}"),
                toolCall("searchKnowledgeBase", "{\"query\": \"crash\"}"),
                AiMessage.from("1. Update the app. 2. Clear the cache."));

        SupportState state = graph(new LlmClient(model)).run(newConversation(), "C-1001", "The app keeps closing on me");

        assertThat(model.toolResults).hasSize(2);
        assertThat(model.toolResults.get(1)).contains("KB-102");
        assertThat(state.response()).contains("1. Update the app. 2. Clear the cache.");
    }

    @Test
    void routerSeesEarlierConversation() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel(
                AiMessage.from("GENERAL | greeting"),
                AiMessage.from("Hello! How can I help?"),
                AiMessage.from("BILLING | follow-up about the charge mentioned earlier"),
                AiMessage.from("Your latest invoice is INV-9001."));
        SupportGraph graph = graph(new LlmClient(model));
        String conversation = newConversation();

        graph.run(conversation, "C-1001", "Hi there");
        graph.run(conversation, null, "yes, that one");

        assertThat(model.userPrompts.get(2))
                .contains("Customer: Hi there", "Agent (general): Hello! How can I help?", "yes, that one");
    }

    @Test
    void mermaidDiagramListsAllAgents() throws Exception {
        assertThat(offlineGraph().mermaid())
                .contains("router", "billing", "technical", "general", "escalation");
    }

    private static AiMessage toolCall(String name, String arguments) {
        return AiMessage.from(List.of(ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .arguments(arguments)
                .build()));
    }

    /** Returns canned model replies in order and records what the agents sent to the model. */
    private static final class ScriptedChatModel implements ChatModel {
        private final Deque<AiMessage> replies;
        final List<String> userPrompts = new java.util.ArrayList<>();
        final List<String> toolResults = new java.util.ArrayList<>();

        ScriptedChatModel(AiMessage... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            var last = request.messages().get(request.messages().size() - 1);
            if (last instanceof dev.langchain4j.data.message.UserMessage user) {
                userPrompts.add(user.singleText());
            } else if (last instanceof dev.langchain4j.data.message.ToolExecutionResultMessage result) {
                toolResults.add(result.text());
            }
            return ChatResponse.builder().aiMessage(replies.removeFirst()).build();
        }
    }
}
