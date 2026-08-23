package com.jiake.jk.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class AWSUtilsTest {

    @Test
    void generateAccessUrl_shouldKeepMissingObjectKeyAsNull() {
        AWSUtils awsUtils = new AWSUtils();

        assertNull(awsUtils.generateAccessUrl(null));
        assertNull(awsUtils.generateAccessUrl("   "));
    }
}
