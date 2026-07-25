package org.example.agent.user.dal.controller;

import com.mybatisflex.core.paginate.Page;
import org.example.agent.user.dal.entity.UserCompanyRelationEntity;
import org.example.agent.user.dal.service.IUserCompanyRelationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;

/**
 * 员工所属关联表(人事档案) 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/userCompanyRelation")
public class UserCompanyRelationController {

    @Autowired
    private IUserCompanyRelationService userCompanyRelationService;

    /**
     * 添加 员工所属关联表(人事档案)
     *
     * @param userCompanyRelation 员工所属关联表(人事档案)
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody UserCompanyRelationEntity userCompanyRelation) {
        return userCompanyRelationService.save(userCompanyRelation);
    }


    /**
     * 根据主键删除员工所属关联表(人事档案)
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return userCompanyRelationService.removeById(id);
    }


    /**
     * 根据主键更新员工所属关联表(人事档案)
     *
     * @param userCompanyRelation 员工所属关联表(人事档案)
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody UserCompanyRelationEntity userCompanyRelation) {
        return userCompanyRelationService.updateById(userCompanyRelation);
    }


    /**
     * 查询所有员工所属关联表(人事档案)
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<UserCompanyRelationEntity> list() {
        return userCompanyRelationService.list();
    }


    /**
     * 根据员工所属关联表(人事档案)主键获取详细信息。
     *
     * @param id userCompanyRelation主键
     * @return 员工所属关联表(人事档案)详情
     */
    @GetMapping("/getInfo/{id}")
    public UserCompanyRelationEntity getInfo(@PathVariable Serializable id) {
        return userCompanyRelationService.getById(id);
    }


    /**
     * 分页查询员工所属关联表(人事档案)
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<UserCompanyRelationEntity> page(Page<UserCompanyRelationEntity> page) {
        return userCompanyRelationService.page(page);
    }
}