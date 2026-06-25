package com.reForm.backend.submission.repository;

import com.reForm.backend.submission.entity.Submission;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;


import java.awt.print.Pageable;
import java.util.UUID;

public interface  SubmissionRepository extends JpaRepository<Submission, UUID> {

    Page<Submission> findByFormIdOrderByCreatedAtDesc(UUID uuid, Pageable pageable);
}
