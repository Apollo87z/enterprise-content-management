package com.ecm.enterprisecontentmanagementbackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    protected DocumentEntity() {
    }

    public DocumentEntity(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public void  setName(String name){this.name=name;}

    public void setContentType(String contentType){this.contentType = contentType;}
}
