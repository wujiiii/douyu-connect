package io.github.douyuconnect.protocol;
import io.github.douyuconnect.config.Credentials;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/**
 * Wire templates adapted from opensource-douyu-barrage by yijianguanzhu (Apache-2.0).
 * See THIRD_PARTY_NOTICES.md. Credentials and timestamps are never logged.
 */
public final class Commands {
    private Commands() {}
    private static final String VK_SECRET = "r5*^5;}2#${XF[h+;'./.Q'1;,-]f'p[";
    public static String login(String room, Credentials credentials) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("type","loginreq"); fields.put("roomid",room);
        if (credentials != null) {
            long tick = Instant.now().getEpochSecond();
            fields.put("username",credentials.username()); fields.put("password","");
            fields.put("ltkid",Long.toString(credentials.loginTicketId())); fields.put("biz",Integer.toString(credentials.biz()));
            fields.put("stk",credentials.sessionToken()); fields.put("devid",credentials.deviceId());
            fields.put("ct","0"); fields.put("pt","2"); fields.put("rt",Long.toString(tick));
            fields.put("vk",md5(tick + VK_SECRET + credentials.deviceId()));
            fields.put("ver","20180222"); fields.put("aver","219032101"); fields.put("dmbt","chrome"); fields.put("dmbv","98");
        }
        return Stt.encode(fields);
    }
    public static String join(String room, boolean sending) {
        return sending ? Stt.encode(Map.of("type","h5ckreq","rid",room,"ti","2501" + LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE)))
            : Stt.encode(Map.of("type","joingroup","rid",room,"gid","-9999"));
    }
    public static String heartbeat(boolean sending) {
        return sending ? Stt.encode(Map.of("type","keeplive","vbw","0","cdn","","tick",Long.toString(Instant.now().getEpochSecond()),"kd",""))
            : "type@=mrkl/";
    }
    public static String chat(String text, Credentials credentials) {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("content",text); fields.put("col","0"); fields.put("type","chatmessage");
        fields.put("sender",Long.toString(credentials.userId())); fields.put("ifs","0"); fields.put("nc","0");
        fields.put("dat","0"); fields.put("rev","0"); fields.put("admzq","0");
        fields.put("cst",Long.toString(System.currentTimeMillis())); fields.put("dmt","3");
        return Stt.encode(fields);
    }
    private static String md5(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("MD5 unavailable",e); }
    }
}
