package com.reForm.backend.form.entity.block;

import java.util.UUID;

public interface IFormBlock {

    public UUID getId();

    public String getType();

    public Integer getSortOrder();

}
