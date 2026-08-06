package org.example.agent.user.dal.controller;

import com.mybatisflex.core.paginate.Page;
import org.example.agent.user.dal.entity.UserPreferenceBiasEntity;
import org.example.agent.user.dal.service.IUserPreferenceBiasService;
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
 * 用户喜好偏差表 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/userPreferenceBias")
public class UserPreferenceBiasController {

    @Autowired
    private IUserPreferenceBiasService userPreferenceBiasService;

    /**
     * 添加 用户喜好偏差表
     *
     * @param userPreferenceBias 用户喜好偏差表
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody UserPreferenceBiasEntity userPreferenceBias) {
        return userPreferenceBiasService.save(userPreferenceBias);
    }


    /**
     * 根据主键删除用户喜好偏差表
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return userPreferenceBiasService.removeById(id);
    }


    /**
     * 根据主键更新用户喜好偏差表
     *
     * @param userPreferenceBias 用户喜好偏差表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody UserPreferenceBiasEntity userPreferenceBias) {
        return userPreferenceBiasService.updateById(userPreferenceBias);
    }


    /**
     * 查询所有用户喜好偏差表
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<UserPreferenceBiasEntity> list() {
        return userPreferenceBiasService.list();
    }


    /**
     * 根据用户喜好偏差表主键获取详细信息。
     *
     * @param id userPreferenceBias主键
     * @return 用户喜好偏差表详情
     */
    @GetMapping("/getInfo/{id}")
    public UserPreferenceBiasEntity getInfo(@PathVariable Serializable id) {
        return userPreferenceBiasService.getById(id);
    }


    /**
     * 分页查询用户喜好偏差表
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<UserPreferenceBiasEntity> page(Page<UserPreferenceBiasEntity> page) {
        return userPreferenceBiasService.page(page);
    }
}