package com.expygen.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseReturnForm {

    private LocalDate returnDate;

    private String notes;

    private List<PurchaseReturnLineForm> items = new ArrayList<>();
}
