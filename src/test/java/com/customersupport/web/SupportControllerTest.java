package com.customersupport.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "support.llm.api-key=")
@AutoConfigureMockMvc
class SupportControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void chatRunsTheAgentGraph() throws Exception {
        mvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "C-1001", "message": "Can you explain my invoice?"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLING"))
                .andExpect(jsonPath("$.handledBy").value("billing"))
                .andExpect(jsonPath("$.escalated").value(false))
                .andExpect(jsonPath("$.trace.length()").value(2));
    }

    @Test
    void escalatedChatShowsUpInTicketQueue() throws Exception {
        mvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "C-1002", "message": "I want to talk to a real person"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalated").value(true))
                .andExpect(jsonPath("$.ticketId").exists());

        mvc.perform(get("/api/support/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value("C-1002"));
    }

    @Test
    void blankMessageIsRejected() throws Exception {
        mvc.perform(post("/api/support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest());
    }
}
