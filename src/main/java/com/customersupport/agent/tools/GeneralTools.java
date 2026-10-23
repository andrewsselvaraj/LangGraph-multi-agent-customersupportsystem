package com.customersupport.agent.tools;

import com.customersupport.service.Faq;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/** What the general support agent can do. */
public class GeneralTools extends SupportTools {

    private final Faq faq;

    public GeneralTools(Faq faq) {
        this.faq = faq;
    }

    @Tool("Look up company policy and FAQs: opening hours, contact details, cancellation, privacy, feedback")
    public String searchFaq(@P("Topic keywords") String topic) {
        return faq.answer(topic).orElse("No FAQ entry for '" + topic + "'.");
    }
}
