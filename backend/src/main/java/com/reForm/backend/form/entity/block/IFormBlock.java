package com.reForm.backend.form.entity.block;

import org.springframework.cglib.core.Block;

import java.util.UUID;

public interface IFormBlock {

    public UUID getId();

    public BlockType getType();

    public Integer getSortOrder();

}
