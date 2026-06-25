package com.reForm.backend.form.port;

import com.reForm.backend.form.dto.PublicFormResponseDto;

public interface IFormRenderingService  {

    public PublicFormResponseDto getPublicForm(String slug);
}
