package io.quiniela.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @Column(nullable = false)
  private UserRole role;

  @Column(name = "invited_by_user_id")
  private Long invitedByUserId;

  @Column(name = "invite_path", unique = true)
  private String invitePath;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  public User(String googleSub, String email, String displayName, String avatarUrl, UserRole role) {
    this.googleSub = googleSub;
    this.email = email;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
    this.role = role;
  }

  @PrePersist
  void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  public Long getInvitedByUserId() {
    return invitedByUserId;
  }

  public void setInvitedByUserId(Long id) {
    this.invitedByUserId = id;
  }

  public String getInvitePath() {
    return invitePath;
  }

  public void setInvitePath(String invitePath) {
    this.invitePath = invitePath;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // ── Compatibility bridges for AuthController / JwtService ────────────────
  // TODO(Task 5): remove when AuthController + JwtService are rewritten to use UserRole.

  /**
   * @deprecated use {@link #User(String, String, String, String, UserRole)} instead; removed in
   *     Task 5.
   */
  @Deprecated
  public User(String googleSub, String email, String displayName, String avatarUrl, boolean admin) {
    this(googleSub, email, displayName, avatarUrl, admin ? UserRole.ADMIN : UserRole.PLAYER);
  }

  /**
   * @deprecated use {@link #getRole()} instead; removed in Task 5.
   */
  @Deprecated
  public boolean isAdmin() {
    return role == UserRole.ADMIN;
  }

  /**
   * @deprecated use {@link #setRole(UserRole)} instead; removed in Task 5.
   */
  @Deprecated
  public void setAdmin(boolean admin) {
    if (!admin) {
      throw new UnsupportedOperationException(
          "setAdmin(false) is not supported; use setRole(UserRole) — bridge removed in Task 5");
    }
    this.role = UserRole.ADMIN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
