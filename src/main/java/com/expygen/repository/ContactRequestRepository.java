package com.expygen.repository;

import com.expygen.entity.ContactRequest;
import com.expygen.entity.ContactRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    List<ContactRequest> findAllByOrderByCreatedAtDesc();
    long countByStatus(ContactRequestStatus status);
}