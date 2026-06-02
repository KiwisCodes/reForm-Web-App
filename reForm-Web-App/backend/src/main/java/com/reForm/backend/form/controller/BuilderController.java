package com.reForm.backend.form.controller;

import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.port.IFormBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/form")
@RequiredArgsConstructor
public class BuilderController {

    private final IFormBuilderService service;

    @GetMapping("{id}")
    public Form getForm(@PathVariable UUID id, @RequestHeader("X-Workspace-Id") UUID workspaceId){
        return service.retrieveForm(id, workspaceId);
    }

    @GetMapping
    public List<Form> getAllForms(@RequestHeader("X-Workspace-Id") UUID workspaceId){
        return service.listAllForms(workspaceId);
    }

    @PostMapping(params = "title")
    public Form createForm(@RequestHeader("X-Workspace-Id") UUID workspaceId
                            , @RequestBody Map<String, String> payload){
        String title = payload.get("title");
        return service.createForm(title, workspaceId);
    }
}