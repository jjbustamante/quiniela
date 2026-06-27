package io.quiniela.api.footballdata;

import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Enqueues an HTTP-target Cloud Task that calls back into this service's /internal/sync/results
 * endpoint, carrying the shared-secret header. Authenticates to Cloud Tasks via the runtime SA's
 * ADC. Active only when app.sync.tasks.enabled=true.
 */
@Component
@ConditionalOnProperty(prefix = "app.sync.tasks", name = "enabled", havingValue = "true")
public class CloudTasksResultsQueue implements ResultsTaskQueue {

  private static final Logger log = LoggerFactory.getLogger(CloudTasksResultsQueue.class);

  private final SyncProperties props;

  public CloudTasksResultsQueue(SyncProperties props) {
    this.props = props;
  }

  @Override
  public void enqueue(long matchId, Instant when, String dedupName) {
    String queue = props.tasks().queue();
    String url = props.tasks().targetBase() + "/internal/sync/results?matchId=" + matchId;
    HttpRequest req =
        HttpRequest.newBuilder()
            .setUrl(url)
            .setHttpMethod(HttpMethod.POST)
            .putHeaders("X-Sync-Token", props.token() == null ? "" : props.token())
            .setBody(ByteString.EMPTY)
            .build();
    Task task =
        Task.newBuilder()
            .setName(queue + "/tasks/" + dedupName)
            .setHttpRequest(req)
            .setScheduleTime(Timestamp.newBuilder().setSeconds(when.getEpochSecond()).build())
            .build();
    try (CloudTasksClient client = CloudTasksClient.create()) {
      client.createTask(queue, task);
      log.info("enqueued result check for match {} at {} (name={})", matchId, when, dedupName);
    } catch (com.google.api.gax.rpc.AlreadyExistsException e) {
      log.debug("task {} already enqueued; skipping (dedup)", dedupName);
    } catch (Exception e) {
      log.warn("failed to enqueue result check for match {}", matchId, e);
    }
  }

  @Override
  public void enqueueFixturesRefresh(Instant when, String dedupName) {
    String queue = props.tasks().queue();
    String url = props.tasks().targetBase() + "/internal/sync/fixtures";
    HttpRequest req =
        HttpRequest.newBuilder()
            .setUrl(url)
            .setHttpMethod(HttpMethod.POST)
            .putHeaders("X-Sync-Token", props.token() == null ? "" : props.token())
            .setBody(ByteString.EMPTY)
            .build();
    Task task =
        Task.newBuilder()
            .setName(queue + "/tasks/" + dedupName)
            .setHttpRequest(req)
            .setScheduleTime(Timestamp.newBuilder().setSeconds(when.getEpochSecond()).build())
            .build();
    try (CloudTasksClient client = CloudTasksClient.create()) {
      client.createTask(queue, task);
      log.info("enqueued fixtures refresh at {} (name={})", when, dedupName);
    } catch (com.google.api.gax.rpc.AlreadyExistsException e) {
      log.debug("fixtures task {} already enqueued; skipping (dedup)", dedupName);
    } catch (Exception e) {
      log.warn("failed to enqueue fixtures refresh (name={})", dedupName, e);
    }
  }
}
