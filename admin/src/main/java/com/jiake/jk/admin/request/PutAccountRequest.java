package com.jiake.jk.admin.request;

import lombok.Data;

import java.util.List;

@Data
public class PutAccountRequest {
    private String name;
    private String password;
    private List<String> permissionList;
}