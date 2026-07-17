package com.reForm.backend.form.port;

import com.reForm.backend.form.dto.FormSubmissionDto;

import java.util.UUID;

public interface IFormQueryPort {

    FormSubmissionDto fetchForm(UUID formId);
}
