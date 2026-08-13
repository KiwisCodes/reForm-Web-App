package com.reForm.backend.ai.strategy.block;

import com.reForm.backend.form.entity.block.AbstractBlock;
import com.reForm.backend.form.entity.block.BlockType;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalBlock;
import com.reForm.backend.form.entity.block.conversationalBlock.ConversationalGoal;
import com.reForm.backend.form.entity.block.conversationalBlock.RubricCriterion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CONVERSATIONAL BLOCK EXECUTION STRATEGY
 * 
 * Concrete strategy bean for CONVERSATIONAL blocks.
 * Handles goal checklist formatting, tool whitelist filtering, block voice override resolution,
 * and rubric extraction.
 */
@Slf4j
@Component
public class ConversationalBlockExecutionStrategy implements IBlockExecutionStrategy {

    @Override
    public boolean supports(BlockType blockType) {
        return blockType == BlockType.CONVERSATIONAL;
    }

    @Override
    public String compileGoalSection(AbstractBlock block) {
        if (!(block instanceof ConversationalBlock convBlock)) {
            return "";
        }

        List<ConversationalGoal> goals = convBlock.getGoals();
        if (goals == null || goals.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[INTERVIEW GOALS CHECKLIST]").append(System.lineSeparator());
        sb.append("Cover the following topics with the respondent during this conversational section:").append(System.lineSeparator());

        for (ConversationalGoal goal : goals) {
            sb.append("- [ ] ").append(goal.key()).append(": ").append(goal.title());
            if (goal.description() != null && !goal.description().isBlank()) {
                sb.append(" (").append(goal.description()).append(")");
            }
            if (goal.isRequired()) {
                sb.append(" [REQUIRED]");
            }
            sb.append(System.lineSeparator());
        }

        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> filterAllowedTools(AbstractBlock block, List<Map<String, Object>> globalTools) {
        if (!(block instanceof ConversationalBlock convBlock) || globalTools == null || globalTools.isEmpty()) {
            return globalTools != null ? globalTools : Collections.emptyList();
        }

        List<String> allowedToolNames = convBlock.getAllowedToolNames();
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return globalTools; // Empty whitelist means all tools are allowed
        }

        List<Map<String, Object>> filteredGroup = new ArrayList<>();
        for (Map<String, Object> toolGroup : globalTools) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> declarations = (List<Map<String, Object>>) toolGroup.get("functionDeclarations");
            if (declarations == null) {
                filteredGroup.add(toolGroup);
                continue;
            }

            List<Map<String, Object>> allowedDeclarations = declarations.stream()
                    .filter(decl -> {
                        String name = (String) decl.get("name");
                        return name != null && allowedToolNames.contains(name);
                    })
                    .toList();

            if (!allowedDeclarations.isEmpty()) {
                filteredGroup.add(Map.of("functionDeclarations", allowedDeclarations));
            }
        }

        log.info("🎯 Scoped {} tool declaration groups to allowed whitelist: {}", filteredGroup.size(), allowedToolNames);
        return filteredGroup;
    }

    @Override
    public String resolveVoiceName(AbstractBlock block) {
        if (block instanceof ConversationalBlock convBlock) {
            String v = convBlock.getVoiceName();
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public List<RubricCriterion> getEvaluationRubric(AbstractBlock block) {
        if (block instanceof ConversationalBlock convBlock && convBlock.getEvaluationRubric() != null) {
            return convBlock.getEvaluationRubric();
        }
        return Collections.emptyList();
    }
}
