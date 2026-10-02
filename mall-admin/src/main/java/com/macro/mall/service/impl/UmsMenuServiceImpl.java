package com.macro.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.dto.UmsMenuNode;
import com.macro.mall.mapper.UmsMenuMapper;
import com.macro.mall.model.*;
import com.macro.mall.service.UmsMenuService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 后台菜单管理Service实现类
 * Created by macro on 2020/2/2.
 */
@Service
public class UmsMenuServiceImpl extends ServiceImpl<UmsMenuMapper, UmsMenu> implements UmsMenuService {

    @Override
    public int create(UmsMenu umsMenu) {
        umsMenu.setCreateTime(LocalDateTime.now());
        updateLevel(umsMenu);
        return baseMapper.insert(umsMenu);
    }

    /**
     * 修改菜单层级
     */
    private void updateLevel(UmsMenu umsMenu) {
        if (umsMenu.getParentId() == 0) {
            //没有父菜单时为一级菜单
            umsMenu.setLevel(0);
        } else {
            //有父菜单时选择根据父菜单level设置
            UmsMenu parentMenu = this.getById(umsMenu.getParentId());
            if (parentMenu != null) {
                umsMenu.setLevel(parentMenu.getLevel() + 1);
            } else {
                umsMenu.setLevel(0);
            }
        }
    }

    @Override
    public int update(Long id, UmsMenu umsMenu) {
        umsMenu.setId(id);
        updateLevel(umsMenu);
        return baseMapper.updateById(umsMenu);
    }

    @Override
    public UmsMenu getItem(Long id) {
        return this.getById(id);
    }

    @Override
    public int delete(Long id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public IPage<UmsMenu> list(Long parentId, Integer pageSize, Integer pageNum) {
        LambdaQueryWrapper<UmsMenu> wrapper = new LambdaQueryWrapper<UmsMenu>()
                .eq(UmsMenu::getParentId, parentId)
                .orderByDesc(UmsMenu::getSort);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<UmsMenuNode> treeList() {
        List<UmsMenu> menuList = this.list(new LambdaQueryWrapper<>());
        List<UmsMenuNode> result = menuList.stream()
                .filter(menu -> menu.getParentId().equals(0L))
                .map(menu -> covertMenuNode(menu, menuList))
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public int updateHidden(Long id, Integer hidden) {
        UmsMenu umsMenu = new UmsMenu();
        umsMenu.setId(id);
        umsMenu.setHidden(hidden);
        return baseMapper.updateById(umsMenu);
    }

    /**
     * 将UmsMenu转化为UmsMenuNode并设置children属性
     */
    private UmsMenuNode covertMenuNode(UmsMenu menu, List<UmsMenu> menuList) {
        UmsMenuNode node = new UmsMenuNode();
        BeanUtils.copyProperties(menu, node);
        List<UmsMenuNode> children = menuList.stream()
                .filter(subMenu -> subMenu.getParentId().equals(menu.getId()))
                .map(subMenu -> covertMenuNode(subMenu, menuList)).collect(Collectors.toList());
        node.setChildren(children);
        return node;
    }
}
