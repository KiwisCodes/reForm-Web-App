package com.reForm.backend.submission.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.reForm.backend.core.domain.BaseEntity;
import com.reForm.backend.form.entity.Form;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name="submission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Submission extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answers;

    private String submitterIp; // Why do we need this?

    private String userAgent;
}
