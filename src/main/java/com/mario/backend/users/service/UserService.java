package com.mario.backend.users.service;

import com.mario.backend.common.exception.ApiException;
import com.mario.backend.common.exception.ErrorCode;
import com.mario.backend.logging.annotation.Traceable;
import com.mario.backend.rbac.entity.Permission;
import com.mario.backend.rbac.entity.Role;
import com.mario.backend.users.dto.UpdateProfileRequest;
import com.mario.backend.users.dto.UpdateUserProfileRequest;
import com.mario.backend.users.dto.UserProfileResponse;
import com.mario.backend.users.dto.UserResponse;
import com.mario.backend.users.entity.User;
import com.mario.backend.users.entity.UserProfile;
import com.mario.backend.users.repository.UserProfileRepository;
import com.mario.backend.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Traceable("user.getProfile")
    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        return mapToResponse(user);
    }

    @Traceable("user.updateProfile")
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(request.getPhone());
        }

        user = userRepository.save(user);

        if (request.getProfile() != null) {
            updateUserProfile(user, request.getProfile());
        }

        return mapToResponse(user);
    }

    private void updateUserProfile(User user, UpdateUserProfileRequest profileRequest) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder().user(user).build());

        if (profileRequest.getGender() != null) {
            profile.setGender(UserProfile.Gender.valueOf(profileRequest.getGender()));
        }
        if (profileRequest.getDateOfBirth() != null) {
            profile.setDateOfBirth(profileRequest.getDateOfBirth());
        }
        if (profileRequest.getAvatarUrl() != null) {
            profile.setAvatarUrl(profileRequest.getAvatarUrl());
        }
        if (profileRequest.getDisplayName() != null) {
            profile.setDisplayName(profileRequest.getDisplayName());
        }
        if (profileRequest.getBio() != null) {
            profile.setBio(profileRequest.getBio());
        }
        if (profileRequest.getAddressLine1() != null) {
            profile.setAddressLine1(profileRequest.getAddressLine1());
        }
        if (profileRequest.getAddressLine2() != null) {
            profile.setAddressLine2(profileRequest.getAddressLine2());
        }
        if (profileRequest.getCity() != null) {
            profile.setCity(profileRequest.getCity());
        }
        if (profileRequest.getState() != null) {
            profile.setState(profileRequest.getState());
        }
        if (profileRequest.getPostalCode() != null) {
            profile.setPostalCode(profileRequest.getPostalCode());
        }
        if (profileRequest.getCountry() != null) {
            profile.setCountry(profileRequest.getCountry());
        }

        userProfileRepository.save(profile);
    }

    @Traceable("user.updateStatus")
    @Transactional
    public UserResponse updateStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        try {
            user.setStatus(User.UserStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_USER_STATUS, "Invalid status: " + status + ". Must be one of: activated, deactivated, banned");
        }

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    public UserResponse mapToResponse(User user) {
        Role role = user.getRole();
        UserResponse.RoleInfo roleInfo = null;
        if (role != null) {
            List<String> permissions = role.getPermissions().stream()
                    .map(Permission::getName)
                    .toList();
            roleInfo = UserResponse.RoleInfo.builder()
                    .name(role.getName())
                    .permissions(permissions)
                    .build();
        }

        UserProfileResponse profileResponse = userProfileRepository.findByUserId(user.getId())
                .map(this::mapProfileToResponse)
                .orElse(null);

        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus().name())
                .role(roleInfo)
                .profile(profileResponse)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private UserProfileResponse mapProfileToResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .gender(profile.getGender() != null ? profile.getGender().name() : null)
                .dateOfBirth(profile.getDateOfBirth())
                .avatarUrl(profile.getAvatarUrl())
                .displayName(profile.getDisplayName())
                .bio(profile.getBio())
                .addressLine1(profile.getAddressLine1())
                .addressLine2(profile.getAddressLine2())
                .city(profile.getCity())
                .state(profile.getState())
                .postalCode(profile.getPostalCode())
                .country(profile.getCountry())
                .build();
    }
}
