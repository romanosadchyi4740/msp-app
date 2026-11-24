/**
 * What every module needs: security and web configuration, the authenticated principal, error handling and
 * small utilities. Open, so every module may use any of its types; it never depends on a feature module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.romanosadchyi.smp.music_app.shared;

import org.springframework.modulith.ApplicationModule;
