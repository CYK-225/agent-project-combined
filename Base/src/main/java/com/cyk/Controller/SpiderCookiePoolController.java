package com.cyk.Controller;


import com.cyk.Enity.table.SpiderCookiePoolEntity;
import com.cyk.Service.ISpiderCookiePoolService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.List;

import static com.cyk.Enity.table.table.SpiderCookiePoolEntityTableDef.SPIDER_COOKIE_POOL_ENTITY;

/**
 * 爬虫 Cookie 池管理表 控制层。
 *
 * @author mybatis-flex-helper automatic generation
 * @since 1.0
 */
@RestController
@RequestMapping("/spiderCookiePool")
public class SpiderCookiePoolController {

    @Resource
    private ISpiderCookiePoolService spiderCookiePoolService;

    /**
     * 添加 爬虫 Cookie 池管理表
     *
     * @param spiderCookiePool 爬虫 Cookie 池管理表
     * @return {@code true} 添加成功，{@code false} 添加失败
     */
    @PostMapping("/save")
    public boolean save(@RequestBody SpiderCookiePoolEntity spiderCookiePool) {
        return spiderCookiePoolService.save(spiderCookiePool);
    }

    /**
     * 根据主键删除爬虫 Cookie 池管理表
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("/remove/{id}")
    public boolean remove(@PathVariable Serializable id) {
        return spiderCookiePoolService.removeById(id);
    }


    /**
     * 根据主键更新爬虫 Cookie 池管理表
     *
     * @param spiderCookiePool 爬虫 Cookie 池管理表
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("/update")
    public boolean update(@RequestBody SpiderCookiePoolEntity spiderCookiePool) {
        return spiderCookiePoolService.updateById(spiderCookiePool);
    }


    /**
     * 查询所有爬虫 Cookie 池管理表
     *
     * @return 所有数据
     */
    @GetMapping("/list")
    public List<SpiderCookiePoolEntity> list() {
        return spiderCookiePoolService.list();
    }


    /**
     * 根据爬虫 Cookie 池管理表主键获取详细信息。
     *
     * @param id spiderCookiePool主键
     * @return 爬虫 Cookie 池管理表详情
     */
    @GetMapping("/getInfo/{id}")
    public SpiderCookiePoolEntity getInfo(@PathVariable Serializable id) {
        return spiderCookiePoolService.getById(id);
    }


    /**
     * 分页条件查询爬虫 Cookie 池管理表
     * * @param pageNumber 页码
     * @param pageSize   每页条数
     * @param site       站点标识 (如: fengniao)
     * @param status     状态: 1-有效可用, 0-失效
     * @return 分页对象
     */
    @GetMapping("/page")
    public Page<SpiderCookiePoolEntity> page(
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) Integer status) {

        return spiderCookiePoolService.page(new Page<>(pageNumber, pageSize),
                QueryWrapper.create()
                        .select()
                        .from(SPIDER_COOKIE_POOL_ENTITY)
                        // 站点模糊查询（只要包含输入的字母就能查到）
                        .where(SPIDER_COOKIE_POOL_ENTITY.SITE.like(site, site != null && !site.isEmpty()))
                        // 状态精确匹配
                        .and(SPIDER_COOKIE_POOL_ENTITY.STATUS.eq(status, status != null))
                        // 默认按最新创建的时间倒序排列
                        .orderBy(SPIDER_COOKIE_POOL_ENTITY.CREATE_TIME.desc())
        );
    }
}