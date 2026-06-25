package com.reForm.backend.form.entity.block.staticblock.complex;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class DateTimeStaticBlock extends StaticBlock {

    private String minDate;

    private String maxDate;

    private boolean includeTime = false;

}
