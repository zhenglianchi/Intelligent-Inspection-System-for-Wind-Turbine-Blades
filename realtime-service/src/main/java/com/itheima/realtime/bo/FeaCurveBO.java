package com.itheima.realtime.bo;

import com.itheima.consultant.constant.Constants;
import com.itheima.realtime.entity.FeaPointDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Queue;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeaCurveBO {

    /**
     * 曲线容量，最大存储多少个特征点
     */
    private Integer capacity;
    /**
     * 风机编号
     */
    private Integer windturbine;
    /**
     * 风场编号
     */
    private String windfarm;
    /**
     * 特征点队列（自动淘汰旧数据）
     */
    private Queue<FeaPointDO> feePoints;

    /**
     * 新增特征点
     * 如果队列已满，自动淘汰最早的点
     *
     * @param feaPointDO 特征点
     * @return 成功返回1，失败返回0
     */
    public Integer addFeePoint(FeaPointDO feaPointDO) {
        if (null == capacity || capacity.equals(0)) {
            return Constants.FAIL_INT;
        }

        if (feePoints.size() >= capacity) {
            feePoints.poll();
        }

        feePoints.offer(feaPointDO);
        return Constants.SUCCESS_INT;
    }
}
