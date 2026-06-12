package io.quiniela.api.footballdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Config for the results auto-sync. Bound from {@code app.sync.*}. */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    String token,
    Integer firstPollOffsetMinutes,
    Integer pollWindowHours,
    Integer retryIntervalMinutes,
    Tasks tasks) {

  public SyncProperties {
    if (firstPollOffsetMinutes == null) firstPollOffsetMinutes = 0;
    if (pollWindowHours == null) pollWindowHours = 5;
    if (retryIntervalMinutes == null) retryIntervalMinutes = 5;
    if (tasks == null) tasks = new Tasks(null, null, null);
  }

  /** Cloud Tasks settings. {@code enabled=false} ⇒ no-op queue (local/test). */
  public record Tasks(Boolean enabled, String queue, String targetBase) {
    public Tasks {
      if (enabled == null) enabled = false;
    }
  }
}
