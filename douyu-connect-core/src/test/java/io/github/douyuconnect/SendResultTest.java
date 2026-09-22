package io.github.douyuconnect;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SendResultTest {
    @Test void mapsOnlyVerifiedChatResponseCodes() {
        Map<String,SendFailureReason> reasons = Map.of(
            "2", SendFailureReason.ROOM_MUTED, "5", SendFailureReason.GLOBAL_MUTED,
            "6", SendFailureReason.FANS_ONLY, "206", SendFailureReason.INVALID_OPERATION,
            "208", SendFailureReason.TARGET_USER_NOT_FOUND, "289", SendFailureReason.DUPLICATE_MESSAGE,
            "290", SendFailureReason.RATE_LIMITED, "391", SendFailureReason.ACCOUNT_VERIFICATION_REQUIRED);
        reasons.forEach((code,reason) -> {
            SendResult result = SendResult.fromChatResponse(Map.of("type","chatres","res",code));
            assertEquals(SendResult.Status.REJECTED,result.status());
            assertEquals(code,result.serverCode()); assertEquals(reason,result.reason());
            assertNotNull(result.message()); assertFalse(result.message().isBlank());
        });
        assertEquals("需要账号安全验证",SendResult.fromChatResponse(Map.of("type","chatres","res","391")).message());
    }

    @Test void preservesRawReceiptWithoutMutationAndDoesNotLogItsPayload() {
        Map<String,String> fields = new LinkedHashMap<>(Map.of("type","chatres","res","391","cd","5","len","50","extension","private-server-field"));
        SendResult result = SendResult.fromChatResponse(fields);
        fields.put("res","0");
        assertEquals("391",result.rawResponse().get("res"));
        assertEquals("5",result.rawResponse().get("cd"));
        assertEquals("private-server-field",result.rawResponse().get("extension"));
        assertThrows(UnsupportedOperationException.class,() -> result.rawResponse().put("res","0"));
        assertFalse(result.toString().contains("private-server-field"));
    }

    @Test void unknownCodesAndOtherNamespacesAreNotInvented() {
        for (String code : List.of("288","308","356","363","4202","4207","999999","")) {
            SendResult result = SendResult.fromChatResponse(Map.of("type","chatres","res",code));
            assertEquals(SendFailureReason.UNKNOWN_SERVER_CODE,result.reason());
            assertEquals(code,result.serverCode());
        }
        assertThrows(IllegalArgumentException.class,() -> SendResult.fromChatResponse(Map.of("type","error","code","4202")));
        assertThrows(IllegalArgumentException.class,() -> SendResult.fromChatResponse(Map.of("type","chatres")));
    }

    @Test void successAndLocalFailuresHaveDistinctReasonsAndKeepOldConstructorUsable() {
        assertEquals(SendFailureReason.NONE,SendResult.fromChatResponse(Map.of("type","chatres","res","0")).reason());
        assertEquals(SendFailureReason.SENDER_NOT_READY,new SendResult(SendResult.Status.NOT_READY,null).reason());
        assertEquals(SendFailureReason.SEND_QUEUE_FULL,new SendResult(SendResult.Status.QUEUE_FULL,null).reason());
        assertEquals(SendFailureReason.REQUEST_CANCELLED,new SendResult(SendResult.Status.CANCELLED,null).reason());
        assertEquals(SendFailureReason.DELIVERY_UNKNOWN,new SendResult(SendResult.Status.UNKNOWN,null).reason());
        assertEquals(SendFailureReason.ACCOUNT_VERIFICATION_REQUIRED,new SendResult(SendResult.Status.REJECTED,"391").reason());
    }
}
