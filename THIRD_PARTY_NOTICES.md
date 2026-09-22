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

Netty is a runtime dependency licensed under Apache-2.0; JUnit is a test-only dependency licensed
under EPL-2.0. The example executable bundles Netty and retains its packaged notices.

Dependency reference: https://netty.io/news/2026/09/09/4-1-138-Final.html
