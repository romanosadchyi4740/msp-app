package com.romanosadchyi.smp.music_app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Module boundaries (CLAUDE.md section 4). Needs no Spring context and no database.
 */
class ArchitectureTest {

    static final String BASE_PACKAGE = "com.romanosadchyi.smp.music_app";

    @Test
    @Disabled("KAN-64: enabled once every legacy package is extracted into a module")
    void verifyModularity() {
        ApplicationModules.of(MusicAppApplication.class).verify();
    }

    /**
     * Guards the extracted modules while {@link #verifyModularity()} is disabled: other code uses only their
     * {@code external} package.
     */
    @ParameterizedTest
    @ValueSource(strings = {"users", "storage", "music", "playlists", "chats", "telemetry"})
    void moduleInternalIsUsedOnlyByItsModule(String module) {
        noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + "." + module + "..")
                .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + "." + module + ".internal..")
                .check(importMainClasses());
    }

    /**
     * {@code music} depends on nothing that is not yet a module, so later extractions cannot create a cycle back
     * into it.
     */
    @Test
    void musicDependsOnlyOnModules() {
        classes().that().resideInAPackage(BASE_PACKAGE + ".music..")
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(BASE_PACKAGE + "..")
                        .or(resideInAnyPackage(BASE_PACKAGE + ".music..", BASE_PACKAGE + ".shared..",
                                BASE_PACKAGE + ".users.external..", BASE_PACKAGE + ".storage.external..")))
                .check(importMainClasses());
    }

    /**
     * Like {@link #musicDependsOnlyOnModules}: {@code playlists} reads tracks and users only through their lookups.
     */
    @Test
    void playlistsDependsOnlyOnModules() {
        classes().that().resideInAPackage(BASE_PACKAGE + ".playlists..")
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(BASE_PACKAGE + "..")
                        .or(resideInAnyPackage(BASE_PACKAGE + ".playlists..", BASE_PACKAGE + ".shared..",
                                BASE_PACKAGE + ".users.external..", BASE_PACKAGE + ".music.external..")))
                .check(importMainClasses());
    }

    /**
     * Like {@link #musicDependsOnlyOnModules}: {@code chats} reads users, tracks and files only through their
     * {@code external} interfaces.
     */
    @Test
    void chatsDependsOnlyOnModules() {
        classes().that().resideInAPackage(BASE_PACKAGE + ".chats..")
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(BASE_PACKAGE + "..")
                        .or(resideInAnyPackage(BASE_PACKAGE + ".chats..", BASE_PACKAGE + ".shared..",
                                BASE_PACKAGE + ".users.external..", BASE_PACKAGE + ".music.external..",
                                BASE_PACKAGE + ".storage.external..")))
                .check(importMainClasses());
    }

    /**
     * Like {@link #musicDependsOnlyOnModules}: {@code telemetry} reads users and tracks only through their lookups.
     */
    @Test
    void telemetryDependsOnlyOnModules() {
        classes().that().resideInAPackage(BASE_PACKAGE + ".telemetry..")
                .should().onlyDependOnClassesThat(resideOutsideOfPackage(BASE_PACKAGE + "..")
                        .or(resideInAnyPackage(BASE_PACKAGE + ".telemetry..", BASE_PACKAGE + ".shared..",
                                BASE_PACKAGE + ".users.external..", BASE_PACKAGE + ".music.external..")))
                .check(importMainClasses());
    }

    @Test
    void onlyStorageUsesTheMinioClient() {
        noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".storage..")
                .should().dependOnClassesThat().resideInAPackage("io.minio..")
                .check(importMainClasses());
    }

    /**
     * Guards {@code shared} while the legacy packages still exist and {@link #verifyModularity()} is disabled.
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
