CREATE INDEX ix_invoice_item_company_allocation_invoice
    ON invoice_item (company_id, billing_allocation_id, invoice_id)
    INCLUDE (allocated_amount);
