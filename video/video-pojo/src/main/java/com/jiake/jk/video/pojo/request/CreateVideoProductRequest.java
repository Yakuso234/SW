package com.jiake.jk.video.pojo.request;

public record CreateVideoProductRequest(Long videoId, String name, String description,
                                        String imageUrl, Integer priceCent, Integer stock) {
}

