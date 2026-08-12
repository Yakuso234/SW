package com.jiake.jk.admin.service;

import com.jiake.jk.admin.request.PostAccountRequest;
import com.jiake.jk.admin.request.PutAccountRequest;
import com.jiake.jk.admin.response.AccountResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface AccountService {
    void postAccount(PostAccountRequest postAccountRequest) throws BadRequestException;

    void deleteAccount(Long id);

    void updateAccount(PutAccountRequest putAccountRequest);

    List<AccountResponse> getAccounts(Long id, String name, String createdTime);

    List<AccountResponse> getAllAccounts();

    void putPassword(Long id, String password);
}
