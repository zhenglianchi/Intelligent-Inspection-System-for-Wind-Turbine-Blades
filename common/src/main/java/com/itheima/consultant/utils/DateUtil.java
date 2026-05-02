package com.itheima.consultant.utils;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期工具类
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
public class DateUtil {

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return Timestamp
     */
    public static Timestamp currentDateTimeMillis() {
        Calendar calendar = Calendar.getInstance();
        return new Timestamp(calendar.getTimeInMillis());
    }

    /**
     * 获取当前时间戳
     *
     * @return Timestamp
     */
    public static Timestamp currentDateTime() {
        Date dt = new Date();
        return new Timestamp(dt.getTime());
    }
}
