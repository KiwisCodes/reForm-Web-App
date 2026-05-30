package com.reForm.backend.user.mapper;

import com.reForm.backend.user.dto.WorkspaceResponseDto;
import com.reForm.backend.user.entity.Workspace;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface WorkspaceMapper {
    WorkspaceResponseDto toWorkspaceResponseDto(Workspace workspace);
}
