package com.pqixing.modularization.android

import com.pqixing.modularization.Keys
import org.gradle.api.Project
import org.gradle.api.Task;

/**
 * Created by pqixing on 17-12-7.
 */

class LibraryPlugin extends AndroidPlugin {
    @Override
    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected String getAndroidPlugin() {
        return Keys.NAME_LIBRARY
    }

    @Override
    Set<String> getIgnoreFields() {
        return ["scr/dev"]
    }

    @Override
    List<Class<? extends Task>> linkTask() {
        return null
    }
}
