package com.itheima.realtime.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itheima.consultant.entity.WindfarmInfoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 风场信息 Mapper接口
 * 对应数据库表 hm_windfarm_info
 *
 * @Author MH.Zhang
 * @Migration migrated from wtb-health-monitor
 */
@Mapper
public interface WindfarmMapper extends BaseMapper<WindfarmInfoDO> {
}
