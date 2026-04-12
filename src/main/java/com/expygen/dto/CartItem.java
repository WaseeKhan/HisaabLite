package com.expygen.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem {

    private Long productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;


    // GST fields here
    private Integer gstPercent;
    private BigDecimal gstAmount;
    private BigDecimal totalWithGst;
    private Boolean prescriptionRequired;
}
