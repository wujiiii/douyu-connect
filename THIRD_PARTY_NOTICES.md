# Third-party notices and source provenance

The protocol templates in `douyu-connect-core/src/main/java/io/github/douyuconnect/protocol/Commands.java`
are adapted from the `opensource-douyu-barrage` module in the user's local RuoYi-Vue checkout.

Original attribution:

Copyright (c) 2021-2031, yijianguanzhu (yijianguanzhu@gmail.com).
Licensed under the Apache License, Version 2.0. The full license is included in `LICENSE`.

Reference files: `DefaultPushMessageType2PullBarrage.java`, `DefaultPushMessageType2PushBarrage.java`,
and `DefaultWebSocketClientConfiguration.java`. This implementation changes configuration ownership,
STT escaping, timestamp generation, lifecycle management and credential handling.

Message field mappings were checked against the local RuoYi-Vue `ruoyi-danmu` handlers at commit
`62fd2240` and the working tree inspected on 2026-09-22. The original RuoYi project is MIT licensed,
Copyright (c) 2018 RuoYi. No business service, database mapper or Redis implementation is bundled.

The documented original-field getters in `message/` also reference the Apache-2.0 licensed
`opensource-douyu-barrage/model/BaseMessage.java` field definitions (attribution above).
They preserve decoded field values, add JavaBean accessors and distinguish protocol room IDs
from connection metadata; no default counts, gift pricing or account business rules are copied.

The anonymous receive-login template follows the user-provided example and the original
`ruoyi-danmu/client/DouyuDanmuClient.java` (RuoYi attribution above). Random visitor identifiers
are generated with the JDK rather than introducing a Hutool dependency; authenticated sender
login retains its separate upstream template.

Netty is a runtime dependency licensed under Apache-2.0; JUnit is a test-only dependency licensed
under EPL-2.0. The example executable bundles Netty and retains its packaged notices.

Dependency reference: https://netty.io/news/2026/09/09/4-1-138-Final.html
