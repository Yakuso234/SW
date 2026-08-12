package com.jiake.jk.product.pojo.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.jiake.jk.product.pojo.model.entity.Product;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductManageItemResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productId;
    private String title;
    private Product.ProductStatus status;
    private Integer salesVolume;
    private Float rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
