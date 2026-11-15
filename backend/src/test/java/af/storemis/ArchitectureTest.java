package af.storemis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Structural rules from docs/phase-1: exact money types and thin controllers. */
class ArchitectureTest {

    static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("af.storemis");
    }

    @Test
    void noFloatingPointFieldsInEntitiesOrDtos() {
        fields().that().areDeclaredInClassesThat().resideInAnyPackage("..entity..", "..dto..")
                .should().notHaveRawType(float.class).andShould().notHaveRawType(double.class)
                .andShould().notHaveRawType(Float.class).andShould().notHaveRawType(Double.class)
                .because("money and quantities must use BigDecimal / NUMERIC")
                .check(classes);
    }

    @Test
    void controllersDoNotUseRepositoriesDirectly() {
        noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("business logic and data access belong in services")
                .check(classes);
    }

    @Test
    void entitiesDoNotDependOnWebOrServices() {
        noClasses().that().resideInAPackage("..entity..").should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..")
                .check(classes);
    }
}
