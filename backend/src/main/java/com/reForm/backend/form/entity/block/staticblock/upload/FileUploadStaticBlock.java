package com.reForm.backend.form.entity.block.staticblock.upload;

import com.reForm.backend.form.entity.block.staticblock.StaticBlock;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class FileUploadStaticBlock extends StaticBlock {

    private List<String> typeOfFiles = List.of(".png", ".jpg", ".docx", ".pdf", ".txt");

    private Long maxFileSize = 10L; // MB

    private boolean multipleFile = false;


}
