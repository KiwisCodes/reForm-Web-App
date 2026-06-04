package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import com.reForm.backend.form.entity.block.AbstractBlock;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forms")

public class Form extends BaseEntity {

    private String title;

    @Convert(converter = AbstractBlockConverter.class)

    private List<AbstractBlock> blocks = new ArrayList<>();

    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    private FormStatus status;


}
