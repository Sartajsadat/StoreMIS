-- Reference data required by every installation. The first admin user is created at
-- application start-up (BootstrapAdminRunner) from environment variables, never stored here.

INSERT INTO currencies (code, name, symbol, decimal_places) VALUES
    ('AFN', 'Afghan Afghani', '؋', 2),
    ('USD', 'US Dollar', '$', 2);

INSERT INTO roles (code, name) VALUES
    ('ADMIN', 'Administrator'),
    ('ENTRY_USER', 'Entry User');

INSERT INTO permissions (code, description, default_for_entry_user) VALUES
    ('RECEIVE_CUSTOMER_PAYMENTS', 'Record customer receipts against invoices', TRUE),
    ('GIVE_DISCOUNT',             'Give item and overall discounts in POS', TRUE),
    ('RECORD_PURCHASES',          'Create and confirm purchases, register suppliers', FALSE),
    ('PAY_SUPPLIERS',             'Record supplier payments', FALSE),
    ('RECORD_EXPENSES',           'Record expenses', FALSE),
    ('PROCESS_SALES_RETURNS',     'Create sales returns', FALSE),
    ('VIEW_COST_PRICES',          'See average cost and margins', FALSE),
    ('ADJUST_STOCK',              'Stock adjustments and damage entries', FALSE),
    ('VIEW_ALL_SALES',            'See sales of all users', FALSE);

INSERT INTO business_settings (id, business_name, base_currency_code, default_currency_code)
VALUES (1, 'My Store', 'AFN', 'AFN');

INSERT INTO document_sequences (doc_type, prefix, current_year) VALUES
    ('SALE',             'INV', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('PURCHASE',         'PUR', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('SALES_RETURN',     'SRN', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('PURCHASE_RETURN',  'PRN', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('CUSTOMER_PAYMENT', 'RCV', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('SUPPLIER_PAYMENT', 'PAY', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('EXPENSE',          'EXP', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('STOCK_ADJUSTMENT', 'ADJ', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('CASH_TRANSFER',    'TRF', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul')),
    ('CASH_TXN',         'CTX', EXTRACT(YEAR FROM now() AT TIME ZONE 'Asia/Kabul'));
