package com.reForm.backend.ai.strategy.block;

import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import com.reForm.backend.form.entity.block.conversationalBlock.RubricCriterion;

import java.util.List;
import java.util.Map;

/**
 * BLOCK EXECUTION STRATEGY INTERFACE (Strategy Pattern)
 * 
 * ROLE IN ARCHITECTURE:
 * Encapsulates block-type-specific execution behavior (prompt goal compilation, tool whitelist
 * filtering, voice override resolution, and evaluation rubric extraction).
 * 
 * SOLID GOVERNANCE:
 * - Single Responsibility (SRP): Separates entity data from session execution behavior.
 * - Open-Closed (OCP): Adding new block types requires creating a new strategy bean — zero changes
 *   to SessionContextService or WebSocket adapters.
 */
public interface IBlockExecutionStrategy {

    /**
     * Determines whether this strategy supports the given block type.
     */
    boolean supports(BlockType blockType);

    /**
     * Compiles structured goal checklist section text for injection into system instruction prompt.
     */
    String compileGoalSection(AbstractBlock block);

    /**
     * Filters global function tool declarations against block's allowed tool whitelist.
     */
    List<Map<String, Object>> filterAllowedTools(AbstractBlock block, List<Map<String, Object>> globalTools);

    /**
     * Resolves block-level voice override choice if present (Level 3 cascade).
     */
    String resolveVoiceName(AbstractBlock block);

    /**
     * Extracts post-session evaluation rubric criteria for EvaluationAgent.
     */
    List<RubricCriterion> getEvaluationRubric(AbstractBlock block);
}
