package com.reForm.backend.form.entity.block;

import jakarta.persistence.Enumerated;

public enum BlockType {
    TEXT,
    CHOICE, 
    CONVERSATIONAL,
    STATIC
}
