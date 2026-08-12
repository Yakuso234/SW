package com.jiake.jk.user.pojo.response;

import com.jiake.jk.user.pojo.entity.Merchant;
import lombok.Data;

@Data
public class MerchantBasicDataResponse {
    private String name;
    private String contactPhone;
    private String avatarUrl;
    private Merchant.CertificationStatus certificationStatus;
}
