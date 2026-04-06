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
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    private String description;

    private String barcode;

    private String genericName;

    private String manufacturer;

    private String packSize;

    @Column(nullable = false)
    private BigDecimal price;

    private BigDecimal mrp;

    private BigDecimal purchasePrice;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    @Builder.Default
    private Integer minStock = 5;

    //GST Related 
    @Builder.Default
    private Integer gstPercent = 0;

    @Builder.Default
    private boolean prescriptionRequired = false;

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    @JsonIgnore
    private Shop shop;

    @Builder.Default
    private boolean active = true;

}
