package com.tianji.aigc.constants;

public interface Constant {

    String REQUEST_ID = "requestId";
    String USER_ID = "userId";
    String ID = "id";
    String STOP = "STOP";

    interface Tools {  // 通过子接口的方式，对常量进行分类，使得结构更清晰
        String QUERY_COURSE_BY_ID = "根据课程id查询平台真实存在且当前可购买的课程详细信息；购买前应先用此工具核验课程id";
        String SEARCH_COURSES_BY_KEYWORD = "根据关键词搜索平台真实存在的课程，返回可推荐的课程详情列表；禁止自行编造课程ID";
        String PRE_PLACE_ORDER = "购买课程预下单操作；仅可传入已通过课程查询工具确认存在的真实课程id";
    }

    interface ToolParams {
        String COURSE_ID = "课程id";
        String COURSE_KEYWORD = "课程搜索关键词，应来自用户的学习方向，例如Java、微服务或前端";
        String COURSE_IDS = "课程id列表";
    }

}
