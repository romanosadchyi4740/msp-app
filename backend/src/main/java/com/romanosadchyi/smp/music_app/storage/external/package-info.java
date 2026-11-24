/**
 * What other modules may use from {@code storage}: {@code FileStorage} and its exception. Which object store is
 * behind it (MinIO now, S3 in production) stays inside the module.
 */
@NamedInterface("external")
package com.romanosadchyi.smp.music_app.storage.external;

import org.springframework.modulith.NamedInterface;
