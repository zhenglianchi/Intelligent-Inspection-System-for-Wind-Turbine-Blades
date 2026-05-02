package com.itheima.consultant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.consultant.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户信息 Mapper接口
 * 对应数据库表 hm_user
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    List<UserDO> searchAll();

    /**
     * 根据用户名查询用户数量（用于判断用户是否存在）
     *
     * @param user 用户名
     * @return 用户数量
     */
    Integer countUser(@Param("user") String user);

    /**
     * 根据用户名查询密码（用于登录验证）
     *
     * @param user 用户名
     * @return 密码哈希
     */
    String queryPwd(@Param("user") String user);

    /**
     * 根据用户名查询用户名（判断用户是否存在）
     *
     * @param user 用户名
     * @return 用户名
     */
    String queryUser (@Param("user") String user);

    /**
     * 根据用户名删除用户
     *
     * @param user 用户名
     */
    void deleteUser(@Param("user") String user);
}
