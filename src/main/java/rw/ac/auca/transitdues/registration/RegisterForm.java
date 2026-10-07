package rw.ac.auca.transitdues.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Self-registration input, bound from the public /register form. Deliberately
 * separate from the Operator/UserAccount entities (never bind a form straight
 * to an entity) so a submitted request can only ever describe an OPERATOR
 * account - there is no role field here for a caller to tamper with.
 */
@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Full name is required.")
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Phone number is required.")
    @Pattern(regexp = "^(07[0-9]{8})$", message = "Phone number must be a valid Rwandan phone number (e.g. 07XXXXXXXX)")
    private String phoneNumber;

    @NotBlank(message = "Plate number is required.")
    private String plateNumber;

    @NotNull(message = "Choose a stage.")
    private UUID stageId;

    @NotBlank(message = "Password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String password;

    @NotBlank(message = "Confirm your password.")
    private String confirmPassword;
}
