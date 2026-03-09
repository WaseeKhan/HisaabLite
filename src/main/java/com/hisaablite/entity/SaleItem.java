package com.hisaablite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer quantity;

    private BigDecimal priceAtSale;

    private BigDecimal subtotal;

     // GST fields here
    private Integer gstPercent;
    private BigDecimal gstAmount;
    private BigDecimal totalWithGst;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    private Sale sale;
}