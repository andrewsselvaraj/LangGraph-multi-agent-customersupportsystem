package com.customersupport.agent;

import com.customersupport.agent.tools.BillingTools;
import com.customersupport.graph.SupportState;
import com.customersupport.llm.LlmClient;
import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.CustomerAccountService.Account;
import com.customersupport.service.CustomerAccountService.Invoice;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Billing specialist. With an LLM it works as a tool-calling agent: it looks up the account
 * and invoices itself, issues refunds for clear billing errors, and calls a human when a request
 * is out of policy. Refunds above the configured limit always go to a human, in either mode.
 */
@Component
public class BillingAgent implements NodeAction<SupportState> {

    static final String NAME = "billing";

    private static final Pattern AMOUNT = Pattern.compile("\\$\\s?(\\d+(?:[.,]\\d{1,2})?)");

    private static final String SYSTEM_PROMPT = """
            You are a billing support specialist. Use your tools to look up the customer's account and \
            invoices instead of guessing. You may refund clear billing errors such as duplicate charges, \
            up to %s USD. For anything else that needs a decision you cannot make, call escalateToHuman. \
            Reply to the customer in a few friendly sentences.""";

    private final LlmClient llm;
    private final CustomerAccountService accounts;
    private final BigDecimal refundLimit;

    public BillingAgent(LlmClient llm,
                        CustomerAccountService accounts,
                        @Value("${support.escalation.refund-limit:500}") BigDecimal refundLimit) {
        this.llm = llm;
        this.accounts = accounts;
        this.refundLimit = refundLimit;
    }

    @Override
    public Map<String, Object> apply(SupportState state) {
        String lower = state.message().toLowerCase(Locale.ROOT);

        Optional<BigDecimal> amount = extractAmount(state.message());
        if (lower.contains("refund") && amount.isPresent() && amount.get().compareTo(refundLimit) > 0) {
            return AgentReply.escalate(state, NAME,
                    "Refund of $" + amount.get() + " exceeds the $" + refundLimit + " approval limit");
        }

        BillingTools tools = new BillingTools(accounts, state.customerId(), refundLimit);
        return llm.runWithTools(SYSTEM_PROMPT.formatted(refundLimit),
                        "Customer ID: " + state.customerId() + "\n\n" + state.promptWithHistory(), tools)
                .map(run -> AgentReply.fromRun(state, NAME, run, tools))
                .orElseGet(() -> ruleBasedAnswer(state, lower));
    }

    private Map<String, Object> ruleBasedAnswer(SupportState state, String lower) {
        Optional<Account> found = accounts.find(state.customerId());
        if (found.isEmpty()) {
            return AgentReply.resolved(state, NAME, "I couldn't find a billing account for customer ID \""
                    + state.customerId() + "\". Please reply with the customer ID shown on your invoice "
                    + "and I'll look into it right away.");
        }
        Account account = found.get();
        String greeting = "Hi " + account.name().split(" ")[0] + ", ";

        if (lower.contains("refund") || lower.contains("charged twice") || lower.contains("double charge")
                || lower.contains("duplicate")) {
            Optional<Invoice> duplicate = account.invoices().stream()
                    .filter(i -> account.invoices().stream()
                            .anyMatch(o -> o != i && o.date().equals(i.date()) && o.amount().equals(i.amount())))
                    .filter(i -> accounts.refundFor(i.id()).isEmpty())
                    .findFirst();
            if (duplicate.isPresent()) {
                Invoice invoice = duplicate.get();
                accounts.issueRefund(state.customerId(), invoice.id(), invoice.amount(), "Duplicate charge");
                return AgentReply.resolved(state, NAME, greeting + "I found a duplicate charge of $" + invoice.amount()
                        + " on " + invoice.date() + " (" + invoice.id() + ") and refunded it. It will appear on "
                        + "your statement within 5-7 business days.", List.of(NAME + " -> refund " + invoice.id()));
            }
            return AgentReply.escalate(state, NAME, "Refund requested but no billing error found on the account");
        }

        if (lower.contains("overdue") || lower.contains("unpaid") || lower.contains("balance") || lower.contains("owe")) {
            String overdue = account.invoices().stream()
                    .filter(i -> "OVERDUE".equals(i.status()))
                    .map(i -> i.id() + " ($" + i.amount() + ", " + i.date() + ")")
                    .collect(Collectors.joining(", "));
            return AgentReply.resolved(state, NAME, overdue.isEmpty()
                    ? greeting + "your account is fully paid up. Thank you!"
                    : greeting + "these invoices are overdue: " + overdue + ". You can pay them under Billing > Invoices.");
        }

        if (lower.contains("plan") || lower.contains("upgrade") || lower.contains("downgrade") || lower.contains("price")) {
            return AgentReply.resolved(state, NAME, greeting + "you're on the " + account.plan() + " plan at $"
                    + account.monthlyPrice() + "/month. You can change plans any time under Billing > Plan; "
                    + "changes are prorated on your next invoice.");
        }

        Invoice latest = account.invoices().get(0);
        return AgentReply.resolved(state, NAME, greeting + "your latest invoice is " + latest.id() + " for $"
                + latest.amount() + " (" + latest.status().toLowerCase(Locale.ROOT) + "). Your " + account.plan()
                + " plan renews on " + account.nextBillingDate() + ".");
    }

    static Optional<BigDecimal> extractAmount(String message) {
        Matcher m = AMOUNT.matcher(message);
        return m.find() ? Optional.of(new BigDecimal(m.group(1).replace(',', '.'))) : Optional.empty();
    }
}
