package com.reForm.backend.form.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.reForm.backend.form.dto.PublicFormResponseDto;
import com.reForm.backend.form.port.IFormRenderingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/f")
@RequiredArgsConstructor

public class PublicRenderController {

    private final IFormRenderingService service;

    @GetMapping("{slug}")
    public PublicFormResponseDto getPublicForm(@PathVariable String slug){
        return service.getPublicForm(slug);
    }

}
