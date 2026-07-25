package org.example.agent.user.dal.controller;

import com.mybatisflex.core.paginate.Page;
import org.example.agent.user.dal.entity.UserInfoEntity;
import org.example.agent.user.dal.service.IUserInfoService;
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
 * 用户信息表 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/userInfo")
public class UserInfoController {

    @Autowired
    private IUserInfoService userInfoService;

    /**
     * 添加 用户信息表
     *
     * @param userInfo 用户信息表
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody UserInfoEntity userInfo) {
        return userInfoService.save(userInfo);
    }


    /**
     * 根据主键删除用户信息表
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return userInfoService.removeById(id);
    }


    /**
     * 根据主键更新用户信息表
     *
     * @param userInfo 用户信息表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody UserInfoEntity userInfo) {
        return userInfoService.updateById(userInfo);
    }


    /**
     * 查询所有用户信息表
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<UserInfoEntity> list() {
        return userInfoService.list();
    }


    /**
     * 根据用户信息表主键获取详细信息。
     *
     * @param id userInfo主键
     * @return 用户信息表详情
     */
    @GetMapping("/getInfo/{id}")
    public UserInfoEntity getInfo(@PathVariable Serializable id) {
        return userInfoService.getById(id);
    }


    /**
     * 分页查询用户信息表
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<UserInfoEntity> page(Page<UserInfoEntity> page) {
        return userInfoService.page(page);
    }
}