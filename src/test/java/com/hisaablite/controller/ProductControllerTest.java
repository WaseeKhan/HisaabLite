package com.hisaablite.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.hisaablite.admin.service.AuditService;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Product;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.ProductRepository;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.PlanLimitService;
import com.hisaablite.service.ProductService;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductService productService;

    @Mock
    private PlanLimitService planLimitService;

    @Mock
    private AuditService auditService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ProductController productController;

    @Test
    void saveOrUpdateProductCreatesNewProductForCurrentShop() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product formProduct = Product.builder()
                .name("Tea")
                .description("Leaf tea")
                .price(new BigDecimal("99.00"))
                .stockQuantity(10)
                .gstPercent(5)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(planLimitService.getProductLimit(shop)).thenReturn(1000);
        when(planLimitService.canAddProduct(shop)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(55L);
            return saved;
        });

        String view = productController.saveOrUpdateProduct(
                formProduct,
                authentication,
                new RedirectAttributesModelMap());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product savedProduct = productCaptor.getValue();

        assertEquals("redirect:/products", view);
        assertEquals(shop, savedProduct.getShop());
        assertEquals(true, savedProduct.isActive());
        assertEquals(5, savedProduct.getMinStock());
        verify(auditService).logAction(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void saveOrUpdateProductUpdatesManagedEntityInsteadOfDetachedFormObject() {
        Shop shop = testShop();
        User owner = testOwner(shop);
        Product existingProduct = Product.builder()
                .name("Old Name")
                .description("Old")
                .price(new BigDecimal("45.00"))
                .stockQuantity(7)
                .minStock(2)
                .gstPercent(18)
                .shop(shop)
                .active(true)
                .build();
        existingProduct.setId(10L);
        existingProduct.setVersion(1L);

        Product submittedProduct = Product.builder()
                .id(10L)
                .name("New Name")
                .description("Updated")
                .price(new BigDecimal("60.00"))
                .stockQuantity(15)
                .minStock(3)
                .gstPercent(12)
                .build();

        when(authentication.getName()).thenReturn(owner.getUsername());
        when(userRepository.findByUsername(owner.getUsername())).thenReturn(Optional.of(owner));
        when(productRepository.findById(10L)).thenReturn(Optional.of(existingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String view = productController.saveOrUpdateProduct(
                submittedProduct,
                authentication,
                new RedirectAttributesModelMap());

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());

        Product savedProduct = productCaptor.getValue();
        assertEquals("redirect:/products", view);
        assertSame(existingProduct, savedProduct);
        assertEquals("New Name", existingProduct.getName());
        assertEquals(15, existingProduct.getStockQuantity());
        assertEquals(new BigDecimal("60.00"), existingProduct.getPrice());
        verify(planLimitService, never()).canAddProduct(shop);
    }

    private Shop testShop() {
        return Shop.builder()
                .id(1L)
                .name("Core Shop")
                .panNumber("ABCDE1234F")
                .planType(PlanType.PREMIUM)
                .active(true)
                .build();
    }

    private User testOwner(Shop shop) {
        return User.builder()
                .id(1L)
                .name("Owner")
                .username("owner@test.com")
                .phone("9999999999")
                .password("encoded")
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .approved(true)
                .currentPlan(PlanType.PREMIUM)
                .build();
    }
}
