/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.javascript.sonarlint;

import static org.sonar.plugins.javascript.sonarlint.SonarLintTypeCheckingFilter.FileFilter;
import static org.sonar.plugins.javascript.sonarlint.SonarLintTypeCheckingFilter.PathFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.plugins.javascript.utils.PathWalker;
import org.sonarsource.api.sonarlint.SonarLintSide;

@SonarLintSide(lifespan = "MODULE")
public class SonarLintTypeCheckingCheckerImpl implements SonarLintTypeCheckingChecker {

  private static final Logger LOG = Loggers.get(SonarLintTypeCheckingCheckerImpl.class);
  static final String MAX_FILES_PROPERTY = "sonar.javascript.sonarlint.typechecking.maxfiles";
  static final int DEFAULT_MAX_FILES_FOR_TYPE_CHECKING = 20_000;
  private static final int FILE_WALK_MAX_DEPTH = 20;

  private boolean beyondLimit = true;

  private boolean shouldCheck = true;

  public boolean isBeyondLimit() {
    return beyondLimit;
  }

  public void checkOnce(SensorContext context) {
    if (shouldCheck) {
      checkLimit(context);
      shouldCheck = false;
    }
  }

  private void checkLimit(SensorContext context) {
    try {
      var typeCheckingLimit = getTypeCheckingLimit(context);
      var projectSize = countProjectSize(context, typeCheckingLimit);

      beyondLimit = projectSize >= typeCheckingLimit;
      if (!beyondLimit) {
        LOG.info("Turning on type-checking of JavaScript files");
      } else {
        // TypeScript type checking mechanism creates performance issues for large projects. Analyzing a file can take more than a minute in
        // SonarLint, and it can even lead to runtime errors due to Node.js being out of memory during the process.
        LOG.warn(
          "Turning off type-checking of JavaScript files due to the project size exceeding the limit ({} files)",
          typeCheckingLimit
        );
        LOG.warn("This may cause rules dependent on type information to not behave as expected");
        LOG.warn(
          "Check the list of impacted rules at https://rules.sonarsource.com/javascript/tag/type-dependent"
        );
        LOG.warn(
          "To turn type-checking back on, increase the \"{}\" property value",
          MAX_FILES_PROPERTY
        );
        LOG.warn(
          "Please be aware that this could potentially impact the performance of the analysis"
        );
      }
    } catch (RuntimeException e) {
      // Any runtime error raised by the SonarLint API would be caught here to let the analyzer proceed with the rules that don't require
      // type checking.
      LOG.warn("Turning off type-checking of JavaScript files due to unexpected error", e);
    }
  }

  private static long countProjectSize(SensorContext context, long maxSize) {
    var fileFilter = new FileFilter(context.config());

    try (var files = walkProjectFiles(context)) {
      return files
        .filter(path -> Files.isRegularFile(path) && fileFilter.test(path))
        .limit(maxSize)
        .count();
    }
  }

  private static Stream<Path> walkProjectFiles(SensorContext context) {
    // The Files.walk() is failing on Windows with WSL (see https://bugs.openjdk.org/browse/JDK-8259617)
    return PathWalker.stream(
      context.fileSystem().baseDir().toPath(),
      FILE_WALK_MAX_DEPTH,
      new PathFilter(context.config())
    );
  }

  private static int getTypeCheckingLimit(SensorContext context) {
    return Math.max(
      context.config().getInt(MAX_FILES_PROPERTY).orElse(DEFAULT_MAX_FILES_FOR_TYPE_CHECKING),
      0
    );
  }
}
