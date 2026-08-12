package com.jiake.jk.user.service;

public interface PreferencesService {

    void initUserPreferences(Long userId);

    float[] getUserVideoPreferences(Long userId);
}
