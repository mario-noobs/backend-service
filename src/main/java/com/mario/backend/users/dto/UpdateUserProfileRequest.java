package com.mario.backend.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    @Pattern(regexp = "^(male|female|other|prefer_not_to_say)$", message = "Gender must be one of: male, female, other, prefer_not_to_say")
    private String gender;

    @Past(message = "Date of birth must be in the past")
    @JsonProperty("date_of_birth")
    private LocalDate dateOfBirth;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    @JsonProperty("avatar_url")
    private String avatarUrl;

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    @JsonProperty("display_name")
    private String displayName;

    @Size(max = 2000, message = "Bio must not exceed 2000 characters")
    private String bio;

    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    @JsonProperty("address_line_1")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    @JsonProperty("address_line_2")
    private String addressLine2;

    @Size(max = 128, message = "City must not exceed 128 characters")
    private String city;

    @Size(max = 128, message = "State must not exceed 128 characters")
    private String state;

    @Size(max = 40, message = "Postal code must not exceed 40 characters")
    @JsonProperty("postal_code")
    private String postalCode;

    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter uppercase ISO 3166-1 alpha-2 code")
    private String country;
}
