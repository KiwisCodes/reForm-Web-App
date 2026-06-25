package com.reForm.backend.form.entity.block.staticblock.quantitative;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OpinionScaleStaticBlock extends StaticBlock {

    private String leftMostLabel = "Strongly Disagree";

    private String leftLabel = "Partly Disagree";

    private String neutralLabel = "Neutral";

    private String rightLabel = "Partly Agree";

    private String rightMostLabel = "Strongly Agree";

}
