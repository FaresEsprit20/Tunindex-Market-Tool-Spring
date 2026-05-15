package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.Invoice;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;

import java.util.ArrayList;
import java.util.List;

public class InvoiceValidator {

    public static void validate(InvoiceDto invoice) {
        List<String> errors = new ArrayList<>();

        if (invoice == null) {
            errors.add("Invoice cannot be null");
            throw new InvalidEntityException("Invoice is null", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }

        // Validate invoice number
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            errors.add("Invoice number must not be null or empty");
        }
        if (invoice.getInvoiceNumber() != null && invoice.getInvoiceNumber().length() > 50) {
            errors.add("Invoice number must not exceed 50 characters");
        }

        // Validate user ID
        if (invoice.getUserId() == null || invoice.getUserId() <= 0) {
            errors.add("User ID must be a positive number");
        }

        // Validate transaction ID
        if (invoice.getTransactionId() == null || invoice.getTransactionId() <= 0) {
            errors.add("Transaction ID must be a positive number");
        }

        // Validate amount
        if (invoice.getAmount() == null) {
            errors.add("Amount must not be null");
        }
        if (invoice.getAmount() != null && invoice.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be greater than 0");
        }

        // Validate tax amount (can be null, but if present must be >= 0)
        if (invoice.getTaxAmount() != null && invoice.getTaxAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
            errors.add("Tax amount cannot be negative");
        }

        // Validate total amount
        if (invoice.getTotalAmount() != null && invoice.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Total amount must be greater than 0");
        }

        // Validate currency
        if (invoice.getCurrency() == null || invoice.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }

        // Validate status
        if (invoice.getStatus() == null) {
            errors.add("Invoice status must not be null");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid invoice", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }
    }

    public static void validatePayment(Invoice invoice) {
        List<String> errors = new ArrayList<>();

        if (invoice == null) {
            errors.add("Invoice not found");
            throw new InvalidEntityException("Invoice not found", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            errors.add("Invoice is already paid");
        }

        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            errors.add("Cannot pay a cancelled invoice");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Cannot process invoice payment", ErrorCodes.INVOICE_ALREADY_PAID, errors);
        }
    }
}