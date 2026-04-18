package com.expygen.service;

import com.expygen.dto.ContactRequestForm;
import com.expygen.entity.ContactRequest;
import com.expygen.entity.ContactRequestStatus;
import com.expygen.repository.ContactRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRequestRepository contactRequestRepository;

    public void save(ContactRequestForm form) {
        ContactRequest entity = ContactRequest.builder()
                .storeName(form.getStoreName())
                .contactPerson(form.getContactPerson())
                .email(form.getEmail())
                .phone(form.getPhone())
                .topic(form.getTopic())
                .message(form.getMessage())
                .status(ContactRequestStatus.NEW)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        contactRequestRepository.save(entity);
    }

    public List<ContactRequest> findAll() {
        return contactRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public ContactRequest findById(Long id) {
        return contactRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact request not found: " + id));
    }

    public void updateStatus(Long id, ContactRequestStatus status) {
        ContactRequest request = findById(id);
        request.setStatus(status);
        request.setUpdatedAt(LocalDateTime.now());
        contactRequestRepository.save(request);
    }

    public long countNewRequests() {
        return contactRequestRepository.countByStatus(ContactRequestStatus.NEW);
    }
}