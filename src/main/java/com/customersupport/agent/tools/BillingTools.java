package com.customersupport.agent.tools;

import com.customersupport.service.CustomerAccountService;
import com.customersupport.service.CustomerAccountService.Account;
import com.customersupport.service.CustomerAccountService.Invoice;
import com.customersupport.service.CustomerAccountService.Refund;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Collectors;

/** What the billing agent can do, always scoped to the customer in the conversation. */
public class BillingTools extends SupportTools {

    private final CustomerAccountService accounts;
    private final String customerId;
    private final BigDecimal refundLimit;

    public BillingTools(CustomerAccountService accounts, String customerId, BigDecimal refundLimit) {
        this.accounts = accounts;
        this.customerId = customerId;
        this.refundLimit = refundLimit;
    }

    @Tool("Get the customer's name, plan, monthly price and next billing date")
    public String getAccount() {
        return accounts.find(customerId)
                .map(a -> "Name: " + a.name() + ", plan: " + a.plan() + " ($" + a.monthlyPrice()
                        + "/month), next billing date: " + a.nextBillingDate())
                .orElse("No account found for customer " + customerId + ". Ask the customer for their customer ID.");
    }

    @Tool("List the customer's invoices with id, date, amount and status (PAID, OVERDUE or REFUNDED)")
    public String listInvoices() {
        return accounts.find(customerId)
                .map(a -> a.invoices().stream()
                        .map(i -> i.id() + " " + i.date() + " $" + i.amount() + " "
                                + (accounts.refundFor(i.id()).isPresent() ? "REFUNDED" : i.status()))
                        .collect(Collectors.joining("\n")))
                .orElse("No account found for customer " + customerId + ".");
    }

    @Tool("Refund all or part of one of the customer's invoices. Only use it for a clear billing error "
            + "such as a duplicate charge, and only after checking the invoices.")
    public String issueRefund(@P("Invoice id, for example INV-9004") String invoiceId,
                              @P("Amount to refund in USD") double amount,
                              @P("Why the refund is justified") String reason) {
        Optional<Account> account = accounts.find(customerId);
        Optional<Invoice> invoice = account.flatMap(a -> a.invoices().stream()
                .filter(i -> i.id().equalsIgnoreCase(invoiceId))
                .findFirst());
        if (invoice.isEmpty()) {
            return "Invoice " + invoiceId + " does not belong to this customer. No refund issued.";
        }
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.compareTo(refundLimit) > 0) {
            return "Refunds above $" + refundLimit + " need human approval. Call escalateToHuman instead.";
        }
        if (value.compareTo(invoice.get().amount()) > 0) {
            return "Refund amount is larger than the invoice ($" + invoice.get().amount() + "). No refund issued.";
        }
        return accounts.issueRefund(customerId, invoice.get().id(), value, reason)
                .map(Refund::id)
                .map(id -> "Refund " + id + " of $" + value + " issued for " + invoiceId
                        + ". It appears on the customer's statement within 5-7 business days.")
                .orElse("Invoice " + invoiceId + " has already been refunded. No new refund issued.");
    }
}
