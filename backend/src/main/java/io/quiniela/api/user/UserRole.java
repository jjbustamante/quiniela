package io.quiniela.api.user;

public enum UserRole {
  ADMIN,
  CAPTAIN,
  PLAYER;

  public boolean canInvite() {
    return this == ADMIN || this == CAPTAIN;
  }

  /** Role assigned to a new sign-up invited by someone of this role. */
  public UserRole invitee() {
    return switch (this) {
      case ADMIN -> CAPTAIN;
      case CAPTAIN -> PLAYER;
      case PLAYER -> throw new IllegalStateException("Players cannot invite");
    };
  }
}
