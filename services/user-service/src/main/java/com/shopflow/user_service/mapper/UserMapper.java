package com.shopflow.user_service.mapper;

import com.shopflow.user_service.domain.User;
import com.shopflow.user_service.domain.UserRole;
import com.shopflow.user_service.dto.UserDto;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;

/**
 * MapStruct generates the implementation of this interface at compile time.
 * Run 'mvn compile' and look in target/generated-sources to see it.
 *
 * componentModel = "spring" means the generated class is a @Component
 * so Spring can inject it anywhere with @Autowired / constructor injection.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(mapRoles(user.getUserRoles()))")
    UserDto.UserResponse toResponse(User user);

    List<UserDto.UserResponse> toResponseList(List<User> users);

    // ── Custom mapping methods ─────────────────────────────────────

    default List<String> mapRoles(Set<UserRole> userRoles) {
        if (userRoles == null) return List.of();
        return userRoles.stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();
    }
}