package com.itheima.consultant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统用户实体
 * 对应数据库表 hm_user - 存储系统用户信息
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@TableName("hm_user")
@Data
public class UserDO {

    @TableId(type = IdType.AUTO)
    public Integer id;
    /**
     * 用户姓名
     */
    public String name;
    /**
     * 联系电话
     */
    public String tel;
    /**
     * 性别
     */
    public String sex;
    /**
     * 年龄
     */
    public Integer age;
    /**
     * 地址
     */
    public String address;
    /**
     * 用户名（登录账号）
     */
    public String user;
    /**
     * 密码
     */
    public String pwd;
    /**
     * 职位/权限等级
     */
    public Integer position;

}
