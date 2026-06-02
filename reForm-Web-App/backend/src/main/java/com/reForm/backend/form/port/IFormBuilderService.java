package com.reForm.backend.form.port;

import com.reForm.backend.form.entity.Form;
import com.reForm.backend.form.entity.block.AbstractBlock;

import java.util.List;
import java.util.UUID;

public interface IFormBuilderService{
    public Form createForm(String title, UUID workspaceId);

    public Form retrieveForm(UUID id, UUID workspaceId);

    public List<Form> listAllForms(UUID workspaceId);

    public Form updateBlocks(UUID id, UUID workspaceId, List<AbstractBlock> blocks);

    public void deleteForm(UUID id, UUID workspaceId);
}