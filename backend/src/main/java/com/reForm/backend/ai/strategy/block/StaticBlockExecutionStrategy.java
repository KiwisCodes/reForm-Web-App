package com.reForm.backend.ai.strategy.block;

import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import com.reForm.backend.form.entity.block.conversationalBlock.RubricCriterion;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * STATIC BLOCK EXECUTION STRATEGY
 * 
 * Concrete strategy bean for STATIC blocks (no-op strategy).
 * Static blocks do not define goals, tool whitelists, voice overrides, or rubrics.
 */
@Component
public class StaticBlockExecutionStrategy implements IBlockExecutionStrategy {

    @Override
    public boolean supports(BlockType blockType) {
        return blockType == BlockType.STATIC;
    }

    @Override
    public String compileGoalSection(AbstractBlock block) {
        return "";
    }

    @Override
    public List<Map<String, Object>> filterAllowedTools(AbstractBlock block, List<Map<String, Object>> globalTools) {
        return globalTools != null ? globalTools : Collections.emptyList();
    }

    @Override
    public String resolveVoiceName(AbstractBlock block) {
        return null;
    }

    @Override
    public List<RubricCriterion> getEvaluationRubric(AbstractBlock block) {
        return Collections.emptyList();
    }
}
