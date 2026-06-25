package com.reForm.backend.form.entity.block.staticblock.selection;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
@NoArgsConstructor
public class ChoiceStaticBlock extends StaticBlock {

    private SelectionType selectionType;

    private List<String> options;

    private boolean allowMultiSelect; // true for check box, false for others

}
