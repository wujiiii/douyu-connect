package io.github.douyuconnect;
import java.util.concurrent.CompletionStage;
public interface Subscription { CompletionStage<Void> cancel(); }
