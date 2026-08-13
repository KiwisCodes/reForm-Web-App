package com.reForm.backend.form.entity.block.conversationalBlock;

/**
 * RUBRIC CRITERION VALUE OBJECT
 * 
 * Represents a weighted scoring criterion for post-session transcript evaluation.
 * Evaluated asynchronously by EvaluationAgent using Gemini 3.6 Flash / Spring AI ChatClient.
 *
 * @param criterionKey     Unique criterion identifier (e.g. "technical_depth")
 * @param title            Human-readable criterion title (e.g. "Technical Architecture Depth")
 * @param weightPercentage Weight percentage for overall score calculation (e.g. 40)
 * @param scoringGuide     Scoring rubric guide (e.g. "0-50: Superficial. 51-80: Solid. 81-100: Expert mastery.")
 */
public record RubricCriterion(
    String criterionKey,
    String title,
    int weightPercentage,
    String scoringGuide
) {}
