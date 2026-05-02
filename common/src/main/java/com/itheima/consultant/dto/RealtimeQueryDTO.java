package com.itheima.consultant.dto;

import lombok.Data;

/**
 * 实时数据多条件查询 DTO
 * 所有字段均为可选，LLM 可按用户意图传入任意条件组合
 */
@Data
public class RealtimeQueryDTO {
    /** 风场编号，可选 */
    private String windfarm;
    /** 风机编号，可选 */
    private Integer windturbine;
    /** 状态码：0-正常 1-故障 9-未连接，可选 */
    private Integer status;
    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss，可选 */
    private String startTime;
    /** 结束时间，格式 yyyy-MM-dd HH:mm:ss，可选 */
    private String endTime;
    /** 返回条数上限，默认 50 */
    private Integer limit;
}
