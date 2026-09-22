package io.github.douyuconnect.protocol;

import org.junit.jupiter.api.Test;
import io.github.douyuconnect.message.*;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClassificationTest {
    private DouyuMessage parse(String text) { return MessageParser.parse("123", "room-1", "connection-1", 7, Instant.EPOCH, text); }
    @Test void classifiesAllSupportedTypesWithoutBusinessRules() {
        Map<String, Category> cases = Map.of(
            "type@=chatmsg/", Category.CHAT, "type@=dgb/", Category.GIFT,
            "type@=dfobc/", Category.FANS_BADGE, "type@=dfrbc/", Category.FANS_BADGE,
            "type@=anbc/", Category.NOBLE, "type@=rss/", Category.ROOM_STATUS,
            "type@=gbroadcast/btype@=pandora/", Category.PANDORA_BROADCAST,
            "type@=gbroadcast/btype@=voiceDanmu/", Category.VOICE_DANMU,
            "type@=newtype/btype@=fantasy/", Category.GENERIC);
        cases.forEach((text, category) -> assertEquals(category, parse(text).category()));
    }
    @Test void preservesGiftCountsIdsAndZeroValues() {
        GiftMessage gift = (GiftMessage) parse("type@=dgb/gfid@=0/pid@=12/hits@=3/gfcnt@=10/extra@=x/");
        assertEquals("0", gift.giftId()); assertEquals("12", gift.propId());
        assertEquals(3L, gift.hits()); assertEquals(10L, gift.count());
        assertEquals("x", gift.context().rawFields().get("extra"));
    }
    @Test void keepsBroadcastNestedAndDoesNotGuessQuantity() {
        String nested = Stt.encode(Map.of("nn", "中文/@S", "txt", "hello"));
        PandoraBroadcastMessage message = (PandoraBroadcastMessage) parse(Stt.encode(Map.of(
            "type", "gbroadcast", "btype", "pandora", "chatmsg", nested, "txt5", "2组，每组10个")));
        assertEquals("2组，每组10个", message.quantityText());
        assertEquals("中文/@S", message.chatFields().get("nn"));
    }
    @Test void keepsMissingNumbersNullableAndReportsMalformedValues() {
        GiftMessage gift = (GiftMessage) parse("type@=dgb/hits@=bad/gfcnt@=/");
        assertNull(gift.hits()); assertNull(gift.count());
        assertFalse(gift.context().parseIssues().isEmpty());
        assertEquals("123", gift.context().roomId()); assertEquals(7, gift.context().sequence());
    }
    @Test void distinguishesFanActionsAndPreservesRawRoomId() {
        FansBadgeMessage open = (FansBadgeMessage) parse("type@=dfobc/rid@=999/");
        assertEquals(FansBadgeMessage.Action.OPEN, open.action());
        assertEquals(FansBadgeMessage.Action.RENEW, ((FansBadgeMessage) parse("type@=dfrbc/")).action());
        assertEquals("999", open.context().rawFields().get("rid"));
        assertThrows(UnsupportedOperationException.class, () -> open.context().rawFields().put("x", "y"));
    }
}
