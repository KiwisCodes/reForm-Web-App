package com.reForm.backend.submission.entity;

import tools.jackson.databind.JsonNode;
import com.reForm.backend.core.domain.BaseEntity;
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

    private UUID formId;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode answers;

//    private String submitterIp; // Why do we need this?

    private String userAgent;
}
