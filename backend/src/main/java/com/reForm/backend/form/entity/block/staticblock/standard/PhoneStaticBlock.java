package com.reForm.backend.form.entity.block.staticblock.standard;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class PhoneStaticBlock extends StaticBlock {

    private boolean detectCountryCode = true;

    private String defaultCountryCode = "VNM";

}
