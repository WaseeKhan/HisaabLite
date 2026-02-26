package com.hisaablite.service;

import com.hisaablite.dto.ShopProfileUpdateRequest;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;

    @Transactional
    public void updateProfile(User loggedInUser,
                              ShopProfileUpdateRequest request) {

        Shop shop = loggedInUser.getShop();

        shop.setGstNumber(request.getGstNumber());
        shop.setAddress(request.getAddress());
        shop.setCity(request.getCity());
        shop.setState(request.getState());
        shop.setPincode(request.getPincode());
        shop.setUpiId(request.getUpiId());

        shopRepository.save(shop);
    }
}