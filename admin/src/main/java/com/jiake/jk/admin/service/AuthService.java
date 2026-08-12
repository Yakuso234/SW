package com.jiake.jk.admin.service;

import com.jiake.jk.admin.request.LoginRequest;
import org.apache.coyote.BadRequestException;

public interface AuthService {
    String login(LoginRequest loginRequest) throws BadRequestException;
}
