package com.jiake.jk.video.pojo.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatorAnalyticsTrendResponse {
    private String date;
    private Long views;
}
