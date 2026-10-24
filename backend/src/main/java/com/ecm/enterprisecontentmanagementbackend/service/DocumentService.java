package com.ecm.enterprisecontentmanagementbackend.service;

import com.ecm.enterprisecontentmanagementbackend.entity.DocumentEntity;
import com.ecm.enterprisecontentmanagementbackend.model.Document;
import com.ecm.enterprisecontentmanagementbackend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DocumentService {

    private final DocumentRepository repository;

    public DocumentService (DocumentRepository repository){
        this.repository = repository;
    }

    public List<DocumentEntity> getAllDocuments(){
        return  repository.findAll();
    }

    public Optional<DocumentEntity> getDocumentById(long id){
        return repository.findById(id);
    }

    public DocumentEntity createDocument(String name, String contentType){
        DocumentEntity document = new DocumentEntity(name, contentType);
        return repository.save(document);

    }

    public Optional<DocumentEntity> updateDocument(
            Long id, String name, String contentType
    ){
        return repository.findById(id).map(existing ->{
            existing.setName(name);
            existing.setContentType(contentType);
            return  repository.save(existing);

        });
    }

    public boolean deleteDocument( Long id){
        if (!repository.existsById(id)){
            return  false;
        }
        repository.deleteById(id);
        return  true;
    }
}
