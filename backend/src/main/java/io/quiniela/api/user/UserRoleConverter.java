package io.quiniela.api.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps UserRole enum ↔ lowercase VARCHAR stored by V003 migration.
 *
 * <p>V003 stores role values as lowercase ('admin', 'captain', 'player') to match the CHECK
 * constraint. JPA's @Enumerated(EnumType.STRING) would write the enum's .name() (uppercase), which
 * would violate that constraint. This converter translates in both directions.
 */
@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

  @Override
  public String convertToDatabaseColumn(UserRole role) {
    if (role == null) return null;
    return role.name().toLowerCase();
  }

  @Override
  public UserRole convertToEntityAttribute(String dbValue) {
    if (dbValue == null) return null;
    return UserRole.valueOf(dbValue.toUpperCase());
  }
}
