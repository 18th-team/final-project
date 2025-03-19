package com.team.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, UserCreateForm> {
    @Override
    public boolean isValid(UserCreateForm userCreateForm, ConstraintValidatorContext context) {
        return userCreateForm.getPassword1() != null && userCreateForm.getPassword1().equals(userCreateForm.getPassword2());
    }
}