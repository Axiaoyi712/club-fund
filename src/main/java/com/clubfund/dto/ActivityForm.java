package com.clubfund.dto;

import com.clubfund.entity.BudgetItem;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 活动申请表表单对象：接收页面提交的全部数据。
 * 包含基本信息 + 预算明细行（items）。
 * 页面上明细行的 name 是 items[0].itemName、items[1].itemName ... 这种格式，
 * Spring 会自动把每一行封装成 BudgetItem 对象放进列表。
 */
@Data
public class ActivityForm {

    private String submissionToken; // 每次打开新增表单生成，成功提交后不可重复使用

    private Long clubId;           // 主办社团

    private String organizer;      // 承办/协办单位

    private String title;          // 活动内容

    private String location;       // 活动地点

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;   // 活动开始时间

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;     // 活动结束时间

    private List<BudgetItem> items = new ArrayList<>();  // 预算明细行
}
