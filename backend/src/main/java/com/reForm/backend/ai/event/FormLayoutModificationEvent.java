package com.reForm.backend.ai.event;

import com.reForm.backend.form.entity.block.AbstractBlock;
import java.util.List;
import java.util.UUID;

/**
 * FORM LAYOUT MODIFICATION EVENT
 * 
 * Immutable event record published when a form layout change is requested
 * (e.g. by Mode 2 chat or Mode 4 voice tool handler). Consumed asynchronously by LayoutAgent.
 */
public record FormLayoutModificationEvent(
    UUID formId,
    String userIntent,
    List<AbstractBlock> targetBlocks,
    String sessionId
) {}