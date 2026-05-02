package com.itheima.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.consultant.common.Result;
import com.itheima.consultant.entity.UserDO;
import com.itheima.consultant.mapper.UserMapper;
import com.itheima.consultant.service.JwtService;
import com.itheima.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理API控制器
 * 提供用户注册、登录、查询、删除等功能
 * 登录成功后返回JWT访问令牌
 *
 * @Author AAA
 * @Migration migrated from wtb-health-monitor
 */
@CrossOrigin
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    UserMapper userMapper;

    @Autowired
    JwtService jwtService;

    /**
     * 新建用户
     *
     * @param userDO 用户信息
     * @return 操作结果
     */
    @PostMapping("/createNewUser")
    public Result save(@RequestBody UserDO userDO) {
        boolean successFlag = userService.save(userDO);

        if (!successFlag) {
            return Result.buildResult(Result.Status.ERROR, "用户已存在或信息错误",false);
        }

        return Result.buildResult(Result.Status.SUCCESS, "新用户创建成功",true);
    }

    /**
     * 用户登录
     * 验证用户名密码成功后生成JWT令牌返回
     *
     * @param userDo 用户登录信息（用户名和密码）
     * @return 登录结果，成功则包含JWT令牌
     */
    @PostMapping("/login")
    public Result login(@RequestBody UserDO userDo) {
        String user = userDo.getUser();
        String pwd = userDo.getPwd();
        Boolean isUserExist = userService.queryUser(user);
        if(!isUserExist){
            return Result.buildResult(Result.Status.UNAUTHORIZED,"用户不存在",false);
        }
        Boolean loginSuccess = userService.checkPwd(user, pwd);
        if (!loginSuccess) {
            return Result.buildResult(Result.Status.PWD_ERROR, "密码错误",false);
        }

        // 生成JWT令牌
        String token = jwtService.generateToken(user);

        // 构造返回结果，包含token
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("token", token);
        resultData.put("username", user);

        return Result.buildResult(Result.Status.SUCCESS, "密码校验通过", resultData);
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    @GetMapping("/searchAllUser")
    public Result<UserDO> searchAllUser() {
        Result result = userService.searchAllUser();
        return result;
    }

    /**
     * 分页查询用户数据
     *
     * @param current 当前页
     * @param size    每页大小
     * @return 分页用户数据
     */
    @GetMapping("/searchUser")
    public Result<UserDO> searchUser(int current, int size) {
        Page<UserDO> page = new Page<>(current, size);
        userMapper.selectPage(page, null);
        List<UserDO> list = page.getRecords();
        Result result = Result.buildResult(Result.Status.SUCCESS, list);
        return result;
    }

    /**
     * 删除用户数据
     *
     * @param user 用户名
     * @return 删除结果
     */
    @GetMapping("/deleteUser")
    public Result deleteUser(String user){
        Boolean isDelete = userService.deleteUser(user);
        if (!isDelete) {
            return Result.buildResult(Result.Status.PWD_ERROR, "删除失败",false);
        }

        return Result.buildResult(Result.Status.SUCCESS, "删除成功",true);
    }
}
