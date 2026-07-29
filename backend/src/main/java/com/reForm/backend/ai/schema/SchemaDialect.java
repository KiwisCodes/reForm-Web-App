package com.reForm.backend.ai.schema;

// The structural shape (type/properties/required/enum/items keys) is the same across vendors —
// Gemini's own docs describe its Schema object as a subset of the OpenAPI 3.0 Schema object. The
// one thing that actually differs is the literal token used for each primitive type: Gemini uses
// its own uppercase Type enum (STRING, BOOLEAN, ...), while OpenAI/Claude/standard JSON Schema use
// lowercase ("string", "boolean", ...). This isolates that single difference so BlockSchemaGenerator
// stays vendor-agnostic everywhere else.
public enum SchemaDialect {

    GEMINI("STRING", "BOOLEAN", "INTEGER", "ARRAY", "OBJECT"),
    JSON_SCHEMA("string", "boolean", "integer", "array", "object");

    private final String stringType;
    private final String booleanType;
    private final String integerType;
    private final String arrayType;
    private final String objectType;

    SchemaDialect(String stringType, String booleanType, String integerType, String arrayType, String objectType) {
        this.stringType = stringType;
        this.booleanType = booleanType;
        this.integerType = integerType;
        this.arrayType = arrayType;
        this.objectType = objectType;
    }

    public String stringType() {
        return stringType;
    }

    public String booleanType() {
        return booleanType;
    }

    public String integerType() {
        return integerType;
    }

    public String arrayType() {
        return arrayType;
    }

    public String objectType() {
        return objectType;
    }
}
