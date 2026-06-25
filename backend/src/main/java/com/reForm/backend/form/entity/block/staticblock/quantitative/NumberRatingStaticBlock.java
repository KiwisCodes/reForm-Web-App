package com.reForm.backend.form.entity.block.staticblock.quantitative;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NumberRatingStaticBlock extends StaticBlock {

    private Integer maxScale = 10;

    private Integer minScale = 1;
}
