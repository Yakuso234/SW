package com.jiake.jk.user.service;

import com.jiake.jk.user.pojo.request.ProfileRequest;
import com.jiake.jk.user.pojo.response.ProfileBasicResponse;
import com.jiake.jk.user.pojo.response.ProfileResponse;
import com.jiake.jk.user.pojo.response.ProfileStatsResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProfileService {
    ProfileResponse getProfile(Long userId);

    ProfileResponse getProfile(Long userId, Long currentUserId);

    ProfileBasicResponse getProfileBasic(Long userId);

    ProfileStatsResponse getProfileStats(Long user);

    String postAvatar(Long userId, MultipartFile avatar) throws IOException;

    void putProfile(Long userId, ProfileRequest profileRequest);

    String randomAvatar(Long userId) throws IOException;
}
