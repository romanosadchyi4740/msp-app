package com.romanosadchyi.smp.music_app;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Module boundaries (CLAUDE.md section 4). Needs no Spring context and no database.
 */
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.romanosadchyi.smp.music_app";

    static final ApplicationModules MODULES = ApplicationModules.of(MusicAppApplication.class);

    /**
     * Other modules use only a module's {@code external} named interface, and modules have no cycles.
     */
    @Test
    void verifyModularity() {
        MODULES.verify();
    }

    /**
     * Every top-level package becomes a module, so a stray package (such as the old {@code service} or
     * {@code model}) that uses only {@code external} types would pass {@link #verifyModularity()}; adding a module
     * means adding it here.
     */
    @Test
    void modulesAreTheKnownOnes() {
        assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .containsExactlyInAnyOrder("shared", "users", "storage", "music", "playlists", "chats", "telemetry",
                        "search", "tabs", "rooms");
    }

    /**
     * A feature module has only {@code internal} and {@code external} subpackages, so nothing sits in its base
     * package, which Spring Modulith would expose to every other module.
     */
    @ParameterizedTest
    @ValueSource(strings = {"users", "storage", "music", "playlists", "chats", "telemetry", "search", "tabs", "rooms"})
    void moduleHasOnlyInternalAndExternalPackages(String module) {
        String modulePackage = BASE_PACKAGE + "." + module;
        classes().that().resideInAPackage(modulePackage + "..")
                .should().resideInAnyPackage(modulePackage + ".internal..", modulePackage + ".external..")
                .allowEmptyShould(true)
                .check(importMainClasses());
    }

    /**
     * Which way dependencies point: {@link #verifyModularity()} allows any module's {@code external} as long as there
     * is no cycle, so each module lists the modules it may use (besides {@code shared}). Changing a list is a design
     * decision.
     */
    @ParameterizedTest
    @MethodSource("allowedDependencies")
    void moduleUsesOnlyItsAllowedModules(String module, List<String> allowed) {
        String[] packages = Stream.concat(Stream.of(module, "shared"), allowed.stream())
                .map(name -> BASE_PACKAGE + "." + name + "..")
                .toArray(String[]::new);
        DescribedPredicate<JavaClass> allowedClasses = resideOutsideOfPackage(BASE_PACKAGE + "..")
                .or(resideInAnyPackage(packages));
        classes().that().resideInAPackage(BASE_PACKAGE + "." + module + "..")
                .should().onlyDependOnClassesThat(allowedClasses)
                .allowEmptyShould(true)
                .check(importMainClasses());
    }

    static Stream<Arguments> allowedDependencies() {
        return Stream.of(
                Arguments.of("users", List.of()),
                Arguments.of("storage", List.of()),
                Arguments.of("music", List.of("users", "storage")),
                Arguments.of("playlists", List.of("users", "music")),
                Arguments.of("chats", List.of("users", "music", "storage")),
                Arguments.of("telemetry", List.of("users", "music")),
                Arguments.of("search", List.of("users", "music")),
                Arguments.of("tabs", List.of("users", "storage")),
                Arguments.of("rooms", List.of("users")));
    }

    @Test
    void onlyStorageUsesTheMinioClient() {
        noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".storage..")
                .should().dependOnClassesThat().resideInAPackage("io.minio..")
                .check(importMainClasses());
    }

    /**
     * {@code shared} is an open module that every module uses, so it must not use any of them.
     */
    @Test
    void sharedDependsOnNoFeatureCode() {
        classes().that().resideInAPackage(BASE_PACKAGE + ".shared..")
                .should().onlyDependOnClassesThat(
                        resideOutsideOfPackage(BASE_PACKAGE + "..").or(resideInAPackage(BASE_PACKAGE + ".shared..")))
                .check(importMainClasses());
    }

    private static JavaClasses importMainClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }
}
