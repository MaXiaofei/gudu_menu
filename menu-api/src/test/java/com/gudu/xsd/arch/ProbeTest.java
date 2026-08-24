package com.gudu.xsd.arch;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ProbeTest {
    @Test
    void probe() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.gudu.xsd");
        
        classes.get("com.gudu.xsd.modules.dish.DishService").getMethods().stream()
                .filter(mm -> mm.isAnnotatedWith("org.springframework.transaction.annotation.Transactional"))
                .findFirst()
                .ifPresent(mm -> {
                    JavaAnnotation<?> a = mm.getAnnotationOfType("org.springframework.transaction.annotation.Transactional");
                    System.out.println("props=" + a.getProperties().keySet());
                    System.out.println("rollbackFor=" + a.getProperties().get("rollbackFor"));
                });
    }
}
