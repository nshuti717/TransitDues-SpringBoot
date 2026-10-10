package rw.ac.auca.transitdues.verification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyAccountForm {

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Enter the 6-digit code from your email.")
    @Pattern(regexp = "^[0-9]{6}$", message = "The code must be exactly 6 digits.")
    private String otpCode;
}
