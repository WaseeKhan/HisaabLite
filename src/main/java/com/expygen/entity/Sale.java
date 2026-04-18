package com.expygen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime saleDate;

    private BigDecimal totalAmount;

    //Tax related fields
    private BigDecimal totalGstAmount;
    private BigDecimal taxableAmount;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    private List<SaleItem> items;

    @Enumerated(EnumType.STRING)
    private SaleStatus status;

    // billing page

    // CUSTOMER DETAILS
    private String customerName;
    private String customerPhone;
    private String doctorName;
    private LocalDate prescriptionDate;
    private String prescriptionReference;
    private String prescriptionDocumentPath;
    private String prescriptionDocumentName;
    private String prescriptionDocumentContentType;
    @Builder.Default
    private boolean prescriptionRequired = false;
    @Builder.Default
    private boolean prescriptionVerified = false;

    // PAYMENT DETAILS
    private String paymentMode; // CASH / UPI / CARD
    private Double amountReceived;
    private Double changeReturned;

    // billiing page end here

    // discount logic
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;
    // private BigDecimal taxableAmount = BigDecimal.ZERO;


    
}
