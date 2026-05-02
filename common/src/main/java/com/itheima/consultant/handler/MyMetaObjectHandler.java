package com.itheima.consultant.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.itheima.consultant.utils.DateUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * MyBatis-Plus自动字段填充处理器
 * 插入和更新时自动填充时间字段
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充创建时间和修改时间
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Timestamp now = DateUtil.currentDateTime();
        this.setFieldValByName("gmtModified", now, metaObject);
        this.setFieldValByName("gmtCreate", now, metaObject);
    }

    /**
     * 更新时自动填充修改时间
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        Timestamp now = DateUtil.currentDateTime();
        this.setFieldValByName("gmtModified", now, metaObject);
    }
}
