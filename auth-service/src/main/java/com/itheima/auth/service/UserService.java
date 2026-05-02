package com.itheima.auth.service;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.UserDO;

import java.util.List;

/**
 * 用户服务接口
 * 处理用户注册、查询、删除、登录密码验证
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public interface UserService {

    /**
     * 保存新用户数据
     *
     * @param userDO 用户对象
     * @return 保存成功返回true，失败返回false
     */
    boolean save(UserDO userDO);

    /**
     * 查询所有用户信息
     *
     * @return 用户列表结果
     */
    Result<List<UserDO>> searchAllUser();

    /**
     * 验证用户名密码是否匹配
     *
     * @param user 用户名
     * @param pwd  密码
     * @return 匹配返回true，不匹配返回false
     */
    Boolean checkPwd(String user, String pwd);

    /**
     * 查询用户名是否存在
     *
     * @param user 用户名
     * @return 存在返回true，不存在返回false
     */
    Boolean queryUser(String user);

    /**
     * 根据用户名删除用户
     *
     * @param user 用户名
     * @return 删除成功返回true，失败返回false
     */
    Boolean deleteUser(String user);
}
