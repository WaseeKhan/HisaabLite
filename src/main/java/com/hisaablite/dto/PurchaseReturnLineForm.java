package com.hisaablite.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseReturnLineForm {

    private Long purchaseBatchId;

    private Integer quantity;

    private String reason;
}
