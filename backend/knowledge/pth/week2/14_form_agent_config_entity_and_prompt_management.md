# 14. FormAgentConfig Entity & Prompt Management
**Document Version:** 1.0  
**Target System:** reForm Modular Monolith (`com.reForm.backend.ai`)  
**Author:** Senior Technical Lead & AI System Architect  

---

## 1. Why Prompts Are Not Hardcoded Java Strings

In production SaaS platforms (Vapi, Retell AI, OpenAI Assistants API), system instructions are **never hardcoded in Java strings**.

### Problems with Hardcoded Strings:
* Updating a persona prompt requires compiling, testing, and redeploying the backend JAR.
* Form builders cannot customize their recruiter persona or candidate goal rules.

---

## 2. Database Entity Schema: `FormAgentConfig.java`

Instead of scattering prompt strings across `Form.java`, we create a dedicated entity: **`FormAgentConfig.java`** bound 1-to-1 with `Form` / `ConversationalBlock`:

```java
package com.reForm.backend.form.entity;

import com.reForm.backend.core.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * FORM AGENT CONFIGURATION ENTITY
 * Stores production AI instructions, persona prompts, model choice, 
 * voice settings, and target evaluation goals attached to a form or conversational block.
 */
@Entity
@Table(name = "form_agent_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormAgentConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    @Column(name = "model_key", nullable = false, length = 50)
    private String modelKey; // e.g. "GEMINI_3_1_LIVE"

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt; // Custom persona instructions set by John

    @Column(name = "voice_name", length = 50)
    private String voiceName; // Prebuilt voice (e.g. "Puck", "Kore")

    @Column(name = "temperature")
    private Float temperature; // e.g. 0.7

    @Column(name = "byok_api_key_encrypted", length = 512)
    private String byokApiKeyEncrypted;
}
```

---

## 3. Runtime Prompt Compilation

When candidate Sarah opens a form, `SessionContextService`:
1. Queries `formAgentConfigRepository.findByFormId(formId)`.
2. Fetches `systemPrompt` and `goals` (JSONB array).
3. Compiles final prompt string:

```text
  Final Prompt = System Prompt + "\n\nTARGET GOALS:\n" + Goals List + "\n\nSafety Instructions"
```

4. Wraps inside `buildSetupContext` payload map.
