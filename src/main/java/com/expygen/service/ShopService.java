package com.expygen.service;

import com.expygen.dto.ShopProfileUpdateRequest;
import com.expygen.admin.service.AuditService;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.UserRepository;

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
    private final UserRepository userRepository;

    
    @Transactional
    public void updateProfile(User loggedInUser,
            ShopProfileUpdateRequest request) {

        Shop shop = loggedInUser.getShop();
        Map<String, Object> oldProfile = snapshotShopProfile(shop);

        shop.setName(request.getShopName());
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
        snapshot.put("logoOriginalFilename", shop.getLogoOriginalFilename());
        snapshot.put("logoStoredFilename", shop.getLogoStoredFilename());
        snapshot.put("sealOriginalFilename", shop.getSealOriginalFilename());
        snapshot.put("sealStoredFilename", shop.getSealStoredFilename());
        snapshot.put("whatsappNumber", shop.getWhatsappNumber());
        snapshot.put("whatsappConnected", shop.isWhatsappConnected());
        return snapshot;
    }


    public Shop getShopByUsername(String username) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found: " + username));
    return user.getShop();
}
}
