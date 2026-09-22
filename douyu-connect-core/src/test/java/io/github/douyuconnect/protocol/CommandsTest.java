package io.github.douyuconnect.protocol;

import io.github.douyuconnect.config.Credentials;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandsTest {
    @Test void receiveLoginUsesTheVisitorTemplateAndRequestedRandomRanges() {
        for (int i = 0; i < 100; i++) {
            String packet = Commands.login("123",null);
            var fields = Stt.decode(packet);
            assertEquals(List.of("type","roomid","dfl","username","uid","ver","aver","ct"),List.copyOf(fields.keySet()));
            assertEquals("loginreq",fields.get("type")); assertEquals("123",fields.get("roomid"));
            assertEquals("",fields.get("dfl"));
            assertTrue(fields.get("username").startsWith("visitor"));
            int uid = Integer.parseInt(fields.get("uid"));
            int suffix = Integer.parseInt(fields.get("username").substring("visitor".length()));
            assertTrue(uid >= 10000 && uid < 19999);
            assertTrue(suffix >= 1000000000 && suffix < 1999999999);
            assertEquals("20220825",fields.get("ver"));
            assertEquals("218101901",fields.get("aver")); assertEquals("0",fields.get("ct"));
            assertEquals("type@=loginreq/roomid@=123/dfl@=/username@=" + fields.get("username")
                + "/uid@=" + uid + "/ver@=20220825/aver@=218101901/ct@=0/",packet);
        }
    }

    @Test void senderLoginRetainsCookieAuthenticationAndItsOwnVersionFields() {
        Credentials credentials = new Credentials("test-device",123,"account/user@name",456,"test-token",1);
        var fields = Stt.decode(Commands.login("123",credentials));
        assertEquals("account/user@name",fields.get("username"));
        assertEquals("",fields.get("password"));
        assertEquals("456",fields.get("ltkid")); assertEquals("1",fields.get("biz"));
        assertEquals("test-token",fields.get("stk")); assertEquals("test-device",fields.get("devid"));
        assertEquals("20180222",fields.get("ver")); assertEquals("219032101",fields.get("aver"));
        assertEquals("0",fields.get("ct")); assertEquals("2",fields.get("pt"));
        assertEquals("chrome",fields.get("dmbt")); assertEquals("98",fields.get("dmbv"));
        assertTrue(Long.parseLong(fields.get("rt")) > 0); assertTrue(fields.get("vk").matches("[0-9a-f]{32}"));
        assertFalse(fields.containsKey("uid")); assertFalse(fields.containsKey("dfl"));
    }
}
