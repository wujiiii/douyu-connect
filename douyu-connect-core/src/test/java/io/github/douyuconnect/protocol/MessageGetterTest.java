package io.github.douyuconnect.protocol;

import io.github.douyuconnect.message.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MessageGetterTest {
    private DouyuMessage parse(Map<String,String> fields) {
        return MessageParser.parse("100","instance","connection",5,Instant.EPOCH,Stt.encode(fields));
    }

    static Stream<Arguments> originalFieldSets() {
        return Stream.of(
            Arguments.of("chatmsg", "", ChatMessage.class,
                "cid gid uid userid nn txt level gt col ct rg pg cmt ic nl nc bnn bl brid hc ol rev hl ifs cst"),
            Arguments.of("dgb", "", GiftMessage.class,
                "gid uid nn ic level gt ct rg pg nl nc bnn bl brid hc ol cst gfid pid gfn gs bg gfcnt hits receive_nn"),
            Arguments.of("dfobc", "", FansBadgeMessage.class, "uid nick icon rnick mn price"),
            Arguments.of("dfrbc", "", FansBadgeMessage.class, "uid nick icon rnick mn price"),
            Arguments.of("anbc", "", NobleMessage.class, "uid unk uic donk nl"),
            Arguments.of("gbroadcast", "pandora", PandoraBroadcastMessage.class, "uid txt4 txt5 txt6 chatmsg"),
            Arguments.of("gbroadcast", "voiceDanmu", VoiceDanmuMessage.class, "uid crealPrice chatmsg"),
            Arguments.of("rss", "", RoomStatusMessage.class, "ss"),
            // Legacy BaseMessage mixed several not-yet-classified message types. Keep these accessible too.
            Arguments.of("unknown", "", GenericMessage.class,
                "cid gid uid userid res nn txt level gt col ct rg pg cmt ic nl nc bnn bl brid hc ol rev hl ifs cst "
                    + "gfid gs bg gfcnt hits sl sid did snk dnk rpt sn dn gn gc drid gb es eid sdid trid content code desc "
                    + "pid gfn receive_nn nick icon rnick mn price unk uic donk txt4 txt5 txt6 chatmsg crealPrice ss")
        );
    }

    @ParameterizedTest
    @MethodSource("originalFieldSets")
    void exposesOriginalFieldsAsLosslessBeanProperties(String type,String btype,Class<?> expectedType,String keys) throws Exception {
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("type",type); fields.put("btype",btype); fields.put("rid","200");
        for (String key : keys.split(" ")) fields.put(key,"raw-" + key + "/@S");
        fields.put("future_field","retained");
        DouyuMessage message = parse(fields);
        assertInstanceOf(expectedType,message);
        Map<String,Method> properties = new HashMap<>();
        for (var property : Introspector.getBeanInfo(expectedType).getPropertyDescriptors()) {
            properties.put(property.getName(),property.getReadMethod());
        }
        for (var field : fields.entrySet()) {
            if (field.getKey().equals("future_field")) continue;
            String property = field.getKey().equals("receive_nn") ? "receiveNn" : field.getKey();
            Method getter = properties.get(property);
            assertNotNull(getter,expectedType.getSimpleName() + " missing readable property " + property);
            assertEquals(field.getValue(),getter.invoke(message),property);
        }
        assertEquals("200",call(message,"getRid"));
        assertEquals("100",call(message,"getConnectionRoomId"));
        assertEquals(fields,call(message,"getRawFields"));
        assertEquals(Stt.encode(fields),call(message,"getRawText"));
    }

    @Test void numericGettersRetainRawValueWhileOldTypedAccessorsRemainCompatible() throws Exception {
        GiftMessage gift = (GiftMessage) parse(Map.of("type","dgb","gfid","0","pid","0007","gfcnt","0002","hits","bad","nn",""));
        assertEquals("0002",call(gift,"getGfcnt")); assertEquals(2L,gift.count());
        assertEquals("bad",call(gift,"getHits")); assertNull(gift.hits());
        assertEquals("0",gift.giftId()); assertEquals("0007",gift.propId());
        assertEquals("",call(gift,"getNn")); assertNull(call(gift,"getUid"));
        assertNull(call(gift,"getRid")); assertEquals("100",gift.context().roomId());
        assertEquals(gift.userId(),call(gift,"getUserId"));
        assertEquals(gift.count(),call(gift,"getCount"));
        assertFalse(gift.context().parseIssues().isEmpty());
    }

    @Test void broadcastsExposeOuterAndNestedFieldsWithoutCreatingExtraMessages() throws Exception {
        String nested = Stt.encode(Map.of("nn","昵称/@S","ic","avatar","uid","nested-user","txt","nested text"));
        for (String kind : List.of("pandora","voiceDanmu")) {
            DouyuMessage message = parse(Map.of("type","gbroadcast","btype",kind,"uid","outer-user","chatmsg",nested,"txt5","2组，每组10个"));
            assertEquals("outer-user",call(message,"getUid"));
            assertEquals(nested,call(message,"getChatmsg"));
            assertEquals("昵称/@S",call(message,"getChatNn"));
            assertEquals("avatar",call(message,"getChatIc"));
            @SuppressWarnings("unchecked") Map<String,String> decoded = (Map<String,String>) call(message,"getChatFields");
            assertEquals("nested-user",decoded.get("uid"));
            assertThrows(UnsupportedOperationException.class,() -> decoded.put("nn","changed"));
            assertEquals(5,message.context().sequence());
        }
        PandoraBroadcastMessage missing = (PandoraBroadcastMessage) parse(Map.of("type","gbroadcast","btype","pandora"));
        assertNull(call(missing,"getChatmsg")); assertNull(call(missing,"getChatNn"));
    }

    @Test void envelopeGettersSeparateTransportMetadataFromProtocolFields() throws Exception {
        DouyuMessage message = parse(Map.of("type","dgb","rid","200"));
        MessageContext context = message.context();
        assertSame(context,call(message,"getContext"));
        assertEquals(Category.GIFT,call(message,"getCategory"));
        assertEquals("100",call(context,"getRoomId"));
        assertEquals("instance",call(context,"getRoomInstanceId"));
        assertEquals("connection",call(context,"getConnectionId"));
        assertEquals(5L,call(context,"getSequence"));
        assertEquals(Instant.EPOCH,call(context,"getReceivedAt"));
        assertEquals("dgb",call(context,"getType")); assertEquals("",call(context,"getBtype"));
        assertSame(context.rawFields(),call(context,"getRawFields"));
        assertEquals(context.rawText(),call(context,"getRawText"));
        assertEquals(context.parseIssues(),call(context,"getParseIssues"));
    }

    private static Object call(Object target,String getter) throws Exception {
        Method method = Arrays.stream(target.getClass().getMethods()).filter(m -> m.getName().equals(getter) && m.getParameterCount()==0).findFirst().orElse(null);
        assertNotNull(method,"Missing getter: " + target.getClass().getSimpleName() + "." + getter);
        return method.invoke(target);
    }
}
