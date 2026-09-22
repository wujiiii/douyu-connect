package io.github.douyuconnect.message;

/**
 * 通用协议消息；兼容原 BaseMessage 与 Handler 的已知字段集合，字段含义依实际 type/btype 判断。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record GenericMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.GENERIC; }

    /** @return 原字段 {@code cid}：弹幕消息 ID；缺失为 null */
    public String getCid() { return context.field("cid"); }

    /** @return 原字段 {@code gid}：弹幕分组 ID；缺失为 null */
    public String getGid() { return context.field("gid"); }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code userid}：推送/登录协议中的 userid，与 uid 分别保留；缺失为 null */
    public String getUserid() { return context.field("userid"); }

    /** @return 原字段 {@code res}：发送响应码，仅在相应协议消息中解释；缺失为 null */
    public String getRes() { return context.field("res"); }

    /** @return 原字段 {@code nn}：发送者昵称；缺失为 null */
    public String getNn() { return context.field("nn"); }

    /** @return 原字段 {@code txt}：消息正文；缺失为 null */
    public String getTxt() { return context.field("txt"); }

    /** @return 原字段 {@code level}：用户等级；缺失为 null */
    public String getLevel() { return context.field("level"); }

    /** @return 原字段 {@code gt}：礼物头衔标识；缺失为 null */
    public String getGt() { return context.field("gt"); }

    /** @return 原字段 {@code col}：弹幕颜色标识；缺失为 null */
    public String getCol() { return context.field("col"); }

    /** @return 原字段 {@code ct}：客户端类型；缺失为 null */
    public String getCt() { return context.field("ct"); }

    /** @return 原字段 {@code rg}：房间权限组；缺失为 null */
    public String getRg() { return context.field("rg"); }

    /** @return 原字段 {@code pg}：平台权限组；缺失为 null */
    public String getPg() { return context.field("pg"); }

    /** @return 原字段 {@code cmt}：弹幕具体类型；缺失为 null */
    public String getCmt() { return context.field("cmt"); }

    /** @return 原字段 {@code ic}：用户头像标识；缺失为 null */
    public String getIc() { return context.field("ic"); }

    /** @return 原字段 {@code nl}：贵族等级标识；缺失为 null */
    public String getNl() { return context.field("nl"); }

    /** @return 原字段 {@code nc}：贵族弹幕标识；缺失为 null */
    public String getNc() { return context.field("nc"); }

    /** @return 原字段 {@code bnn}：粉丝牌名称；缺失为 null */
    public String getBnn() { return context.field("bnn"); }

    /** @return 原字段 {@code bl}：粉丝牌等级；缺失为 null */
    public String getBl() { return context.field("bl"); }

    /** @return 原字段 {@code brid}：粉丝牌关联房间号，不是接收连接房间号；缺失为 null */
    public String getBrid() { return context.field("brid"); }

    /** @return 原字段 {@code hc}：徽章校验信息；缺失为 null */
    public String getHc() { return context.field("hc"); }

    /** @return 原字段 {@code ol}：主播等级；缺失为 null */
    public String getOl() { return context.field("ol"); }

    /** @return 原字段 {@code rev}：反向弹幕标识；缺失为 null */
    public String getRev() { return context.field("rev"); }

    /** @return 原字段 {@code hl}：高亮弹幕标识；缺失为 null */
    public String getHl() { return context.field("hl"); }

    /** @return 原字段 {@code ifs}：粉丝弹幕标识；缺失为 null */
    public String getIfs() { return context.field("ifs"); }

    /** @return 原字段 {@code cst}：协议中的弹幕发送时间，不转换时间单位；缺失为 null */
    public String getCst() { return context.field("cst"); }

    /** @return 原字段 {@code gfid}：礼物 ID，保留 0 等原值，不改写为 pid_xxx；缺失为 null */
    public String getGfid() { return context.field("gfid"); }

    /** @return 原字段 {@code gs}：礼物显示样式；缺失为 null */
    public String getGs() { return context.field("gs"); }

    /** @return 原字段 {@code bg}：大礼物标识；缺失为 null */
    public String getBg() { return context.field("bg"); }

    /** @return 原字段 {@code gfcnt}：协议礼物数量，不与 hits 相乘；缺失为 null */
    public String getGfcnt() { return context.field("gfcnt"); }

    /** @return 原字段 {@code hits}：协议礼物连击计数，不计算相邻消息的增量；缺失为 null */
    public String getHits() { return context.field("hits"); }

    /** @return 原字段 {@code sl}：原 BaseMessage 中的鱼丸数量字段，实际含义按消息类型判断；缺失为 null */
    public String getSl() { return context.field("sl"); }

    /** @return 原字段 {@code sid}：原 BaseMessage 中的礼包产生者 ID，实际含义按消息类型判断；缺失为 null */
    public String getSid() { return context.field("sid"); }

    /** @return 原字段 {@code did}：原 BaseMessage 中的抢礼包者 ID，实际含义按消息类型判断；缺失为 null */
    public String getDid() { return context.field("did"); }

    /** @return 原字段 {@code snk}：原 BaseMessage 中的礼包产生者昵称；缺失为 null */
    public String getSnk() { return context.field("snk"); }

    /** @return 原字段 {@code dnk}：原 BaseMessage 中的抢礼包者昵称；缺失为 null */
    public String getDnk() { return context.field("dnk"); }

    /** @return 原字段 {@code rpt}：礼包类型；缺失为 null */
    public String getRpt() { return context.field("rpt"); }

    /** @return 原字段 {@code sn}：礼物广播中的赠送者昵称；缺失为 null */
    public String getSn() { return context.field("sn"); }

    /** @return 原字段 {@code dn}：礼物广播中的受赠者昵称；缺失为 null */
    public String getDn() { return context.field("dn"); }

    /** @return 原字段 {@code gn}：礼物广播中的礼物名称；缺失为 null */
    public String getGn() { return context.field("gn"); }

    /** @return 原字段 {@code gc}：礼物广播中的礼物数量原值；缺失为 null */
    public String getGc() { return context.field("gc"); }

    /** @return 原字段 {@code drid}：礼物广播中的赠送房间字段，与 rid/连接房间号分别保留；缺失为 null */
    public String getDrid() { return context.field("drid"); }

    /** @return 原字段 {@code gb}：是否存在礼包的协议标识；缺失为 null */
    public String getGb() { return context.field("gb"); }

    /** @return 原字段 {@code es}：广播展示样式；缺失为 null */
    public String getEs() { return context.field("es"); }

    /** @return 原字段 {@code eid}：特效 ID；缺失为 null */
    public String getEid() { return context.field("eid"); }

    /** @return 原字段 {@code sdid}：超级弹幕 ID；缺失为 null */
    public String getSdid() { return context.field("sdid"); }

    /** @return 原字段 {@code trid}：协议中的跳转房间 ID；缺失为 null */
    public String getTrid() { return context.field("trid"); }

    /** @return 原字段 {@code content}：协议内容字符串；可能包含 JSON，getter 不强制转为对象；缺失为 null */
    public String getContent() { return context.field("content"); }

    /** @return 原字段 {@code code}：错误报文中的错误码，不等同于 chatres.res；缺失为 null */
    public String getCode() { return context.field("code"); }

    /** @return 原字段 {@code desc}：错误报文中的说明；缺失为 null */
    public String getDesc() { return context.field("desc"); }

    /** @return 原字段 {@code pid}：道具 ID，与 gfid 分别保留；缺失为 null */
    public String getPid() { return context.field("pid"); }

    /** @return 原字段 {@code gfn}：礼物名称；缺失为 null */
    public String getGfn() { return context.field("gfn"); }

    /** @return 原字段 {@code receive_nn}：接收者昵称；缺失为 null */
    public String getReceiveNn() { return context.field("receive_nn"); }

    /** @return 原字段 {@code nick}：钻粉用户昵称；缺失为 null */
    public String getNick() { return context.field("nick"); }

    /** @return 原字段 {@code icon}：钻粉用户头像；缺失为 null */
    public String getIcon() { return context.field("icon"); }

    /** @return 原字段 {@code rnick}：钻粉接收者昵称；缺失为 null */
    public String getRnick() { return context.field("rnick"); }

    /** @return 原字段 {@code mn}：钻粉开通或续费月数；缺失为 null */
    public String getMn() { return context.field("mn"); }

    /** @return 原字段 {@code price}：钻粉协议金额，保留原始单位，不折算；缺失为 null */
    public String getPrice() { return context.field("price"); }

    /** @return 原字段 {@code unk}：爵位消息发送者昵称；缺失为 null */
    public String getUnk() { return context.field("unk"); }

    /** @return 原字段 {@code uic}：爵位消息发送者头像；缺失为 null */
    public String getUic() { return context.field("uic"); }

    /** @return 原字段 {@code donk}：爵位接收者昵称；缺失为 null */
    public String getDonk() { return context.field("donk"); }

    /** @return 原字段 {@code txt4}：潘多拉广播中的奖励礼物名称；缺失为 null */
    public String getTxt4() { return context.field("txt4"); }

    /** @return 原字段 {@code txt5}：潘多拉广播中的数量文案，不提取或拼接数字；缺失为 null */
    public String getTxt5() { return context.field("txt5"); }

    /** @return 原字段 {@code txt6}：潘多拉广播中的奖励道具 ID，不添加 pid_ 前缀；缺失为 null */
    public String getTxt6() { return context.field("txt6"); }

    /** @return 原字段 {@code chatmsg}：嵌套 chatmsg 的 STT 字符串，仅解码外层转义；使用 getChatFields() 读取内层字段；缺失为 null */
    public String getChatmsg() { return context.field("chatmsg"); }

    /** @return 原字段 {@code crealPrice}：高能/语音弹幕协议金额，保留原始单位，不折算；缺失为 null */
    public String getCrealPrice() { return context.field("crealPrice"); }

    /** @return 原字段 {@code ss}：直播状态原值；原项目将 1 视为直播中，不推断其他值；缺失为 null */
    public String getSs() { return context.field("ss"); }

    /** @return 嵌套 chatmsg 解码一层后的不可变字段 Map；缺失、空串或格式错误为空 Map */
    public java.util.Map<String,String> getChatFields() { return context.nested("chatmsg"); }

    /** @return 原嵌套字段 chatmsg.nn：广播附带的用户昵称；不覆盖外层 uid，缺失为 null */
    public String getChatNn() { return getChatFields().get("nn"); }

    /** @return 原嵌套字段 chatmsg.ic：广播附带的用户头像；缺失为 null */
    public String getChatIc() { return getChatFields().get("ic"); }
}
