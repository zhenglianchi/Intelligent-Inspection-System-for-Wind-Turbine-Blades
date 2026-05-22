package com.itheima.realtime.bo;

import com.itheima.consultant.constant.Constants;
import com.itheima.realtime.entity.FeaPointDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.Queue;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeaCurveBO {

    private Integer capacity;
    private Integer windturbine;
    private String windfarm;
    /**
     * 特征点队列，按 gmtReceived 升序维护，最早的在队头，最新的在队尾
     */
    private LinkedList<FeaPointDO> feePoints;

    /**
     * 按时序插入特征点，队列始终按 gmtReceived 升序排列
     *
     * 处理场景：
     * - 正常 5s 消息 → 新点时间 > 队尾，O(1) 追队尾
     * - QoS1 重投 → 同一秒到达，跳过不重复加入
     * - 网络恢复批量消息 → 不定时序，扫一遍找插入位
     * - 超量 → 踢队头(最旧)使队列保持在 capacity 以内
     */
    public Integer addFeePoint(FeaPointDO feaPointDO) {
        if (null == capacity || capacity.equals(0)) {
            return Constants.FAIL_INT;
        }
        Timestamp newTime = feaPointDO.getGmtReceived();
        if (newTime == null) {
            return Constants.FAIL_INT;
        }

        // 去重：同一秒内不重复加
        for (FeaPointDO p : feePoints) {
            if (Math.abs(p.getGmtReceived().getTime() - newTime.getTime()) < 1000) {
                return Constants.FAIL_INT;
            }
        }

        // 线性扫描找插入位置：队列升序，最早的在 index=0
        int pos = feePoints.size();
        for (int i = feePoints.size() - 1; i >= 0; i--) {
            if (feePoints.get(i).getGmtReceived().compareTo(newTime) <= 0) {
                pos = i + 1;
                break;
            }
            pos = i;
        }

        // 插入到正确位置
        feePoints.add(pos, feaPointDO);

        // 超量踢最旧（队头）
        while (feePoints.size() > capacity) {
            feePoints.poll();
        }

        return Constants.SUCCESS_INT;
    }
}
