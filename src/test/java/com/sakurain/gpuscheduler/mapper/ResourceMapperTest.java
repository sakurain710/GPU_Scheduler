package com.sakurain.gpuscheduler.mapper;

import com.sakurain.gpuscheduler.entity.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceMapper 测试类
 */
@SpringBootTest
@Transactional
class ResourceMapperTest {

    @Autowired
    private ResourceMapper resourceMapper;

    @Test
    void testInsertAndSelectById() {
        Resource resource = Resource.builder()
                .code("system:config")
                .name("系统配置")
                .type(1)
                .sortOrder(1)
                .status(1)
                .build();

        int result = resourceMapper.insert(resource);
        assertEquals(1, result);
        assertNotNull(resource.getId());

        Resource found = resourceMapper.selectById(resource.getId());
        assertNotNull(found);
        assertEquals("system:config", found.getCode());
    }

    @Test
    void testSelectByType() {
        // 创建不同类型的资源
        resourceMapper.insert(Resource.builder()
                .code("menu:user").name("用户菜单").type(1).sortOrder(1).status(1).build());
        resourceMapper.insert(Resource.builder()
                .code("api:user:list").name("用户列表API").type(2).sortOrder(2).status(1).build());

        List<Resource> menus = resourceMapper.selectByType(1);
        assertTrue(menus.size() >= 1);

        List<Resource> apis = resourceMapper.selectByType(2);
        assertTrue(apis.size() >= 1);
    }

    @Test
    void testSelectRootResources() {
        // 创建根资源
        resourceMapper.insert(Resource.builder()
                .code("root:menu1").name("根菜单1").type(1).parentId(null).sortOrder(1).status(1).build());

        List<Resource> roots = resourceMapper.selectRootResources();
        assertFalse(roots.isEmpty());
    }

    @Test
    void testSelectByParentId() {
        // 创建父子资源
        Resource parent = Resource.builder()
                .code("parent:menu").name("父菜单").type(1).sortOrder(1).status(1).build();
        resourceMapper.insert(parent);

        Resource child = Resource.builder()
                .code("child:menu").name("子菜单").type(1).parentId(parent.getId()).sortOrder(1).status(1).build();
        resourceMapper.insert(child);

        List<Resource> children = resourceMapper.selectByParentId(parent.getId());
        assertEquals(1, children.size());
        assertEquals("child:menu", children.get(0).getCode());
    }
}
