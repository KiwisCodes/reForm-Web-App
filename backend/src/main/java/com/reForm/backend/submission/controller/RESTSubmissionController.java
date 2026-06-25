package com.reForm.backend.submission.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.repository.FormRepository;
import com.reForm.backend.submission.dto.SubmissionResponseDto;
import com.reForm.backend.submission.port.ISubmissionProcessor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor

public class RESTSubmissionController {

    private final ISubmissionProcessor processor;

    private final FormRepository repository;

    @PostMapping("{slug}")
    public SubmissionResponseDto saveSubmission(@PathVariable String slug, @RequestBody JsonNode answers, HttpServletRequest request){
        Form form = repository.findBySlug(slug).orElseThrow(() -> new IllegalStateException("Form does not exist"));
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        String clientIp = (xForwardedFor != null && !xForwardedFor.isEmpty())
                ? xForwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();

        // 2. Extract the User-Agent
        String userAgent = request.getHeader("User-Agent");
        UUID formId = form.getId();
        return processor.saveSubmission(formId, answers, clientIp, userAgent);
    }
}
