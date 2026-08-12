package com.jiake.jk.user.service;

import com.jiake.jk.user.pojo.response.MarketplaceHomeDataResponse;

public interface HomeIndexService {
    MarketplaceHomeDataResponse getMarketplaceHomeData(Long userId);
}
