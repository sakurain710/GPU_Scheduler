package com.sakurain.gpuscheduler.dto.rbac;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class MenuNodeResponse {

    private Long id;
    private String code;
    private String name;
    private String path;
    private Long parentId;
    private Integer sortOrder;

    @Builder.Default
    private List<MenuNodeResponse> children = new ArrayList<>();
}
