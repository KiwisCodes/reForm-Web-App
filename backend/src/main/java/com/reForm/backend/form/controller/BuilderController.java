package com.reForm.backend.form.controller;

import com.reForm.backend.auth.service.CustomerUserDetails;
import com.reForm.backend.form.dto.FormCreateDto;
import com.reForm.backend.form.dto.FormResponseDto;
import com.reForm.backend.form.dto.FormUpdateDto;
import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.port.IFormBuilderService;
import com.reForm.backend.form.service.FormBuilderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("api/v1/form")
@RequiredArgsConstructor

public class BuilderController {

    private final IFormBuilderService service;

    @GetMapping("{formId}")
    @PreAuthorize("@formSecurity.isMember(authentication, #formId)")
    public ResponseEntity<FormResponseDto> getForm(@PathVariable UUID formId, @RequestHeader("X-Workspace-Id") UUID workspaceId){
        FormResponseDto formResponseDto = service.retrieveForm(workspaceId, formId);
        return ResponseEntity.status(HttpStatus.OK).body(formResponseDto);
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<List<FormResponseDto>> getAllForms(@RequestHeader("X-Workspace-Id") UUID workspaceId){
        List<FormResponseDto> listOfFormResponseDto = service.getAllFormInWorkspace(workspaceId);
        return ResponseEntity.status(HttpStatus.OK).body(listOfFormResponseDto);
    }

    @PostMapping(params = "title")
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId)")
    public ResponseEntity<FormResponseDto> createForm(@RequestHeader("X-Workspace-Id") UUID workspaceId // Add list of blocks
            , @RequestBody FormCreateDto request
            , @AuthenticationPrincipal CustomerUserDetails customerUserDetails){
        FormResponseDto formResponseDto = service.createForm(request, customerUserDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(formResponseDto);
    }

    @PutMapping("{formId}/blocks")
    @PreAuthorize("@formSecurity.isMember(authentication, #formId)")
    public ResponseEntity<FormResponseDto> updateBlocks(@PathVariable UUID formId,
                                                        @RequestHeader("X-Workspace-Id") UUID workspaceId,
                                                        @RequestBody FormUpdateDto request){

        FormResponseDto formResponseDto = service.updateBlocks(request);
        return ResponseEntity.status(HttpStatus.OK).body(formResponseDto);
    }
    @DeleteMapping("{formId}")
    @PreAuthorize("@formSecurity.isCreator(authentication, #formId)")
    public ResponseEntity<Void> deleteForm(
            @PathVariable UUID formId,
            @RequestHeader("X-Workspace-Id") UUID workspaceId) {
            /* a second independent check at the service layer, so that if @PreAuthorize were ever
            misconfigured, disabled, or bypassed, the service layer still refuses to delete a form
            whose workspaceId doesn't match what the client claims? */
        service.delete(formId, workspaceId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("{formId}/publish")
    @PreAuthorize("@formSecurity.isCreator(authentication, #formId)")
    public ResponseEntity<FormResponseDto> publishForm(@PathVariable UUID formId,
                                                        @RequestHeader("X-Workspace-Id") UUID workspaceId){

        FormResponseDto formResponseDto = service.publishForm(formId, workspaceId);
        return ResponseEntity.status(HttpStatus.OK).body(formResponseDto);
    }
}
