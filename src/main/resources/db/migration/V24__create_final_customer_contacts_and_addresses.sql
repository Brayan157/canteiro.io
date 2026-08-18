ALTER TABLE final_customer
    ADD CONSTRAINT uk_final_customer_id_company UNIQUE (id, company_id);

CREATE TABLE final_customer_contact (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    final_customer_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_customer_contact_customer_company FOREIGN KEY (final_customer_id, company_id)
        REFERENCES final_customer (id, company_id),
    CONSTRAINT ck_final_customer_contact_name_not_blank CHECK (LENGTH(BTRIM(name)) > 0),
    CONSTRAINT ck_final_customer_contact_email_not_blank CHECK (email IS NULL OR LENGTH(BTRIM(email)) > 0),
    CONSTRAINT ck_final_customer_contact_phone_not_blank CHECK (phone IS NULL OR LENGTH(BTRIM(phone)) > 0),
    CONSTRAINT ck_final_customer_contact_channel CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE INDEX ix_final_customer_contact_customer_active
    ON final_customer_contact (company_id, final_customer_id, active);

CREATE TABLE final_customer_address (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,
    final_customer_id UUID NOT NULL,
    label VARCHAR(100),
    postal_code VARCHAR(10),
    street VARCHAR(255) NOT NULL,
    number VARCHAR(50),
    complement VARCHAR(255),
    district VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(2) NOT NULL,
    country VARCHAR(2) NOT NULL DEFAULT 'BR',
    primary_address BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_customer_address_customer_company FOREIGN KEY (final_customer_id, company_id)
        REFERENCES final_customer (id, company_id),
    CONSTRAINT ck_final_customer_address_street_not_blank CHECK (LENGTH(BTRIM(street)) > 0),
    CONSTRAINT ck_final_customer_address_city_not_blank CHECK (LENGTH(BTRIM(city)) > 0),
    CONSTRAINT ck_final_customer_address_state_format CHECK (state ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_final_customer_address_country_format CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_final_customer_address_postal_code_format CHECK (
        postal_code IS NULL OR postal_code ~ '^[0-9]{8}$'
    )
);

CREATE INDEX ix_final_customer_address_customer_active
    ON final_customer_address (company_id, final_customer_id, active);
