package com.sakurain.gpuscheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakurain.gpuscheduler.entity.Resource;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资源Mapper接口
 */
public interface ResourceMapper extends BaseMapper<Resource> {

    /**
     * 根据父资源ID查询子资源列表
     */
    @Select("SELECT * FROM resource WHERE parent_id = #{parentId} AND status = 1 ORDER BY sort_order")
    List<Resource> selectByParentId(@Param("parentId") Long parentId);

    /**
     * 查询所有根资源（顶级菜单）
     */
    @Select("SELECT * FROM resource WHERE parent_id IS NULL AND status = 1 ORDER BY sort_order")
    List<Resource> selectRootResources();

    /**
     * 根据资源类型查询资源列表
     */
    @Select("SELECT * FROM resource WHERE type = #{type} AND status = 1 ORDER BY sort_order")
    List<Resource> selectByType(@Param("type") Integer type);

    /**
     * 根据权限ID列表查询关联的资源列表
     */
    List<Resource> selectByPermissionIds(@Param("permissionIds") List<Long> permissionIds);
}
