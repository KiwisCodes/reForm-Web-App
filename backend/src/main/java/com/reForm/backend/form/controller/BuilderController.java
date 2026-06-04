package com.reForm.backend.form.controller;

import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.service.FormBuilderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<FormResponseDto> getForm(@PathVariable UUID id, @RequestHeader("X-Workspace-Id") UUID workspaceId){
        return ResponseEntity.ok(service.retrieveForm(workspaceId, id));
    }

    @GetMapping
    public ResponseEntity<List<FormResponseDto>> getAllForms(@RequestHeader("X-Workspace-Id") UUID workspaceId){
        return ResponseEntity.ok(service.getAllFormInWorkspace(workspaceId));
    }

    @PostMapping(params = "title")
    public ResponseEntity<FormResponseDto> createForm(@RequestHeader("X-Workspace-Id") UUID workspaceId
            , @RequestBody FormCreateDto request){
        return ResponseEntity.ok(service.createForm(request));
    }

    @PutMapping("{id}/blocks")
    public ResponseEntity<FormResponseDto> updateBlocks(@PathVariable UUID id, @RequestHeader("X-Workspace-Id") UUID workspaceId, @RequestBody FormUpdateDto request){
        return ResponseEntity.ok(service.updateBlocks(request));
    }
    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteForm(
            @PathVariable UUID id,
            @RequestHeader("X-Workspace-Id") UUID workspaceId) {
        service.delete(id, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
