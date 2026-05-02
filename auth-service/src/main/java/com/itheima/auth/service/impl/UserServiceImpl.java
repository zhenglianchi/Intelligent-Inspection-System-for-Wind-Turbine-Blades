package com.itheima.auth.service.impl;

import com.itheima.consultant.common.Result;
import com.itheima.consultant.constant.Constants;
import com.itheima.consultant.entity.UserDO;
import com.itheima.consultant.mapper.UserMapper;
import com.itheima.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 用户服务实现类
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    UserMapper userMapper;

    /**
     * 保存新用户
     *
     * @param userDO 用户对象
     * @return 保存成功返回true，失败返回false
     */
    @Override
    public boolean save(UserDO userDO) {

        if (!userIsAvailable(userDO)) {
            return false;
        }

        int insert = userMapper.insert(userDO);
        return insert > Constants.ZERO;
    }

    /**
     * 查询所有用户信息
     *
     * @return 用户列表结果
     */
    @Override
    public Result<List<UserDO>> searchAllUser() {
        List<UserDO> userList = userMapper.searchAll();
        if (userList.size() == 0) {
            return Result.buildResult(Result.Status.NOT_FOUND, "用户表为空");
        }
        return Result.buildResult(Result.Status.SUCCESS, userList);
    }

    /**
     * 验证用户名密码是否匹配
     *
     * @param user 用户名
     * @param pwd  密码
     * @return 匹配返回true，不匹配返回false
     */
    @Override
    public Boolean checkPwd(String user, String pwd) {
        String realPwd = userMapper.queryPwd(user);
        return realPwd != null && realPwd.equals(pwd);
    }

    /**
     * 查询用户名是否存在
     *
     * @param user 用户名
     * @return 存在返回true，不存在返回false
     */
    @Override
    public Boolean queryUser(String user) {
        String realuser = userMapper.queryUser(user);
        return realuser != null;
    }

    /**
     * 根据用户名删除用户
     *
     * @param user 用户名
     * @return 删除成功返回true，失败返回false
     */
    @Override
    public Boolean deleteUser(String user) {
        String originUser = userMapper.queryUser(user);
        if (originUser == null) {
            return false;
        }
        userMapper.deleteUser(user);
        String afterUser = userMapper.queryUser(user);
        return afterUser == null;
    }

    /**
     * 新用户合法性校验
     * 用户名和密码不能为空，用户名不能重复
     *
     * @param userDO 用户对象
     * @return 合法返回true，不合法返回false
     */
    private Boolean userIsAvailable(UserDO userDO) {
        String user = userDO.getUser();
        if (Objects.isNull(user) || "".equals(user)) {
            return false;
        }

        String pwd = userDO.getPwd();
        if (Objects.isNull(pwd) || "".equals(pwd)) {
            return false;
        }

        Integer num = userMapper.countUser(user);
        return num < Constants.ONE;
    }
}
