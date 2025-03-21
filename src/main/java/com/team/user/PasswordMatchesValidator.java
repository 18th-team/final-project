package com.team.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, UserCreateForm> {
    @Override
    public boolean isValid(UserCreateForm userCreateForm, ConstraintValidatorContext context) {
        String password1 = userCreateForm.getPassword1();
        String password2 = userCreateForm.getPassword2();

        // null 체크 및 일치 여부 확인
        boolean isValid = password1 != null && password1.equals(password2);

        if (!isValid) {
            // 기본 메시지 비활성화
            context.disableDefaultConstraintViolation();
            // 커스텀 메시지 추가 (password2 필드에 에러 표시)
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("password2")
                    .addConstraintViolation();
        }

        return isValid;
    }
}