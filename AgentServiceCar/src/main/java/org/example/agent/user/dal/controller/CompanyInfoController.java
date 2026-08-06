package org.example.agent.user.dal.controller;

import com.mybatisflex.core.paginate.Page;
import org.example.agent.user.dal.entity.CompanyInfoEntity;
import org.example.agent.user.dal.service.ICompanyInfoService;
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
 * 公司主体信息表 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/companyInfo")
public class CompanyInfoController {

    @Autowired
    private ICompanyInfoService companyInfoService;

    /**
     * 添加 公司主体信息表
     *
     * @param companyInfo 公司主体信息表
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody CompanyInfoEntity companyInfo) {
        return companyInfoService.save(companyInfo);
    }


    /**
     * 根据主键删除公司主体信息表
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return companyInfoService.removeById(id);
    }


    /**
     * 根据主键更新公司主体信息表
     *
     * @param companyInfo 公司主体信息表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody CompanyInfoEntity companyInfo) {
        return companyInfoService.updateById(companyInfo);
    }


    /**
     * 查询所有公司主体信息表
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<CompanyInfoEntity> list() {
        return companyInfoService.list();
    }


    /**
     * 根据公司主体信息表主键获取详细信息。
     *
     * @param id companyInfo主键
     * @return 公司主体信息表详情
     */
    @GetMapping("/getInfo/{id}")
    public CompanyInfoEntity getInfo(@PathVariable Serializable id) {
        return companyInfoService.getById(id);
    }


    /**
     * 分页查询公司主体信息表
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<CompanyInfoEntity> page(Page<CompanyInfoEntity> page) {
        return companyInfoService.page(page);
    }
}