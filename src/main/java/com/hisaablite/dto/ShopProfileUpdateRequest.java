package com.hisaablite.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShopProfileUpdateRequest {

    private String gstNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String upiId;
}