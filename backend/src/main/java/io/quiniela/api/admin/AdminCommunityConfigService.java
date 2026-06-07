package io.quiniela.api.admin;

import io.quiniela.api.pool.Pool;
import io.quiniela.api.pool.PoolRepository;
import io.quiniela.api.user.AdminGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only community config: the WhatsApp group link + the "available to captains" switch. */
@Service
public class AdminCommunityConfigService {

  private static final Long ACTIVE_POOL_ID = 1L;
  private static final String WHATSAPP_PREFIX = "https://chat.whatsapp.com/";

  private final PoolRepository pools;
  private final AdminGuard adminGuard;

  public AdminCommunityConfigService(PoolRepository pools, AdminGuard adminGuard) {
    this.pools = pools;
    this.adminGuard = adminGuard;
  }

  public record CommunityConfigView(String url, boolean enabled) {}

  public record UpdateRequest(String url, Boolean enabled) {}

  @Transactional(readOnly = true)
  public CommunityConfigView getConfig(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return view(pool());
  }

  @Transactional
  public CommunityConfigView updateConfig(Long callerId, UpdateRequest req) {
    adminGuard.requireAdmin(callerId);
    boolean enabled = Boolean.TRUE.equals(req.enabled());
    String url = req.url() == null ? null : req.url().trim();
    if (url != null && url.isBlank()) {
      url = null;
    }
    if (enabled) {
      if (url == null || !url.startsWith(WHATSAPP_PREFIX)) {
        throw new IllegalArgumentException(
            "A valid WhatsApp invite link (" + WHATSAPP_PREFIX + "…) is required to enable");
      }
    }
    Pool pool = pool();
    pool.setWhatsappGroupUrl(url);
    pool.setWhatsappGroupEnabled(enabled);
    pools.save(pool);
    return view(pool);
  }

  private Pool pool() {
    return pools
        .findById(ACTIVE_POOL_ID)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pool not found"));
  }

  private CommunityConfigView view(Pool pool) {
    return new CommunityConfigView(
        pool.getWhatsappGroupUrl(), Boolean.TRUE.equals(pool.getWhatsappGroupEnabled()));
  }
}
