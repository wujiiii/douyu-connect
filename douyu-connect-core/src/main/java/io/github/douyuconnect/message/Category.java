package io.github.douyuconnect.message;

/** SDK 分类；仅依据协议 type/btype，不进行业务关联或价值判断。 */
public enum Category {
    /** type=chatmsg，普通聊天报文。 */
    CHAT,
    /** type=dgb，礼物原报文，不区分普通或活动来源。 */
    GIFT,
    /** type=dfobc/dfrbc，钻粉开通或续费。 */
    FANS_BADGE,
    /** type=anbc，爵位报文。 */
    NOBLE,
    /** btype=pandora，潘多拉广播。 */
    PANDORA_BROADCAST,
    /** btype=voiceDanmu，高能/语音弹幕广播。 */
    VOICE_DANMU,
    /** type=rss，直播状态报文。 */
    ROOM_STATUS,
    /** 未提供专用分类的报文，完整原字段仍保留。 */
    GENERIC
}
