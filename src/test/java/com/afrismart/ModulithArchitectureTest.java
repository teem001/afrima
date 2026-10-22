package com.afrismart;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void modulesAreConsistent() {
        ApplicationModules.of(AfriSmartApplication.class).verify();
    }
}
