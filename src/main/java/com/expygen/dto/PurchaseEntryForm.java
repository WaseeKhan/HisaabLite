package com.expygen.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseEntryForm {

    private LocalDate purchaseDate;

    private String supplierName;

    private String supplierInvoiceNumber;

    private String notes;

    private List<PurchaseLineForm> items = new ArrayList<>();
}
