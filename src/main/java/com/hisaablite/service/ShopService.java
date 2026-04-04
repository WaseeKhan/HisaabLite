package com.hisaablite.service;

import com.hisaablite.dto.ShopProfileUpdateRequest;
import com.hisaablite.admin.service.AuditService;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final AuditService auditService;

    @Transactional
    public void updateProfile(User loggedInUser,
            ShopProfileUpdateRequest request) {

        Shop shop = loggedInUser.getShop();
        Map<String, Object> oldProfile = snapshotShopProfile(shop);

        shop.setGstNumber(request.getGstNumber());
        shop.setAddress(request.getAddress());
        shop.setCity(request.getCity());
        shop.setState(request.getState());
        shop.setPincode(request.getPincode());
        shop.setUpiId(request.getUpiId());

        Shop savedShop = shopRepository.save(shop);
        auditService.logAction(
                loggedInUser.getUsername(),
                loggedInUser.getRole().name(),
                savedShop,
                "SHOP_PROFILE_UPDATED",
                "Shop",
                savedShop.getId(),
                "SUCCESS",
                oldProfile,
                snapshotShopProfile(savedShop),
                "Shop profile updated");
    }

    public Shop saveShop(Shop shop) {
    return shopRepository.save(shop);
}

    private Map<String, Object> snapshotShopProfile(Shop shop) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", shop.getId());
        snapshot.put("name", shop.getName());
        snapshot.put("gstNumber", shop.getGstNumber());
        snapshot.put("address", shop.getAddress());
        snapshot.put("city", shop.getCity());
        snapshot.put("state", shop.getState());
        snapshot.put("pincode", shop.getPincode());
        snapshot.put("upiId", shop.getUpiId());
        snapshot.put("whatsappNumber", shop.getWhatsappNumber());
        snapshot.put("whatsappConnected", shop.isWhatsappConnected());
        return snapshot;
    }
}
