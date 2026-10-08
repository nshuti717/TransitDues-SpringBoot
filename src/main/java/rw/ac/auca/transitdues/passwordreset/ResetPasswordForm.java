package rw.ac.auca.transitdues.passwordreset;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordForm {

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Enter the 6-digit code from your email.")
    @Pattern(regexp = "^[0-9]{6}$", message = "The code must be exactly 6 digits.")
    private String otpCode;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String newPassword;

    @NotBlank(message = "Confirm your new password.")
    private String confirmPassword;
}
