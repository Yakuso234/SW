package com.jiake.jk.user.service;

import com.jiake.jk.user.pojo.request.LoginRequest;
import com.jiake.jk.user.pojo.request.RegisterRequest;
import org.apache.coyote.BadRequestException;

public interface AuthService {
    void register(RegisterRequest registerRequest) throws BadRequestException;
    String login(LoginRequest loginRequest) throws BadRequestException;
}
