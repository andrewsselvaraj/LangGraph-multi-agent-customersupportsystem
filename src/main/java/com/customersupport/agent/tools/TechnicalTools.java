package com.customersupport.agent.tools;

import com.customersupport.service.KnowledgeBase;
import com.customersupport.service.ServiceStatus;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/** What the technical support agent can do. */
public class TechnicalTools extends SupportTools {

    private final KnowledgeBase knowledgeBase;
    private final ServiceStatus serviceStatus;

    public TechnicalTools(KnowledgeBase knowledgeBase, ServiceStatus serviceStatus) {
        this.knowledgeBase = knowledgeBase;
        this.serviceStatus = serviceStatus;
    }

    @Tool("Search the help center for troubleshooting articles. Use short keyword queries such as 'password reset'.")
    public String searchKnowledgeBase(@P("Search keywords") String query) {
        return knowledgeBase.search(query)
                .map(a -> a.id() + " - " + a.title() + ": " + a.solution())
                .orElse("No article found for '" + query + "'. Try other keywords or escalate.");
    }

    @Tool("Check whether there is an ongoing incident or outage on our platform")
    public String checkServiceStatus() {
        return serviceStatus.current();
    }
}
