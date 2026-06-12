package io.quiniela.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SyncTokenFilterTest {

  private MockHttpServletResponse run(String configured, String header) throws Exception {
    SyncTokenFilter filter = new SyncTokenFilter(configured);
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/internal/sync/daily");
    if (header != null) req.addHeader("X-Sync-Token", header);
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, new MockFilterChain());
    return res;
  }

  @Test
  void rejectsMissingHeader() throws Exception {
    assertThat(run("secret", null).getStatus()).isEqualTo(401);
  }

  @Test
  void rejectsWrongToken() throws Exception {
    assertThat(run("secret", "nope").getStatus()).isEqualTo(401);
  }

  @Test
  void failsClosedWhenNoTokenConfigured() throws Exception {
    assertThat(run("", "anything").getStatus()).isEqualTo(401);
  }

  @Test
  void passesOnMatch() throws Exception {
    MockHttpServletResponse res = run("secret", "secret");
    assertThat(res.getStatus()).isEqualTo(200);
  }
}
