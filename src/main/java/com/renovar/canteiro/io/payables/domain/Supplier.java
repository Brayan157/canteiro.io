package com.renovar.canteiro.io.payables.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Supplier(UUID id, UUID companyId, String name, String document, String contactName, String contactPhone, String contactEmail, boolean active, long version, Instant createdAt, Instant updatedAt) {
    public Supplier {
        Objects.requireNonNull(companyId,"Company is required");
        name=PayableCatalogValues.text(name,255,true);document=PayableCatalogValues.document(document);
        contactName=PayableCatalogValues.text(contactName,120,false);
        contactPhone=PayableCatalogValues.text(contactPhone,40,false);
        contactEmail=PayableCatalogValues.text(contactEmail,254,false);
        if(contactEmail!=null){try{new jakarta.mail.internet.InternetAddress(contactEmail,true).validate();}
            catch(jakarta.mail.internet.AddressException e){throw new IllegalArgumentException("Invalid contact email");}}
        if(version<0)throw new IllegalArgumentException("Version must be non-negative");
    }
    public Supplier(UUID id,UUID companyId,String name,String document,boolean active,long version,Instant createdAt,Instant updatedAt){
        this(id,companyId,name,document,null,null,null,active,version,createdAt,updatedAt);
    }
    public static Supplier create(UUID company,String name,String document) {return new Supplier(null,company,name,document,true,0,null,null);}
    public Supplier update(String name,String document) {requireActive();return new Supplier(id,companyId,name,document,contactName,contactPhone,contactEmail,true,version,createdAt,updatedAt);}
    public Supplier withContact(String contactName,String contactPhone,String contactEmail){
        return new Supplier(id,companyId,name,document,contactName,contactPhone,contactEmail,active,version,createdAt,updatedAt);
    }
    public Supplier deactivate() {requireActive();return new Supplier(id,companyId,name,document,contactName,contactPhone,contactEmail,false,version,createdAt,updatedAt);}
    private void requireActive(){if(!active)throw new IllegalStateException("Inactive supplier cannot be changed");}
}
