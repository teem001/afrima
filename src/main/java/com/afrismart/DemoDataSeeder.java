package com.afrismart;

import java.time.LocalTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.afrismart.catalog.CatalogService;
import com.afrismart.catalog.CreateProductRequest;
import com.afrismart.catalog.ProductResponse;
import com.afrismart.identity.AuthService;
import com.afrismart.identity.RegisterRequest;
import com.afrismart.identity.UserAccountRepository;
import com.afrismart.identity.UserResponse;
import com.afrismart.inventory.InventoryService;
import com.afrismart.inventory.UpsertInventoryRequest;
import com.afrismart.shared.security.AuthUser;
import com.afrismart.shared.security.Role;
import com.afrismart.stores.StoreResponse;
import com.afrismart.stores.StoreService;
import com.afrismart.stores.UpsertStoreRequest;

@Component
@Order(1)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password123!";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserAccountRepository users;
    private final AuthService authService;
    private final CatalogService catalogService;
    private final StoreService storeService;
    private final InventoryService inventoryService;

    public DemoDataSeeder(
            UserAccountRepository users,
            AuthService authService,
            CatalogService catalogService,
            StoreService storeService,
            InventoryService inventoryService
    ) {
        this.users = users;
        this.authService = authService;
        this.catalogService = catalogService;
        this.storeService = storeService;
        this.inventoryService = inventoryService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        log.info("Seeding AfriSmart demo data (Lagos shops + pack of pens)");

        UserResponse consumer = register("ada@afrismart.ng", "Ada Okonkwo", "08030000001", Role.CONSUMER);
        UserResponse yabaMerchant = register("yaba@afrismart.ng", "Yaba Campus Mart", "08030000002", Role.MERCHANT);
        UserResponse surulereMerchant = register("surulere@afrismart.ng", "Surulere Stationers", "08030000003", Role.MERCHANT);
        UserResponse ikejaMerchant = register("ikeja@afrismart.ng", "Ikeja CV Mart", "08030000004", Role.MERCHANT);

        ProductResponse pens = catalogService.create(new CreateProductRequest(
                "PEN-PACK-10",
                "Pack of 10 ballpoint pens",
                "A pack of ten blue ballpoint pens",
                List.of("pack of pens", "biro", "biros", "pens", "pen pack")
        ));
        ProductResponse rice = catalogService.create(new CreateProductRequest(
                "RICE-50KG",
                "50kg bag of rice",
                "Staple 50kg rice bag",
                List.of("rice", "bag of rice")
        ));
        ProductResponse water = catalogService.create(new CreateProductRequest(
                "WATER-SACHET-20",
                "Pack of 20 sachet water",
                "Pure water sachet pack",
                List.of("sachet water", "pure water", "water")
        ));
        ProductResponse books = catalogService.create(new CreateProductRequest(
                "EXERCISE-80",
                "80-leaf exercise book",
                "Standard school exercise book",
                List.of("exercise book", "notebook")
        ));

        StoreResponse yaba = storeService.create(
                merchant(yabaMerchant),
                new UpsertStoreRequest(
                        "Yaba Campus Mart",
                        "Kiosk beside UNILAG / Yaba market",
                        "Yaba, Lagos",
                        6.5095,
                        3.3711,
                        LocalTime.of(8, 0),
                        LocalTime.of(20, 0),
                        4000
                )
        );
        StoreResponse surulere = storeService.create(
                merchant(surulereMerchant),
                new UpsertStoreRequest(
                        "Surulere Stationers",
                        "Preferred stationer — currently out of pens",
                        "Surulere, Lagos",
                        6.4992,
                        3.3556,
                        LocalTime.of(8, 0),
                        LocalTime.of(19, 0),
                        3500
                )
        );
        StoreResponse ikeja = storeService.create(
                merchant(ikejaMerchant),
                new UpsertStoreRequest(
                        "Ikeja Computer Village Mart",
                        "Electronics and stationery stall",
                        "Computer Village, Ikeja, Lagos",
                        6.6018,
                        3.3515,
                        LocalTime.of(9, 0),
                        LocalTime.of(21, 0),
                        6000
                )
        );

        stock(yabaMerchant, yaba.id(), pens.id(), 40, 75_000);
        stock(yabaMerchant, yaba.id(), rice.id(), 12, 6_200_000);
        stock(yabaMerchant, yaba.id(), water.id(), 80, 40_000);
        stock(yabaMerchant, yaba.id(), books.id(), 25, 35_000);

        stock(surulereMerchant, surulere.id(), pens.id(), 0, 70_000);
        stock(surulereMerchant, surulere.id(), rice.id(), 5, 6_400_000);
        stock(surulereMerchant, surulere.id(), water.id(), 20, 35_000);
        stock(surulereMerchant, surulere.id(), books.id(), 60, 40_000);

        stock(ikejaMerchant, ikeja.id(), pens.id(), 18, 90_000);
        stock(ikejaMerchant, ikeja.id(), books.id(), 10, 45_000);

        log.info(
                "Demo consumer {} / {} | search pack of pens near Yaba lat=6.5158 lng=3.3791",
                consumer.email(),
                DEMO_PASSWORD
        );
    }

    private UserResponse register(String email, String name, String phone, Role role) {
        return authService.register(new RegisterRequest(email, DEMO_PASSWORD, name, phone, role)).user();
    }

    private AuthUser merchant(UserResponse user) {
        return new AuthUser(user.id(), user.email(), user.role());
    }

    private void stock(UserResponse merchant, java.util.UUID storeId, java.util.UUID productId, int qty, long kobo) {
        inventoryService.upsert(merchant(merchant), storeId, new UpsertInventoryRequest(productId, qty, kobo));
    }
}
