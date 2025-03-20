package com.team.user;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@PasswordMatches
public class UserCreateForm {
    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;

    @Email(message = "유효한 이메일 형식이 아닙니다.")
    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,20}$",
            message = "비밀번호는 영문자, 숫자, 특수문자를 포함해야 합니다.")
    private String password1;

    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    @Size(min = 8, max = 20, message = "비밀번호 확인은 8자 이상 20자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*])[A-Za-z\\d!@#$%^&*]{8,20}$",
            message = "비밀번호 확인은 영문자, 숫자, 특수문자를 포함해야 합니다.")
    private String password2;

    @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^01[016789]\\d{7,8}$", message = "유효한 전화번호 형식이 아닙니다.")
    private String phone;

    @NotBlank(message = "통신사를 선택하세요.")
    private String cellCorp;
    @NotBlank(message = "생년월일은 필수 입력 항목입니다.")
    @Size(min = 6, max = 6, message = "생년월일은 6자여야 합니다.")
    private String birthDay1;

    @NotBlank(message = "성별은 필수 입력 항목입니다.")
    private String birthDay2;

    @Size(min = 6, max = 6, message = "보안문자 6자여야 합니다.")
    @NotBlank(message = "보안문자는 필수 입력 항목입니다.")
    private String captchaInput;

    private String otp;

    private String profileImage;

    @NotBlank
    private String otpVerified;
    @NotBlank
    private String clientKey;
}
