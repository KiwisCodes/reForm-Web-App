package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import com.reForm.backend.form.entity.block.AbstractBlock;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "forms",
        indexes = {
        @Index(name = "idx_form_slug", columnList = "slug")
        }
)
@NoArgsConstructor
@AllArgsConstructor

public class Form extends BaseEntity {

    private String title;

    @Convert(converter = AbstractBlockConverter.class)

    private List<AbstractBlock> blocks = new ArrayList<>();

    private UUID workspaceId;

    private String slug;

    @Enumerated(EnumType.STRING)
    private FormStatus status;

    @Override
    protected void onCreate() {

        super.onCreate();

        if (this.slug != null && !this.slug.trim().isEmpty()) {
            return;
        }

        if (this.title == null || this.title.trim().isEmpty()) {
            throw new IllegalStateException("Title must be set before generating a slug.");
        }

        this.slug = slugify(this.title);
    }

    private String slugify(String input) {
        String baseSlug = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("^-|-$", "");

        // Append a short 6-character random suffix
        String shortId = UUID.randomUUID().toString().substring(0, 6);

        return baseSlug + "-" + shortId;
    }


}
