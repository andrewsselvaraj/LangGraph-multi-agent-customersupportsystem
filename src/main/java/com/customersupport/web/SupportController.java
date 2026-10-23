package com.customersupport.web;

import com.customersupport.graph.SupportGraph;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.TicketService;
import com.customersupport.service.TicketService.Ticket;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportGraph graph;
    private final TicketService tickets;
    private final LlmClient llm;

    public SupportController(SupportGraph graph, TicketService tickets, LlmClient llm) {
        this.graph = graph;
        this.tickets = tickets;
        this.llm = llm;
    }

    /** Runs one customer message through the multi-agent graph. */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();
        return ChatResponse.from(conversationId, graph.run(conversationId, request.customerId(), request.message()));
    }

    /** The full history of a conversation, as remembered by the graph's checkpointer. */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationView> conversation(@PathVariable String conversationId) {
        return ResponseEntity.of(graph.conversation(conversationId)
                .map(state -> new ConversationView(conversationId, state.customerId(), state.history())));
    }

    public record ConversationView(String conversationId, String customerId, List<String> history) {
    }

    /** Tickets waiting for a human agent. */
    @GetMapping("/tickets")
    public Collection<Ticket> tickets() {
        return tickets.all();
    }

    /** The agent graph as a Mermaid diagram (paste it into mermaid.live). */
    @GetMapping(value = "/graph", produces = MediaType.TEXT_PLAIN_VALUE)
    public String graph() {
        return graph.mermaid();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "llmEnabled", llm.enabled());
    }
}
