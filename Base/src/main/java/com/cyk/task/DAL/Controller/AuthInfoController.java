package com.cyk.task.DAL.Controller;


import com.cyk.task.DAL.DO.AuthInfoEntity;
import com.cyk.task.DAL.Service.IAuthInfoService;
import com.mybatisflex.core.paginate.Page;
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
 * 鉴权信息表-存储各网站登录凭证及配置 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/authInfo")
public class AuthInfoController {

    @Autowired
    private IAuthInfoService authInfoService;

    /**
     * 添加 鉴权信息表-存储各网站登录凭证及配置
     *
     * @param authInfo 鉴权信息表-存储各网站登录凭证及配置
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody AuthInfoEntity authInfo) {
        return authInfoService.save(authInfo);
    }


    /**
     * 根据主键删除鉴权信息表-存储各网站登录凭证及配置
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return authInfoService.removeById(id);
    }


    /**
     * 根据主键更新鉴权信息表-存储各网站登录凭证及配置
     *
     * @param authInfo 鉴权信息表-存储各网站登录凭证及配置
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody AuthInfoEntity authInfo) {
        return authInfoService.updateById(authInfo);
    }


    /**
     * 查询所有鉴权信息表-存储各网站登录凭证及配置
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<AuthInfoEntity> list() {
        return authInfoService.list();
    }


    /**
     * 根据鉴权信息表-存储各网站登录凭证及配置主键获取详细信息。
     *
     * @param id authInfo主键
     * @return 鉴权信息表-存储各网站登录凭证及配置详情
     */
    @GetMapping("/getInfo/{id}")
    public AuthInfoEntity getInfo(@PathVariable Serializable id) {
        return authInfoService.getById(id);
    }


    /**
     * 分页查询鉴权信息表-存储各网站登录凭证及配置
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<AuthInfoEntity> page(Page<AuthInfoEntity> page) {
        return authInfoService.page(page);
    }
}