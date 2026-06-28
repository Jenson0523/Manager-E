package com.tengyei.common;

import com.tengyei.common.response.Result;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ResultTest {

    @Test
    void success_returns_code_zero() {
        Result<String> result = Result.ok("hello");
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isEqualTo("hello");
        assertThat(result.getMsg()).isEqualTo("success");
    }

    @Test
    void ok_without_data_returns_null_data() {
        Result<Void> result = Result.ok();
        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).isNull();
    }

    @Test
    void fail_returns_error_code_and_message() {
        Result<Void> result = Result.fail(403, "无权限");
        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMsg()).isEqualTo("无权限");
        assertThat(result.getData()).isNull();
    }

    @Test
    void fail_with_message_only_returns_500() {
        Result<Void> result = Result.fail("出错了");
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("出错了");
    }
}
