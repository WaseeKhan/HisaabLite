package com.expygen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expygen.admin.service.AuditService;
import com.expygen.dto.ShopProfileUpdateRequest;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.ShopRepository;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ShopService shopService;

    @Test
    void updateProfileUpdatesShopFieldsAndWritesAudit() {
        Shop shop = Shop.builder()
                .id(8L)
                .name("My Shop")
                .panNumber("ABCDE1234F")
                .city("Lucknow")
                .state("UP")
                .planType(PlanType.BASIC)
                .build();

        User owner = User.builder()
                .id(1L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.BASIC)
                .build();

        ShopProfileUpdateRequest request = new ShopProfileUpdateRequest();
        request.setGstNumber("09ABCDE1234F1Z8");
        request.setAddress("Main Road");
        request.setCity("Kanpur");
        request.setState("UP");
        request.setPincode("226001");
        request.setUpiId("shop@upi");

        when(shopRepository.save(any(Shop.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shopService.updateProfile(owner, request);

        ArgumentCaptor<Shop> shopCaptor = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).save(shopCaptor.capture());

        Shop savedShop = shopCaptor.getValue();
        assertEquals("Kanpur", savedShop.getCity());
        assertEquals("Main Road", savedShop.getAddress());
        assertEquals("shop@upi", savedShop.getUpiId());
        verify(auditService).logAction(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
